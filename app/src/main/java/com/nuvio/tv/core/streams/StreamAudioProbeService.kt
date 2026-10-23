package com.nuvio.tv.core.streams

import android.util.Log
import com.nuvio.tv.core.debrid.DirectDebridResolveResult
import com.nuvio.tv.core.debrid.DirectDebridResolver
import com.nuvio.tv.domain.model.Stream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

private const val TAG = "StreamAudioProbeService"

sealed class StreamAudioProbeResult {
    data class Success(
        val hasLatino: Boolean,
        val hasCastellano: Boolean,
        val detectedLanguages: List<String>,
        val tracks: List<ProbeAudioTrack> = emptyList(),
        val provenance: ProbeProvenance = ProbeProvenance.MKV_EBML
    ) : StreamAudioProbeResult() {
        val hasSpanish: Boolean get() = hasLatino || hasCastellano
    }

    data object Inconclusive : StreamAudioProbeResult()

    /**
     * The host asked us to back off (429/403/5xx) or we are still inside the
     * service cooldown. The caller must stop the batch instead of retrying.
     */
    data class RateLimited(
        val httpCode: Int,
        val retryAfterMs: Long?
    ) : StreamAudioProbeResult()
}

data class ProbeAudioTrack(
    val language: String,
    val languageBcp47: String,
    val name: String,
    val codec: String,
    val channels: Int? = null,
    val isDefault: Boolean = false,
    val isForced: Boolean = false
)

/**
 * Probes a remote container header (EBML / future MP4 / HLS) to verify the
 * embedded audio tracks without downloading the video payload.
 *
 * Safety rules:
 * - header-only; no [android.media.MediaExtractor] on remote URLs (it can pull
 *   the whole file on non-faststart MP4s and burn debrid quota);
 * - a single 256 KB range request per probe;
 * - a 429/403/5xx puts the whole service into cooldown so the rest of the batch
 *   stops instead of hammering the host.
 */
