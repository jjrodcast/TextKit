package com.jjrodcast.textkit.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.jjrodcast.textkit.theme.tokens.TextKitDarkTokens
import com.jjrodcast.textkit.theme.tokens.TextKitLightTokens

/**
 * The color roles used across TextKit UI (formatting bar, popups, editor). Modeled on Material 3:
 * every container role has a matching `on` role for the content drawn on top of it — always pair
 * them (e.g. content over [surface] uses [onSurface]) so contrast holds in light and dark.
 *
 * Obtain the active instance with `TextKitTheme.colors` inside a [TextKitTheme] scope. Build custom
 * palettes with [light] / [dark], overriding only the roles you need.
 *
 * See `README.md` in this package for guidance on choosing between similar roles.
 *
 * @property primary Main accent: active/selected states (toggled formatting-bar button, caret) and primary actions.
 * @property onPrimary Content (text/icons) drawn on top of [primary].
 * @property primaryContainer Softer accent surface tied to [primary]: highlighted chips, selected embeds, subtle emphasis.
 * @property onPrimaryContainer Content drawn on top of [primaryContainer].
 * @property secondary Supporting accent for smaller cues (e.g. the expandable-item indicator).
 * @property onSecondary Content drawn on top of [secondary].
 * @property highlight Background of text carrying the highlight mark. A warm amber, deliberately off
 *   the teal [primary] family so it stays distinct from the text selection painted over it.
 * @property onHighlight Content drawn on top of [highlight].
 * @property background The editor's base surface (the `BasicTextField` area).
 * @property onBackground Content drawn on top of [background].
 * @property surface Raised containers: the formatting-bar `Card`, popups, tooltips, menus.
 * @property onSurface Content drawn on top of [surface].
 * @property surfaceVariant Secondary/muted surfaces: popup sections, disabled backgrounds.
 * @property onSurfaceVariant Muted content: placeholder/hint text, over [surface] or [surfaceVariant].
 * @property outline Prominent borders: outlined text fields, focused strokes.
 * @property outlineVariant Subtle separators: dividers and hairlines.
 * @property error Error states: validation messages, destructive actions.
 * @property onError Content drawn on top of [error].
 */
@Immutable
class TextKitColors(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val highlight: Color,
    val onHighlight: Color,
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val outlineVariant: Color,
    val error: Color,
    val onError: Color,
) {

    companion object {

        /**
         * The light palette. Every role defaults to its [TextKitLightTokens] value; pass any
         * parameter to override that single role while the rest keep their token default.
         */
        fun light(
            primary: Color = TextKitLightTokens.PRIMARY,
            onPrimary: Color = TextKitLightTokens.ON_PRIMARY,
            primaryContainer: Color = TextKitLightTokens.PRIMARY_CONTAINER,
            onPrimaryContainer: Color = TextKitLightTokens.ON_PRIMARY_CONTAINER,
            secondary: Color = TextKitLightTokens.SECONDARY,
            onSecondary: Color = TextKitLightTokens.ON_SECONDARY,
            highlight: Color = TextKitLightTokens.HIGHLIGHT,
            onHighlight: Color = TextKitLightTokens.ON_HIGHLIGHT,
            background: Color = TextKitLightTokens.BACKGROUND,
            onBackground: Color = TextKitLightTokens.ON_BACKGROUND,
            surface: Color = TextKitLightTokens.SURFACE,
            onSurface: Color = TextKitLightTokens.ON_SURFACE,
            surfaceVariant: Color = TextKitLightTokens.SURFACE_VARIANT,
            onSurfaceVariant: Color = TextKitLightTokens.ON_SURFACE_VARIANT,
            outline: Color = TextKitLightTokens.OUTLINE,
            outlineVariant: Color = TextKitLightTokens.OUTLINE_VARIANT,
            error: Color = TextKitLightTokens.ERROR,
            onError: Color = TextKitLightTokens.ON_ERROR

        ): TextKitColors {
            return TextKitColors(
                primary = primary,
                onPrimary = onPrimary,
                primaryContainer = primaryContainer,
                onPrimaryContainer = onPrimaryContainer,
                secondary = secondary,
                onSecondary = onSecondary,
                highlight = highlight,
                onHighlight = onHighlight,
                background = background,
                onBackground = onBackground,
                surface = surface,
                onSurface = onSurface,
                surfaceVariant = surfaceVariant,
                onSurfaceVariant = onSurfaceVariant,
                outline = outline,
                outlineVariant = outlineVariant,
                error = error,
                onError = onError
            )
        }

        /**
         * The dark palette. Every role defaults to its [TextKitDarkTokens] value; pass any
         * parameter to override that single role while the rest keep their token default.
         */
        fun dark(
            primary: Color = TextKitDarkTokens.PRIMARY,
            onPrimary: Color = TextKitDarkTokens.ON_PRIMARY,
            primaryContainer: Color = TextKitDarkTokens.PRIMARY_CONTAINER,
            onPrimaryContainer: Color = TextKitDarkTokens.ON_PRIMARY_CONTAINER,
            secondary: Color = TextKitDarkTokens.SECONDARY,
            onSecondary: Color = TextKitDarkTokens.ON_SECONDARY,
            highlight: Color = TextKitDarkTokens.HIGHLIGHT,
            onHighlight: Color = TextKitDarkTokens.ON_HIGHLIGHT,
            background: Color = TextKitDarkTokens.BACKGROUND,
            onBackground: Color = TextKitDarkTokens.ON_BACKGROUND,
            surface: Color = TextKitDarkTokens.SURFACE,
            onSurface: Color = TextKitDarkTokens.ON_SURFACE,
            surfaceVariant: Color = TextKitDarkTokens.SURFACE_VARIANT,
            onSurfaceVariant: Color = TextKitDarkTokens.ON_SURFACE_VARIANT,
            outline: Color = TextKitDarkTokens.OUTLINE,
            outlineVariant: Color = TextKitDarkTokens.OUTLINE_VARIANT,
            error: Color = TextKitDarkTokens.ERROR,
            onError: Color = TextKitDarkTokens.ON_ERROR

        ): TextKitColors {
            return TextKitColors(
                primary = primary,
                onPrimary = onPrimary,
                primaryContainer = primaryContainer,
                onPrimaryContainer = onPrimaryContainer,
                secondary = secondary,
                onSecondary = onSecondary,
                highlight = highlight,
                onHighlight = onHighlight,
                background = background,
                onBackground = onBackground,
                surface = surface,
                onSurface = onSurface,
                surfaceVariant = surfaceVariant,
                onSurfaceVariant = onSurfaceVariant,
                outline = outline,
                outlineVariant = outlineVariant,
                error = error,
                onError = onError
            )
        }
    }
}
