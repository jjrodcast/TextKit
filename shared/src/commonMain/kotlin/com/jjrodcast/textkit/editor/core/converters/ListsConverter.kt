package com.jjrodcast.textkit.editor.core.converters

import com.jjrodcast.textkit.editor.core.converters.models.PositionalListItem
import com.jjrodcast.textkit.editor.core.converters.utils.flatten
import com.jjrodcast.textkit.editor.core.models.MultiPieceParagraph
import com.jjrodcast.textkit.editor.utils.fastForEach
import com.jjrodcast.textkit.editor.utils.fastMapIndexed

internal object ListsConverter {

    internal fun fromPieceMultiParagraph(multiparagraphs: MultiPieceParagraph): List<PositionalListItem> {
        val localListItems = multiparagraphs.paragraphs.fastMapIndexed { index, paragraph ->
            PositionalListItem(index = index, richPiece = paragraph.startPiece, offsetInDocument = paragraph.startOffset)
        }
        return createLists(localListItems)
    }

    internal fun fromPositionalListItems(positionalListItems: List<PositionalListItem>): List<PositionalListItem> {
        val localListItems = positionalListItems.flatten().fastMapIndexed { index, item ->
            PositionalListItem(index = index, richPiece = item.richPiece, newRichPiece = item.newRichPiece, offsetInDocument = item.offsetInDocument)
        }
        return createLists(localListItems)
    }

    internal fun convertToLocalListItems(multiparagraphs: MultiPieceParagraph): List<PositionalListItem> {
        return multiparagraphs.paragraphs.fastMapIndexed { index, paragraph ->
            PositionalListItem(index = index, richPiece = paragraph.startPiece, offsetInDocument = paragraph.startOffset)
        }
    }

    internal fun createPositionalItemsFrom(listItems: List<PositionalListItem>): List<PositionalListItem> {
        return createLists(listItems)
    }

    /**
     * Builds a nested [PositionalListItem] tree from a flat document-ordered list in a
     * **single O(N) pass**, replacing the previous O(N × L) level-by-level loop.
     *
     * Algorithm: scan items in document order, maintaining a [parentStack] that maps each
     * level to the most recent item seen at that level. Each item is nested under
     * [parentStack][level - 1]; if no parent exists yet the item is treated as a root.
     *
     * This replaces the old [createLists] + [insertInCorrectList] pair, which filtered the
     * full list once per level (O(N × L)) and called `.reversed()` + recursive search for
     * every insertion (O(N²) worst case).
     */
    private fun createLists(paragraphs: List<PositionalListItem>): List<PositionalListItem> {
        if (paragraphs.isEmpty()) return emptyList()
        val minLevel = paragraphs.minOf { it.getLevel() }
        val finalLevels = arrayListOf<PositionalListItem>()
        // Maps level → most recent item seen at that level so each new item can be
        // attached to its nearest preceding parent in O(1).
        val parentStack = HashMap<Int, PositionalListItem>()
        paragraphs.fastForEach { item ->
            val level = item.getLevel()
            if (level == minLevel) {
                finalLevels.add(item)
            } else {
                val parent = parentStack[level - 1]
                if (parent != null) {
                    parent.positionalListItems.add(item)
                } else {
                    finalLevels.add(item)
                }
            }
            parentStack[level] = item
        }
        return finalLevels
    }
}
