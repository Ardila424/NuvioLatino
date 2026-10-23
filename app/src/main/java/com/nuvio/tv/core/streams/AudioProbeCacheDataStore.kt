package com.nuvio.tv.core.streams

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.data.local.ProfileDataStoreFactory
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataStore-backed [AudioProbeCache]. One JSON string per probe cache key, with
 * a hard entry cap and LRU eviction by `lastUsedMs`.
 */
@Singleton
class AudioProbeCacheDataStore @Inject constructor(
    private val factory: ProfileDataStoreFactory,
    private val profileManager: ProfileManager
) : AudioProbeCache {

    private fun store() = factory.get(profileManager.activeProfileId.value, FEATURE)

    override suspend fun get(cacheKey: String): StreamAudioProbeResult? {
        val prefKey = prefKey(cacheKey)
        val prefs = store().data.first()
        val raw = prefs[prefKey] ?: return null
        val entry = parseEntry(raw)
        val now = System.currentTimeMillis()

        val expired = entry == null ||
            entry.parserVersion != AudioProbeCachePolicy.PARSER_VERSION ||
            !AudioProbeCachePolicy.isFresh(
                savedAtMs = entry.savedAtMs,
                inconclusive = entry.kind == KIND_INCONCLUSIVE,
                nowMs = now
            )

        if (expired) {
            store().edit { mutable -> mutable.remove(prefKey) }
            return null
        }

        // Refresh LRU timestamp, but not on every single hit (avoid useless IO).
        if (now - entry.lastUsedMs > LAST_USED_WRITE_THRESHOLD_MS) {
            store().edit { mutable -> mutable[prefKey] = entry.copy(lastUsedMs = now).toJson() }
        }

        return entry.toResult()
    }

    override suspend fun put(cacheKey: String, result: StreamAudioProbeResult) {
        if (result is StreamAudioProbeResult.RateLimited) return

        val now = System.currentTimeMillis()
        val entry = when (result) {
            is StreamAudioProbeResult.Success -> Entry(
                kind = KIND_VERIFIED,
                tracks = result.tracks,
                hasLatino = result.hasLatino,
                hasCastellano = result.hasCastellano,
                detectedLanguages = result.detectedLanguages,
                provenance = result.provenance,
                parserVersion = AudioProbeCachePolicy.PARSER_VERSION,
                savedAtMs = now,
                lastUsedMs = now
            )
            else -> Entry(
                kind = KIND_INCONCLUSIVE,
                tracks = emptyList(),
                hasLatino = false,
                hasCastellano = false,
                detectedLanguages = emptyList(),
                provenance = ProbeProvenance.NAME_HEURISTIC,
                parserVersion = AudioProbeCachePolicy.PARSER_VERSION,
                savedAtMs = now,
                lastUsedMs = now
            )
        }

        val prefKey = prefKey(cacheKey)
        store().edit { prefs ->
            prefs[prefKey] = entry.toJson()

            val probeEntries = prefs.asMap().entries
                .filter { it.key.name.startsWith(PREF_PREFIX) }
                .map { stored ->
                    stringPreferencesKey(stored.key.name) to
                        (parseEntry(stored.value as? String)?.lastUsedMs ?: 0L)
                }
            if (probeEntries.size > AudioProbeCachePolicy.MAX_ENTRIES) {
                val overflow = probeEntries.size - AudioProbeCachePolicy.MAX_ENTRIES
                probeEntries
                    .sortedBy { it.second }
                    .take(overflow)
                    .forEach { (storedKey, _) -> prefs.remove(storedKey) }
            }
        }
    }

    private fun prefKey(cacheKey: String): Preferences.Key<String> {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(cacheKey.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return stringPreferencesKey("$PREF_PREFIX$digest")
    }

    private data class Entry(
        val kind: String,
        val tracks: List<ProbeAudioTrack>,
        val hasLatino: Boolean,
        val hasCastellano: Boolean,
        val detectedLanguages: List<String>,
        val provenance: ProbeProvenance,
        val parserVersion: Int,
        val savedAtMs: Long,
        val lastUsedMs: Long
    ) {
        fun toJson(): String = JSONObject().apply {
            put("kind", kind)
            put("hasLatino", hasLatino)
            put("hasCastellano", hasCastellano)
            put("detectedLanguages", JSONArray(detectedLanguages))
            put("provenance", provenance.name)
            put("parserVersion", parserVersion)
            put("savedAtMs", savedAtMs)
            put("lastUsedMs", lastUsedMs)
            if (kind == KIND_VERIFIED) {
                put("tracks", JSONArray().apply {
                    tracks.forEach { track ->
                        put(JSONObject().apply {
                            put("language", track.language)
                            put("languageBcp47", track.languageBcp47)
                            put("name", track.name)
                            put("codec", track.codec)
                            track.channels?.let { put("channels", it) }
                            put("isDefault", track.isDefault)
                            put("isForced", track.isForced)
                        })
                    }
                })
            }
        }.toString()

        fun toResult(): StreamAudioProbeResult {
            if (kind == KIND_INCONCLUSIVE) return StreamAudioProbeResult.Inconclusive
            return StreamAudioProbeResult.Success(
                hasLatino = hasLatino,
                hasCastellano = hasCastellano,
                detectedLanguages = detectedLanguages,
                tracks = tracks,
                provenance = provenance
            )
        }
    }

    private companion object {
        const val FEATURE = "audio_probe_cache"
        const val PREF_PREFIX = "probe_"
        const val KIND_VERIFIED = "verified"
        const val KIND_INCONCLUSIVE = "inconclusive"
        const val LAST_USED_WRITE_THRESHOLD_MS = 60_000L

        fun parseEntry(raw: String?): Entry? {
            if (raw.isNullOrBlank()) return null
            return runCatching {
                val json = JSONObject(raw)

                val tracksArray = json.optJSONArray("tracks")
                val tracks = buildList {
                    if (tracksArray != null) {
                        for (i in 0 until tracksArray.length()) {
                            val track = tracksArray.optJSONObject(i) ?: continue
                            add(
                                ProbeAudioTrack(
                                    language = track.optString("language"),
                                    languageBcp47 = track.optString("languageBcp47"),
                                    name = track.optString("name"),
                                    codec = track.optString("codec"),
                                    channels = if (track.has("channels")) track.optInt("channels") else null,
                                    isDefault = track.optBoolean("isDefault", false),
                                    isForced = track.optBoolean("isForced", false)
                                )
                            )
                        }
                    }
                }

                val detectedArray = json.optJSONArray("detectedLanguages")
                val detected = buildList {
                    if (detectedArray != null) {
                        for (i in 0 until detectedArray.length()) {
                            add(detectedArray.optString(i))
                        }
                    }
                }

                val provenanceName = json.optString("provenance", "")
                val provenance = ProbeProvenance.values()
                    .firstOrNull { it.name == provenanceName }
                    ?: ProbeProvenance.NAME_HEURISTIC

                Entry(
                    kind = json.optString("kind", KIND_VERIFIED),
                    tracks = tracks,
                    hasLatino = json.optBoolean("hasLatino", false),
                    hasCastellano = json.optBoolean("hasCastellano", false),
                    detectedLanguages = detected,
                    provenance = provenance,
                    parserVersion = json.optInt("parserVersion", 0),
                    savedAtMs = json.optLong("savedAtMs", 0L),
                    lastUsedMs = json.optLong("lastUsedMs", 0L)
                )
            }.getOrNull()
        }
    }
}
