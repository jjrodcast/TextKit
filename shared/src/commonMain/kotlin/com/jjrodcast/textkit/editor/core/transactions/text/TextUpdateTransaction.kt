package com.jjrodcast.textkit.editor.core.transactions.text

import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.core.TextKitEditorManager
import com.jjrodcast.textkit.editor.core.models.MultiPieceParagraph
import com.jjrodcast.textkit.editor.core.models.PieceParagraph
import com.jjrodcast.textkit.editor.core.models.TextEditorModel
import com.jjrodcast.textkit.editor.core.transactions.TextEditorTransaction
import com.jjrodcast.textkit.editor.core.transactions.lists.models.TextEditorListItemTransaction
import com.jjrodcast.textkit.editor.core.transactions.models.TextEditorAction
import com.jjrodcast.textkit.editor.core.transactions.text.TextTransactionsUtils.getOffsetAfterDecorator
import com.jjrodcast.textkit.editor.core.transactions.text.TextTransactionsUtils.reorderListItemsOnUpdate
import com.jjrodcast.textkit.editor.core.transactions.text.TextTransactionsUtils.updateTransaction
import com.jjrodcast.textkit.editor.utils.endsWithLineBreak
import com.jjrodcast.textkit.editor.utils.isLineBreak

internal object TextUpdateTransaction {
    internal fun updateText(
        lines: MultiPieceParagraph,
        actionModel: TextEditorAction.TextUpdated,
        manager: TextKitEditorManager
    ): Pair<TextRange, List<TextEditorListItemTransaction>> {
        val extended = extendPastOrphanedDecorator(lines, actionModel, manager)
        if (extended != null) {
            val newLines = manager.transaction.getLineContentWithNeighborParagraphs(
                extended.offset,
                extended.offset + extended.removeLength
            )
            return updateText(newLines, extended, manager)
        }

        val selectedParagraphs = lines.paragraphsInSelectedRange.filter { it.piecesInSelectedRange.isNotEmpty() }
        return when {
            // No piece in range — the window sits on an empty paragraph or at the document end
            // (an IME composition delivering text there). Degrade to a plain replacement at the
            // offset instead of crashing on the empty selection (issue #77).
            selectedParagraphs.isEmpty() -> manager.transaction.updateOutsidePieces(actionModel)

            selectedParagraphs.size > 1 -> {
                val firstParagraphInRange = selectedParagraphs.first()
                val lastParagraphInRange = selectedParagraphs.last()
                manager.transaction.updateOnMultipleParagraphs(firstParagraphInRange, lastParagraphInRange, lines, actionModel)
            }

            else -> manager.transaction.updateOnSingleParagraph(selectedParagraphs.first(), actionModel)
        }
    }

    /**
     * A replace whose removal window consumes a paragraph's terminating line break and stops
     * exactly at the next list item's start merges the two lines — and, unless the replacement
     * text restores the break, leaves that item's decorator orphaned mid-line in the text stream
     * (the delete path got the same rule for issue #67). Reach one character into the following
     * decorator and re-dispatch, so the existing partially-selected-decorator handling swallows it
     * whole and renumbers the remaining items. Returns `null` when no extension applies; the
     * extended range ends inside the decorator, so the re-dispatch can never extend a second time.
     */
    private fun extendPastOrphanedDecorator(
        lines: MultiPieceParagraph,
        actionModel: TextEditorAction.TextUpdated,
        manager: TextKitEditorManager
    ): TextEditorAction.TextUpdated? {
        val end = actionModel.offset + actionModel.removeLength
        if (end >= manager.text.length) return null
        if (manager.text.getOrNull(end - 1)?.isLineBreak() != true) return null
        if (actionModel.text.endsWithLineBreak()) return null
        val firstParagraph = lines.paragraphsInSelectedRange.firstOrNull { it.piecesInSelectedRange.isNotEmpty() }
            ?: return null
        // Unlike a plain delete, any non-empty replacement itself becomes a head for the merged
        // line, so the following decorator is orphaned even when the whole first line is removed.
        val keepsLineHead = actionModel.text.isNotEmpty() ||
            firstParagraph.isListItem || actionModel.offset > firstParagraph.startOffset
        if (!keepsLineHead) return null
        lines.paragraphs.firstOrNull { it.startOffset == end && it.isListItem } ?: return null
        return actionModel.copy(removeLength = actionModel.removeLength + 1)
    }

    private fun TextEditorTransaction.updateOutsidePieces(
        actionModel: TextEditorAction.TextUpdated
    ): Pair<TextRange, List<TextEditorListItemTransaction>> {
        // At the document end there is no piece at the offset itself; inherit the marks of the
        // character before it so an IME composition finishing there continues the formatting.
        val marks = when {
            actionModel.offset < text.length -> marksAtOrEmpty(actionModel.offset)
            actionModel.offset > 0 -> marksAtOrEmpty(actionModel.offset - 1)
            else -> emptySet()
        }
        // Text landing in a quoted paragraph inherits its quote attribute (#126), the way it
        // inherits marks — otherwise the continuation of a quote types out unquoted.
        val quoteDecorator = quoteDecoratorAt(actionModel.offset)
        val model = TextEditorModel.create(text = actionModel.text, marks = marks, decorator = quoteDecorator)
        val transaction = updateTransaction(actionModel.offset, model, actionModel.removeLength)
        return Pair(TextRange(actionModel.offset + actionModel.text.length), listOf(transaction))
    }

