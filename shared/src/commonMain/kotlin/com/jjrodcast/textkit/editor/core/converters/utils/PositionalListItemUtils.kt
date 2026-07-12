package com.jjrodcast.textkit.editor.core.converters.utils

import com.jjrodcast.textkit.editor.components.TextEditorDecoratorItem
import com.jjrodcast.textkit.editor.core.converters.ListsConverter
import com.jjrodcast.textkit.editor.core.converters.models.PositionalListItem
import com.jjrodcast.textkit.editor.core.models.MultiPieceParagraph
import com.jjrodcast.textkit.editor.core.models.TextEditorModel
import com.jjrodcast.textkit.editor.core.piecetable.models.TextDecoratorModel
import com.jjrodcast.textkit.editor.core.piecetable.models.TextDecoratorModel.Companion.createDecoratorString
import com.jjrodcast.textkit.editor.core.piecetable.models.TextDecoratorModel.Companion.toLevel
import com.jjrodcast.textkit.editor.core.piecetable.models.TextDecoratorModel.Companion.toNewDecoratorModel
import com.jjrodcast.textkit.editor.core.piecetable.models.TextDecoratorModel.Companion.toTextEditorListItem
import com.jjrodcast.textkit.editor.core.transactions.lists.models.TextEditorDecoratorTransactionType.Delete
import com.jjrodcast.textkit.editor.core.transactions.lists.models.TextEditorDecoratorTransactionType.Insert
import com.jjrodcast.textkit.editor.core.transactions.lists.models.TextEditorDecoratorTransactionType.Update
import com.jjrodcast.textkit.editor.core.transactions.lists.models.TextEditorListItemTransaction
import com.jjrodcast.textkit.editor.utils.fastForEach
import com.jjrodcast.textkit.editor.utils.fastMap

internal object PositionalListItemUtils {

    internal fun replaceDecorators(lines: MultiPieceParagraph, indices: List<Int>, listItem: TextEditorDecoratorItem): List<PositionalListItem> {
        val items = ListsConverter.convertToLocalListItems(lines)
        val level = lines.paragraphsInSelectedRange.first().startPiece.decorator.toLevel()
        items.fastForEach {
            if (it.index in indices) {
                val count = it.richPiece.decorator?.toCount() ?: 0
                it.newRichPiece = it.richPiece.copy(decorator = listItem.toTextDecoratorModel(count, level))
            }
        }
        return ListsConverter.createPositionalItemsFrom(items)
    }

    internal fun replaceDecorator(lines: MultiPieceParagraph, index: Int, listItem: TextEditorDecoratorItem): List<PositionalListItem> {
        val level = lines.paragraphs[index].startPiece.decorator.toLevel()
        val paragraphs = lines.searchSameLevelItems(index, level, keepSearching = level == 1)
        val targetOffset = lines.paragraphs[index].startOffset
        val items = ListsConverter.fromPieceMultiParagraph(MultiPieceParagraph(paragraphs, lines.start, lines.end))
        items.fastForEach {
            if (it.offsetInDocument == targetOffset) {
                val count = it.richPiece.decorator?.toCount() ?: 0
                it.newRichPiece = it.richPiece.copy(decorator = listItem.toTextDecoratorModel(count, level))
            }
        }
        return items
    }

    internal fun increaseLevels(lines: MultiPieceParagraph, indices: List<Int>): List<PositionalListItem> {
        val items = ListsConverter.fromPieceMultiParagraph(lines)
        items.increaseLevelsInplace(indices)
        return items
    }

    internal fun increaseLevels(
        positionalListItem: List<PositionalListItem>,
        indices: List<Int>,
        excludedIndices: List<Int> = emptyList()
    ): List<PositionalListItem> {
        positionalListItem.increaseLevelsInplace(indices, excludedIndices)
        return positionalListItem
    }

