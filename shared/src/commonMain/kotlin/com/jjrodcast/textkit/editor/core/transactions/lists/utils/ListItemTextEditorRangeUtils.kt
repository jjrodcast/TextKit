package com.jjrodcast.textkit.editor.core.transactions.lists.utils

import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.core.converters.models.PositionalListItem
import com.jjrodcast.textkit.editor.core.piecetable.models.TextDecoratorModel.Companion.toLevel

internal object ListItemTextEditorRangeUtils {

    internal fun getTextEditorRangeForCollapsed(
        items: List<PositionalListItem>,
        index: Int,
        range: TextRange
    ): TextRange {
        val diffs = items
            .filter { it.modified && it.index <= index }
            .sumOf { it.getDecoratorDiff() }

        return TextRange(start = range.start + diffs, end = range.end + diffs)
    }

    internal fun getTextEditorRangeForRange(
        items: List<PositionalListItem>,
        indices: List<Int>,
        range: TextRange
    ): TextRange {
        val startDiff = items
            .firstOrNull { it.modified && it.index == indices.first() }.getDecoratorDiff()

        val totalDiff = items
            .filter { it.modified && it.index in indices }
            .sumOf { it.getDecoratorDiff() }

        return TextRange(start = range.start + startDiff, end = range.end + totalDiff)
    }

    internal fun getTextEditorRangeForMultiRange(
        items: List<PositionalListItem>,
        indices: List<Int>,
        range: TextRange
    ): TextRange {
        val startDiff = items
            .firstOrNull { it.modified && it.index == indices.first() }.getDecoratorDiffForMultiple()

        val totalDiff = items
            .filter { it.modified && it.index in indices }
            .sumOf { it.getDecoratorDiffForMultiple() }

        return TextRange(start = range.start + startDiff, end = range.end + totalDiff)
    }

    internal fun getTextEditorRangeForInsertedRange(
        items: List<PositionalListItem>,
        indices: List<Int>,
        range: TextRange
    ): TextRange {
        val startDiff = items
            .firstOrNull { it.modified && it.index == indices.first() }
            ?.newRichPiece?.decorator?.length ?: 0

        val totalDiff = items
            .filter { it.modified && it.index in indices }
            .sumOf { it.newRichPiece.decorator?.length ?: 0 }

        return TextRange(start = range.start + startDiff, end = range.end + totalDiff)
    }

    private fun PositionalListItem?.getDecoratorDiff(): Int {
        return if (this == null) 0
        else {
            (if (newRichPiece.decorator.toLevel(0) == 0) 0 else (newRichPiece.decorator?.length ?: 0)) -
                (richPiece.decorator?.length ?: 0)
        }
    }

    private fun PositionalListItem?.getDecoratorDiffForMultiple(): Int {
        return if (this == null) 0
        else {
            (if (newRichPiece.decorator.toLevel(0) == 0) 0 else (newRichPiece.decorator?.length ?: 0)) -
                (if (richPiece.decorator.toLevel(0) == 0) 0 else (richPiece.decorator?.length ?: 0))
        }
    }
}
