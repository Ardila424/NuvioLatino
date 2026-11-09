package com.nuvio.tv.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Non-destructive overrides for the active profile. When enabled, the home
 * and card surfaces skip the expensive visual work that can make a low-end
 * TV stutter, without rewriting the user's saved design settings.
 */
val LocalForceSmoothMode = staticCompositionLocalOf { false }
