package com.jjrodcast.textkit.editor.core.transactions.text

import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.components.TextEditorListItem
import com.jjrodcast.textkit.editor.core.converters.ListsConverter
import com.jjrodcast.textkit.editor.core.converters.models.PositionalListItem
import com.jjrodcast.textkit.editor.core.converters.utils.PositionalListItemUtils
import com.jjrodcast.textkit.editor.core.converters.utils.createTransactions
import com.jjrodcast.textkit.editor.core.converters.utils.flatten
import com.jjrodcast.textkit.editor.core.models.MultiPieceParagraph
import com.jjrodcast.textkit.editor.core.models.PieceParagraph
import com.jjrodcast.textkit.editor.core.models.TextEditorModel
import com.jjrodcast.textkit.editor.core.piecetable.models.TextDecoratorModel
import com.jjrodcast.textkit.editor.core.piecetable.models.TextDecoratorModel.Companion.createDecoratorString
import com.jjrodcast.textkit.editor.core.piecetable.models.TextDecoratorModel.Companion.toLevel
import com.jjrodcast.textkit.editor.core.transactions.lists.models.TextEditorDecoratorTransactionType
import com.jjrodcast.textkit.editor.core.transactions.lists.models.TextEditorListItemTransaction
import com.jjrodcast.textkit.editor.utils.LINE_BREAK

internal object TextTransactionsUtils {

    internal fun insertTransaction(offset: Int, model: TextEditorModel): TextEditorListItemTransaction {
        return createTransaction(offset, TextEditorDecoratorTransactionType.Insert(model))
    }

    internal fun deleteTransaction(offset: Int, length: Int): TextEditorListItemTransaction {
        return createTransaction(offset, TextEditorDecoratorTransactionType.Delete(length))
    }

    internal fun updateTransaction(
        offset: Int,
        model: TextEditorModel,
        deleteLength: Int = model.text.length,
    ): TextEditorListItemTransaction {
        return createTransaction(offset, TextEditorDecoratorTransactionType.Update(model, deleteLength))
    }

    private fun createTransaction(
        offset: Int,
        type: TextEditorDecoratorTransactionType
    ): TextEditorListItemTransaction {
        return TextEditorListItemTransaction(offsetInDocument = offset, type = type)
    }

    internal fun getOffsetAfterDecorator(paragraph: PieceParagraph, offset: Int): Int {
        val decoratorOffset = paragraph.startOffset
        val decoratorLength = paragraph.startPiece.length
        val decoratorEndOffset = decoratorOffset + decoratorLength
        return decoratorEndOffset - offset
    }

    /**
     * After the user types a numbered-list marker on a plain paragraph that sits in a numbered
     * sequence (e.g. exiting an empty item with Enter, then typing `3. `), renumber the connected
     * block so following siblings stay in order.
     */
    internal fun numberedListReorderAfterPatternInsert(
        lines: MultiPieceParagraph,
        paragraph: PieceParagraph,
        insertedDecorator: TextDecoratorModel.NumberDecoratorModel,
    ): List<PositionalListItem> {
        val paragraphs = lines.paragraphs
        val currentIndex = paragraphs.indexOfFirst { it.startOffset == paragraph.startOffset }
        if (currentIndex < 0) return emptyList()

        val numberedIndices = paragraphs.indices.filter {
            paragraphs[it].isListItem && paragraphs[it].paragraphType == TextEditorListItem.NumberedList
        }
        if (numberedIndices.isEmpty()) return emptyList()

        val hasNumberedBefore = numberedIndices.any { it < currentIndex }
        val hasNumberedAfter = numberedIndices.any { it > currentIndex }
        if (!hasNumberedBefore && !hasNumberedAfter) return emptyList()

        val rangeStart = numberedIndices.filter { it < currentIndex }.maxOrNull() ?: currentIndex
        val rangeEnd = numberedIndices.filter { it > currentIndex }.maxOrNull()
            ?: numberedIndices.filter { it <= currentIndex }.maxOrNull()
            ?: currentIndex

        val decoratorText = insertedDecorator.createDecoratorString()
        val localItems = paragraphs.withIndex()
            .filter { (index, pieceParagraph) ->
                index in rangeStart..rangeEnd &&
                    (
                        (pieceParagraph.isListItem && pieceParagraph.paragraphType == TextEditorListItem.NumberedList) ||
                            index == currentIndex
                        )
            }
            .mapIndexed { index, (originalIndex, pieceParagraph) ->
                val richPiece = if (originalIndex == currentIndex) {
                    pieceParagraph.startPiece.copy(
                        decorator = insertedDecorator,
                        length = decoratorText.length,
                    )
                } else {
                    pieceParagraph.startPiece
                }
                PositionalListItem(
                    index = index,
                    richPiece = richPiece,
                    offsetInDocument = pieceParagraph.startOffset,
                )
            }

        if (localItems.size <= 1) return emptyList()
        return PositionalListItemUtils.reorderItems(localItems)
    }

