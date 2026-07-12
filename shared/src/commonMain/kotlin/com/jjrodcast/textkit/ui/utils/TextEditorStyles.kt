package com.jjrodcast.textkit.ui.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import com.jjrodcast.textkit.editor.core.parser.BoldMark
import com.jjrodcast.textkit.editor.core.parser.HighlightMark
import com.jjrodcast.textkit.editor.core.parser.ItalicMark
import com.jjrodcast.textkit.editor.core.parser.LinkMark
import com.jjrodcast.textkit.editor.core.parser.StrikeMark
import com.jjrodcast.textkit.editor.core.parser.TextStyleAttrs
import com.jjrodcast.textkit.editor.core.parser.TextStyleMark
import com.jjrodcast.textkit.editor.core.parser.UnderlineMark
import com.jjrodcast.textkit.editor.core.transactions.models.TextEditorItem
import com.jjrodcast.textkit.editor.models.TextKitConfiguration


internal fun TextEditorItem.createStyle(configuration: TextKitConfiguration): SpanStyle {
    return SpanStyle(
        color = createColorSpanStyle(configuration.textColor),
        fontWeight = createBoldSpanStyle(),
        fontStyle = createItalicSpanStyle(),
        fontSize = createTextSizeStyle(),
        background = createHighlightSpanStyle(configuration.highlightColor),
        textDecoration = TextDecoration.combine(
            listOfNotNull(
                createLinkSpanStyle(),
                createLineThroughSpanStyle()
            )
        )
    )
}

private fun TextEditorItem.createColorSpanStyle(defaultColor: Color): Color {
    val textSizeColorMark = marks.filterIsInstance<TextStyleMark>().firstOrNull()
    val isDefaultTextSize = textSizeColorMark?.isDefaultColor() ?: false
    val isLink = marks.any { it is LinkMark }
    return when {
        ((!isLink && textSizeColorMark != null) || (isLink && textSizeColorMark != null)) &&
                !isDefaultTextSize -> textSizeColorMark.attrs.createColor()

        isLink -> defaultColor
        else -> Color.Unspecified
    }
}

private fun TextEditorItem.createBoldSpanStyle() =
    if (marks.any { it is BoldMark }) FontWeight.Bold else null

private fun TextEditorItem.createItalicSpanStyle() =
    if (marks.any { it is ItalicMark }) FontStyle.Italic else null

private fun TextEditorItem.createTextSizeStyle(textSize: TextUnit = TextUnit.Unspecified): TextUnit {
    val textSizeColorMarks = marks.filterIsInstance<TextStyleMark>()
    return if (textSizeColorMarks.isNotEmpty()) {
        val textStyle = textSizeColorMarks.first()
        TextUnit(textStyle.attrs.fontSize.toFloat(), TextUnitType.Sp)
    } else textSize
}

private fun TextEditorItem.createHighlightSpanStyle(defaultColor: Color) =
    if (marks.any { it is HighlightMark }) defaultColor else Color.Unspecified

private fun TextEditorItem.createLinkSpanStyle() =
    if (marks.any { it is LinkMark || it is UnderlineMark }) TextDecoration.Underline else null

private fun TextEditorItem.createLineThroughSpanStyle() =
    if (marks.any { it is StrikeMark }) TextDecoration.LineThrough else null

private fun TextStyleAttrs.createColor(): Color {
    return try {
        Color(color.orEmpty().toColorInt())
    } catch (e: Exception) {
        Color.Unspecified
    }
}

/**
 * Parses a hex color string (`#RRGGBB` or `#AARRGGBB`, with or without the `#`) into a 32-bit
 * ARGB [Int] suitable for the [Color] constructor. Missing alpha defaults to fully opaque.
 *
 * Multiplatform replacement for Android's `Color.parseColor` / `String.toColorInt`.
 *
 * @throws IllegalArgumentException if the string is not a valid 6/8-digit hex color.
 */
internal fun String.toColorInt(): Int = toColorLong().toInt()

/**
 * Parses a hex color string (`#RRGGBB` or `#AARRGGBB`, with or without the `#`) into an ARGB
 * value packed in the low 32 bits of a [Long]. Missing alpha defaults to fully opaque.
 *
 * @throws IllegalArgumentException if the string is not a valid 6/8-digit hex color.
 */
internal fun String.toColorLong(): Long {
    val hex = removePrefix("#")
    return when (hex.length) {
        6 -> 0xFF000000L or hex.toLong(16)
        8 -> hex.toLong(16)
        else -> throw IllegalArgumentException("Invalid hex color: $this")
    }
}
