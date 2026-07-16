package com.jjrodcast.textkit.editor.core.models

import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.components.TextEditorListItem
import com.jjrodcast.textkit.editor.core.parser.LinkMark
import com.jjrodcast.textkit.editor.core.parser.Mark
import com.jjrodcast.textkit.editor.core.parser.TextStyleMark
import com.jjrodcast.textkit.editor.core.piecetable.models.TextDecoratorModel.Companion.toLevel
import com.jjrodcast.textkit.editor.models.MarkSearchType
import com.jjrodcast.textkit.editor.models.TextKitConfiguration
import com.jjrodcast.textkit.editor.utils.EMPTY
import com.jjrodcast.textkit.editor.utils.fastForEach
import com.jjrodcast.textkit.editor.utils.intersect
import com.jjrodcast.textkit.editor.utils.removeLineBreakSuffix

internal data class MultiPieceParagraph(
    internal val paragraphs: List<PieceParagraph>,
    internal val start: Int,
    internal val end: Int
) {

    val firstParagraph get() = paragraphs.first()

    val lastParagraph get() = paragraphs.last()

    val paragraphsInSelectedRange by lazy {
        paragraphs.filter { intersect(start, end, it.start, it.end) }
    }

    val selectedParagraphIndices: List<Int> by lazy {
        val result = arrayListOf<Int>()
        paragraphs.forEachIndexed { index, paragraph ->
            if (intersect(start, end, paragraph.start, paragraph.end) &&
                paragraph.piecesInSelectedRange.isNotEmpty()
            ) {
                result.add(index)
            }
        }
        result
    }

    val fullText
        get() = buildString {
            paragraphs.fastForEach { paragraph -> paragraph.pieces.fastForEach { append(it.text) } }
        }

    val textInRange
        get() = buildString {
            paragraphs.fastForEach { paragraph ->
                paragraph.piecesInSelectedRange.fastForEach {
                    append(
                        it.text
                    )
                }
            }
        }

    val textOutOfRange
        get() = buildString {
            paragraphs.fastForEach { paragraph -> paragraph.piecesOutOfRange.fastForEach { append(it.text) } }
        }

    // A3: single pass — no intermediate full-list allocation.
    fun getAllModelsInRange(): List<TextEditorModel> = buildList {
        paragraphs.fastForEach { paragraph ->
            paragraph.pieces.fastForEach { piece ->
                val model = piece.copy(paragraphType = paragraph.paragraphType)
                val pieceStart = model.offsetInDocument
                val pieceEnd = pieceStart + model.piece.length
                if (end == pieceEnd || intersect(start, end, pieceStart, pieceEnd)) add(model)
            }
        }
    }

    fun findSelectedParagraphIndicesByLevel(level: Int): List<Int> {
        var index = 0
        val finalIndices = arrayListOf<Int>()
        val indices = selectedParagraphIndices
        while (index < indices.size) {
            val itemLevel = paragraphs[indices[index]].startPiece.decorator.toLevel()
            if (itemLevel < level) break
            else if (itemLevel > level) {
                index++
                continue
            } else {
                finalIndices.add(indices[index])
                index++
            }
        }
        return finalIndices
    }

    /**
     * Find the paragraph index that contains the given offset.
     *
     * Paragraphs are in document order (sorted by [PieceParagraph.startOffset]) and their
     * [startOffset, endOffset] ranges are non-overlapping, so binary search applies.
     * O(log P) instead of O(P) linear scan.
     */
    fun findParagraphIndexBy(offset: Int): Int {
        var lo = 0
        var hi = paragraphs.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val paragraph = paragraphs[mid]
            when {
                offset < paragraph.startOffset -> hi = mid - 1
                offset > paragraph.endOffset -> lo = mid + 1
                else -> return mid
            }
        }
        return -1
    }

    fun findSelectedParagraph(): PieceParagraph? {
        return paragraphs.firstOrNull { it.piecesInSelectedRange.isNotEmpty() }
    }

    /**
     * Find the paragraph that contains the given offset.
     *
     * The range checked is [startOffset, endOffset + endPiece.length]. Consecutive paragraphs
     * share a boundary (endOffset_i + length_i == startOffset_{i+1}), so a boundary offset
     * matches both. A tiebreak check ensures the lower-index paragraph is returned, preserving
     * the semantics of the previous linear scan. O(log P) instead of O(P).
     */
    fun findParagraphBy(offset: Int): PieceParagraph? {
        var lo = 0
        var hi = paragraphs.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val paragraph = paragraphs[mid]
            val paragraphEnd = paragraph.endOffset + paragraph.endPiece.length
            when {
                offset < paragraph.startOffset -> hi = mid - 1
                offset > paragraphEnd -> lo = mid + 1
                else -> {
                    // Tiebreak: if the previous paragraph also covers offset (boundary case),
                    // return it to match linear-scan semantics (first/lower index wins).
                    val prev = if (mid > 0) paragraphs[mid - 1] else null
                    return if (prev != null && offset <= prev.endOffset + prev.endPiece.length) prev else paragraph
                }
            }
        }
        return null
    }

    /**
     * Find the paragraphs out of the range.
     *
     * Replacing the old two-step approach (build intermediate List → filterNot { it in list })
     * eliminates the O(L) List.contains call per element, reducing the overall cost from
     * O(L²) to O(L).
     */
    fun findParagraphsNotInRange(): List<PieceParagraph> {
        val startParagraph = findParagraphBy(start)
        val endParagraph = findParagraphBy(end)
        return paragraphs.filterNot { it == startParagraph || it == endParagraph }
    }

    /**
     * Sorted list of paragraph indices (per level) for non-null decorators.
     * Built once on first use; enables O(log N) binary-search lookups.
     */
    private val paragraphsByLevel: Map<Int, List<Int>> by lazy {
        val map = HashMap<Int, MutableList<Int>>()
        paragraphs.forEachIndexed { index, paragraph ->
            val level = paragraph.startPiece.decorator.toLevel()
            if (level > 0) map.getOrPut(level) { mutableListOf() }.add(index)
        }
        map
    }

    /**
     * Sorted paragraph indices where decorator == null, bookended by sentinels
     * (-1 at the front, paragraphs.size at the back). Used by [searchSameLevelItems]
     * with keepSearching=true to locate the null-barrier region around startIndex.
     */
    private val nullDecoratorBoundaries: List<Int> by lazy {
        buildList {
            add(-1)
            paragraphs.forEachIndexed { index, p -> if (p.startPiece.decorator == null) add(index) }
            add(paragraphs.size)
        }
    }

    /**
     * H3: O(log N + k) replacement for the previous O(N) bidirectional scan.
     *
     * - keepSearching=false: binary-search for startIndex in [paragraphsByLevel], then
     *   expand left/right while consecutive (gap > 1 means a foreign-level or null-decorator
     *   paragraph lies between them — matching the original break-on-mismatch semantics).
     * - keepSearching=true: binary-search [nullDecoratorBoundaries] to bound the region,
     *   then slice [paragraphsByLevel] to that window.
     */
    fun searchSameLevelItems(
        startIndex: Int,
        level: Int,
        keepSearching: Boolean = false
    ): List<PieceParagraph> {
        val levelIndices = paragraphsByLevel[level] ?: return emptyList()

        if (keepSearching) {
            val boundaryPos = nullDecoratorBoundaries.binarySearch(startIndex)
            if (boundaryPos >= 0) return emptyList() // startIndex is itself a null-decorator item
            val ip = -(boundaryPos + 1)
            val lo = nullDecoratorBoundaries[ip - 1] // exclusive lower barrier
            val hi = nullDecoratorBoundaries[ip] // exclusive upper barrier
            val loPos = levelIndices.binarySearch(lo + 1).let { if (it < 0) -(it + 1) else it }
            val hiPos = levelIndices.binarySearch(hi).let { if (it < 0) -(it + 1) else it }
            return levelIndices.subList(loPos, hiPos).map { paragraphs[it] }
        }

        val pos = levelIndices.binarySearch(startIndex)
        if (pos < 0) return emptyList()

        var left = pos
        while (left > 0 && levelIndices[left] - levelIndices[left - 1] == 1) left--
        var right = pos
        while (right < levelIndices.size - 1 && levelIndices[right + 1] - levelIndices[right] == 1) right++

        return levelIndices.subList(left, right + 1).map { paragraphs[it] }
    }

    internal fun getMarksWithType(configuration: TextKitConfiguration): MarkSearchType {
        val range = TextRange(start, end)
        val data = getAllModelsInRange()
        return when {
            data.isEmpty() -> MarkSearchType()
            // When the size is 2 and the range is collapsed means that the cursor is in the middle of 2 pieces,
            // so we take the marks of the first piece on the left side.
            data.size == 1 || (data.size == 2 && range.collapsed) -> {
                val element = data.first()
                if (element.isDecorator) MarkSearchType()
                if (element.piece.marks.any { it is LinkMark }) {
                    val piece = element.piece
                    val newRange = if (range.collapsed) {
                        val hasLineBreak = element.piece.endsWithLineBreak
                        TextRange(
                            start = element.offsetInDocument,
                            end = element.offsetInDocument + if (hasLineBreak) piece.length - 1 else piece.length
                        )
                    } else range

                    MarkSearchType(
                        marks = piece.marks,
                        listItem = element.paragraphType,
                        range = newRange,
                        text = element.text.removeLineBreakSuffix()
                    )
                } else {
                    val textStart = start - element.offsetInDocument
                    val text = element.text.substring(textStart, textStart + range.length)
                    MarkSearchType(
                        marks = element.piece.marks,
                        listItem = element.paragraphType,
                        range = range,
                        text = text
                    )
                }
            }

            else -> {
                val marksInRange = getMarksInRange(data)
                val paragraphTypes = data.map { it.paragraphType }.toSet()
                val listItemType =
                    if (paragraphTypes.size == 1) paragraphTypes.first() else TextEditorListItem.None
                val text = data.filter { !it.isDecorator }
                    .joinToString(separator = EMPTY) { it.text.removeLineBreakSuffix() }
                val filteredMarks = marksInRange.mapNotNull {
                    if (it is TextStyleMark && TextStyleMark.isDefault(it, configuration)) null
                    else it
                }.toSet()
                MarkSearchType(filteredMarks, listItemType, range, text)
            }
        }
    }

    private fun getMarksInRange(data: List<TextEditorModel>): Set<Mark> {
        // Only content pieces take part in the "marks common to the whole range" intersection.
        // Decorator pieces (list bullets/numbers/checkboxes) and pure line-break pieces carry no
        // marks; counting them would dilute the intersection to empty for multi-paragraph or list
        // selections whose text actually shares the same marks.
        val content = data.filter { !it.isDecorator && it.text.removeLineBreakSuffix().isNotEmpty() }
        if (content.isEmpty()) return emptySet()

        // Single pass over all marks: count total occurrences per key and keep the first
        // instance seen. A mark is included in the result only if its count equals content.size,
        // which matches the previous flatMap → groupBy → filter(group.size == content.size) chain.
        val countByKey = HashMap<String, Int>()
        val firstByKey = HashMap<String, Mark>()

        content.forEach { model ->
            model.piece.marks.forEach { mark ->
                val key = mark.createKey()
                countByKey[key] = (countByKey[key] ?: 0) + 1
                if (!firstByKey.containsKey(key)) firstByKey[key] = mark
            }
        }

        return countByKey.entries.mapNotNullTo(HashSet()) { (key, count) ->
            if (count == content.size) firstByKey[key] else null
        }
    }
}