    private fun TextEditorTransaction.updateOnMultipleParagraphs(
        firstParagraph: PieceParagraph,
        lastParagraph: PieceParagraph,
        lines: MultiPieceParagraph,
        actionModel: TextEditorAction.TextUpdated
    ): Pair<TextRange, List<TextEditorListItemTransaction>> {
        val firstParagraphIncludesDecorator = firstParagraph.piecesInSelectedRange.first().piece.isDecorator
        // Document coordinates: the window's end lies strictly inside the last item's decorator
        // span (see the same predicate in TextDeletedTransaction — the previous buffer-offset form
        // went false for decorators deep in the ADDED buffer, leaving mid-line fragments).
        val windowEnd = actionModel.offset + actionModel.removeLength
        val isLastDecoratorPartiallySelected = lastParagraph.isListItem &&
            windowEnd > lastParagraph.startOffset &&
            getOffsetAfterDecorator(lastParagraph, windowEnd) > 0
        val transactions = mutableListOf<TextEditorListItemTransaction>()

        var offset = actionModel.offset
        var length = actionModel.removeLength

        if (firstParagraphIncludesDecorator) {
            // Same clamping as the single-paragraph path: the window start moves past the first
            // item's decorator and the length loses the covered part, never inverting below zero.
            val remainingDecoratorOffset = maxOf(getOffsetAfterDecorator(firstParagraph, actionModel.offset), 0)
            offset += remainingDecoratorOffset
            length = maxOf(length - remainingDecoratorOffset, 0)
        }

        if (isLastDecoratorPartiallySelected) {
            val remainingDecoratorOffset =
                getOffsetAfterDecorator(lastParagraph, actionModel.offset + actionModel.removeLength)
            length += remainingDecoratorOffset
        }

        val marks = marksAtOrEmpty(offset)
        val quoteDecorator = quoteDecoratorAt(offset)
        val model = TextEditorModel.create(text = actionModel.text, marks = marks, decorator = quoteDecorator)
        val deleteTransaction = updateTransaction(offset, model, length)
        transactions.add(deleteTransaction)

        // Same rule as TextDeletedTransaction: the reorder decides from the PRE-replace lines, so
        // it can emit an update for a decorator inside the (decorator-extended) removal window —
        // a marker this replace is removing. Applied together the two overlap and leave a marker
        // fragment mid-line; updates for surviving items all target offsets outside the window.
        val nextParagraphsTransactions = reorderListItemsOnUpdate(lines)
            .filter { it.offsetInDocument < offset || it.offsetInDocument >= offset + length }
        transactions.addAll(nextParagraphsTransactions)

        return Pair(TextRange(offset + actionModel.text.length), transactions)
    }

    /**
     * Marks of the piece at [offset], or none when [offset] sits at the document end (a clamped
     * window on an empty list item lands there — nothing to inherit marks from).
     */
    private fun TextEditorTransaction.marksAtOrEmpty(offset: Int) =
        if (offset < text.length) getTextAt(offset).piece.marks else emptySet()

    private fun TextEditorTransaction.updateOnSingleParagraph(
        paragraph: PieceParagraph,
        actionModel: TextEditorAction.TextUpdated
    ): Pair<TextRange, List<TextEditorListItemTransaction>> {
        return updateTextAfterDecorator(paragraph, actionModel)
    }

    private fun TextEditorTransaction.updateTextAfterDecorator(
        paragraph: PieceParagraph,
        actionModel: TextEditorAction.TextUpdated
    ): Pair<TextRange, List<TextEditorListItemTransaction>> {
        val transactions = mutableListOf<TextEditorListItemTransaction>()
        val selectionIncludesDecorator = paragraph.piecesInSelectedRange.first().piece.isDecorator
        var offset = actionModel.offset
        var length = actionModel.removeLength

        if (selectionIncludesDecorator) {
            // The decorator is presentation-only and atomic, so the replace window is clamped to the
            // item's content: the start moves past the decorator and the length loses the part that
            // lay inside it — never below zero, or the window inverts (a window fully inside the
            // decorator becomes a plain insert at the content start).
            val remainingDecoratorOffset = maxOf(getOffsetAfterDecorator(paragraph, actionModel.offset), 0)
            offset += remainingDecoratorOffset
            length = maxOf(length - remainingDecoratorOffset, 0)
        }

        val marks = this.marksAtOrEmpty(offset)
        val quoteDecorator = quoteDecoratorAt(offset)
        val model = TextEditorModel.create(text = actionModel.text, marks = marks, decorator = quoteDecorator)
        val updateTransaction = updateTransaction(offset, model, length)

        val rangeOffset = offset + actionModel.text.length
        val range = TextRange(rangeOffset)

        transactions.add(updateTransaction)

        return Pair(range, transactions)
    }
}
