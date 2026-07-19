package com.jjrodcast.textkit.theme

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Carries the active [TextKitTheme] down the composition. Provided by the [TextKitTheme] composable
 * and read through the `TextKitTheme` accessor (`TextKitTheme.colors` / `.typography` / `.shapes`).
 *
 * `static` because the theme rarely changes: when it does, the whole subtree recomposes rather than
 * tracking individual reads. It has no default value — reading it outside a [TextKitTheme] provider
 * throws, surfacing a missing provider instead of silently rendering an unthemed UI.
 */
internal val LocalTextKitTheme = staticCompositionLocalOf<TextKitTheme> {
    error("No TextKitTheme provided. Wrap your content in TextKitTheme { }")
}