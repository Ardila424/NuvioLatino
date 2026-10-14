package com.nuvio.tv.core.streams

import com.nuvio.tv.data.local.StreamBadgeSettingsDataStore
import com.nuvio.tv.domain.model.AddonStreams
import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.domain.model.StreamBadge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StreamBadgePresentation @Inject constructor(
    private val dataStore: StreamBadgeSettingsDataStore
) {
    private val badgeFilterCache = AtomicReference<Pair<StreamBadgeRules, List<CompiledStreamBadgeFilter>>?>()

    private val badgeInstancePool = AtomicReference<HashMap<String, StreamBadge>>(HashMap(64))

    suspend fun apply(groups: List<AddonStreams>, latinTitles: List<String> = emptyList()): List<AddonStreams> {
        return withContext(Dispatchers.Default) {
            apply(groups, dataStore.settings.first().rules, latinTitles)
        }
    }

    fun apply(groups: List<AddonStreams>, rules: StreamBadgeRules, latinTitles: List<String> = emptyList()): List<AddonStreams> {
        val filters = getBadgeFilters(rules)
        val pool = badgeInstancePool.get()
        return groups.map { group ->
            group.copy(
                streams = group.streams.map { stream ->
                    val nativeBadges = NativeStreamBadgeDetector.detectBadges(stream, latinTitles)
                    val matchedBadges = if (filters.isNotEmpty()) {
                        StreamBadgeMatcher.matchedBadgesPooled(stream, filters, pool)
                    } else {
                        emptyList()
                    }
                    val combinedBadges = nativeBadges + matchedBadges
                    if (combinedBadges.isEmpty()) {
                        stream
                    } else {
                        stream.copy(badges = mergeBadges(stream.badges, combinedBadges, pool))
                    }
                }
            )
        }
    }

    private fun getBadgeFilters(rules: StreamBadgeRules): List<CompiledStreamBadgeFilter> {
        val normalized = rules.normalized()
        val cached = badgeFilterCache.get()
        if (cached?.first == normalized) return cached.second
        val compiled = StreamBadgeMatcher.compile(normalized)
        // Rebuild the badge instance pool from compiled filters so all
        // matching operations reuse these canonical instances.
        val newPool = HashMap<String, StreamBadge>(compiled.size * 2)
        compiled.forEach { filter ->
            val key = filter.badge.dedupeKey()
            newPool.putIfAbsent(key, filter.badge)
        }
        badgeInstancePool.set(newPool)
        badgeFilterCache.set(normalized to compiled)
        return compiled
    }

    private fun mergeBadges(
        existing: List<StreamBadge>,
        matched: List<StreamBadge>,
        pool: HashMap<String, StreamBadge>
    ): List<StreamBadge> {
        val all = existing + matched
        val hasVerifiedLatino = all.any { it.name.contains("LATINO (Verificado)") }
        val hasNoLatino = all.any { it.name.startsWith("🚫 Sin Latino") }

        val capacity = existing.size + matched.size
        val merged = LinkedHashMap<String, StreamBadge>(capacity)
        for (badge in all) {
            if (hasNoLatino && badge.name.contains("LATINO", ignoreCase = true) && !badge.name.contains("Sin Latino", ignoreCase = true)) {
                continue
            }
            if (hasVerifiedLatino && badge.name == "🇲🇽 LATINO") {
                continue
            }
            val key = badge.dedupeKey()
            if (key !in merged) merged[key] = pool.getOrPut(key) { badge }
        }
        return merged.values.toList()
    }
}
