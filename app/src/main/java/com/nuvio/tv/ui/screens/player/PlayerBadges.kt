package com.nuvio.tv.ui.screens.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.tv.core.streams.StreamBadgeMatcher
import com.nuvio.tv.core.util.AppLanguage
import com.nuvio.tv.domain.model.StreamBadge
import com.nuvio.tv.ui.components.StreamBadgeMark
import com.nuvio.tv.ui.util.languageCodeToName

@Composable
internal fun rememberPlayerBadges(viewModel: PlayerViewModel, uiState: PlayerUiState): List<StreamBadge> {
    val filters = uiState.playerBadgeFilters
    var mediaInfo by remember { mutableStateOf(PlayerMediaInfo()) }
    val selectedAudio = uiState.audioTracks.firstOrNull { it.isSelected }
    LaunchedEffect(
        filters,
        uiState.showControls,
        uiState.showPauseOverlay,
        uiState.isBuffering,
        uiState.currentStreamUrl,
        uiState.internalPlayerEngine,
        selectedAudio?.index
    ) {
        if ((uiState.showControls || uiState.showPauseOverlay) && !uiState.isBuffering) {
            mediaInfo = viewModel.playerMediaInfo()
        }
    }

    val latinoFeaturesEnabled = AppLanguage.latinoFeaturesEnabled(LocalConfiguration.current)
    val techBadges = remember(mediaInfo, selectedAudio, latinoFeaturesEnabled) {
        buildPlayerTechBadges(mediaInfo, selectedAudio, showLanguageBadge = latinoFeaturesEnabled)
    }
    val importedBadges = if (filters.isEmpty()) {
        emptyList()
    } else {
        val language = selectedAudio?.language
        val text = playbackMatchText(
            mediaInfo,
            listOfNotNull(language, language?.let(::languageCodeToName))
        )
        remember(text, filters, latinoFeaturesEnabled) {
            StreamBadgeMatcher.matchedBadges(listOfNotNull(text.takeIf { it.isNotBlank() }), filters)
                .filter { latinoFeaturesEnabled || !it.isForkLanguageBadge() }
        }
    }

    return remember(techBadges, importedBadges) {
        (techBadges + importedBadges).distinctBy { it.name }
    }
}

private fun StreamBadge.isForkLanguageBadge(): Boolean =
    name.contains("LATINO", ignoreCase = true) ||
        name.contains("CASTELLANO", ignoreCase = true) ||
        name.contains("ESPAÑOL", ignoreCase = true) ||
        name.contains("SPANISH", ignoreCase = true)

/**
 * Badges built from the *actual* playing tracks, not from the release name.
 * Solid marks therefore mean "the player is really decoding this".
 */
private fun buildPlayerTechBadges(
    info: PlayerMediaInfo,
    audio: TrackInfo?,
    showLanguageBadge: Boolean
): List<StreamBadge> {
    val badges = mutableListOf<StreamBadge>()
    fun add(name: String, style: String, background: String = "", foreground: String = "#FFFFFF", border: String = "") {
        if (badges.any { it.name == name }) return
        badges += StreamBadge(
            name = name,
            tagStyle = style,
            tagColor = background,
            textColor = foreground,
            borderColor = border
        )
    }

    val width = info.videoWidth ?: 0
    val height = info.videoHeight ?: 0
    when {
        width >= 3800 || height >= 2100 -> add("4K", "tech-4k", "#000000", "#FFFFFF", "#FFFFFF")
        width >= 2500 || height >= 1400 -> add("1440p", "tech-meta", foreground = "#D1D5DB", border = "#4B5563")
        width >= 1900 || height >= 1000 -> add("1080p", "tech-meta", foreground = "#D1D5DB", border = "#4B5563")
        width >= 1260 || height >= 700 -> add("720p", "tech-meta", foreground = "#D1D5DB", border = "#4B5563")
        height > 0 -> add("${height}p", "tech-meta", foreground = "#D1D5DB", border = "#4B5563")
    }

    if ("DV" in info.hdr) add("DV", "tech-dv", "#000000", "#FFFFFF", "#FFFFFF")
    if ("HDR10" in info.hdr) add("HDR10", "tech-hdr", "#0B0B0C", "#FFFFFF", "#FFFFFF")
    if ("HLG" in info.hdr) add("HLG", "tech-hdr", "#0B0B0C", "#FFFFFF", "#6B7280")

    val audioText = listOfNotNull(info.audioCodec, info.audioProfile, audio?.codec)
        .joinToString(" ")
        .uppercase()
    when {
        audioText.contains("ATMOS") || audioText.contains("JOC") ->
            add("ATMOS", "tech-atmos", "#0B0B0C", "#FFFFFF", "#FFFFFF")
        audioText.contains("DTS:X") || audioText.contains("DTSX") ->
            add("DTS:X", "tech-dts", "#0B0B0C", "#FFFFFF", "#6B7280")
        audioText.contains("DTS-HD MA") || audioText.contains("DTSHD MA") ->
            add("DTS-HD MA", "tech-dts", "#0B0B0C", "#FFFFFF", "#6B7280")
        audioText.contains("DTS-HD") || audioText.contains("DTSHD") ->
            add("DTS-HD", "tech-dts", "#0B0B0C", "#FFFFFF", "#6B7280")
        audioText.contains("TRUEHD") ->
            add("TrueHD", "tech-audio", foreground = "#D1D5DB", border = "#4B5563")
        audioText.contains("E-AC-3") || audioText.contains("EAC3") ||
            audioText.contains("DDP") || audioText.contains("DD+") ->
            add("DD+", "tech-audio", foreground = "#D1D5DB", border = "#4B5563")
        audioText.contains("AC-3") || audioText.contains("AC3") ->
            add("DD", "tech-audio", foreground = "#D1D5DB", border = "#4B5563")
        audioText.contains("AAC") ->
            add("AAC", "tech-audio", foreground = "#D1D5DB", border = "#4B5563")
        audioText.contains("DTS") ->
            add("DTS", "tech-dts", "#0B0B0C", "#FFFFFF", "#6B7280")
    }

    val channels = info.audioChannels ?: audio?.channelCount
    when (channels) {
        8 -> add("7.1", "tech-meta", foreground = "#D1D5DB", border = "#4B5563")
        6 -> add("5.1", "tech-meta", foreground = "#D1D5DB", border = "#4B5563")
    }

    if (showLanguageBadge) {
        playerLanguageLabel(audio)?.let { language ->
            add(language, "tech-lang", "#0F766E", "#FFFFFF", "#14B8A6")
        }
    }

    return badges
}

