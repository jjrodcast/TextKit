package com.jjrodcast.textkit.editor.core.piecetable

import com.jjrodcast.textkit.editor.core.interfaces.RichTextEditor
import com.jjrodcast.textkit.editor.core.models.MultiPieceParagraph
import com.jjrodcast.textkit.editor.core.models.PieceParagraph
import com.jjrodcast.textkit.editor.core.models.TextEditorDocumentModel
import com.jjrodcast.textkit.editor.core.models.TextEditorModel
import com.jjrodcast.textkit.editor.core.parser.Mark
import com.jjrodcast.textkit.editor.core.piecetable.models.RichPiece
import com.jjrodcast.textkit.editor.core.piecetable.models.RichPieceTransaction
import com.jjrodcast.textkit.editor.core.piecetable.models.Source
import com.jjrodcast.textkit.editor.core.piecetable.models.TextDecoratorModel
import com.jjrodcast.textkit.editor.core.piecetable.processor.PieceTableProcessor
import com.jjrodcast.textkit.editor.core.piecetable.rope.PieceRope
import com.jjrodcast.textkit.editor.utils.endsWithLineBreak
import com.jjrodcast.textkit.editor.utils.fastForEach
import com.jjrodcast.textkit.editor.utils.isLineBreak
import com.jjrodcast.textkit.editor.utils.removeLineBreakSuffix

