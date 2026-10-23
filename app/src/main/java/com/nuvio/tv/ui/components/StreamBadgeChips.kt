package com.nuvio.tv.ui.components

import com.nuvio.tv.ui.theme.NuvioTheme

import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.ImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.allowRgb565
import coil3.request.crossfade
import coil3.size.Precision
import com.nuvio.tv.domain.model.StreamBadge
import okio.Path.Companion.toOkioPath
import kotlin.math.round

/**
 * Shared badge ImageRequest cache. Badges with the same URL and target decode
 * dimensions share a single [ImageRequest] instance across all composables in
 * the stream list. This avoids per-item allocations of builder objects and
 * intermediate strings that pressure the GC on low-memory TV devices.
 *
 * The cache is keyed by the Coil memory-cache key (url + dimensions) and is
 * bounded by the number of unique badge images (typically 10-30 in practice).
 * Entries are never evicted — they live for the process lifetime which is fine
 * because they hold no bitmaps, only request metadata.
 */
private val badgeImageRequestCache = HashMap<String, ImageRequest>(32)

private var badgeImageLoader: ImageLoader? = null

private fun getBadgeImageLoader(context: android.content.Context): ImageLoader {
    badgeImageLoader?.let { return it }
    val loader = ImageLoader.Builder(context.applicationContext)
        .memoryCache {
            MemoryCache.Builder()
                .maxSizePercent(context.applicationContext, 0.05)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(context.applicationContext.cacheDir.resolve("badge_cache").toOkioPath())
                .maxSizeBytes(50L * 1024 * 1024)
                .build()
        }
        .precision(Precision.INEXACT)
        .crossfade(false)
        .allowHardware(true)
        .allowRgb565(true)
        .build()
    badgeImageLoader = loader
    return loader
}

/** Shared shape instance — all badge chips use the same corner radius. */
private val BadgeChipShape = RoundedCornerShape(6.dp)

/** Marquee scroll speed — matches FocusMarqueeText velocity for visual consistency. */
private val MarqueeVelocity = 45.dp

@Composable
fun StreamBadgeChips(
    badges: List<StreamBadge>,
    fileSizeBytes: Long? = null,
    showFileSizeBadge: Boolean = false,
    animate: Boolean = false,
    focused: Boolean = false,
    modifier: Modifier = Modifier
) {
    val visibleBadges = remember(badges) { badges.filter { it.imageURL.isNotBlank() || it.name.isNotBlank() } }
    val sizeBytes = fileSizeBytes.takeIf { showFileSizeBadge }
    if (visibleBadges.isEmpty() && sizeBytes == null) return

    val chipAlpha = if (animate) {
        val alpha = remember { androidx.compose.animation.core.Animatable(0f) }
        androidx.compose.runtime.LaunchedEffect(Unit) {
            alpha.animateTo(1f, animationSpec = androidx.compose.animation.core.tween(200))
        }
        alpha.value
    } else {
        1f
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
            .then(if (focused) Modifier.basicMarquee(iterations = Int.MAX_VALUE, velocity = MarqueeVelocity, spacing = MarqueeSpacing(36.dp)) else Modifier)
            .then(if (chipAlpha < 1f) Modifier.alpha(chipAlpha) else Modifier),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.wrapContentWidth(align = Alignment.Start, unbounded = true),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.xs)
        ) {
            visibleBadges.forEach { badge ->
                if (badge.imageURL.isNotBlank()) {
                    StreamImportedBadgeChip(badge = badge)
                } else {
                    StreamTextBadgeChip(badge = badge)
                }
            }
            if (sizeBytes != null) {
                StreamFileSizeBadge(bytes = sizeBytes)
            }
        }
    }
}

/**
 * Compact inline badge strip used inside the stream card title row. Keeps the
 * whole card height unchanged while surfacing the release's technical marks.
 */
@Composable
fun StreamInlineBadges(
    badges: List<StreamBadge>,
    fileSizeBytes: Long? = null,
    showFileSizeBadge: Boolean = false,
    maxVisible: Int = INLINE_BADGE_LIMIT,
    startIndex: Int = 0,
    showOverflow: Boolean = true,
    modifier: Modifier = Modifier
) {
    val gbTemplate = stringResource(R.string.unit_size_gb)
    val mbTemplate = stringResource(R.string.unit_size_mb)
    val sizeBadge = remember(fileSizeBytes, showFileSizeBadge, gbTemplate, mbTemplate) {
        if (!showFileSizeBadge || fileSizeBytes == null) return@remember null
        val gib = fileSizeBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
        val label = if (gib >= 1.0) {
            gbTemplate.format((round(gib * 10.0) / 10.0).toString())
        } else {
            mbTemplate.format(round(fileSizeBytes.toDouble() / (1024.0 * 1024.0)).toInt().toString())
        }
        StreamBadge(
            name = label,
            tagStyle = "tech-size",
            textColor = "#D1D5DB",
            borderColor = "#4B5563"
        )
    }
    val ordered = remember(badges, sizeBadge) {
        (badges + listOfNotNull(sizeBadge)).sortedBy(::compactBadgePriority)
    }
    if (ordered.isEmpty() || startIndex >= ordered.size) return

    val visible = ordered.drop(startIndex).take(maxVisible)
    val overflow = if (showOverflow && startIndex == 0) ordered.size - visible.size else 0

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        visible.forEach { badge -> StreamBadgeMark(badge) }
        if (overflow > 0) {
            StreamMetaBadge(label = "+$overflow")
        }
    }
}

