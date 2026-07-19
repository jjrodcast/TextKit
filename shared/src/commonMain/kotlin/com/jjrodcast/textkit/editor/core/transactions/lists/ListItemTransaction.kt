package com.jjrodcast.textkit.editor.core.transactions.lists

import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.components.TextEditorDecoratorItem
import com.jjrodcast.textkit.editor.components.TextEditorListItem.BulletedList
import com.jjrodcast.textkit.editor.components.TextEditorListItem.CheckList
import com.jjrodcast.textkit.editor.components.TextEditorListItem.None
import com.jjrodcast.textkit.editor.components.TextEditorListItem.NumberedList
import com.jjrodcast.textkit.editor.core.converters.ListsConverter
import com.jjrodcast.textkit.editor.core.converters.models.PositionalListItem
import com.jjrodcast.textkit.editor.core.converters.models.PositionalListItem.Companion.getNewDecoratorLength
import com.jjrodcast.textkit.editor.core.converters.utils.PositionalListItemUtils
import com.jjrodcast.textkit.editor.core.converters.utils.createTransactions
import com.jjrodcast.textkit.editor.core.models.MultiPieceParagraph
import com.jjrodcast.textkit.editor.core.models.PieceParagraph
import com.jjrodcast.textkit.editor.core.models.TextEditorModel
import com.jjrodcast.textkit.editor.core.piecetable.models.RichPiece
import com.jjrodcast.textkit.editor.core.piecetable.models.Source
import com.jjrodcast.textkit.editor.core.piecetable.models.TextDecoratorModel.BulletDecoratorModel
import com.jjrodcast.textkit.editor.core.piecetable.models.TextDecoratorModel.Companion.createDecoratorString
import com.jjrodcast.textkit.editor.core.piecetable.models.TextDecoratorModel.Companion.toLevel
import com.jjrodcast.textkit.editor.core.piecetable.models.TextDecoratorModel.Companion.toTextEditorListItem
import com.jjrodcast.textkit.editor.core.piecetable.models.TextDecoratorModel.NumberDecoratorModel
import com.jjrodcast.textkit.editor.core.piecetable.models.TextDecoratorModel.TaskDecoratorModel
import com.jjrodcast.textkit.editor.core.transactions.TextEditorTransaction
import com.jjrodcast.textkit.editor.core.transactions.lists.models.TextEditorDecoratorLine
import com.jjrodcast.textkit.editor.core.transactions.lists.models.TextEditorDecoratorTransactionType.Delete
import com.jjrodcast.textkit.editor.core.transactions.lists.models.TextEditorDecoratorTransactionType.Insert
import com.jjrodcast.textkit.editor.core.transactions.lists.models.TextEditorDecoratorTransactionType.Update
import com.jjrodcast.textkit.editor.core.transactions.lists.models.TextEditorListItemTransaction
import com.jjrodcast.textkit.editor.core.transactions.lists.utils.ListItemTextEditorRangeUtils
import com.jjrodcast.textkit.editor.core.transactions.text.TextTransactionsUtils

internal object ListItemTransaction {

    internal fun toggleParagraphsToListItems(
        transaction: TextEditorTransaction,
        prevListItem: TextEditorDecoratorItem,
        listItem: TextEditorDecoratorItem,
        range: TextRange
    ): Pair<Boolean, TextRange> {
        val originalLines = transaction.getLineContentWithNeigborParagraphs(range.min, range.max)

        val lines = MultiPieceParagraph(paragraphs = originalLines.paragraphs, start = originalLines.start, end = originalLines.end)
        val selectedParagraphs = originalLines.paragraphsInSelectedRange.size

        val (transactions, newRange) = when {
            range.collapsed || selectedParagraphs == 1 -> applyCollapsedChanges(lines, prevListItem, range, listItem)
            else -> applyRangeChanges(lines, prevListItem, range, listItem)
        }
        val hasChanges = transaction.commitChanges(transactions)

        return Pair(hasChanges, newRange)
    }

