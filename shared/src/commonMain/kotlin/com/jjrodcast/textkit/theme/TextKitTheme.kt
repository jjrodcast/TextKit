package com.jjrodcast.textkit.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable

@Composable
fun TextKitTheme(
    colors: TextKitColors = if (isSystemInDarkTheme()) TextKitColors.dark() else TextKitColors.light(),
    typography: TextKitTypography = TextKitTypography.default(),
    shapes: TextKitShapes = TextKitShapes(),
    content: @Composable () -> Unit
) {
    val theme = TextKitTheme(
        colors = colors,
        typography = typography,
        shapes = shapes
    )
    CompositionLocalProvider(LocalTextKitTheme provides theme) {
        content()
    }
}

@ConsistentCopyVisibility
@Immutable
data class TextKitTheme internal constructor(
    val colors: TextKitColors,
    val typography: TextKitTypography,
    val shapes: TextKitShapes
) {

    companion object {

        val colors: TextKitColors
            @Composable @ReadOnlyComposable get() = LocalTextKitTheme.current.colors

        val typography: TextKitTypography
            @Composable @ReadOnlyComposable get() = LocalTextKitTheme.current.typography

        val shapes: TextKitShapes
            @Composable @ReadOnlyComposable get() = LocalTextKitTheme.current.shapes

    }
}