internal const val INLINE_BADGE_LIMIT = 4

/** Shared compact mark: brand wordmarks, verified provenance and imported images. */
@Composable
internal fun StreamBadgeMark(
    badge: StreamBadge,
    modifier: Modifier = Modifier,
    badgeHeight: Dp = 17.dp,
    fontSize: TextUnit = 9.sp,
    horizontalPadding: Dp = 4.dp
) {
    if (badge.imageURL.isNotBlank()) {
        StreamImportedBadgeChip(badge = badge, modifier = modifier)
        return
    }

    val label = compactBadgeLabel(badge)
    if (label.isBlank()) return
    val (background, foreground, outline) = compactBadgeColors(badge)
    val shape = RoundedCornerShape(4.dp)

    Box(
        modifier = modifier
            .height(badgeHeight)
            .clip(shape)
            .then(
                if (background != Color.Transparent) Modifier.background(background, shape)
                else Modifier
            )
            .then(
                if (outline != Color.Transparent) Modifier.border(1.dp, outline, shape)
                else Modifier
            )
            .padding(horizontal = horizontalPadding),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = fontSize,
                lineHeight = fontSize * 1.1f,
                letterSpacing = 0.2.sp
            ),
            color = foreground,
            maxLines = 1
        )
    }
}

@Composable
private fun StreamMetaBadge(label: String) {
    Box(
        modifier = Modifier
            .height(17.dp)
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, Color(0xFF4B5563), RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                lineHeight = 10.sp
            ),
            color = Color(0xFFD1D5DB),
            maxLines = 1
        )
    }
}

private fun compactBadgePriority(badge: StreamBadge): Int = when {
    badge.tagStyle == "tech-lang" -> 0
    badge.name.contains("LATINO (Verificado)") -> 1
    badge.tagStyle == "tech-4k" -> 2
    badge.tagStyle == "tech-dv" -> 3
    badge.tagStyle == "tech-hdr" -> 4
    badge.tagStyle == "tech-atmos" -> 5
    badge.name.contains("REMUX") -> 6
    badge.tagStyle == "tech-dts" -> 7
    badge.tagStyle == "tech-audio" -> 8
    badge.tagStyle == "tech-size" -> 9
    badge.name == "🇲🇽 LATINO" -> 10
    badge.name.startsWith("🚫 Sin Latino") -> 11
    badge.tagStyle == "tech-meta" -> 12
    else -> 20
}

private fun compactBadgeLabel(badge: StreamBadge): String = when {
    badge.name.contains("LATINO (Verificado)") -> "LATINO ✓"
    badge.name == "🇲🇽 LATINO" -> "LATINO ?"
    badge.name.startsWith("🚫 Sin Latino") -> "SIN LATINO"
    badge.name.contains("REMUX") -> "REMUX"
    badge.name.contains("CASTELLANO") -> "ESPAÑOL"
    badge.name.contains("MULTI") -> "MULTI"
    else -> badge.name
}

private fun compactBadgeColors(badge: StreamBadge): Triple<Color, Color, Color> = when {
    badge.tagStyle == "tech-lang" -> Triple(Color(0xFF0F766E), Color.White, Color(0xFF14B8A6))
    badge.name.contains("LATINO (Verificado)") -> Triple(Color(0xFF059669), Color.White, Color(0xFF10B981))
    badge.name == "🇲🇽 LATINO" -> Triple(Color.Transparent, Color(0xFFF59E0B), Color(0xFFF59E0B))
    badge.name.startsWith("🚫 Sin Latino") -> Triple(Color.Transparent, Color(0xFF9CA3AF), Color(0xFF4B5563))
    badge.name.contains("REMUX") -> Triple(Color(0xFF6D28D9), Color.White, Color(0xFF8B5CF6))
    badge.name.contains("CASTELLANO") -> Triple(Color(0xFFB45309), Color.White, Color(0xFFF59E0B))
    badge.name.contains("MULTI") -> Triple(Color(0xFF1D4ED8), Color.White, Color(0xFF3B82F6))
    badge.tagStyle == "tech-4k" || badge.tagStyle == "tech-dv" ->
        Triple(Color(0xFF000000), Color.White, Color.White)
    badge.tagStyle == "tech-hdr" ->
        Triple(Color(0xFF0B0B0C), Color.White, Color(0xFF9CA3AF))
    badge.tagStyle == "tech-atmos" || badge.tagStyle == "tech-dts" ->
        Triple(Color(0xFF0B0B0C), Color.White, Color(0xFF6B7280))
    badge.tagStyle == "tech-audio" || badge.tagStyle == "tech-meta" || badge.tagStyle == "tech-size" ->
        Triple(Color.Transparent, Color(0xFFD1D5DB), Color(0xFF4B5563))
    else -> Triple(
        badge.tagColor.toBadgeColorOrNull() ?: Color.White.copy(alpha = 0.08f),
        badge.textColor.toBadgeColorOrNull() ?: Color.White,
        badge.borderColor.toBadgeColorOrNull() ?: Color.White.copy(alpha = 0.18f)
    )
}

