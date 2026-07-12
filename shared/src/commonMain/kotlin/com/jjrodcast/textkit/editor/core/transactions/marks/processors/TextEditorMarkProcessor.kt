package com.jjrodcast.textkit.editor.core.transactions.marks.processors

import com.jjrodcast.textkit.editor.core.parser.LinkMark
import com.jjrodcast.textkit.editor.core.parser.Mark
import com.jjrodcast.textkit.editor.core.parser.TextStyleAttrs
import com.jjrodcast.textkit.editor.core.parser.TextStyleMark
import com.jjrodcast.textkit.editor.models.TextKitConfiguration

/**
 * This object is responsible for processing the marks of a text editor when selecting one or multiple pieces.
 */
internal object TextEditorMarkProcessor {

    /**
     * This function processes the marks of a text editor when selecting one or multiple pieces.
     *
     * @param pieceMarks The marks of the selected piece.
     * @param prevFormatMarks The marks of the previous state in the format bar.
     * @param currFormatMarks The marks of the current state in the format bar.
     */
    fun process(
        pieceMarks: Set<Mark>,
        prevFormatMarks: Set<Mark>,
        currFormatMarks: Set<Mark>,
        configuration: TextKitConfiguration
    ): Set<Mark> {
        // Single pass over pieceMarks — replaces 2× filterIsInstance + filterNot (3 traversals → 1).
        var pieceLinkMark: LinkMark? = null
        var pieceTextStyleMark: TextStyleMark? = null
        val otherPieceMarks = mutableSetOf<Mark>()
        for (mark in pieceMarks) {
            when (mark) {
                is LinkMark -> pieceLinkMark = mark
                is TextStyleMark -> pieceTextStyleMark = mark
                else -> otherPieceMarks.add(mark)
            }
        }

        // Single pass over currFormatMarks — replaces filterIsInstance + filterNot (2 traversals → 1).
        var formatTextStyleMark: TextStyleMark? = null
        val otherFormatMarks = mutableSetOf<Mark>()
        for (mark in currFormatMarks) {
            when (mark) {
                is TextStyleMark -> formatTextStyleMark = mark
                else -> otherFormatMarks.add(mark)
            }
        }

        val newTextStyleMark = when (pieceTextStyleMark != null || formatTextStyleMark != null) {
            true -> {
                /*AdskBlue500.hexValue else Charcoal900.hexValue*/
                // TODO()
                val defaultColor = if (pieceLinkMark != null) "" else ""

                val color = when (formatTextStyleMark?.attrs?.color) {
                    null -> pieceTextStyleMark?.attrs?.color ?: defaultColor
                    else -> formatTextStyleMark.attrs.color
                }
                val fontSize = when (formatTextStyleMark?.attrs?.fontSize) {
                    null -> pieceTextStyleMark?.attrs?.fontSize
                        ?: configuration.fontSize
                    else -> formatTextStyleMark.attrs.fontSize
                }

                TextStyleMark(TextStyleAttrs(color, fontSize))
            }

            false -> null
        }

        return when {
            prevFormatMarks.size > currFormatMarks.size -> {
                val finalFormatMarks = prevFormatMarks - otherFormatMarks
                otherPieceMarks - finalFormatMarks + setOfNotNull(pieceLinkMark, newTextStyleMark)
            }

            prevFormatMarks.size < currFormatMarks.size -> {
                otherPieceMarks + otherFormatMarks + setOfNotNull(pieceLinkMark, newTextStyleMark)
            }

            else -> {
                val (otherMarks, linkMark) = currFormatMarks.partition { it !is LinkMark }
                val isValidLink =
                    (linkMark.firstOrNull() as? LinkMark)?.attrs?.href?.isNotEmpty() ?: false
                val link = if (isValidLink) linkMark.firstOrNull() else null
                otherMarks.toSet() + otherPieceMarks + setOfNotNull(link, newTextStyleMark)
            }
        }
    }
}