@Singleton
class StreamAudioProbeService @Inject constructor(
    private val directDebridResolver: DirectDebridResolver,
    @Named("addonPermissive") private val okHttpClient: OkHttpClient
) {
    private val probeCache = ConcurrentHashMap<String, StreamAudioProbeResult>()

    @Volatile
    private var cooldownUntilMs: Long = 0L

    suspend fun probeStreamAudio(
        stream: Stream,
        season: Int? = null,
        episode: Int? = null
    ): StreamAudioProbeResult = withContext(Dispatchers.IO) {
        val cacheKey = stream.badgeMergeKey()
        probeCache[cacheKey]?.let { return@withContext it }

        remainingCooldownMs()?.let { remaining ->
            return@withContext StreamAudioProbeResult.RateLimited(
                httpCode = 429,
                retryAfterMs = remaining
            )
        }

        val playableUrl = resolvePlayableUrl(stream, season, episode)
        if (playableUrl.isNullOrBlank()) {
            return@withContext StreamAudioProbeResult.Inconclusive
        }

        val result = withTimeoutOrNull(PROBE_TIMEOUT_MS) {
            probeUrl(playableUrl)
        } ?: StreamAudioProbeResult.Inconclusive

        // Cache every terminal outcome. Inconclusive results are cached too so
        // focus events don't re-probe the same stream over and over. RateLimited
        // is host-wide and is handled by the cooldown instead.
        if (result !is StreamAudioProbeResult.RateLimited) {
            probeCache[cacheKey] = result
        }
        result
    }

    private fun remainingCooldownMs(): Long? {
        val remaining = cooldownUntilMs - System.currentTimeMillis()
        return remaining.takeIf { it > 0L }
    }

    private fun probeUrl(url: String): StreamAudioProbeResult {
        val fetch = fetchHeaderBytes(url)
        fetch.rateLimited?.let { return it }
        val headerBytes = fetch.bytes ?: return StreamAudioProbeResult.Inconclusive

        if (isEbmlHeader(headerBytes)) {
            val tracks = parseMkvAudioTracks(headerBytes)
            if (tracks.isNotEmpty()) {
                Log.d(TAG, "Parsed ${tracks.size} audio tracks from MKV header")
                return evaluateAudioTracks(tracks, ProbeProvenance.MKV_EBML)
            }
        }

        return StreamAudioProbeResult.Inconclusive
    }

    private fun isEbmlHeader(bytes: ByteArray): Boolean =
        bytes.size >= 4 &&
            bytes[0] == 0x1A.toByte() &&
            bytes[1] == 0x45.toByte() &&
            bytes[2] == 0xDF.toByte() &&
            bytes[3] == 0xA3.toByte()

    private data class HeaderFetch(
        val bytes: ByteArray?,
        val rateLimited: StreamAudioProbeResult.RateLimited? = null
    )

    private fun fetchHeaderBytes(url: String): HeaderFetch {
        // Derive a client with a hard call timeout so a stalled CDN cannot keep
        // an IO thread (and a debrid range request) alive past the probe budget.
        // newBuilder() shares the dispatcher and connection pool with the
        // injected client, so this is cheap.
        val probeClient = okHttpClient.newBuilder()
            .callTimeout(PROBE_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()
        return runCatching {
            val request = Request.Builder()
                .url(url)
                .header("Range", "bytes=0-${HEADER_RANGE_BYTES - 1}")
                .build()
            probeClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val outcome = ProbeHttpPolicy.classify(response.code)
                    if (ProbeHttpPolicy.stopsBatch(outcome)) {
                        val cooldown = ProbeHttpPolicy.cooldownMs(
                            outcome = outcome,
                            retryAfterHeader = response.header("Retry-After")
                        )
                        cooldownUntilMs = System.currentTimeMillis() + cooldown
                        Log.w(TAG, "Probe stopped: HTTP ${response.code}, cooldown ${cooldown}ms")
                        return HeaderFetch(
                            bytes = null,
                            rateLimited = StreamAudioProbeResult.RateLimited(
                                httpCode = response.code,
                                retryAfterMs = cooldown
                            )
                        )
                    }
                    return HeaderFetch(bytes = null)
                }

                val source = response.body.source()
                val buffer = Buffer()
                var totalRead = 0L
                while (totalRead < HEADER_RANGE_BYTES && !source.exhausted()) {
                    val read = source.read(buffer, HEADER_RANGE_BYTES - totalRead)
                    if (read == -1L) break
                    totalRead += read
                }
                HeaderFetch(bytes = buffer.readByteArray())
            }
        }.getOrElse { error ->
            Log.d(TAG, "Header fetch failed: ${error.message}")
            HeaderFetch(bytes = null)
        }
    }

    fun parseMkvAudioTracks(buffer: ByteArray): List<ProbeAudioTrack> {
        val tracks = mutableListOf<ProbeAudioTrack>()
        val tracksId = byteArrayOf(0x16.toByte(), 0x54.toByte(), 0xAE.toByte(), 0x6B.toByte())
        var idx = 0
        var tracksPos = -1
        while (idx < buffer.size - 4) {
            val found = indexOf(buffer, tracksId, idx)
            if (found == -1) break
            // Skip SeekHead SeekID (0x53 0xAB 0x84 0x16 0x54 0xAE 0x6B)
            if (found >= 3 && buffer[found - 3] == 0x53.toByte() && buffer[found - 2] == 0xAB.toByte()) {
                idx = found + 4
                continue
            }
            tracksPos = found
            break
        }
        if (tracksPos == -1) return emptyList()

        val (size, vlen) = readVint(buffer, tracksPos + 4) ?: return emptyList()
        val end = minOf(buffer.size.toLong(), tracksPos.toLong() + 4 + vlen + size).toInt()
        var pos = tracksPos + 4 + vlen

        while (pos < end) {
            val (elId, idLen) = readElementId(buffer, pos) ?: break
            val (elSize, sizeLen) = readVint(buffer, pos + idLen) ?: break
            val contentPos = pos + idLen + sizeLen
            val elEnd = minOf(buffer.size.toLong(), contentPos.toLong() + elSize).toInt()

            if (elId == 0xAE) { // TrackEntry
                var tPos = contentPos
                var tType = 0
                var lang = ""
                var bcp = ""
                var name = ""
                var codec = ""
                var channels: Int? = null
                var isDefault = false
                var isForced = false

                while (tPos < elEnd) {
                    val (subId, subIdLen) = readElementId(buffer, tPos) ?: break
                    val (subSize, subSizeLen) = readVint(buffer, tPos + subIdLen) ?: break
                    val subContent = tPos + subIdLen + subSizeLen
                    if (subContent > buffer.size) break
                    val subLen = minOf(subSize.toInt(), buffer.size - subContent)
                    if (subLen < 0) break

                    when (subId) {
                        0x83 -> { // TrackType
                            var v = 0
                            for (i in 0 until subLen) {
                                v = (v shl 8) or (buffer[subContent + i].toInt() and 0xFF)
                            }
                            tType = v
                        }
                        0x22B59C -> { // Language
                            lang = String(buffer, subContent, subLen, Charsets.ISO_8859_1)
                        }
                        0x22B59D -> { // LanguageBCP47
                            bcp = String(buffer, subContent, subLen, Charsets.UTF_8)
                        }
                        0x536E -> { // Name
                            name = String(buffer, subContent, subLen, Charsets.UTF_8)
                        }
                        0x86 -> { // CodecID
                            codec = String(buffer, subContent, subLen, Charsets.US_ASCII)
                        }
                        0x9F -> { // Channels
                            var v = 0
                            for (i in 0 until subLen) {
                                v = (v shl 8) or (buffer[subContent + i].toInt() and 0xFF)
                            }
                            channels = v.takeIf { it > 0 }
                        }
                        // FlagDefault (0x88) and FlagForced (0x55AA) are uint elements
                        0x88 -> {
                            isDefault = subLen > 0 && buffer[subContent] != 0.toByte()
                        }
                        0x55AA -> {
                            isForced = subLen > 0 && buffer[subContent] != 0.toByte()
                        }
                    }
                    val nextTPos = subContent.toLong() + subSize
                    if (nextTPos <= tPos || nextTPos > elEnd) break
                    tPos = nextTPos.toInt()
                }

                if (tType == 2) { // Audio track
                    tracks.add(
                        ProbeAudioTrack(
                            language = lang.trim(),
                            languageBcp47 = bcp.trim(),
                            name = name.trim(),
                            codec = codec.trim(),
                            channels = channels,
                            isDefault = isDefault,
                            isForced = isForced
                        )
                    )
                }
            }
            if (elEnd <= pos) break
            pos = elEnd
        }
        return tracks
    }

    internal fun evaluateAudioTracks(
        tracks: List<ProbeAudioTrack>,
        provenance: ProbeProvenance = ProbeProvenance.MKV_EBML
    ): StreamAudioProbeResult {
        if (tracks.isEmpty()) return StreamAudioProbeResult.Inconclusive

        val latinoCodes = setOf(
            "es-419", "es-mx", "es-ar", "es-co", "es-cl", "es-pe", "es-ve", "es-ec",
            "es-gt", "es-cr", "es-pa", "es-do", "es-us", "es-la", "es-latam", "lat", "la"
        )
        val castellanoCodes = setOf("es-es")
        val spanishCodes = setOf("spa", "es", "esl")

        var hasLatino = false
        var hasCastellano = false
        val detected = mutableListOf<String>()

        for (track in tracks) {
            val bcp = track.languageBcp47.lowercase()
            val lang = track.language.lowercase()
            val name = track.name.lowercase()

            val displayCode = bcp.ifBlank { lang }.ifBlank { "und" }
            if (displayCode != "und") {
                detected.add(displayCode)
            }

            val isLatino = bcp in latinoCodes ||
                lang in latinoCodes ||
                bcp.startsWith("es-419") ||
                bcp.startsWith("es-mx") ||
                bcp.startsWith("es-ar") ||
                bcp.startsWith("es-co") ||
                name.contains("latino") ||
                name.contains("latam") ||
                name.contains("latin america") ||
                name.contains("latinoamericano") ||
                name.contains("latina") ||
                name.contains("es-419") ||
                name.contains("audio latino")

            val isExplicitCastellano = bcp in castellanoCodes ||
                bcp.startsWith("es-es") ||
                name.contains("castellano") ||
                name.contains("españa") ||
                name.contains("espana") ||
                name.contains("castilian")

            val isGenericSpanish = !isLatino && !isExplicitCastellano && (
                lang in spanishCodes || bcp.startsWith("es") ||
                    name.contains("español") || name.contains("espanol") || name.contains("spanish")
                )

            if (isLatino) {
                hasLatino = true
            }
            if (isExplicitCastellano || isGenericSpanish) {
                hasCastellano = true
            }
        }

        val distinctDetected = detected.distinct()
        if (distinctDetected.isEmpty() && !hasCastellano && !hasLatino) {
            return StreamAudioProbeResult.Inconclusive
        }

        return StreamAudioProbeResult.Success(
            hasLatino = hasLatino,
            hasCastellano = hasCastellano,
            detectedLanguages = distinctDetected,
            tracks = tracks,
            provenance = provenance
        )
    }

    private fun readElementId(buffer: ByteArray, offset: Int): Pair<Int, Int>? {
        if (offset >= buffer.size) return null
        val b0 = buffer[offset].toInt() and 0xFF
        var mask = 0x80
        var vlen = 1
        while ((b0 and mask) == 0 && vlen <= 4) {
            mask = mask shr 1
            vlen++
        }
        if (vlen > 4 || offset + vlen > buffer.size) return null
        var id = 0
        for (i in 0 until vlen) {
            id = (id shl 8) or (buffer[offset + i].toInt() and 0xFF)
        }
        return Pair(id, vlen)
    }

    private fun readVint(buffer: ByteArray, offset: Int): Pair<Long, Int>? {
        if (offset >= buffer.size) return null
        val b0 = buffer[offset].toInt() and 0xFF
        var mask = 0x80
        var vlen = 1
        while ((b0 and mask) == 0 && vlen <= 8) {
            mask = mask shr 1
            vlen++
        }
        if (vlen > 8 || offset + vlen > buffer.size) return null
        var value = (b0 and (mask - 1)).toLong()
        for (i in 1 until vlen) {
            value = (value shl 8) or ((buffer[offset + i].toInt() and 0xFF).toLong())
        }
        return Pair(value, vlen)
    }

    private fun indexOf(source: ByteArray, target: ByteArray, fromIndex: Int): Int {
        if (target.isEmpty() || fromIndex >= source.size) return -1
        outer@ for (i in fromIndex..(source.size - target.size)) {
            for (j in target.indices) {
                if (source[i + j] != target[j]) continue@outer
            }
            return i
        }
        return -1
    }

    private suspend fun resolvePlayableUrl(
        stream: Stream,
        season: Int?,
        episode: Int?
    ): String? {
        val directUrl = stream.getStreamUrl()
        if (!directUrl.isNullOrBlank()) return directUrl

        if (directDebridResolver.shouldResolveToPlayableStream(stream)) {
            val resolveResult = directDebridResolver.resolve(stream, season, episode)
            if (resolveResult is DirectDebridResolveResult.Success) {
                return resolveResult.url
            }
        }
        return null
    }

    private companion object {
        /** 256 KB header window. */
        const val HEADER_RANGE_BYTES = 262_144L

        /** Per-probe wall clock budget. */
        const val PROBE_TIMEOUT_MS = 4_500L
    }
}
