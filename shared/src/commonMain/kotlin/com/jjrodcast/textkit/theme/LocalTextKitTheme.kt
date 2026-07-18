package com.jjrodcast.textkit.theme

import androidx.compose.runtime.staticCompositionLocalOf

internal val LocalTextKitTheme = staticCompositionLocalOf<TextKitTheme> {
    error("No TextKitTheme provided. Wrap your content in TextKitTheme { }")
}