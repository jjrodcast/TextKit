package com.jjrodcast.textkit.editor.core.transactions.text

import com.jjrodcast.textkit.editor.core.TextEditorManager
import com.jjrodcast.textkit.editor.core.converters.ListsConverter
import com.jjrodcast.textkit.editor.core.converters.utils.PositionalListItemUtils
import com.jjrodcast.textkit.editor.core.converters.utils.createTransactions
import com.jjrodcast.textkit.editor.core.models.MultiPieceParagraph
import com.jjrodcast.textkit.editor.core.models.PieceParagraph
import com.jjrodcast.textkit.editor.core.transactions.TextEditorTransaction
import com.jjrodcast.textkit.editor.core.transactions.lists.models.TextEditorListItemTransaction
import com.jjrodcast.textkit.editor.core.transactions.models.TextEditorAction
import com.jjrodcast.textkit.editor.core.transactions.models.TextEditorRange
import com.jjrodcast.textkit.editor.core.transactions.text.TextTransactionsUtils.getOffsetAfterDecorator
import com.jjrodcast.textkit.editor.core.transactions.text.TextTransactionsUtils.reorderListItemsOnUpdate
import com.jjrodcast.textkit.editor.utils.isLineBreak

internal object TextDeletedTransaction {
    /**
     * Get a list of transactions and the new cursor position when a delete action is performed.
     *
     *  @param lines a [MultiPieceParagraph] with all the neighbor paragraphs for the current selected range.
     *  @param actionModel A [TextEditorAction.TextRemoved] with the cursor position and the length of the deleted text.
     *  @param controller A [TextEditorTransaction] that will handle the interactions with the piece table.
     *
     * @return A Pair with the new cursor position and a list of transactions [TextEditorListItemTransaction].
     */
    internal fun deleteText(
        lines: MultiPieceParagraph,
        actionModel: TextEditorAction.TextRemoved,
        manager: TextEditorManager
    ): Pair<TextEditorRange, List<TextEditorListItemTransaction>> {
        val selectedParagraphs = lines.paragraphsInSelectedRange.filter { it.piecesInSelectedRange.isNotEmpty() }
        return if (selectedParagraphs.size > 1) {
            val firstParagraphInRange = selectedParagraphs.first()
            val lastParagraphInRange = selectedParagraphs.last()
            deleteOnMultipleParagraphs(firstParagraphInRange, lastParagraphInRange, lines, actionModel)
        } else {
            manager.transaction.deleteOnSingleParagraph(selectedParagraphs.first(), lines, actionModel)
        }
    }

    /**
     * Get a list of transactions and the new cursor position when a delete action is performed on multiple paragraphs.
     *
     * Numbered or bulleted decorators should not be deleted when multiple paragraphs are selected and the selection includes them either at the beginning or at the end.
     * so in this function we need to move the cursor to the nearest available text and then perform the deletion.
     *
     *  @param firstParagraph The first paragraph in the selection.
     *  @param lastParagraph The last paragraph in the selection.
     *  @param lines a [MultiPieceParagraph] with all the neighbor paragraphs for the selected range.
     *  @param actionModel A [TextEditorAction.TextRemoved] with the cursor position and the length of the deleted text.
     *
     * @return A Pair with the new cursor position and a list of transactions [TextEditorListItemTransaction].
     */
    private fun deleteOnMultipleParagraphs(
        firstParagraph: PieceParagraph,
        lastParagraph: PieceParagraph,
        lines: MultiPieceParagraph,
        actionModel: TextEditorAction.TextRemoved
    ): Pair<TextEditorRange, List<TextEditorListItemTransaction>> {
        val firstParagraphIncludesDecorator = firstParagraph.piecesInSelectedRange.first().piece.isDecorator
        val isLastDecoratorPartiallySelected =
            getOffsetAfterDecorator(lastParagraph, lastParagraph.piecesInSelectedRange.last().piece.offset) > 0
        val transactions = mutableListOf<TextEditorListItemTransaction>()

        var offset = actionModel.offset
        var length = actionModel.length

        if (firstParagraphIncludesDecorator) {
            val remainingDecoratorOffset = getOffsetAfterDecorator(firstParagraph, actionModel.offset)
            offset += remainingDecoratorOffset
            length -= remainingDecoratorOffset
        }

        if (isLastDecoratorPartiallySelected) {
            val remainingDecoratorOffset = getOffsetAfterDecorator(lastParagraph, actionModel.offset + actionModel.length)
            length += remainingDecoratorOffset
        }

        val deleteTransaction = TextTransactionsUtils.deleteTransaction(offset, length)
        transactions.add(deleteTransaction)

        val nextParagraphsTransactions = reorderListItemsOnUpdate(lines)
        transactions.addAll(nextParagraphsTransactions)

        return Pair(TextEditorRange(offset), transactions)
    }

