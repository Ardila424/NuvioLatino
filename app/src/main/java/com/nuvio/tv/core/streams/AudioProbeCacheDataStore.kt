package com.nuvio.tv.core.streams

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.data.local.ProfileDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataStore-backed [AudioProbeCache]. One JSON string per probe cache key, with
 * a hard entry cap and LRU eviction by `lastUsedMs`.
 *
 * Writes are coalesced: a burst of probe results (the batch probe finishes up to
 * 8 streams within a couple of seconds) is buffered and flushed as a single
 * DataStore edit. DataStore rewrites the whole preferences file per edit, so
 * batching keeps IO proportional to list loads rather than to probe count.
 */
@Singleton
class AudioProbeCacheDataStore @Inject constructor(
    private val factory: ProfileDataStoreFactory,
    private val profileManager: ProfileManager
) : AudioProbeCache {

    /** Pref-key name -> serialized entry, waiting for the next flush. */
    private val pending = LinkedHashMap<String, String>()
    private val pendingLock = Any()
    private val flushMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var flushJob: Job? = null

    private fun store() = factory.get(profileManager.activeProfileId.value, FEATURE)

    override suspend fun get(cacheKey: String): StreamAudioProbeResult? {
        val prefKeyName = prefKeyName(cacheKey)
        val now = System.currentTimeMillis()

        // Buffered writes are newer than the persisted copy.
        val buffered = synchronized(pendingLock) { pending[prefKeyName] }
        if (buffered != null) {
            val entry = parseEntry(buffered) ?: return null
            return entry.takeIf { isUsable(it, now) }?.toResult()
        }

        val key = stringPreferencesKey(prefKeyName)
        val prefs = store().data.first()
        val raw = prefs[key] ?: return null
        val entry = parseEntry(raw)

        if (entry == null || !isUsable(entry, now)) {
            store().edit { mutable -> mutable.remove(key) }
            return null
        }

        // Refresh LRU timestamp through the batched buffer (never a synchronous
        // whole-file rewrite on a cache hit).
        if (now - entry.lastUsedMs > LAST_USED_WRITE_THRESHOLD_MS) {
            synchronized(pendingLock) {
                pending[prefKeyName] = entry.copy(lastUsedMs = now).toJson()
                scheduleFlushLocked()
            }
        }

        return entry.toResult()
    }

    override suspend fun put(cacheKey: String, result: StreamAudioProbeResult) {
        if (result is StreamAudioProbeResult.RateLimited) return

        val now = System.currentTimeMillis()
        val json = result.toEntry(now).toJson()
        val prefKeyName = prefKeyName(cacheKey)

        synchronized(pendingLock) {
            pending[prefKeyName] = json
            scheduleFlushLocked()
        }
    }

    private fun isUsable(entry: Entry, nowMs: Long): Boolean =
        entry.parserVersion == AudioProbeCachePolicy.PARSER_VERSION &&
            AudioProbeCachePolicy.isFresh(
                savedAtMs = entry.savedAtMs,
                inconclusive = entry.kind == KIND_INCONCLUSIVE,
                nowMs = nowMs
            )

    private fun scheduleFlushLocked() {
        if (flushJob?.isActive == true) return
        flushJob = scope.launch {
            try {
                delay(FLUSH_DEBOUNCE_MS)
                drainPending()
            } finally {
                synchronized(pendingLock) {
                    flushJob = null
                    // A put that landed while we were flushing must not be stranded.
                    if (pending.isNotEmpty()) scheduleFlushLocked()
                }
            }
        }
    }

    private suspend fun drainPending() {
        while (true) {
            val batch = synchronized(pendingLock) {
                if (pending.isEmpty()) return
                val copy = pending.toMap()
                pending.clear()
                copy
            }

            flushMutex.withLock {
                store().edit { prefs ->
                    batch.forEach { (key, json) ->
                        prefs[stringPreferencesKey(key)] = json
                    }
                    evictLocked(prefs)
                }
            }
        }
    }

    private fun evictLocked(prefs: MutablePreferences) {
        val probeEntries = prefs.asMap().entries
            .filter { it.key.name.startsWith(PREF_PREFIX) }
            .map { stored ->
                stringPreferencesKey(stored.key.name) to
                    (parseEntry(stored.value as? String)?.lastUsedMs ?: 0L)
            }
        if (probeEntries.size <= AudioProbeCachePolicy.MAX_ENTRIES) return

        val overflow = probeEntries.size - AudioProbeCachePolicy.MAX_ENTRIES
        probeEntries
            .sortedBy { it.second }
            .take(overflow)
            .forEach { (storedKey, _) -> prefs.remove(storedKey) }
    }

    private fun prefKeyName(cacheKey: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(cacheKey.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "$PREF_PREFIX$digest"
    }

    private fun StreamAudioProbeResult.toEntry(now: Long): Entry = when (this) {
        is StreamAudioProbeResult.Success -> Entry(
            kind = KIND_VERIFIED,
            tracks = tracks,
            hasLatino = hasLatino,
            hasCastellano = hasCastellano,
            detectedLanguages = detectedLanguages,
            provenance = provenance,
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
        const val FLUSH_DEBOUNCE_MS = 1_500L

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
