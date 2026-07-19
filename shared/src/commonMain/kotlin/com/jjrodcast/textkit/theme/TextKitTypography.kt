package com.jjrodcast.textkit.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.Font
import textkit.shared.generated.resources.Res
import textkit.shared.generated.resources.noto_sans_italic_variable_font
import textkit.shared.generated.resources.noto_sans_variable_font

/**
 * The typography used by TextKit UI and rendered content.
 *
 * Obtain the active instance with `TextKitTheme.typography` inside a [TextKitTheme] scope.
 *
 * @property fontFamily The font family applied to editor text and TextKit controls.
 */
@Immutable
data class TextKitTypography(
    val fontFamily: FontFamily
) {

    companion object {
        /**
         * The default typography. [fontFamily] defaults to the bundled Noto Sans (regular + italic);
         * pass a custom [FontFamily] to override it. Composable because loading font resources is.
         */
        @Composable
        fun default(
            fontFamily: FontFamily = notoSansFamily()
        ): TextKitTypography = TextKitTypography(fontFamily = fontFamily)

        @Composable
        private fun notoSansFamily(): FontFamily = FontFamily(
            Font(
                Res.font.noto_sans_variable_font,
                weight = FontWeight.Normal
            ),
            Font(
                Res.font.noto_sans_italic_variable_font,
                weight = FontWeight.Normal,
                style = FontStyle.Italic
            )
        )
    }
}