    //region Collapsed
    private fun applyCollapsedChanges(
        lines: MultiPieceParagraph,
        prevListItem: TextEditorDecoratorItem,
        range: TextRange,
        listItem: TextEditorDecoratorItem
    ): Pair<List<TextEditorListItemTransaction>, TextRange> {
        val filteredLines = lines.paragraphsInSelectedRange.filter { !it.isAtEndOfParagraph(range.start, range.end) }
        val newItems =
            if (range.collapsed && filteredLines.isEmpty()) listOfNotNull(lines.findParagraphBy(range.end + 1)) else filteredLines
        val items = newItems.map(::transformToTextEditorDecoratorLine)

        // Empty paragraph cases
        if (items.isEmpty()) {
            val newDecoratorMarksModel = listItem.toTextDecoratorModel(level = 1)
            val textMarksModel = TextEditorModel.create(
                text = newDecoratorMarksModel.createDecoratorString(),
                decorator = newDecoratorMarksModel
            )
            val transactions = listOf(TextTransactionsUtils.insertTransaction(range.start, textMarksModel))
            val newRange = TextRange(range.end + textMarksModel.text.length)
            return Pair(transactions, newRange)
        }

        val currentItem = items.first()
        val transformedParagraphs = lines.paragraphs.map(::transformToTextEditorDecoratorLine)
        val currentIndex = transformedParagraphs.indexOf(currentItem)
        val currentListItem = listItem.toFinalListItemType(prevListItem, currentItem.piece.decorator.toLevel(0))

        return if (prevListItem == None) {
            // Changing paragraph to list item
            updateParagraphToListItem(lines, currentIndex, currentListItem, range)
        } else if (currentListItem == None) {
            // Changing list item to paragraph. This only happens when the change is made in the level 1
            updateListItemToParagraph(lines, currentIndex, range)
        } else {
            // change list item type or levels
            updateNestedListItems(currentIndex, lines, prevListItem, currentListItem, range)
        }
    }

    private fun updateParagraphToListItem(
        lines: MultiPieceParagraph,
        currentIndex: Int,
        currListItem: TextEditorDecoratorItem,
        range: TextRange
    ): Pair<List<TextEditorListItemTransaction>, TextRange> {
        // Updating the decorator of the current paragraph — functional update, no mutation.
        val updatedParagraphs = lines.paragraphs.toMutableList()
        updatedParagraphs[currentIndex] = updatedParagraphs[currentIndex].addDecoratorIfNull(currListItem)
        val updatedLines = lines.copy(paragraphs = updatedParagraphs)
        val flattenItems = PositionalListItemUtils.reorderItems(multiPieceParagraph = updatedLines, coerceLevel = true)
        val modifiedItem = flattenItems.firstOrNull { it.index == currentIndex }
        val transactions = flattenItems.createTransactions(listOf(modifiedItem))
        val offset = modifiedItem.getNewDecoratorLength()
        return Pair(transactions, TextRange(start = range.start + offset, end = range.end + offset))
    }

    private fun PieceParagraph.addDecoratorIfNull(currListItem: TextEditorDecoratorItem): PieceParagraph {
        if (startPiece.decorator != null) return this
        val level = if (currListItem in listOf(BulletedList, CheckList)) 0 else 1
        val newDecorator = currListItem.toTextDecoratorModel(count = 0, level = level)
        val newModel = TextEditorModel.create(
            piece = RichPiece(Source.ADDED, startOffset, newDecorator?.length ?: 0, newDecorator),
            text = newDecorator.createDecoratorString()
        )
        return copy(pieces = listOf(newModel) + pieces)
    }

    private fun RichPiece.addDecoratorIfNull(currListItem: TextEditorDecoratorItem, restartLevel: Boolean = false): RichPiece {
        return if (decorator == null) {
            val level = if (currListItem == BulletedList) 0 else 1
            val newDecorator = currListItem.toTextDecoratorModel(count = 0, level = if (restartLevel) 0 else level)
            val newLength = newDecorator?.length ?: 0
            copy(source = Source.ADDED, offset = this.offset, decorator = newDecorator, length = newLength)
        } else {
            this
        }
    }

