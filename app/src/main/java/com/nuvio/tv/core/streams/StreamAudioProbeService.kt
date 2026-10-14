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
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

sealed class StreamAudioProbeResult {
    data class Success(
        val hasLatinoOrSpanish: Boolean,
        val detectedLanguages: List<String>
    ) : StreamAudioProbeResult()
    object Inconclusive : StreamAudioProbeResult()
}

/**
 * Service that probes a focused stream's remote container header (EBML / MP4)
 * to verify actual embedded audio tracks without downloading the video payload.
 */
@Singleton
class StreamAudioProbeService @Inject constructor(
    private val directDebridResolver: DirectDebridResolver
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
                val extractor = MediaExtractor()
                try {
                    extractor.setDataSource(playableUrl)
                    val trackCount = extractor.trackCount
                    val languages = mutableListOf<String>()
                    var hasSpanish = false

                    for (i in 0 until trackCount) {
                        val format = extractor.getTrackFormat(i)
                        val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                        if (mime.startsWith("audio/")) {
                            val lang = if (format.containsKey(MediaFormat.KEY_LANGUAGE)) {
                                format.getString(MediaFormat.KEY_LANGUAGE)?.lowercase()
                            } else null

                            val trackName = if (format.containsKey("title")) {
                                format.getString("title")?.lowercase()
                            } else null

                            if (!lang.isNullOrBlank()) {
                                languages.add(lang)
                                if (isSpanishLanguageCode(lang) || trackName?.contains("latino") == true) {
                                    hasSpanish = true
                                }
                            } else if (trackName != null && (trackName.contains("latino") || trackName.contains("spanish") || trackName.contains("español"))) {
                                languages.add("lat")
                                hasSpanish = true
                            }
                        }
                    }
                    StreamAudioProbeResult.Success(
                        hasLatinoOrSpanish = hasSpanish,
                        detectedLanguages = languages.distinct()
                    )
                } finally {
                    extractor.release()
                }
            }.getOrNull()
        } ?: StreamAudioProbeResult.Inconclusive

        if (result is StreamAudioProbeResult.Success) {
            probeCache[cacheKey] = result
        }
        result
    }

    private fun isSpanishLanguageCode(code: String): Boolean {
        val normalized = code.lowercase()
        return normalized == "spa" || normalized == "es" || normalized == "es-419" || normalized == "la" || normalized == "lat"
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