internal abstract class RichTextEditorBasePieceTable :
    RichTextEditor<TextEditorDocumentModel, TextEditorModel> {

    protected var originalBuffer = ""
    protected val addedBuffer = StringBuilder()

    private var _cachedText: String? = null

    /** Clears the cached plain-text snapshot. Called after [build] when the document is replaced. */
    protected fun invalidateCache() {
        _cachedText = null
    }

    /**
     * Applies an incremental patch to [_cachedText] if it is warm (non-null), keeping
     * it valid without a full O(P) rebuild.
     *
     * If the cache is cold (null), this is a no-op — [text] will rebuild lazily on the
     * next access via the full O(P) rope traversal.
     *
     * **Complexity:** O(N) string copy with a single [StringBuilder] — 2 allocations
     * (builder + result) vs. the P `substring` allocations of a full rebuild.
     *
     * @param docOffset     Document start offset of the edit.
     * @param deletedLength Number of characters removed starting at [docOffset].
     * @param insertedText  Text inserted at [docOffset] (may be empty for pure deletes).
     */
    protected fun patchCache(docOffset: Int, deletedLength: Int, insertedText: String) {
        val cached = _cachedText ?: return // cold — rebuild lazily on next text access
        _cachedText = buildString(cached.length - deletedLength + insertedText.length) {
            append(cached, 0, docOffset)
            append(insertedText)
            append(cached, docOffset + deletedLength, cached.length)
        }
    }

    // The rope is the single source of truth for the piece sequence.
    // RichTextEditorPieceTable accesses it directly for O(log P) mutations.
    protected val rope = PieceRope()

    // Exposes the piece sequence as a flat List<RichPiece> for the interface contract
    // and for tests that inspect piece counts. O(P) snapshot — not called in hot paths.
    override val pieces: List<RichPiece>
        get() = rope.toList()

    override val text: String
        get() = _cachedText ?: buildString {
            rope.forEach { piece, _ ->
                val source = if (piece.source == Source.ADDED) addedBuffer else originalBuffer
                append(source.substring(piece.offset, piece.length + piece.offset))
            }
        }.also { _cachedText = it }

    // forRange over the full range is O(P + log P) = O(P), replacing the previous
    // O(P log P) loop of per-piece toTextEditorModel (getOffsetAndParagraphTypeAt) calls.
    override val annotatedText: List<TextEditorModel>
        get() = buildList(rope.size) {
            rope.forRange(0, rope.size - 1) { _, piece, offset, paragraphType ->
                add(
                    TextEditorModel(
                        piece = piece,
                        text = piece.getText(),
                        paragraphType = paragraphType,
                        offsetInDocument = offset
                    )
                )
            }
        }

    override fun build(document: TextEditorDocumentModel) {
        originalBuffer = getOriginalText(document)
        rope.buildFrom(loadOriginalPieces(document))
        invalidateCache()
    }

    override fun getTextAt(offset: Int): TextEditorModel {
        val (index, _) = getPieceIndexAndOffset(offset)
        val piece = rope.get(index)
        return piece.toTextEditorModel(index)
    }

    /**
     * This method allow us to find the pieces that are in the selected lines and also other pieces that are part of the same lines (you can
     * identify them just by checking the [PieceParagraph.outOfRange] property.
     */
    override fun getLineContent(start: Int, end: Int): MultiPieceParagraph {
        return MultiPieceParagraph(findFastPiecesMultiLine(start, end), start, end)
    }

    /**
     * Returns the full set of paragraphs needed to apply list-aware formatting around
     * the range [[start], [end]].
     *
     * The result always contains:
     * 1. **The selected paragraphs** — every paragraph that overlaps [[start], [end]].
     * 2. **Previous list items** — consecutive decorator paragraphs that immediately
     *    precede the selection (prepended in order). Included so that toggling a list
     *    type can re-number or re-style all items in the same list block, not just the
     *    selected ones.
     * 3. **Next context** — the content immediately after the selection, chosen as:
     *    - The consecutive decorator paragraphs that immediately follow (if any exist), or
     *    - The single next paragraph (if no decorators follow). This fallback ensures
     *      callers always have a reference paragraph for merge/split decisions even when
     *      the selection ends at a non-list boundary.
     *
     * Note: the "previous" and "next" sides are intentionally asymmetric. Previous items
     * walk backward until a non-decorator is found. The next side adds only one paragraph
     * as fallback because there is no list context to propagate forward.
     *
     * Pieces whose paragraphs fall outside [[start], [end]] are marked with
     * [PieceParagraph.outOfRange] == `true`.
     */
    override fun getLineContentWithNeighborListItems(start: Int, end: Int): MultiPieceParagraph {
        val newStart = start.coerceAtLeast(0)
        val newEnd = end.coerceAtMost(rope.totalLength)
        // Two fused O(log P) walks (findParagraphStartAt / findParagraphEndAt) replace the
        // previous 4 separate walks (2× findByDocumentOffset + 2× paragraph-boundary search).
        val newStartPieceIndex = rope.findParagraphStartAt(newStart)
        val newEndPieceIndex = rope.findParagraphEndAt(newEnd)

        val paragraphs =
            ArrayDeque(findFastPiecesMultiLine(start, end, newStartPieceIndex, newEndPieceIndex))

        // Doc offsets read from the already-built models — O(1), no extra rope walk.
        val previousOffset = paragraphs.first().startOffset
        val nextOffset = paragraphs.last().endOffset + paragraphs.last().endPiece.length + 1

        // Walk backward from the selection and prepend any consecutive decorator paragraphs
        // (findPreviousListItems returns emptyList when newStartPieceIndex == 0).
        paragraphs.addAll(0, findPreviousListItems(newStartPieceIndex, previousOffset))

        if (newEndPieceIndex < rope.size - 1) {
            val nextListItems = findNextListItems(newEndPieceIndex, nextOffset)
            if (nextListItems.isEmpty()) {
                // No adjacent list items — include the immediate next paragraph so callers
                // always have a boundary reference for formatting decisions.
                paragraphs.addAll(findFastPiecesMultiLine(start = nextOffset, end = nextOffset))
            } else {
                paragraphs.addAll(nextListItems)
            }
        }

        return MultiPieceParagraph(paragraphs, start, end)
    }

    protected fun getPieceIndexAndOffset(offset: Int): Pair<Int, Int> {
        if (offset < 0) throw IndexOutOfBoundsException("Index out of bounds:getPieceIndexAndOffset: $offset")

        if (rope.isEmpty()) rope.addSingle(createEmptyPiece(Source.ADDED))

        // O(log P) walk-down through the rope tree: finds the leftmost piece
        // whose cumulative end length >= offset, without a pre-sorted array.
        val (pieceIndex, pieceStartOffset) = rope.findByDocumentOffset(offset)

        if (pieceIndex < rope.size) {
            val piece = rope.get(pieceIndex)
            return Pair(pieceIndex, piece.offset + (offset - pieceStartOffset))
        }
        throw IndexOutOfBoundsException("Index out of bounds:getPieceIndexAndOffset-remaining: $offset")
    }

    /**
     * When the cursor is between [start] and [end] we search for all the pieces that are in the selected lines. At least one line
     * will be selected.
     *
     * This offset-based overload is used by [getLineContent] and the fallback branch in
     * [getLineContentWithNeighborListItems]. It computes the boundary piece indices internally.
     */
    private fun findFastPiecesMultiLine(start: Int, end: Int): List<PieceParagraph> {
        val newStart = start.coerceAtLeast(0)
        val newEnd = end.coerceAtMost(rope.totalLength)
        // Two fused O(log P) walks replace the previous 4 separate walks.
        return findFastPiecesMultiLine(
            start,
            end,
            rope.findParagraphStartAt(newStart),
            rope.findParagraphEndAt(newEnd)
        )
    }

    /**
     * Index-based overload: accepts pre-computed [newStartPieceIndex] and [newEndPieceIndex]
     * so that callers who already hold the boundary indices avoid redundant O(log P) rope walks.
     *
     * forRange delivers offset + paragraphType in a single O(R + log P) walk,
     * replacing the previous O(R log P) loop of per-piece toTextEditorModel calls.
     */
    private fun findFastPiecesMultiLine(
        start: Int,
        end: Int,
        newStartPieceIndex: Int,
        newEndPieceIndex: Int
    ): List<PieceParagraph> {
        val paragraphPieces = arrayListOf<PieceParagraph>()
        // Single buffer reused across all paragraphs — grows to the max paragraph size and
        // stays pre-sized, avoiding L-1 fresh ArrayDeque allocations for multi-paragraph ranges.
        val internalPieces = ArrayDeque<TextEditorModel>()
        rope.forRange(newStartPieceIndex, newEndPieceIndex) { _, piece, offset, paragraphType ->
            val model = TextEditorModel(
                piece = piece,
                text = piece.getText(),
                paragraphType = paragraphType,
                offsetInDocument = offset
            )
            internalPieces.add(model)
            if (piece.endsWithLineBreak) {
                // Copy into a fresh ArrayList before clearing — PieceParagraph must not share
                // the buffer reference since we clear and reuse internalPieces immediately.
                paragraphPieces.add(PieceParagraph(ArrayList(internalPieces), start, end))
                internalPieces.clear()
            }
        }

        if (internalPieces.isNotEmpty()) {
            // Last (or only) paragraph: hand off the buffer directly — it won't be reused.
            paragraphPieces.add(PieceParagraph(internalPieces, start, end))
        }

        return paragraphPieces
    }

    /**
     * Walk backward from [startPieceIndex] collecting consecutive decorator paragraphs.
     *
     * Eliminates the per-iteration [PieceRope.get] call (O(log P)) from the previous
     * implementation by building each paragraph first and then checking
     * [PieceParagraph.startPiece].isDecorator (O(1) — piece already in memory), at the
     * cost of one extra [buildParagraphFromPieceRange] call for the non-decorator
     * terminator. Net: saves N × O(log P), spends O(R_term + log P) once.
     *
     * [offset] is the document offset of [startPieceIndex], used as the [PieceParagraph] marker.
     */
    private fun findPreviousListItems(startPieceIndex: Int, offset: Int): List<PieceParagraph> {
        if (startPieceIndex <= 0) return emptyList()
        var endIndex = startPieceIndex - 1
        var startIndex = changeStartPieceIndex(endIndex)
        // Single buffer reused across all decorator paragraphs — avoids N-1 ArrayList
        // constructions for documents with consecutive list items.
        val buffer = ArrayList<TextEditorModel>()
        return buildList {
            while (true) {
                val paragraph =
                    buildParagraphFromPieceRange(startIndex, endIndex, offset, offset, buffer)
                // O(1): startPiece is already in memory from buildParagraphFromPieceRange —
                // no additional rope.get(startIndex) O(log P) walk needed.
                if (!paragraph.startPiece.isDecorator) break
                add(paragraph)
                if (startIndex <= 0) break
                endIndex = startIndex - 1
                startIndex = changeStartPieceIndex(endIndex)
            }
        }.reversed()
    }

    /**
     * Walk forward from [endPieceIndex] + 1 collecting consecutive decorator paragraphs.
     *
     * Uses a single [PieceRope.forRangeWhile] pass over the rope tail, paying O(log P)
     * **once** for the initial tree descent and O(1) per piece thereafter. The traversal
     * stops as soon as the first piece of a new paragraph is not a decorator, giving
     * **O(log P + N×R)** total cost where N = decorator paragraphs collected and R = average
     * pieces per paragraph — vs the previous O(N × 3 log P) multi-walk approach.
     *
     * [offset] is the document offset just after the selection, used as the [PieceParagraph] marker.
     */
    private fun findNextListItems(endPieceIndex: Int, offset: Int): List<PieceParagraph> {
        if (endPieceIndex >= rope.size - 1) return emptyList()
        val result = mutableListOf<PieceParagraph>()
        val paragraphBuffer = ArrayList<TextEditorModel>()
        var isFirstPieceOfParagraph = true
        // Single O(log P + R) descent: visits only pieces in the decorator block,
        // stopping immediately when the first non-decorator paragraph start is reached.
        rope.forRangeWhile(endPieceIndex + 1, rope.size - 1) { _, piece, docOffset, paragraphType ->
            if (isFirstPieceOfParagraph && !piece.isDecorator) return@forRangeWhile false
            isFirstPieceOfParagraph = false
            paragraphBuffer.add(
                TextEditorModel(
                    piece = piece,
                    text = piece.getText(),
                    paragraphType = paragraphType,
                    offsetInDocument = docOffset
                )
            )
            if (piece.endsWithLineBreak) {
                result.add(PieceParagraph(ArrayList(paragraphBuffer), offset, offset))
                paragraphBuffer.clear()
                isFirstPieceOfParagraph = true
            }
            true
        }
        // Flush the last decorator paragraph if it didn't end with a linebreak
        // (loadOriginalPieces strips the trailing LF from the last piece in the document).
        if (paragraphBuffer.isNotEmpty()) {
            result.add(PieceParagraph(ArrayList(paragraphBuffer), offset, offset))
        }
        return result
    }

    /**
     * Builds a [PieceParagraph] for the piece range [[startPieceIndex], [endPieceIndex]].
     *
     * When [buffer] is supplied (by loop callers such as [findPreviousListItems] and
     * [findNextListItems]), it is cleared and reused across iterations — saving N-1
     * [ArrayList] constructions per invocation. The buffer's contents are snapshotted
     * into a fresh [ArrayList] before being handed to [PieceParagraph], so the caller
     * can safely clear and refill it on the next iteration.
     *
     * When no buffer is supplied, a fresh [ArrayList] is allocated and passed directly
     * to [PieceParagraph] with no extra copy.
     */
    private fun buildParagraphFromPieceRange(
        startPieceIndex: Int,
        endPieceIndex: Int,
        start: Int,
        end: Int,
        buffer: ArrayList<TextEditorModel>? = null
    ): PieceParagraph {
        val models = buffer?.also { it.clear() } ?: ArrayList()
        rope.forRange(startPieceIndex, endPieceIndex) { _, piece, offset, paragraphType ->
            models.add(
                TextEditorModel(
                    piece = piece,
                    text = piece.getText(),
                    paragraphType = paragraphType,
                    offsetInDocument = offset
                )
            )
        }
        // When a shared buffer is provided, snapshot before handing off so the caller
        // can reuse it. When no buffer was given, models is already an owned ArrayList.
        return PieceParagraph(if (buffer != null) ArrayList(models) else models, start, end)
    }

    /**
     * This function search for the remaining pieces on the left side to be included in the [PieceParagraph].
     * O(log P) via [PieceRope.findLastLineBreakEndBefore] + [RopeNode.containsLineBreakEnd] pruning.
     */
    private fun changeStartPieceIndex(startPieceIndex: Int): Int {
        val lastIdx = rope.findLastLineBreakEndBefore(startPieceIndex)
        return if (lastIdx < 0) 0 else lastIdx + 1
    }

    /**
     * This function search for the remaining pieces on the right side to be included in the [PieceParagraph].
     * O(log P) via [PieceRope.findFirstLineBreakEndFrom] + [RopeNode.containsLineBreakEnd] pruning.
     */
    private fun changeEndPieceIndex(endPieceIndex: Int): Int {
        return rope.findFirstLineBreakEndFrom(endPieceIndex)
    }

    internal fun updateDecorator(model: TextEditorModel): Boolean {
        val piece = model.piece
        return when (val decorator = piece.decorator) {
            is TextDecoratorModel.TaskDecoratorModel -> {
                val index = getIndexOf(model)
                if (index < 0) return false
                rope.replaceAt(
                    index,
                    piece.copy(decorator = decorator.copy(checked = !decorator.checked))
                )
                return true
            }

            else -> false
        }
    }

    private fun getIndexOf(model: TextEditorModel): Int {
        // model.offsetInDocument was computed when the model was created via forRange/toTextEditorModel,
        // so we can use it to locate the piece quickly via the rope's >= semantics. We check the
        // candidate and, if it doesn't match by identity, also try index + 1.
        val piece = model.piece
        val (index, _) = rope.findByDocumentOffset(model.offsetInDocument)
        if (index < rope.size && rope.get(index) === piece) return index
        val next = index + 1
        return if (next < rope.size && rope.get(next) === piece) next else -1
    }

    internal fun getLastOffset(): Int = rope.getOffsetAt(rope.size - 1)

    private fun getOriginalText(document: TextEditorDocumentModel): String {
        val totalCount = document.paragraph.sumOf { it.styledText.size }
        var index = 0
        return buildString {
            document.paragraph.fastForEach { paragraph ->
                paragraph.styledText.fastForEach { model ->
                    val text = if (index == totalCount - 1) {
                        if (model.text.endsWithLineBreak()) model.text else model.text.removeLineBreakSuffix()
                    } else {
                        model.text
                    }
                    append(text)
                    index++
                }
            }
        }
    }

    private fun loadOriginalPieces(document: TextEditorDocumentModel): List<RichPiece> {
        if (document.paragraph.isEmpty()) return listOf(createEmptyPiece(Source.ORIGINAL))
        val totalCount = document.paragraph.sumOf { it.styledText.size }
        val result = ArrayList<RichPiece>(totalCount)
        var offset = 0
        var index = 0
        document.paragraph.fastForEach { paragraph ->
            paragraph.styledText.fastForEach { model ->
                val isLastItem = index == totalCount - 1 && model.text.endsWithLineBreak()
                val pieceLength = if (isLastItem) model.text.length - 1 else model.text.length
                val pieceText = originalBuffer.substring(offset, offset + pieceLength)
                result.add(
                    RichPiece(
                        source = Source.ORIGINAL,
                        offset = offset,
                        length = pieceLength,
                        marks = model.piece.marks,
                        decorator = model.piece.decorator,
                        token = model.piece.token,
                        isLineBreak = pieceText.isLineBreak(),
                        endsWithLineBreak = pieceText.endsWithLineBreak()
                    )
                )
                offset += model.text.length
                index++
            }
        }
        return result
    }

    private fun createEmptyPiece(source: Source) =
        RichPiece(source = source, offset = 0, length = 0)

    /**
     * Returns a copy of this piece with [endsWithLineBreak] recomputed from the
     * backing buffer. Called on pieces produced by [PieceTableProcessor] (which uses `.copy()`
     * without buffer access) before they are inserted into the rope via [updateMarks].
     */
    private fun RichPiece.withCorrectEndsWithLineBreak(): RichPiece {
        if (length == 0) return this
        val buf: CharSequence = if (source == Source.ADDED) addedBuffer else originalBuffer
        val correct = buf[offset + length - 1].isLineBreak()
        return if (correct == endsWithLineBreak) this else copy(endsWithLineBreak = correct)
    }

    internal fun RichPiece.getText(): String {
        return if (source == Source.ADDED) {
            addedBuffer.substring(offset, offset + length)
        } else {
            originalBuffer.substring(offset, offset + length)
        }
    }

    /**
     * Computes [offsetInDocument] and [paragraphType] in a **single O(log P)
     * walk** via [PieceRope.getOffsetAndParagraphTypeAt], then builds a [TextEditorModel].
     * Replaces the old O(P) `updatePieces()` forward scan with per-piece on-demand
     * computation, and the previous two independent walks with one fused walk.
     */
    private fun RichPiece.toTextEditorModel(pieceIndex: Int): TextEditorModel {
        val (docOffset, paragraphType) = rope.getOffsetAndParagraphTypeAt(pieceIndex)
        return TextEditorModel(
            piece = this,
            text = getText(),
            paragraphType = paragraphType,
            offsetInDocument = docOffset
        )
    }

    override fun updateMarks(transactions: List<RichPieceTransaction>): Boolean {
        if (transactions.isEmpty()) return false

        // Build a removal set across all transactions — O(T)
        val removedSet = HashSet<RichPiece>(transactions.sumOf { it.removedPieces.size } * 2)
        transactions.forEach { removedSet.addAll(it.removedPieces) }

        // Sort ascending by insertAtIndex for a single forward-pass — O(T log T)
        val sortedInserts = transactions
            .filter { it.insertAtIndex >= 0 }
            .sortedBy { it.insertAtIndex }

        // Single forward-pass: rebuild piece list applying all transactions at once — O(P)
        val result =
            ArrayList<RichPiece>(rope.size + sortedInserts.sumOf { it.insertedPieces.size })
        var txIdx = 0
        rope.forEachIndexed { originalIdx, piece, _ ->
            while (txIdx < sortedInserts.size && sortedInserts[txIdx].insertAtIndex == originalIdx) {
                sortedInserts[txIdx].insertedPieces.forEach { result.add(it.withCorrectEndsWithLineBreak()) }
                txIdx++
            }
            if (piece !in removedSet) result.add(piece)
        }
        // Transactions whose insertAtIndex falls past the end of the original array
        while (txIdx < sortedInserts.size) {
            sortedInserts[txIdx].insertedPieces.forEach { result.add(it.withCorrectEndsWithLineBreak()) }
            txIdx++
        }

        rope.buildFrom(result)
        // text content is unchanged — marks/decorators are metadata only, cache stays valid.
        return true
    }

    override fun updateMarks(transaction: RichPieceTransaction): Boolean {
        if (transaction.insertAtIndex < 0) return false
        // insertAtIndex is always the rope index of the first removed piece, and all
        // removed pieces are contiguous from that point — invariant guaranteed by
        // PieceTableProcessor. A single splice replaces them in O(K log P) instead of
        // the previous O(P) toList + removeAll + addAll + buildFrom.
        rope.splice(
            from = transaction.insertAtIndex,
            to = transaction.insertAtIndex + transaction.removedPieces.size,
            replacements = transaction.insertedPieces.map { it.withCorrectEndsWithLineBreak() }
        )
        // text content is unchanged — marks/decorators are metadata only, cache stays valid.
        return true
    }

    override fun getTransactionMarks(
        leftModel: TextEditorModel?,
        centralModel: TextEditorModel,
        rightModel: TextEditorModel?,
        offset: Int,
        length: Int,
        marks: Set<Mark>
    ): RichPieceTransaction {
        val leftPiece = leftModel?.piece
        val rightPice = rightModel?.piece
        return when {
            leftPiece != null && rightPice != null -> {
                val indexOfLeftPiece = getIndexOf(leftModel)
                val indexOfCentralPiece = getIndexOf(centralModel)
                PieceTableProcessor.getBothPiecesTransaction(
                    leftModel,
                    indexOfLeftPiece,
                    centralModel,
                    indexOfCentralPiece,
                    rightModel,
                    offset,
                    length,
                    marks
                )
            }

            leftPiece != null && rightPice == null -> {
                val indexOfLeftPiece = getIndexOf(leftModel)
                val indexOfCentralPiece = getIndexOf(centralModel)
                PieceTableProcessor.getLeftPieceTransaction(
                    leftModel,
                    indexOfLeftPiece,
                    centralModel,
                    indexOfCentralPiece,
                    offset,
                    length,
                    marks
                )
            }

            leftPiece == null && rightPice != null -> {
                val indexOfCentralPiece = getIndexOf(centralModel)
                PieceTableProcessor.getRightPieceTransaction(
                    rightModel,
                    centralModel,
                    indexOfCentralPiece,
                    offset,
                    length,
                    marks
                )
            }

            else -> {
                val indexOfCentralPiece = getIndexOf(centralModel)
                PieceTableProcessor.getCentralPieceTransaction(
                    centralModel,
                    indexOfCentralPiece,
                    offset,
                    length,
                    marks
                )
            }
        }
    }
}