private val LATINO_LANGUAGE_CODES = setOf(
    "es-419", "es-005", "es-mx", "es-ar", "es-co", "es-ve", "es-cl", "es-pe",
    "es-ec", "es-uy", "es-py", "es-bo", "es-do", "es-cr", "es-gt", "es-hn",
    "es-ni", "es-pa", "es-pr", "es-sv", "es-cu", "la", "lat", "latino"
)

private fun playerLanguageLabel(audio: TrackInfo?): String? {
    val code = audio?.language?.lowercase()?.trim().orEmpty()
    val name = audio?.name?.lowercase().orEmpty()
    if (code.isEmpty() && name.isEmpty()) return null

    val isLatinoNamed = name.contains("latino") || name.contains("latinoam")
    val isCastellanoNamed = name.contains("castellano")
    val base = code.substringBefore('-')

    return when {
        isLatinoNamed || code in LATINO_LANGUAGE_CODES -> "ESPAÑOL (LATINO)"
        isCastellanoNamed || base == "es" || base == "spa" -> "ESPAÑOL"
        base == "en" || base == "eng" -> "ENGLISH"
        base == "pt" || base == "por" -> "PORTUGUÊS"
        base == "fr" || base == "fra" || base == "fre" -> "FRANÇAIS"
        base == "de" || base == "deu" || base == "ger" -> "DEUTSCH"
        base == "it" || base == "ita" -> "ITALIANO"
        base == "ja" || base == "jpn" -> "日本語"
        base == "ko" || base == "kor" -> "한국어"
        code.isNotEmpty() -> languageCodeToName(code).uppercase()
        else -> null
    }
}

@Composable
internal fun PlayerBadges(
    badges: List<StreamBadge>,
    modifier: Modifier = Modifier
) {
    Layout(
        content = {
            badges.forEach {
                StreamBadgeMark(
                    badge = it,
                    badgeHeight = 21.dp,
                    fontSize = 10.sp,
                    horizontalPadding = 5.dp
                )
            }
        },
        modifier = modifier
    ) { measurables, constraints ->
        val gap = 4.dp.roundToPx()
        val placeables = measurables.map { it.measure(Constraints(maxWidth = constraints.maxWidth)) }
        var width = 0
        var visibleCount = 0
        for (placeable in placeables) {
            val next = if (visibleCount == 0) placeable.width else width + gap + placeable.width
            if (next > constraints.maxWidth) break
            width = next
            visibleCount++
        }
        val visible = placeables.take(visibleCount)
        val height = visible.maxOfOrNull { it.height } ?: 0
        layout(width, height) {
            var x = 0
            visible.forEach { placeable ->
                placeable.placeRelative(x, (height - placeable.height) / 2)
                x += placeable.width + gap
            }
        }
    }
}