    private fun updateListItemToParagraph(
        lines: MultiPieceParagraph,
        currentIndex: Int,
        range: TextRange
    ): Pair<List<TextEditorListItemTransaction>, TextRange> {
        val positionalListItems = PositionalListItemUtils.decreaseLevels(lines, listOf(currentIndex))
        val flattenItems = PositionalListItemUtils.reorderItems(items = positionalListItems, coerceLevel = false)
        val transactions = flattenItems.createTransactions()
        val length = flattenItems.firstOrNull { it.index == currentIndex }.getNewDecoratorLength()
        // Clamp both endpoints at 0: for the first item `range.start` is at/near the document
        // start, so subtracting the removed decorator width would otherwise underflow and make
        // `TextRange` throw. For any non-first item `range.start - length` is already >= 0, so
        // the coercion is a no-op there.
        return Pair(
            transactions,
            TextRange(
                start = (range.start - length).coerceAtLeast(0),
                end = (range.end - length).coerceAtLeast(0)
            )
        )
    }

    private fun updateNestedListItems(
        index: Int,
        lines: MultiPieceParagraph,
        prevListItem: TextEditorDecoratorItem,
        currListItem: TextEditorDecoratorItem,
        range: TextRange
    ): Pair<List<TextEditorListItemTransaction>, TextRange> {
        // Reducing levels
        return if (prevListItem == currListItem) {
            val positionalListItems = PositionalListItemUtils.decreaseLevels(lines, listOf(index))
            val flattenItems = PositionalListItemUtils.reorderItems(items = positionalListItems)
            val transactions = flattenItems.createTransactions()
            Pair(transactions, range)
        } else {
            // Here we have: prevListItem != currListItem
            // change from numbered to bulleted or bulleted to numbered
            // We don't need to change the levels because we are changing the type of list item
            // we just need to change the numbers or bulletes
            val positionalListItem = PositionalListItemUtils.replaceDecorator(lines, index, currListItem)
            val flattenItems = PositionalListItemUtils.reorderItems(items = positionalListItem, coerceLevel = false)
            val transactions = flattenItems.createTransactions()
            // replaceDecorator returns a same-level subset re-indexed from 0, so `index`
            // (position in lines.paragraphs) may exceed the re-index of the user's item
            // when nested items at other levels appear before it in lines.paragraphs.
            // Resolve the correct position by matching the user item's document offset.
            val userItemOffset = lines.paragraphs[index].startOffset
            val reIndexedIndex = flattenItems.indexOfFirst { it.offsetInDocument == userItemOffset }
                .coerceAtLeast(0)
            val newRange = ListItemTextEditorRangeUtils.getTextEditorRangeForCollapsed(flattenItems, reIndexedIndex, range)
            Pair(transactions, newRange)
        }
    }
    //endregion

    //region Range
    private fun applyRangeChanges(
        lines: MultiPieceParagraph,
        prevListItem: TextEditorDecoratorItem,
        range: TextRange,
        listItem: TextEditorDecoratorItem,
        onListItemChanged: (() -> Unit)? = null
    ): Pair<List<TextEditorListItemTransaction>, TextRange> {
        val filteredParagraphs = lines.paragraphsInSelectedRange.filter { it.findPiecesInRange(range.start, range.end).isNotEmpty() }
        return when (filteredParagraphs.all { it.isListItem }) {
            true -> {
                if (checkIfAllAreSameType(lines)) {
                    // We are removing or changing decorators
                    applyAllItemsChanges(lines, prevListItem, listItem, range)
                } else {
                    val newPrevListItem = lines.paragraphsInSelectedRange.first().startPiece.decorator.toTextEditorListItem()
                    applyDifferentItemsChanges(lines, newPrevListItem, listItem, range, onListItemChanged)
                }
            }

            else -> {
                // All the items are paragraphs (so we need to create the list items) (Easy case)
                if (checkIfAllAreParagraphs(lines)) {
                    applyChangesToMultipleParagraphs(lines, listItem, range)
                } else {
                    // Here we are including paragraphs and list items (many groups) (Complex Case)
                    applyMultipleParagraphsLevels(lines, listItem, range)
                }
            }
        }
    }

