package com.jjrodcast.textkit.editor.core.transactions.text

import com.jjrodcast.textkit.editor.core.TextEditorManager
import com.jjrodcast.textkit.editor.core.models.MultiPieceParagraph
import com.jjrodcast.textkit.editor.core.models.PieceParagraph
import com.jjrodcast.textkit.editor.core.models.TextEditorModel
import com.jjrodcast.textkit.editor.core.transactions.TextEditorTransaction
import com.jjrodcast.textkit.editor.core.transactions.lists.models.TextEditorListItemTransaction
import com.jjrodcast.textkit.editor.core.transactions.models.TextEditorAction
import com.jjrodcast.textkit.editor.core.transactions.models.TextEditorRange
import com.jjrodcast.textkit.editor.core.transactions.text.TextTransactionsUtils.getOffsetAfterDecorator
import com.jjrodcast.textkit.editor.core.transactions.text.TextTransactionsUtils.reorderListItemsOnUpdate
import com.jjrodcast.textkit.editor.core.transactions.text.TextTransactionsUtils.updateTransaction

internal object TextUpdateTransaction {
    internal fun updateText(
        lines: MultiPieceParagraph,
        actionModel: TextEditorAction.TextUpdated,
        manager: TextEditorManager
    ): Pair<TextEditorRange, List<TextEditorListItemTransaction>> {
        val selectedParagraphs = lines.paragraphsInSelectedRange.filter { it.piecesInSelectedRange.isNotEmpty() }
        return if (selectedParagraphs.size > 1) {
            val firstParagraphInRange = selectedParagraphs.first()
            val lastParagraphInRange = selectedParagraphs.last()
            manager.transaction.updateOnMultipleParagraphs(firstParagraphInRange, lastParagraphInRange, lines, actionModel)
        } else {
            manager.transaction.updateOnSingleParagraph(selectedParagraphs.first(), actionModel)
        }
    }

    private fun TextEditorTransaction.updateOnMultipleParagraphs(
        firstParagraph: PieceParagraph,
        lastParagraph: PieceParagraph,
        lines: MultiPieceParagraph,
        actionModel: TextEditorAction.TextUpdated
    ): Pair<TextEditorRange, List<TextEditorListItemTransaction>> {
        val firstParagraphIncludesDecorator = firstParagraph.piecesInSelectedRange.first().piece.isDecorator
        val isLastDecoratorPartiallySelected =
            getOffsetAfterDecorator(lastParagraph, lastParagraph.piecesInSelectedRange.last().piece.offset) > 0
        val transactions = mutableListOf<TextEditorListItemTransaction>()

        var offset = actionModel.offset
        var length = actionModel.removeLength

        if (firstParagraphIncludesDecorator) {
            val remainingDecoratorOffset = getOffsetAfterDecorator(firstParagraph, actionModel.offset)
            offset += remainingDecoratorOffset
            length -= remainingDecoratorOffset
        }

        if (isLastDecoratorPartiallySelected) {
            val remainingDecoratorOffset =
                getOffsetAfterDecorator(lastParagraph, actionModel.offset + actionModel.removeLength)
            length += remainingDecoratorOffset
        }

        val marks = getTextAt(offset).piece.marks
        val model = TextEditorModel.create(text = actionModel.text, marks = marks, decorator = null)
        val deleteTransaction = updateTransaction(offset, model, length)
        transactions.add(deleteTransaction)

        // Update next items
        val nextParagraphsTransactions = reorderListItemsOnUpdate(lines)
        transactions.addAll(nextParagraphsTransactions)

        return Pair(TextEditorRange(offset + actionModel.text.length), transactions)
    }

    private fun TextEditorTransaction.updateOnSingleParagraph(
        paragraph: PieceParagraph,
        actionModel: TextEditorAction.TextUpdated
    ): Pair<TextEditorRange, List<TextEditorListItemTransaction>> {
        return updateTextAfterDecorator(paragraph, actionModel)
    }

    private fun TextEditorTransaction.updateTextAfterDecorator(
        paragraph: PieceParagraph,
        actionModel: TextEditorAction.TextUpdated
    ): Pair<TextEditorRange, List<TextEditorListItemTransaction>> {
        val transactions = mutableListOf<TextEditorListItemTransaction>()
        val selectionIncludesDecorator = paragraph.piecesInSelectedRange.first().piece.isDecorator
        var offset = actionModel.offset
        var length = actionModel.removeLength

        if (selectionIncludesDecorator) {
            val remainingDecoratorOffset = getOffsetAfterDecorator(paragraph, actionModel.offset)
            offset += maxOf(remainingDecoratorOffset, 0)
            length -= maxOf(remainingDecoratorOffset, 0)
        }

        val marks = this.getTextAt(offset).piece.marks
        val model = TextEditorModel.create(text = actionModel.text, marks = marks, decorator = null)
        val updateTransaction = updateTransaction(offset, model, length)

        val rangeOffset = offset + actionModel.text.length
        val range = TextEditorRange(rangeOffset)

        transactions.add(updateTransaction)

        return Pair(range, transactions)
    }
}