    private fun TextEditorTransaction.deleteOnSingleParagraph(
        paragraph: PieceParagraph,
        lines: MultiPieceParagraph,
        actionModel: TextEditorAction.TextRemoved
    ): Pair<TextEditorRange, List<TextEditorListItemTransaction>> {
        val firstSelectedPiece = paragraph.piecesInSelectedRange.first()
        val isDecoratorSelected = firstSelectedPiece.piece.isDecorator
        val isParagraphEmpty = firstSelectedPiece.text.isLineBreak()

        return if (paragraph.piecesInSelectedRange.size == 1) {
            when {
                isDecoratorSelected -> deleteDecorator(lines, paragraph, actionModel)
                isParagraphEmpty -> deleteEmptyParagraph(lines, actionModel)
                else -> deleteTextAndDecorator(paragraph, actionModel)
            }
        } else {
            deleteTextAndDecorator(paragraph, actionModel)
        }
    }

    private fun deleteDecorator(
        lines: MultiPieceParagraph,
        paragraph: PieceParagraph,
        actionModel: TextEditorAction.TextRemoved
    ): Pair<TextEditorRange, List<TextEditorListItemTransaction>> {
        return TextDecoratorTransaction.getDeleteTransaction(paragraph, lines, actionModel)
    }

    private fun deleteTextAndDecorator(
        paragraph: PieceParagraph,
        actionModel: TextEditorAction.TextRemoved
    ): Pair<TextEditorRange, List<TextEditorListItemTransaction>> {
        return if (paragraph.piecesInSelectedRange.first().piece.isDecorator) {
            val remainingDecoratorOffset = getOffsetAfterDecorator(paragraph, actionModel.offset)
            val newOffset = actionModel.offset + remainingDecoratorOffset
            val newLength = actionModel.length - remainingDecoratorOffset
            val deleteTransaction = TextTransactionsUtils.deleteTransaction(newOffset, newLength)
            val range = TextEditorRange(newOffset)

            Pair(range, listOf(deleteTransaction))
        } else {
            val deleteTransaction = TextTransactionsUtils.deleteTransaction(actionModel.offset, actionModel.length)
            val range = TextEditorRange(actionModel.offset)

            Pair(range, listOf(deleteTransaction))
        }
    }

    private fun TextEditorTransaction.deleteEmptyParagraph(
        lines: MultiPieceParagraph,
        actionModel: TextEditorAction.TextRemoved
    ): Pair<TextEditorRange, List<TextEditorListItemTransaction>> {
        val transactions = mutableListOf<TextEditorListItemTransaction>()
        val deleteTransaction = TextTransactionsUtils.deleteTransaction(actionModel.offset, actionModel.length)
        val range = TextEditorRange(actionModel.offset)

        // We are deleting empty paragraph, so we need to merge previous and next items in case we have list items.
        val updatedNextParagraphsTransactions = getMergeListsParagraphsTransactions(lines, actionModel)

        transactions.add(deleteTransaction)
        transactions.addAll(updatedNextParagraphsTransactions)

        return Pair(range, transactions)
    }

    /**
     * Get a list of transactions when combining lists and reorder them.
     *
     * This function merge two lists that are of the same type, for example with this kind of document:
     *
     *  1. Hello
     *  2. World
     *
     *  1. Hello
     *  2. World
     *
     * In case we delete the space between both lists, we need to merge them and reorder the numbers, ending with something like this:
     *
     *  1. Hello
     *  2. World
     *  3. Hello
     *  4. World
     *
     *  @param lines a [MultiPieceParagraph] with all the neighbor paragraphs for the selected range.
     *  @param actionModel A [TextEditorAction.TextRemoved] with the cursor position and the length of the deleted text.
     *
     * @return A Pair with the new cursor position and a list of transactions [TextEditorListItemTransaction].
     */
    private fun TextEditorTransaction.getMergeListsParagraphsTransactions(
        lines: MultiPieceParagraph,
        actionModel: TextEditorAction.TextRemoved
    ): List<TextEditorListItemTransaction> {
        // If next and previous paragraphs are of the same type, merge list items
        val paragraph = lines.paragraphsInSelectedRange.first()
        val paragraphOffset = paragraph.startOffset
        val maxOffset = getLastOffset()
        val nextParagraphOffset = (paragraph.endOffset + paragraph.endPiece.length) + 1

        if (nextParagraphOffset > maxOffset) return emptyList()

        val nextParagraph = getLineContent(
            paragraph.endOffset + paragraph.endPiece.length + 1,
            paragraph.endOffset + paragraph.endPiece.length + 1 + actionModel.length
        ).paragraphsInSelectedRange.firstOrNull()
        val paragraphType = paragraph.paragraphType
        val nextParagraphType = nextParagraph?.paragraphType

        if (paragraphType != nextParagraphType) return emptyList()

        val mergedParagraphs = getLineContentWithNeigborParagraphs(paragraphOffset, nextParagraphOffset)
        val listParagraphs = mergedParagraphs.paragraphs.filter { it.paragraphType == paragraphType }
        val positionalItems = ListsConverter.fromPieceMultiParagraph(MultiPieceParagraph(listParagraphs, lines.start, lines.end))
        val reorderedItems = PositionalListItemUtils.reorderItems(positionalItems)

        return reorderedItems.createTransactions()
    }
}