    private fun applyAllItemsChanges(
        lines: MultiPieceParagraph,
        prevListItem: TextEditorDecoratorItem,
        listItem: TextEditorDecoratorItem,
        range: TextRange
    ): Pair<List<TextEditorListItemTransaction>, TextRange> {
        val selectedParagraphs = lines.paragraphsInSelectedRange.filter { it.findPiecesInRange(range.start, range.end).isNotEmpty() }
        val currentItem = selectedParagraphs.first()
        val currentListItem = listItem.toFinalListItemType(prevListItem, currentItem.startPiece.decorator.toLevel(0))

        return if (currentListItem == None) {
            // Removing decorator
            val positionalListItem = PositionalListItemUtils.decreaseLevels(lines, lines.selectedParagraphIndices)
            val flattenItems = PositionalListItemUtils.reorderItems(items = positionalListItem, coerceLevel = false)
            val transactions = flattenItems.createTransactions()
            val newRange = ListItemTextEditorRangeUtils.getTextEditorRangeForRange(flattenItems, lines.selectedParagraphIndices, range)
            Pair(transactions, newRange)
        } else {
            // Numbered to bulleted or bulleted to numbered
            val positionalListItem = PositionalListItemUtils.replaceDecorators(lines, lines.selectedParagraphIndices, currentListItem)
            val flattenItems = PositionalListItemUtils.reorderItems(items = positionalListItem, coerceLevel = false)
            val transactions = flattenItems.createTransactions()
            val newRange = ListItemTextEditorRangeUtils.getTextEditorRangeForRange(flattenItems, lines.selectedParagraphIndices, range)
            Pair(transactions, newRange)
        }
    }

    private fun applyDifferentItemsChanges(
        lines: MultiPieceParagraph,
        prevListItem: TextEditorDecoratorItem,
        listItem: TextEditorDecoratorItem,
        range: TextRange,
        onListItemChanged: (() -> Unit)? = null
    ): Pair<List<TextEditorListItemTransaction>, TextRange> {
        // We need to change the type of all items in the selection using the currentListItem type
        val selectedParagraphs = lines.paragraphsInSelectedRange.filter { it.findPiecesInRange(range.start, range.end).isNotEmpty() }
        val currentItem = selectedParagraphs.first()
        val currentLevel = currentItem.startPiece.decorator.toLevel(0)
        val currentListItem = listItem.toFinalListItemType(prevListItem, currentLevel)

        // if the prevListItem and the listItem are the same this means that
        // we already have the same type selected, and now we are removing the decorators
        return if (prevListItem == listItem) {
            val positionalListItem = PositionalListItemUtils.decreaseLevels(lines, lines.findSelectedParagraphIndicesByLevel(currentLevel))
            val flattenItems = PositionalListItemUtils.reorderItems(items = positionalListItem, coerceLevel = false)
            val transactions = flattenItems.createTransactions()
            onListItemChanged?.invoke()
            val newRange = ListItemTextEditorRangeUtils.getTextEditorRangeForRange(flattenItems, lines.selectedParagraphIndices, range)
            Pair(transactions, newRange)
        } else if (listItem == None) {
            // We are removing the items
            // If we want to remove we just need to change the level to 0 (zero)
            val positionalListItem = PositionalListItemUtils.decreaseLevels(lines, lines.findSelectedParagraphIndicesByLevel(currentLevel))
            val flattenItems = PositionalListItemUtils.reorderItems(items = positionalListItem, coerceLevel = false)
            val transactions = flattenItems.createTransactions()
            val newRange = ListItemTextEditorRangeUtils.getTextEditorRangeForRange(flattenItems, lines.selectedParagraphIndices, range)
            Pair(transactions, newRange)
        } else {
            // We are changing the type of the list item
            val positionalListItem =
                PositionalListItemUtils.replaceDecorators(lines, lines.findSelectedParagraphIndicesByLevel(currentLevel), currentListItem)
            val flattenItems = PositionalListItemUtils.reorderItems(items = positionalListItem, coerceLevel = false)
            val transactions = flattenItems.createTransactions()
            val newRange = ListItemTextEditorRangeUtils.getTextEditorRangeForRange(flattenItems, lines.selectedParagraphIndices, range)
            Pair(transactions, newRange)
        }
    }