@Composable
private fun StreamTextBadgeChip(badge: StreamBadge) {
    val backgroundColor = remember(badge.tagColor) {
        badge.tagColor.toBadgeColorOrNull() ?: Color(0xFF1E293B)
    }
    val outlineColor = remember(badge.borderColor, backgroundColor) {
        badge.borderColor.toBadgeColorOrNull() ?: backgroundColor
    }
    val textColor = remember(badge.textColor) {
        badge.textColor.toBadgeColorOrNull() ?: Color.White
    }

    Box(
        modifier = Modifier
            .height(20.dp)
            .clip(BadgeChipShape)
            .background(backgroundColor, BadgeChipShape)
            .border(NuvioTheme.spacing.hairline, outlineColor, BadgeChipShape)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = badge.name,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = textColor,
            maxLines = 1
        )
    }
}

@Composable
private fun StreamFileSizeBadge(bytes: Long) {
    val gbTemplate = stringResource(R.string.unit_size_gb)
    val mbTemplate = stringResource(R.string.unit_size_mb)
    val label = remember(bytes, gbTemplate, mbTemplate) {
        val gib = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
        if (gib >= 1.0) {
            val roundedGiB = round(gib * 10.0) / 10.0
            gbTemplate.format(roundedGiB.toString())
        } else {
            val mib = bytes.toDouble() / (1024.0 * 1024.0)
            mbTemplate.format(round(mib).toInt().toString())
        }
    }
    Box(
        modifier = Modifier
            .height(20.dp)
            .clip(BadgeChipShape)
            .background(Color(0xFF0A0C0C), BadgeChipShape)
            .border(NuvioTheme.spacing.hairline, Color(0xFF0A0C0C), BadgeChipShape)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.streams_size, label),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White
        )
    }
}

@Composable
internal fun StreamImportedBadgeChip(
    badge: StreamBadge,
    crossfade: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val backgroundColor = remember(badge.tagColor, badge.tagStyle) {
        badge.tagColor.toBadgeColorOrNull()
            ?.takeIf { badge.tagStyle.equals("filled", ignoreCase = true) }
    }
    val outlineColor = remember(badge.borderColor) {
        badge.borderColor.toBadgeColorOrNull()
    }
    // Pre-upscale: decode at 2× target pixels so the hardware compositor
    // has enough pixel data for smooth edges inside Card RenderNodes.
    val decodeHeight = remember(density) {
        with(density) { NuvioTheme.spacing.lg.roundToPx() } * 2
    }
    // Use a wide max-width to let Coil decode at aspect ratio constrained by height.
    val decodeWidth = remember(density) {
        with(density) { 92.dp.roundToPx() } * 2
    }
    // Reuse ImageRequest instances across composables via a shared HashMap.
    // This avoids creating a new Builder + intermediate strings per badge per
    // recomposition and ensures Coil's internal equality checks hit the same
    // object reference — preventing redundant decode dispatches.
    val cacheKey = "${badge.imageURL}_${decodeWidth}x${decodeHeight}"
    val imageRequest = remember(cacheKey) {
        badgeImageRequestCache.getOrPut(cacheKey) {
            ImageRequest.Builder(context)
                .data(badge.imageURL)
                .size(width = decodeWidth, height = decodeHeight)
                .memoryCacheKey(cacheKey)
                .diskCacheKey(badge.imageURL)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .crossfade(false)
                .build()
        }
    }
    val chipModifier = Modifier
        .height(20.dp)
        .then(if (backgroundColor != null) Modifier.background(backgroundColor, BadgeChipShape) else Modifier)
        .then(if (outlineColor != null) Modifier.border(NuvioTheme.spacing.hairline, outlineColor, BadgeChipShape) else Modifier)

    Box(
        modifier = modifier
            .then(chipModifier)
            .padding(horizontal = 3.dp, vertical = NuvioTheme.spacing.xxs),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = imageRequest,
            imageLoader = getBadgeImageLoader(context),
            contentDescription = badge.name,
            modifier = Modifier
                .height(NuvioTheme.spacing.lg)
                .widthIn(min = 34.dp, max = 92.dp)
                .clip(BadgeChipShape),
            contentScale = ContentScale.Fit
        )
    }
}

private fun String.toBadgeColorOrNull(): Color? {
    val hex = trim().removePrefix("#")
    val argb = when (hex.length) {
        6 -> "FF$hex"
        8 -> hex
        else -> return null
    }
    return argb.toLongOrNull(16)?.let { Color(it) }
}