    private fun increaseLevel(root: PositionalListItem, excludedIndices: List<Int> = emptyList()) {
        if (root.index in excludedIndices) return
        val queue = ArrayDeque<PositionalListItem>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val item = queue.removeAt(0)
            val decorator = item.richPiece.decorator?.copyValue(level = item.getLevel(0) + 1)
            item.newRichPiece = item.richPiece.copy(decorator = decorator, length = decorator.createDecoratorString().length)
            queue.addAll(item.positionalListItems)
        }
    }

    internal fun decreaseLevels(lines: MultiPieceParagraph, indices: List<Int>, level: Int? = null): List<PositionalListItem> {
        val items = ListsConverter.fromPieceMultiParagraph(lines)
        items.decreaseLevelsInplace(indices, level)
        return items
    }

    private fun decreaseLevel(root: PositionalListItem, level: Int?) {
        val queue = ArrayDeque<PositionalListItem>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val item = queue.removeAt(0)
            val decorator = item.richPiece.decorator?.copyValue(level = level ?: (item.getLevel() - 1))
            item.newRichPiece = item.richPiece.copy(decorator = decorator, length = decorator.createDecoratorString().length)
            queue.addAll(item.positionalListItems)
        }
    }

    internal fun reorderItems(items: List<PositionalListItem>, start: Int = 1, coerceLevel: Boolean = true): List<PositionalListItem> {
        // flatten() gives document order regardless of current nesting structure (O(N)).
        // reorderItemsFlatInternal replaces the previous fromPositionalListItems (flatten +
        // re-index + rebuild nested tree) + reorderItemsInternal (traverse tree + flatten again)
        // — eliminating one complete O(N) tree build and one O(N) tree traversal per call.
        val flat = items.flatten()
        if (flat.isEmpty()) return flat
        val minLevel = flat.minOf { it.getLevel() }
        return reorderItemsFlatInternal(flat, start, minLevel, coerceLevel)
    }

    internal fun findItemByOffset(items: List<PositionalListItem>, offset: Int): PositionalListItem? {
        if (items.isEmpty()) return null
        // Binary search: find the rightmost item with offsetInDocument <= offset.
        var lo = 0
        var hi = items.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (items[mid].offsetInDocument <= offset) lo = mid else hi = mid - 1
        }
        val item = items[lo]
        val currentItemStart = item.offsetInDocument
        val currentItemEnd = currentItemStart + item.richPiece.length
        return when {
            offset in currentItemStart until currentItemEnd -> item
            offset < currentItemStart -> {
                // offset landed before the first item; check if it falls before children start.
                if (item.positionalListItems.isNotEmpty()) {
                    val nextItemStart = item.positionalListItems.first().offsetInDocument
                    if (offset < nextItemStart) item else null
                } else null
            }

            item.positionalListItems.isNotEmpty() ->
                findItemByOffset(item.positionalListItems, offset)

            else -> null
        }
    }

    internal fun findSameLevelItems(items: List<PositionalListItem>, startIndex: Int): List<PositionalListItem> {
        val result = mutableListOf<PositionalListItem>()
        val target = items[startIndex]

        for (i in startIndex + 1 until items.size) {
            val currentItem = items[i]
            when {
                currentItem.level > target.level -> continue // hijo anidado, ignorar
                currentItem.level < target.level -> break // subió de nivel, diferente rama
                currentItem.type != target.type -> break // mismo nivel, distinto tipo de lista
                else -> result.add(currentItem)
            }
        }

        return result
    }

    internal fun reorderItems(
        multiPieceParagraph: MultiPieceParagraph,
        start: Int = 1,
        coerceLevel: Boolean = true
    ): List<PositionalListItem> {
        // convertToLocalListItems produces a flat list directly (no tree build).
        // reorderItemsFlatInternal replaces the previous fromPieceMultiParagraph (flat →
        // build nested tree) + reorderItemsInternal (traverse tree + flatten again) —
        // eliminating one complete O(N) tree build and one O(N) tree traversal per call.
        val flat = ListsConverter.convertToLocalListItems(multiPieceParagraph)
        if (flat.isEmpty()) return flat
        val minLevel = flat.minOf { it.getLevel() }
        return reorderItemsFlatInternal(flat, start, minLevel, coerceLevel)
    }

    /**
     * Renumbers a **flat** document-order list of [PositionalListItem]s in a single O(N) pass,
     * replacing the previous recursive [reorderItemsInternal] that required a pre-built nested tree.
     *
     * Algorithm: maintain [countByDepth] — a map from item level → next count to assign at
     * that level. Root items (level == [minLevel]) start at [start]; all deeper levels start
     * at 1. After processing each item at depth `d`, [countByDepth][d + 1] is reset to 1 so
     * that children of the NEXT parent at depth `d` always restart their own counter.
     *
     * This matches the semantics of the old recursive approach:
     * - [NumberDecoratorModel] siblings share an incrementing counter.
     * - Any other decorator type resets the sibling counter to 1.
     * - Each group of children starts independently at 1 (or [start] for root).
     */
    private fun reorderItemsFlatInternal(
        items: List<PositionalListItem>,
        start: Int,
        minLevel: Int,
        coerceLevel: Boolean
    ): List<PositionalListItem> {
        val result = ArrayList<PositionalListItem>(items.size)
        val countByDepth = HashMap<Int, Int>()
        countByDepth[minLevel] = start
        items.fastForEach { item ->
            val depth = item.getLevel()
            val count = countByDepth.getOrElse(depth) { 1 }
            val decorator = item.newRichPiece.decorator?.toNewDecoratorModel(count = count, coerceLevel = coerceLevel)
            if (decorator != null) {
                item.newRichPiece = item.newRichPiece.copy(decorator = decorator, length = decorator.createDecoratorString().length)
            }
            if (decorator is TextDecoratorModel.NumberDecoratorModel) {
                countByDepth[depth] = count + 1
            } else {
                countByDepth[depth] = 1
            }
            // Children of this item always start their own counter at 1.
            countByDepth[depth + 1] = 1
            result.add(item.copy(positionalListItems = ArrayList(0)))
        }
        return result
    }

    private fun List<PositionalListItem>.increaseLevelsInplace(
        indices: List<Int>,
        excludedIndices: List<Int> = emptyList()
    ): List<PositionalListItem> {
        fastForEach {
            it.positionalListItems.increaseLevelsInplace(indices, excludedIndices)
            if (it.index in indices) {
                increaseLevel(it, excludedIndices)
            } else return@fastForEach
        }
        return this
    }

    private fun List<PositionalListItem>.decreaseLevelsInplace(indices: List<Int>, level: Int?): List<PositionalListItem> {
        fastForEach {
            it.positionalListItems.decreaseLevelsInplace(indices, level)
            if (it.index in indices) {
                decreaseLevel(it, level)
            } else return@fastForEach
        }
        return this
    }
}