    private fun applyChangesToMultipleParagraphs(
        lines: MultiPieceParagraph,
        listItem: TextEditorDecoratorItem,
        range: TextRange
    ): Pair<List<TextEditorListItemTransaction>, TextRange> {
        val selectedParagraphs = lines.paragraphsInSelectedRange.filter { it.findPiecesInRange(range.start, range.end).isNotEmpty() }.toSet()
        val updatedLines = lines.copy(
            paragraphs = lines.paragraphs.map { if (it in selectedParagraphs) it.addDecoratorIfNull(listItem) else it }
        )
        val flattenItems = PositionalListItemUtils.reorderItems(multiPieceParagraph = updatedLines)
        val modifiedItems = flattenItems.filter { it.index in lines.selectedParagraphIndices }
        val transactions = flattenItems.createTransactions(modifiedItems)
        val newRange = ListItemTextEditorRangeUtils.getTextEditorRangeForInsertedRange(flattenItems, lines.selectedParagraphIndices, range)
        return Pair(transactions, newRange)
    }

    private fun applyMultipleParagraphsLevels(
        lines: MultiPieceParagraph,
        listItem: TextEditorDecoratorItem,
        range: TextRange
    ): Pair<List<TextEditorListItemTransaction>, TextRange> {
        val positionalListItems = ListsConverter.convertToLocalListItems(lines)

        // Cache once — selectedParagraphIndices is a computed getter (O(P)) accessed 6 times originally.
        val selectedIndices = lines.selectedParagraphIndices

        // First selected index that is a plain paragraph (no decorator).
        val firstParagraphIndex = selectedIndices.firstOrNull { positionalListItems[it].richPiece.decorator == null }
            ?: return Pair(emptyList(), range)

        // Items before the selection — not modified.
        val startItems = positionalListItems.subList(0, selectedIndices.first())

        // Single-pass categorization over selected indices — replaces 3 separate mapNotNull + partition.
        val prevElements = mutableListOf<PositionalListItem>()
        val nextElements = arrayListOf<PositionalListItem>()
        val nextTaskListElements = mutableListOf<PositionalListItem>()
        val nextIndices = arrayListOf<Int>() // indices of nextElements items (passed to increaseLevels)
        val excludedIndices = arrayListOf<Int>() // indices of task-list items (excluded from increaseLevels)

        for (index in selectedIndices) {
            val item = positionalListItems[index]
            when {
                index < firstParagraphIndex -> prevElements.add(item)
                item.richPiece.decorator is TaskDecoratorModel -> {
                    nextTaskListElements.add(item)
                    excludedIndices.add(item.index)
                }

                else -> {
                    nextElements.add(item)
                    nextIndices.add(item.index)
                }
            }
        }

        // Collect trailing list items after the last selected index (in-place, no extra list copy).
        val lastSelectedIndex = selectedIndices.last()
        if (positionalListItems[lastSelectedIndex].richPiece.decorator != null) {
            var index = lastSelectedIndex + 1
            while (index < lines.paragraphs.size) {
                val item = positionalListItems[index]
                val itemLevel = item.richPiece.decorator.toLevel(0)
                if (itemLevel < 1 || item.richPiece.decorator is TaskDecoratorModel) break
                nextElements.add(item)
                nextIndices.add(item.index)
                index++
            }
        }

        // Paragraph positions within nextElements that will receive a new decorator (for transaction tracking).
        val selectedParagraphIndices = nextElements.mapIndexedNotNull { idx, item -> if (item.richPiece.decorator == null) idx else null }

        // Mutate newRichPiece in-place for prevElements that need a type change.
        prevElements.forEach { item ->
            if (item.richPiece.decorator.toTextEditorListItem() != listItem) {
                val decorator = listItem.toTextDecoratorModel(level = item.getLevel())
                item.newRichPiece = item.richPiece.copy(
                    source = Source.ADDED,
                    offset = item.offsetInDocument,
                    decorator = decorator,
                    length = decorator?.length ?: 0
                )
            }
        }

        // Add decorator to plain paragraphs in nextElements.
        val nextElementsWithDecorators = nextElements.map {
            if (it.richPiece.decorator == null) {
                val newRichPiece = it.richPiece.copy().addDecoratorIfNull(listItem, restartLevel = true)
                it.copy(
                    richPiece = it.richPiece.copy(length = newRichPiece.length, decorator = newRichPiece.decorator),
                    offsetInDocument = it.offsetInDocument
                )
            } else it.copy(richPiece = it.richPiece.copy())
        }

        // Recreate the list items — nextIndices/excludedIndices built during categorization (no extra map calls).
        val itemsWithChangedLevels = PositionalListItemUtils.increaseLevels(nextElementsWithDecorators, nextIndices, excludedIndices)

        val newPositionalListItems = ListsConverter.fromPositionalListItems(startItems + prevElements + itemsWithChangedLevels)
        val flattenItems = PositionalListItemUtils.reorderItems(items = newPositionalListItems)

        val selectedParagraphs = selectedParagraphIndices.map { flattenItems[startItems.size + prevElements.size + it] }
        val transactions = flattenItems.createTransactions(selectedParagraphs)
        val newRange = ListItemTextEditorRangeUtils.getTextEditorRangeForMultiRange(flattenItems, selectedIndices, range)
        return Pair(transactions, newRange)
    }
    //endregion
}

