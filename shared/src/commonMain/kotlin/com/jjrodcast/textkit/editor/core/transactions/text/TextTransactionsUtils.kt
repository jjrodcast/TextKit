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
import com.jjrodcast.textkit.editor.utils.replaceLineBreakWith
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
    /**
     * The characters a just-matched list pattern already occupies in [paragraph] — what the marker
     * update must consume from the document. The validated line is the paragraph's literal text
     * plus the inserted text, so the document's share is exactly the paragraph's text (the marker
     * text for a task item being revalidated, mirroring the selection in matchesListItemPattern),
     * line breaks excluded. The previous per-platform arithmetic derived this from the NEW marker
     * string minus its key — i.e. the tab-prefix length — which equals the typed characters only
     * for a one-character trigger at level one: an insert carrying the whole pattern found nothing
     * to consume at the offset and instead swallowed the line's terminating break and sheared the
     * first character off the next item's marker (issue #138).
     */
    internal fun patternTextInDocumentLength(paragraph: PieceParagraph): Int {
        val paragraphText = if (paragraph.startPiece.decorator is TextDecoratorModel.TaskDecoratorModel) {
            paragraph.startText
        } else {
            paragraph.text
        }
        return paragraphText.replaceLineBreakWith("").length
    }

    internal fun numberedListReorderAfterPatternInsert(
        lines: MultiPieceParagraph,
        paragraph: PieceParagraph,
        insertedDecorator: TextDecoratorModel.NumberDecoratorModel,
    ): List<PositionalListItem> {
        val paragraphs = lines.paragraphs
        val currentIndex = paragraphs.indexOfFirst { it.startOffset == paragraph.startOffset }
        if (currentIndex < 0) return emptyList()

        val targetLevel = insertedDecorator.level
        val connectedRange = connectedNumberedSiblingRange(paragraphs, currentIndex, targetLevel)
            ?: return emptyList()

        val decoratorText = insertedDecorator.createDecoratorString()
        val localItems = paragraphs.withIndex()
            .filter { (index, pieceParagraph) ->
                index in connectedRange &&
                    (
                        index == currentIndex ||
                            isNumberedSiblingAtLevel(pieceParagraph, targetLevel)
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

        val startCount = localItems.firstNotNullOfOrNull { item ->
            item.richPiece.decorator?.toCount()
        } ?: 1
        return PositionalListItemUtils.reorderItems(localItems, start = startCount)
    }

    /**
     * Document-order span covering the plain paragraph being converted plus numbered siblings at
     * [targetLevel] reachable without crossing another list kind. Plain paragraphs are crossed
     * only as gaps; bullets/tasks/block items act as barriers so distant numbered items in the
     * neighbor window are not pulled into the same renumber pass.
     */
    private fun connectedNumberedSiblingRange(
        paragraphs: List<PieceParagraph>,
        currentIndex: Int,
        targetLevel: Int,
    ): IntRange? {
        fun isNumberedSibling(pieceParagraph: PieceParagraph) =
            isNumberedSiblingAtLevel(pieceParagraph, targetLevel)

        fun isBarrier(pieceParagraph: PieceParagraph) =
            pieceParagraph.isListItem && !isNumberedSibling(pieceParagraph)

        var hasNumberedBefore = false
        var probe = currentIndex - 1
        while (probe >= 0) {
            when {
                isBarrier(paragraphs[probe]) -> break
                isNumberedSibling(paragraphs[probe]) -> {
                    hasNumberedBefore = true
                    break
                }

                else -> probe--
            }
        }

        var hasNumberedAfter = false
        probe = currentIndex + 1
        while (probe < paragraphs.size) {
            when {
                isBarrier(paragraphs[probe]) -> break
                isNumberedSibling(paragraphs[probe]) -> {
                    hasNumberedAfter = true
                    break
                }

                else -> probe++
            }
        }

        if (!hasNumberedBefore && !hasNumberedAfter) return null

        var start = currentIndex
        probe = currentIndex - 1
        while (probe >= 0) {
            when {
                isBarrier(paragraphs[probe]) -> break
                isNumberedSibling(paragraphs[probe]) -> {
                    start = probe
                    probe--
                }

                else -> probe--
            }
        }

        var end = currentIndex
        probe = currentIndex + 1
        while (probe < paragraphs.size) {
            when {
                isBarrier(paragraphs[probe]) -> break
                isNumberedSibling(paragraphs[probe]) -> {
                    end = probe
                    probe++
                }

                else -> probe++
            }
        }

        return start..end
    }

    private fun isNumberedSiblingAtLevel(
        pieceParagraph: PieceParagraph,
        targetLevel: Int,
    ): Boolean =
        pieceParagraph.isListItem &&
            pieceParagraph.paragraphType == TextEditorListItem.NumberedList &&
            pieceParagraph.startPiece.decorator.toLevel() == targetLevel

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
