package com.jjrodcast.textkit.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember

/**
 * Provides the TextKit design tokens ([colors], [typography], [shapes]) to [content] via
 * [LocalTextKitTheme]. Wrap TextKit UI in this so `TextKitTheme.colors` / `.typography` / `.shapes`
 * resolve; reading them outside a provider throws.
 *
 * [colors] defaults to the light or dark palette based on the system setting, so the theme switches
 * reactively when the system (or a passed override) changes. Override any parameter to customize.
 *
 * @param colors The color palette to expose. Defaults to [TextKitColors.dark]/[TextKitColors.light]
 *   per [isSystemInDarkTheme].
 * @param typography The typography to expose.
 * @param shapes The corner shapes to expose.
 * @param content UI that reads the theme through the [TextKitTheme] accessor.
 */
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
    // Themed text-selection: the drag handles and the highlighted range. Caret color is set per
    // field via BasicTextField's `cursorBrush`; selection has no such parameter and is driven by
    // this CompositionLocal, so providing it here themes the editor and every popup text field.
    val selectionColors = remember(colors.primary) {
        TextSelectionColors(
            handleColor = colors.primary,
            backgroundColor = colors.primary.copy(alpha = 0.4f)
        )
    }
    CompositionLocalProvider(
        LocalTextKitTheme provides theme,
        LocalTextSelectionColors provides selectionColors
    ) {
        content()
    }
}

/**
 * Bundle of the active TextKit design tokens carried by [LocalTextKitTheme]. Construct and provide it
 * through the [TextKitTheme] composable rather than directly (its constructor is internal).
 *
 * The companion doubles as the accessor for the current theme (Material-style), e.g.
 * `TextKitTheme.colors`, `TextKitTheme.typography`, `TextKitTheme.shapes`.
 *
 * @property colors The active color palette.
 * @property typography The active typography.
 * @property shapes The active corner shapes.
 */
@ConsistentCopyVisibility
@Immutable
data class TextKitTheme internal constructor(
    val colors: TextKitColors,
    val typography: TextKitTypography,
    val shapes: TextKitShapes
) {

    companion object {

        /** The [TextKitColors] of the nearest [TextKitTheme] provider. */
        val colors: TextKitColors
            @Composable @ReadOnlyComposable get() = LocalTextKitTheme.current.colors

        /** The [TextKitTypography] of the nearest [TextKitTheme] provider. */
        val typography: TextKitTypography
            @Composable @ReadOnlyComposable get() = LocalTextKitTheme.current.typography

        /** The [TextKitShapes] of the nearest [TextKitTheme] provider. */
        val shapes: TextKitShapes
            @Composable @ReadOnlyComposable get() = LocalTextKitTheme.current.shapes

    }
}
