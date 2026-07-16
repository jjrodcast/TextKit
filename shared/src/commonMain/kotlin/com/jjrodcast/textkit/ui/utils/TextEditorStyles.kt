package com.jjrodcast.textkit.ui.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import com.jjrodcast.textkit.editor.core.parser.BoldMark
import com.jjrodcast.textkit.editor.core.parser.EmbedTokenType
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
    val base = SpanStyle(
        color = createColorSpanStyle(configuration.linkColor),
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
    if (!isToken) return base
    // An embedded block (table/image/document) renders as a neutral chip: it has no trigger, so it
    // uses the link color as accent + a translucent background, at a medium weight.
    if (tokenType == EmbedTokenType) {
        return base.copy(
            color = configuration.linkColor,
            fontWeight = base.fontWeight ?: FontWeight.Medium,
            background = configuration.linkColor.copy(alpha = 0.12f)
        )
    }
    // An atomic token (mention, hashtag, …) renders as an accented chip: it keeps whatever its marks
    // imply (bold, italic, underline, strike, size) but overlays the color configured for its trigger
    // + translucent background, and defaults to a medium weight when it is not explicitly bold.
    val tokenColor = configuration.triggerForType(tokenType)?.color ?: configuration.linkColor
    return base.copy(
        color = tokenColor,
        fontWeight = base.fontWeight ?: FontWeight.Medium,
        background = tokenColor.copy(alpha = 0.12f)
    )
}

private fun TextEditorItem.createColorSpanStyle(linkColor: Color): Color {

    val explicitColor = marks.filterIsInstance<TextStyleMark>()
        .firstOrNull()
        ?.attrs
        ?.takeIf { !it.color.isNullOrEmpty() }
        ?.createColor()
        ?.takeIf { it != Color.Unspecified }

    return when {
        explicitColor != null -> explicitColor
        marks.any { it is LinkMark } -> linkColor
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
    } catch (_: Exception) {
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