    internal fun reorderListItemsOnUpdate(lines: MultiPieceParagraph): List<TextEditorListItemTransaction> {
        val currentParagraphIndex = lines.selectedParagraphIndices.first()
        val nextParagraphIndex = lines.selectedParagraphIndices.last() + 1

        val currentParagraph = lines.paragraphs.getOrNull(currentParagraphIndex)
        val nextParagraph = lines.paragraphs.getOrNull(nextParagraphIndex)

        val paragraphsAreListItems = currentParagraph?.isListItem ?: false && nextParagraph?.isListItem ?: false
        val paragraphsHaveSameListType = currentParagraph?.paragraphType == nextParagraph?.paragraphType

        return if (paragraphsAreListItems && paragraphsHaveSameListType) {
            val lastSelectedParagraph = lines.paragraphsInSelectedRange.last()
            val firstSelectedParagraphLevel = lines.paragraphsInSelectedRange.first().startPiece.decorator.toLevel()
            val nextParagraphLevel = nextParagraph.startPiece.decorator.toLevel()
            val lastSelectedParagraphLevel = lastSelectedParagraph.startPiece.decorator.toLevel()

            if (nextParagraphLevel < lastSelectedParagraphLevel) return emptyList()

            if (lastSelectedParagraphLevel == nextParagraphLevel) {
                updateSameLevelParagraphs(lines)
            } else {
                updateNestedLevelsParagraphs(lines, firstSelectedParagraphLevel, nextParagraphLevel)
            }
        } else {
            emptyList()
        }
    }

    private fun updateSameLevelParagraphs(lines: MultiPieceParagraph): List<TextEditorListItemTransaction> {
        // Remove selected elements from lines
        val selectedParagraphIndices =
            if (lines.selectedParagraphIndices.size == 1) lines.selectedParagraphIndices else lines.selectedParagraphIndices.drop(1)
        val positionalParagraphs = ListsConverter.convertToLocalListItems(lines)
        // Convert to HashSet so `index !in` is O(1) instead of O(S) per element — O(N) total vs O(N×S).
        val excludedIndices = HashSet(selectedParagraphIndices)
        // Reorder items
        val multiPieceParagraph = positionalParagraphs.filterIndexed { index, _ -> index !in excludedIndices }
        val reorderedParagraphs = PositionalListItemUtils.reorderItems(multiPieceParagraph)
        // Create transactions
        return reorderedParagraphs.createTransactions()
    }

    private fun updateNestedLevelsParagraphs(
        lines: MultiPieceParagraph,
        firstSelectedParagraphLevel: Int,
        nextParagraphLevel: Int,
    ): List<TextEditorListItemTransaction> {
        // Reduce nested levels
        val selectedParagraphIndices =
            if (lines.selectedParagraphIndices.size == 1) lines.selectedParagraphIndices else lines.selectedParagraphIndices.drop(1)
        val reductionLevel = if (nextParagraphLevel - firstSelectedParagraphLevel > 1) firstSelectedParagraphLevel + 1 else null
        val decreaseStartIndex = lines.selectedParagraphIndices.first() + 1
        val decreasedLevelParagraphs = PositionalListItemUtils.decreaseLevels(lines, listOf(decreaseStartIndex), reductionLevel)
        // Convert to HashSet so `index !in` is O(1) instead of O(S) per element — O(N) total vs O(N×S).
        val excludedIndices = HashSet(selectedParagraphIndices)
        val newUnselectedParagraphs = decreasedLevelParagraphs.flatten().filterIndexed { index, _ ->
            index !in excludedIndices
        }
        // Reorder items
        val reorderedParagraphs = PositionalListItemUtils.reorderItems(newUnselectedParagraphs)
        // Create transactions
        return reorderedParagraphs.createTransactions()
    }

    /**
     * This function creates transactions for the decorators deletion.
     *
     * When we delete a decorator, we need to check if the previous paragraph is of the same type as current.
     * if it is the same type we need to remove the decorator and the previous line break.
     * Otherwise, we just need to remove the decorator.
     *
     * Once we remove the decorator we need to reorder the items and create their transactions.
     *
     * @return A Pair with the new cursor position and a list of transactions [TextEditorListItemTransaction].
     */
    internal fun getCommonDeleteDecoratorTransactions(
        paragraph: PieceParagraph,
        lines: MultiPieceParagraph
    ): Pair<TextRange, List<TextEditorListItemTransaction>> {
        val decoratorPiece = paragraph.startPiece
        val previousLineBreakLength = "$LINE_BREAK".length
        val selectedIndex = lines.selectedParagraphIndices.first()
        val previousItemType = lines.paragraphs.getOrNull(selectedIndex - 1)?.paragraphType
        val previousItemLevel = lines.paragraphs.getOrNull(selectedIndex - 1)?.startPiece?.decorator?.level ?: 1
        val currentDecoratorLevel = paragraph.startPiece.decorator?.level ?: 1

        // A nested item can be the document's first paragraph (its level beats the defaulted
        // previous level of 1) — there is no previous line break to delete then, and subtracting
        // one would produce a negative offset.
        val needToDeletePreviousLineBreak = paragraph.startOffset >= previousLineBreakLength &&
            (previousItemType == paragraph.paragraphType || currentDecoratorLevel > previousItemLevel)
        val offset =
            if (needToDeletePreviousLineBreak) paragraph.startOffset - previousLineBreakLength else paragraph.startOffset
        val deleteLength = if (needToDeletePreviousLineBreak) decoratorPiece.length + previousLineBreakLength else decoratorPiece.length

        val deleteTransaction = deleteTransaction(offset, deleteLength)
        val range = TextRange(offset)

        val nextItemsTransactions = reorderListItemsOnUpdate(lines)
        val transactions = mutableListOf<TextEditorListItemTransaction>()

        transactions.addAll(nextItemsTransactions.plus(deleteTransaction))

        return Pair(range, transactions)
    }
}
