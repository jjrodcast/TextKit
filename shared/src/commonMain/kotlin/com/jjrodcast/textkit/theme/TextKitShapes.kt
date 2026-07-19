package com.jjrodcast.textkit.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Immutable
import com.jjrodcast.textkit.theme.tokens.TextKitShapeTokens

/**
 * The corner shapes used to round TextKit surfaces. Pick the tier by component size: [small] for
 * chips and toolbar buttons, [medium] for cards and popups, [large] for prominent containers.
 *
 * Obtain the active instance with `TextKitTheme.shapes` inside a [TextKitTheme] scope. Each tier
 * defaults to its [TextKitShapeTokens] value.
 *
 * @property small Corner shape for small elements (chips, toolbar buttons).
 * @property medium Corner shape for medium containers (cards, popups).
 * @property large Corner shape for large/prominent containers.
 */
@Immutable
class TextKitShapes(
    val small: CornerBasedShape = TextKitShapeTokens.SMALL,
    val medium: CornerBasedShape = TextKitShapeTokens.MEDIUM,
    val large: CornerBasedShape = TextKitShapeTokens.LARGE
) {

    companion object {

        /** Builds a shape set with explicit [small], [medium] and [large] corner shapes. */
        fun default(
            small: CornerBasedShape,
            medium: CornerBasedShape,
            large: CornerBasedShape
        ): TextKitShapes {
            return TextKitShapes(
                small = small,
                medium = medium,
                large = large
            )
        }
    }
}