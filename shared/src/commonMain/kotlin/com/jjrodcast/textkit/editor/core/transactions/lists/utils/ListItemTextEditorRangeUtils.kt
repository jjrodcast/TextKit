package com.jjrodcast.textkit.editor.core.transactions.lists.utils

import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.core.converters.models.PositionalListItem
import com.jjrodcast.textkit.editor.core.piecetable.models.TextDecoratorModel
import com.jjrodcast.textkit.editor.core.piecetable.models.TextDecoratorModel.Companion.createDecoratorString
import com.jjrodcast.textkit.editor.core.piecetable.models.TextDecoratorModel.Companion.toLevel

internal object ListItemTextEditorRangeUtils {

    /**
     * Builds a selection range after shifting by decorator diffs, keeping it valid. Removing list
     * decorators shifts content left, so a selection that started at the document start (or whose
     * end shrinks past its start) can drive [start]/[end] negative or inverted; a [TextRange] must be
     * non-negative with `start <= end`, so we coerce instead of crashing.
     */
    private fun safeRange(start: Int, end: Int): TextRange {
        val safeStart = start.coerceAtLeast(0)
        return TextRange(start = safeStart, end = end.coerceAtLeast(safeStart))
    }

    internal fun getTextEditorRangeForCollapsed(
        items: List<PositionalListItem>,
        index: Int,
        range: TextRange
    ): TextRange {
        // A collapsed caret sits after the decorator of its own paragraph, so it shifts by every
        // decorator change up to and including that paragraph.
        val diffs = items
            .filter { it.modified && it.index <= index }
            .sumOf { it.removalDiff() }

        return safeRange(start = range.start + diffs, end = range.end + diffs)
    }

    internal fun getTextEditorRangeForRange(
        items: List<PositionalListItem>,
        indices: List<Int>,
        range: TextRange
    ): TextRange = shiftedRange(items, indices, range) { it.removalDiff() }

    internal fun getTextEditorRangeForMultiRange(
        items: List<PositionalListItem>,
        indices: List<Int>,
        range: TextRange
    ): TextRange = shiftedRange(items, indices, range) { it.mixedDiff() }

    internal fun getTextEditorRangeForInsertedRange(
        items: List<PositionalListItem>,
        indices: List<Int>,
        range: TextRange
    ): TextRange = shiftedRange(items, indices, range) { it.insertionDiff() }

    /**
     * Shifts [range] by decorator [diff]s. Decorators sit at the start of each paragraph, so:
     * - the START only shifts by changes strictly BEFORE the first selected paragraph — the first
     *   selected paragraph's own decorator sits at/after the start and must not move it (moving it
     *   drifts the selection and, when removing, could go negative);
     * - the END shifts by every change within the selection, so it stays on the last selected char.
     */
    private fun shiftedRange(
        items: List<PositionalListItem>,
        indices: List<Int>,
        range: TextRange,
        diff: (PositionalListItem) -> Int
    ): TextRange {
        val startDiff = items
            .filter { it.modified && it.index < indices.first() }
            .sumOf(diff)
        val totalDiff = items
            .filter { it.modified && it.index in indices }
            .sumOf(diff)
        return safeRange(start = range.start + startDiff, end = range.end + totalDiff)
    }

    /**
     * Diff for a removal / type change: whether a decorator is present in the flat stream depends on
     * the OPERATION, not just the level — here a level-0 NEW decorator means the item left the list
     * (its marker is removed from the stream), so it contributes 0.
     */
    private fun PositionalListItem.removalDiff(): Int {
        val newLength = if (newRichPiece.decorator.toLevel(0) == 0) 0 else newRichPiece.decorator.flatLength()
        return newLength - richPiece.decorator.flatLength()
    }

    /**
     * Diff for inserting a list onto plain paragraphs: the whole NEW decorator is added to the
     * stream. `richPiece` is not the prior plain state here (it already carries the new decorator),
     * so it must not be subtracted — only the inserted length counts.
     */
    private fun PositionalListItem.insertionDiff(): Int = newRichPiece.decorator.flatLength()

    /** Diff for mixed (paragraph + list) selections: a level-0 decorator is absent on either side. */
    private fun PositionalListItem.mixedDiff(): Int {
        val newLength = if (newRichPiece.decorator.toLevel(0) == 0) 0 else newRichPiece.decorator.flatLength()
        val oldLength = if (richPiece.decorator.toLevel(0) == 0) 0 else richPiece.decorator.flatLength()
        return newLength - oldLength
    }

    /**
     * Full length the decorator occupies in the flat text stream: its complete string (indentation
     * tabs + marker) as [createDecoratorString] produces, or 0 when null. Using `decorator.length`
     * here would only count the marker and under-count the indentation tabs, drifting the selection.
     */
    private fun TextDecoratorModel?.flatLength(): Int = createDecoratorString().length
}