/**
 * Returns a flat document-order list of all [PositionalListItem]s in this tree.
 *
 * The previous implementation used mutual recursion (flatten calls flatten on each child list),
 * which risked a StackOverflowError for deeply nested documents. This iterative version uses an
 * explicit [ArrayDeque] stack and processes nodes in the same pre-order (parent before children)
 * as the recursive version, with O(N) time and O(depth) stack space.
 */
internal fun List<PositionalListItem>.flatten(): List<PositionalListItem> {
    val result = ArrayList<PositionalListItem>(size)
    val stack = ArrayDeque<PositionalListItem>()
    for (i in indices.reversed()) stack.addLast(this[i])
    while (stack.isNotEmpty()) {
        val item = stack.removeAt(stack.lastIndex)
        result.add(item.copy(positionalListItems = ArrayList(0)))
        for (i in item.positionalListItems.indices.reversed()) {
            stack.addLast(item.positionalListItems[i])
        }
    }
    return result
}

internal fun List<PositionalListItem>.createTransactions(currentItems: List<PositionalListItem?> = emptyList()): List<TextEditorListItemTransaction> {
    // Convert to HashSet upfront so that `element in currentItemsSet` is O(1) instead of
    // O(M) per element (List.contains). Total: O(N) vs previous O(N × M).
    val currentItemsSet = if (currentItems.isEmpty()) emptySet() else HashSet(currentItems)
    return filter { it.modified }
        .fastMap { element ->
            val newDecorator = element.newRichPiece.decorator
            val model = TextEditorModel.create(
                text = newDecorator.createDecoratorString(),
                decorator = newDecorator,
                paragraphType = newDecorator.toTextEditorListItem()
            )

            val type = when {
                currentItemsSet.isEmpty() -> if (element.newRichPiece.decorator.toLevel() == 0) {
                    Delete(element.richPiece.length)
                } else {
                    Update(model, element.richPiece.length)
                }

                element in currentItemsSet -> Insert(model)
                else -> Update(model, element.richPiece.length)
            }

            TextEditorListItemTransaction(offsetInDocument = element.offsetInDocument, type = type)
        }
}
