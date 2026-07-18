package com.jjrodcast.textkit.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class TextKitColors(
    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val onSecondary: Color,
    val surface: Color,
    val onSurface: Color,
    val error: Color,
    val onError: Color,
) {

    companion object {

        fun light(): TextKitColors {
            return TextKitColors(
                primary = Color(0xFF046B5C),
                onPrimary = Color(0xFFFFFFFF),
                secondary = Color(0xFF4A635D),
                onSecondary = Color(0xFFFFFFFF),
                surface = Color(0xFFF5FBF7),
                onSurface = Color(0xFF171D1B),
                error = Color(0xFFBA1A1A),
                onError = Color(0xFFFFFFFF)
            )
        }

        fun dark(): TextKitColors {
            return TextKitColors(
                primary = Color(0xFF84D6C3),
                onPrimary = Color(0xFF00382F),
                secondary = Color(0xFFB1CCC4),
                onSecondary = Color(0xFF1C352F),
                surface = Color(0xFF0E1513),
                onSurface = Color(0xFFDEE4E1),
                error = Color(0xFFFFB4AB),
                onError = Color(0xFF690005)
            )
        }
    }
}