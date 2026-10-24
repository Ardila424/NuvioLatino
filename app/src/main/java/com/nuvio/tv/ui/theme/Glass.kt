package com.nuvio.tv.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Global switch for the soft glass focus chrome. Real backdrop blur stays opt-in per surface. */
val LocalGlassEffectsEnabled = staticCompositionLocalOf { true }

/**
 * Subtle synthetic glass: a top specular sheen, a bottom shade and a hairline edge.
 * Deliberately cheap: two gradients and one outline. No per-card backdrop sampling.
 */
fun Modifier.nuvioGlassChrome(
    shape: Shape,
    focused: Boolean = false,
    enabled: Boolean = true
): Modifier {
    if (!enabled) return this
    val topAlpha = if (focused) 0.20f else 0.10f
    val edgeAlpha = if (focused) 0.42f else 0.20f
    val bottomAlpha = if (focused) 0.20f else 0.12f

    return drawWithCache {
        val topSheen = Brush.verticalGradient(
            colorStops = arrayOf(
                0f to Color.White.copy(alpha = topAlpha),
                0.28f to Color.White.copy(alpha = topAlpha * 0.22f),
                0.52f to Color.Transparent
            ),
            startY = 0f,
            endY = size.height
        )
        val bottomShade = Brush.verticalGradient(
            colorStops = arrayOf(
                0f to Color.Transparent,
                1f to Color.Black.copy(alpha = bottomAlpha)
            ),
            startY = size.height * 0.55f,
            endY = size.height
        )
        val edge = Brush.verticalGradient(
            colorStops = arrayOf(
                0f to Color.White.copy(alpha = edgeAlpha),
                0.5f to Color.White.copy(alpha = edgeAlpha * 0.25f),
                1f to Color.Black.copy(alpha = 0.18f)
            )
        )
        val outline = shape.createOutline(size, layoutDirection, this)

        onDrawWithContent {
            drawContent()
            drawRect(brush = topSheen, size = size)
            drawRect(
                brush = bottomShade,
                topLeft = Offset(0f, size.height * 0.55f),
                size = Size(size.width, size.height * 0.45f)
            )
            drawOutline(
                outline = outline,
                brush = edge,
                style = Stroke(width = 1.dp.toPx())
            )
        }
    }
}
