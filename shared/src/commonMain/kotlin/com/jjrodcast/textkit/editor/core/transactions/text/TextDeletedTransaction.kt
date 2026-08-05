package com.jjrodcast.textkit.editor.core.transactions.text

import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.core.TextKitEditorManager
import com.jjrodcast.textkit.editor.core.converters.ListsConverter
import com.jjrodcast.textkit.editor.core.converters.utils.PositionalListItemUtils
import com.jjrodcast.textkit.editor.core.converters.utils.createTransactions
import com.jjrodcast.textkit.editor.core.models.MultiPieceParagraph
import com.jjrodcast.textkit.editor.core.models.PieceParagraph
import com.jjrodcast.textkit.editor.core.transactions.TextEditorTransaction
import com.jjrodcast.textkit.editor.core.transactions.lists.models.TextEditorListItemTransaction
import com.jjrodcast.textkit.editor.core.transactions.models.TextEditorAction
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
        manager: TextKitEditorManager
    ): Pair<TextRange, List<TextEditorListItemTransaction>> {
        val extended = extendPastOrphanedDecorator(lines, actionModel, manager)
        if (extended != null) {
            val newLines = manager.transaction.getLineContentWithNeighborParagraphs(
                extended.offset,
                extended.offset + extended.length
            )
            return deleteText(newLines, extended, manager)
        }

        val selectedParagraphs = lines.paragraphsInSelectedRange.filter { it.piecesInSelectedRange.isNotEmpty() }
        return if (selectedParagraphs.size > 1) {
            val firstParagraphInRange = selectedParagraphs.first()
            val lastParagraphInRange = selectedParagraphs.last()
            deleteOnMultipleParagraphs(
                firstParagraphInRange,
                lastParagraphInRange,
                lines,
                actionModel,
                documentLength = manager.text.length,
            )
        } else {
            manager.transaction.deleteOnSingleParagraph(selectedParagraphs.first(), lines, actionModel)
        }
    }

    /**
     * A delete whose range consumes a paragraph's terminating line break and stops exactly at the
     * next list item's decorator merges the two lines — and would leave that decorator orphaned
     * mid-line in the text stream while the export drops it (issue #67). When the merged line keeps
     * a head (the first paragraph's decorator survives by policy, or the delete starts mid-line),
     * reach one character into the following decorator and re-dispatch, so the existing
     * partially-selected-decorator handling swallows it whole and renumbers the remaining items.
     * Returns `null` when no extension applies; the extended range ends inside the decorator, so
     * the re-dispatch can never extend a second time.
     */
    private fun extendPastOrphanedDecorator(
        lines: MultiPieceParagraph,
        actionModel: TextEditorAction.TextRemoved,
        manager: TextKitEditorManager
    ): TextEditorAction.TextRemoved? {
        val end = actionModel.offset + actionModel.length
        if (end >= manager.text.length) return null
        if (manager.text.getOrNull(end - 1)?.isLineBreak() != true) return null
        val firstParagraph = lines.paragraphsInSelectedRange.firstOrNull { it.piecesInSelectedRange.isNotEmpty() }
            ?: return null
        val keepsLineHead = firstParagraph.isListItem || actionModel.offset > firstParagraph.startOffset
        if (!keepsLineHead) return null
        lines.paragraphs.firstOrNull { it.startOffset == end && it.isListItem } ?: return null
        return actionModel.copy(length = actionModel.length + 1)
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
        actionModel: TextEditorAction.TextRemoved,
        documentLength: Int,
    ): Pair<TextRange, List<TextEditorListItemTransaction>> {
        val firstParagraphIncludesDecorator = firstParagraph.piecesInSelectedRange.first().piece.isDecorator
        // Document coordinates: the window's end lies strictly inside the last item's decorator
        // span. The previous form passed the piece's *buffer* offset into document-offset math, so
        // once a decorator lived deep in the ADDED buffer the predicate went false and the partial
        // decorator survived the delete as a mid-line fragment.
        val windowEnd = actionModel.offset + actionModel.length
        val isLastDecoratorPartiallySelected = lastParagraph.isListItem &&
            windowEnd > lastParagraph.startOffset &&
            getOffsetAfterDecorator(lastParagraph, windowEnd) > 0
        val transactions = mutableListOf<TextEditorListItemTransaction>()

        var offset = actionModel.offset
        var length = actionModel.length

        if (firstParagraphIncludesDecorator) {
            val decorSkip = maxOf(getOffsetAfterDecorator(firstParagraph, actionModel.offset), 0)
            val wouldOrphanOpeningDecorator = decorSkip > 0 &&
                actionModel.length >= documentLength - actionModel.offset
            if (!wouldOrphanOpeningDecorator) {
                offset += decorSkip
                length = maxOf(length - decorSkip, 0)
            }
        }

        if (isLastDecoratorPartiallySelected) {
            val remainingDecoratorOffset = getOffsetAfterDecorator(lastParagraph, actionModel.offset + actionModel.length)
            length += remainingDecoratorOffset
        }

        val deleteTransaction = TextTransactionsUtils.deleteTransaction(offset, length)
        transactions.add(deleteTransaction)

        // The reorder decides from the PRE-delete lines, so it can emit an update for a decorator
        // that lies inside the (possibly decorator-extended) delete window — a decorator the delete
        // is removing. Applied together the two overlap and the delete carves through the freshly
        // written marker, leaving a fragment mid-line. A transaction targeting the deleted window
        // can never be meaningful; the surviving items' updates all target offsets outside it.
        val nextParagraphsTransactions = reorderListItemsOnUpdate(lines)
            .filter { it.offsetInDocument < offset || it.offsetInDocument >= offset + length }
        transactions.addAll(nextParagraphsTransactions)

        return Pair(TextRange(offset), transactions)
    }

    private fun TextEditorTransaction.deleteOnSingleParagraph(
        paragraph: PieceParagraph,
        lines: MultiPieceParagraph,
        actionModel: TextEditorAction.TextRemoved
    ): Pair<TextRange, List<TextEditorListItemTransaction>> {
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
    ): Pair<TextRange, List<TextEditorListItemTransaction>> {
        return TextDecoratorTransaction.getDeleteTransaction(paragraph, lines, actionModel)
    }

    private fun TextEditorTransaction.deleteTextAndDecorator(
        paragraph: PieceParagraph,
        actionModel: TextEditorAction.TextRemoved
    ): Pair<TextRange, List<TextEditorListItemTransaction>> {
        return if (paragraph.piecesInSelectedRange.first().piece.isDecorator) {
            val decorSkip = maxOf(getOffsetAfterDecorator(paragraph, actionModel.offset), 0)
            val wouldOrphanOpeningDecorator = decorSkip > 0 &&
                actionModel.length >= text.length - actionModel.offset
            if (wouldOrphanOpeningDecorator) {
                val deleteTransaction = TextTransactionsUtils.deleteTransaction(actionModel.offset, actionModel.length)
                Pair(TextRange(actionModel.offset), listOf(deleteTransaction))
            } else {
                val newOffset = actionModel.offset + decorSkip
                val newLength = maxOf(actionModel.length - decorSkip, 0)
                val deleteTransaction = TextTransactionsUtils.deleteTransaction(newOffset, newLength)
                Pair(TextRange(newOffset), listOf(deleteTransaction))
            }
        } else {
            val deleteTransaction = TextTransactionsUtils.deleteTransaction(actionModel.offset, actionModel.length)
            val range = TextRange(actionModel.offset)

            Pair(range, listOf(deleteTransaction))
        }
    }

    private fun TextEditorTransaction.deleteEmptyParagraph(
        lines: MultiPieceParagraph,
        actionModel: TextEditorAction.TextRemoved
    ): Pair<TextRange, List<TextEditorListItemTransaction>> {
        val transactions = mutableListOf<TextEditorListItemTransaction>()
        val deleteTransaction = TextTransactionsUtils.deleteTransaction(actionModel.offset, actionModel.length)
        val range = TextRange(actionModel.offset)

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

        val mergedParagraphs = getLineContentWithNeighborParagraphs(paragraphOffset, nextParagraphOffset)
        val listParagraphs = mergedParagraphs.paragraphs.filter { it.paragraphType == paragraphType }
        val positionalItems = ListsConverter.fromPieceMultiParagraph(MultiPieceParagraph(listParagraphs, lines.start, lines.end))
        val reorderedItems = PositionalListItemUtils.reorderItems(positionalItems)

        return reorderedItems.createTransactions()
    }
}