private fun checkIfAllAreSameType(lines: MultiPieceParagraph): Boolean {
    val firstItem = lines.paragraphsInSelectedRange.first()
    val currentLevel = firstItem.startPiece.decorator.toLevel(0)
    val countSameType = lines.paragraphsInSelectedRange
        .filter { it.startPiece.decorator.toLevel(0) == currentLevel }
        .count { it.paragraphType == firstItem.paragraphType }
    return countSameType == lines.paragraphsInSelectedRange.size
}

private fun checkIfAllAreParagraphs(lines: MultiPieceParagraph) = lines.paragraphsInSelectedRange.all { it.paragraphType is None }

private fun TextEditorTransaction.commitChanges(transactions: List<TextEditorListItemTransaction>): Boolean {
    transactions
        .sortedByDescending { it.offsetInDocument }
        .forEach { transaction ->
            when (transaction.type) {
                is Insert -> insert(transaction.type.model, transaction.offsetInDocument)
                is Update -> update(transaction.offsetInDocument, transaction.type.length, transaction.type.model)
                is Delete -> delete(transaction.offsetInDocument, transaction.type.length)
            }
        }
    return transactions.isNotEmpty()
}

private fun transformToTextEditorDecoratorLine(paragraph: PieceParagraph): TextEditorDecoratorLine {
    return TextEditorDecoratorLine(
        piece = paragraph.startPiece,
        offsetInDocument = paragraph.startOffset,
        type = when (paragraph.startPiece.decorator) {
            is BulletDecoratorModel -> BulletedList
            is NumberDecoratorModel -> NumberedList
            is TaskDecoratorModel -> CheckList
            else -> None
        }
    )
}
