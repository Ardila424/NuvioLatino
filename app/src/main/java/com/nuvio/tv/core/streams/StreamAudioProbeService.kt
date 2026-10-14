package com.nuvio.tv.core.streams

import android.media.MediaExtractor
import android.media.MediaFormat
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
        val detectedLanguages: List<String>
    ) : StreamAudioProbeResult() {
        val hasSpanish: Boolean get() = hasLatino || hasCastellano
    }
    data object Inconclusive : StreamAudioProbeResult()
}

data class ProbeAudioTrack(
    val language: String,
    val languageBcp47: String,
    val name: String,
    val codec: String
)

/**
 * Service that probes a focused stream's remote container header (EBML / MP4)
 * to verify actual embedded audio tracks without downloading the video payload.
 */
@Singleton
class StreamAudioProbeService @Inject constructor(
    private val directDebridResolver: DirectDebridResolver,
    @Named("addonPermissive") private val okHttpClient: OkHttpClient
) {
    private val probeCache = ConcurrentHashMap<String, StreamAudioProbeResult>()

    suspend fun probeStreamAudio(
        stream: Stream,
        season: Int? = null,
        episode: Int? = null
    ): StreamAudioProbeResult = withContext(Dispatchers.IO) {
        val cacheKey = stream.badgeMergeKey()
        probeCache[cacheKey]?.let { return@withContext it }

        val playableUrl = resolvePlayableUrl(stream, season, episode)
        if (playableUrl.isNullOrBlank()) {
            return@withContext StreamAudioProbeResult.Inconclusive
        }

        val result = withTimeoutOrNull(4500L) {
            runCatching {
                // 1. Fast path: Fetch the first 256 KB of the video container header
                val headerBytes = fetchHeaderBytes(playableUrl)
                if (headerBytes != null && isEbmlHeader(headerBytes)) {
                    val tracks = parseMkvAudioTracks(headerBytes)
                    if (tracks.isNotEmpty()) {
                        Log.d(TAG, "Parsed ${tracks.size} audio tracks directly from MKV header for ${stream.name}")
                        return@runCatching evaluateAudioTracks(tracks)
                    }
                }

                // 2. Fallback: MediaExtractor
                probeWithMediaExtractor(playableUrl)
            }.getOrDefault(StreamAudioProbeResult.Inconclusive)
        } ?: StreamAudioProbeResult.Inconclusive

        if (result is StreamAudioProbeResult.Success) {
            probeCache[cacheKey] = result
        }
        result
    }

    private fun isEbmlHeader(bytes: ByteArray): Boolean =
        bytes.size >= 4 &&
            bytes[0] == 0x1A.toByte() &&
            bytes[1] == 0x45.toByte() &&
            bytes[2] == 0xDF.toByte() &&
            bytes[3] == 0xA3.toByte()

    private fun fetchHeaderBytes(url: String): ByteArray? {
        return runCatching {
            val request = Request.Builder()
                .url(url)
                .header("Range", "bytes=0-262143")
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful && response.code != 206) return@use null
                val source = response.body?.source() ?: return@use null
                val buffer = Buffer()
                val bytesToRead = 262144L
                var totalRead = 0L
                while (totalRead < bytesToRead && !source.exhausted()) {
                    val read = source.read(buffer, bytesToRead - totalRead)
                    if (read == -1L) break
                    totalRead += read
                }
                buffer.readByteArray()
            }
        }.getOrNull()
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
                            codec = codec.trim()
                        )
                    )
                }
            }
            if (elEnd <= pos) break
            pos = elEnd
        }
        return tracks
    }

    internal fun evaluateAudioTracks(tracks: List<ProbeAudioTrack>): StreamAudioProbeResult {
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
            detectedLanguages = distinctDetected
        )
    }

    private fun probeWithMediaExtractor(playableUrl: String): StreamAudioProbeResult {
        return runCatching {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(playableUrl)
                val trackCount = extractor.trackCount
                val tracks = mutableListOf<ProbeAudioTrack>()

                for (i in 0 until trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                    if (mime.startsWith("audio/")) {
                        val lang = if (format.containsKey(MediaFormat.KEY_LANGUAGE)) {
                            format.getString(MediaFormat.KEY_LANGUAGE).orEmpty()
                        } else ""
                        val title = if (format.containsKey("title")) {
                            format.getString("title").orEmpty()
                        } else ""
                        tracks.add(
                            ProbeAudioTrack(
                                language = lang,
                                languageBcp47 = if (lang.contains("-")) lang else "",
                                name = title,
                                codec = mime
                            )
                        )
                    }
                }
                evaluateAudioTracks(tracks)
            } finally {
                extractor.release()
            }
        }.getOrDefault(StreamAudioProbeResult.Inconclusive)
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
}
