package com.jjrodcast.textkit.editor.core.transactions.marks

import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.core.models.TextEditorModel
import com.jjrodcast.textkit.editor.core.parser.Mark
import com.jjrodcast.textkit.editor.core.piecetable.models.RichPiece
import com.jjrodcast.textkit.editor.core.piecetable.models.RichPieceTransaction
import com.jjrodcast.textkit.editor.core.transactions.TextEditorTransaction
import com.jjrodcast.textkit.editor.core.transactions.marks.models.MultiParagraph
import com.jjrodcast.textkit.editor.core.transactions.marks.processors.TextEditorMarkProcessor
import com.jjrodcast.textkit.editor.core.transactions.models.TextEditorTransactionType
import com.jjrodcast.textkit.editor.models.TextKitConfiguration
import com.jjrodcast.textkit.editor.utils.endsWithLineBreak
import com.jjrodcast.textkit.editor.utils.fastForEach
import com.jjrodcast.textkit.editor.utils.isLineBreak

internal object MultiPieceFormatTransaction {

    internal fun modifyPieces(
        transaction: TextEditorTransaction,
        root: List<TextEditorModel>,
        prevFormatMarks: Set<Mark>,
        currFormatMarks: Set<Mark>,
        range: TextRange,
        configuration: TextKitConfiguration,
        transactionType: TextEditorTransactionType
    ): Boolean {
        val paragraphs = createMultiParagraphs(root, range)
        paragraphs.fastForEach { paragraph ->
            upsertMarks(
                transaction,
                paragraph,
                prevFormatMarks,
                currFormatMarks,
                configuration,
                transactionType
            )
        }
        return true
    }

    private fun createMultiParagraphs(
        models: List<TextEditorModel>,
        range: TextRange
    ): List<MultiParagraph> {
        val paragraphs = arrayListOf<MultiParagraph>()
        // var so we can hand ownership to MultiParagraph and start a fresh list each paragraph,
        // avoiding the ArrayList(items) defensive copy + clear() pattern.
        var items = arrayListOf<TextEditorModel>()

        val start = range.min
        val end = range.max

        models.fastForEach { model ->
            if (!model.piece.isDecorator) {
                items.add(model)
            }
            if (model.text.endsWithLineBreak()) {
                val nStart =
                    if (items.first().pieceStart < start) start else items.first().pieceStart
                val nEnd = minOf(items.last().pieceEnd, end)
                paragraphs.add(MultiParagraph(range = TextRange(nStart, nEnd), texts = items))
                items = arrayListOf()
            }
        }
        if (items.isNotEmpty()) {
            val nStart = if (items.first().pieceStart < start) start else items.first().pieceStart
            val nEnd = minOf(items.last().pieceEnd, end)
            paragraphs.add(MultiParagraph(range = TextRange(nStart, nEnd), texts = items))
        }
        return paragraphs.filter { !it.range.collapsed || (it.texts.size == 1 && it.texts.first().text.isLineBreak()) }
    }

    private fun upsertMarks(
        transaction: TextEditorTransaction,
        multiParagraph: MultiParagraph,
        prevFormatMarks: Set<Mark>,
        currFormatMarks: Set<Mark>,
        configuration: TextKitConfiguration,
        transactionType: TextEditorTransactionType
    ) {
        if (multiParagraph.isEmpty()) return

        val range = multiParagraph.range
        val (centralElements, left, right) = findParagraphBoundaries(multiParagraph.texts, range)

        // We need to check how to apply the changes to all the pieces.
        val transactions = createTransactions(
            transaction,
            left,
            centralElements,
            right,
            prevFormatMarks,
            currFormatMarks,
            range,
            configuration,
            transactionType
        )
        transaction.upsertMarks(transactions)
    }

    private fun createTransactions(
        transaction: TextEditorTransaction,
        left: TextEditorModel?,
        centralElements: List<TextEditorModel>,
        right: TextEditorModel?,
        prevFormatMarks: Set<Mark>,
        currFormatMarks: Set<Mark>,
        range: TextRange,
        configuration: TextKitConfiguration,
        transactionType: TextEditorTransactionType
    ): List<RichPieceTransaction> {
        if (centralElements.isEmpty()) return emptyList()

        val transactionManager = MultiPieceTransactionManager()

        if (left != null) {
            val currentModel = centralElements.first()
            val pieceMarks = currentModel.piece.marks + transactionType.marks
            val finalMarks =
                TextEditorMarkProcessor.process(
                    pieceMarks,
                    prevFormatMarks,
                    currFormatMarks,
                    configuration
                )

            val transactionMarks = when {
                canMergeWithPrevious(left.piece, currentModel.piece, finalMarks) -> {
                    val leftModel = TextEditorModel.create(left.piece)
                    val length =
                        leftModel.pieceLength - (range.min - leftModel.pieceStart) + currentModel.pieceLength
                    transaction.getTransactionMarks(
                        leftModel,
                        currentModel,
                        null,
                        range.start,
                        length,
                        finalMarks
                    )
                }

                Mark.areTheSame(finalMarks, currentModel.piece.marks) -> RichPieceTransaction.Empty

                else -> {
                    val length = currentModel.pieceLength - (range.min - currentModel.pieceStart)
                    transaction.getTransactionMarks(
                        null,
                        currentModel,
                        null,
                        range.start,
                        length,
                        finalMarks
                    )
                }
            }

            transactionManager.add(transactionMarks)
        }

        val startIndex = if (left == null) 0 else 1
        for (i in startIndex until centralElements.size) {
            val currentModel = centralElements[i]
            val lastPiece = transactionManager.getLastInsertedPiece()
            val finalMarks = TextEditorMarkProcessor.process(
                currentModel.piece.marks,
                prevFormatMarks,
                currFormatMarks,
                configuration
            )
            val offset = if (i == 0) range.start else currentModel.offsetInDocument
            val adjustedRange =
                getAdjustedRange(currentModel.piece, currentModel.offsetInDocument, range)

            val transactionMarks = when {
                lastPiece != null && canMergeWithPrevious(
                    lastPiece,
                    currentModel.piece,
                    finalMarks
                ) -> {
                    val leftModel = TextEditorModel.create(lastPiece)
                    val mergeTransaction = transaction.getTransactionMarks(
                        leftModel, currentModel, null, offset, adjustedRange.length, finalMarks
                    )
                    transactionManager.handleMergeTransaction(mergeTransaction, lastPiece)
                    RichPieceTransaction.Empty
                }

                Mark.areTheSame(
                    finalMarks,
                    currentModel.piece.marks
                ) && adjustedRange.length == currentModel.pieceLength -> RichPieceTransaction.Empty

                else -> {
                    transaction.getTransactionMarks(
                        null,
                        currentModel,
                        null,
                        offset,
                        adjustedRange.length,
                        finalMarks
                    )
                }
            }

            if (transactionMarks.insertAtIndex >= 0) {
                transactionManager.add(transactionMarks)
            }
        }

        if (right != null) {
            val lastPiece = transactionManager.getLastInsertedPiece()

            val transactionMarks = when {
                lastPiece != null && canMergeWithPrevious(
                    lastPiece,
                    right.piece,
                    right.piece.marks
                ) -> {
                    val centralElement = TextEditorModel.create(lastPiece)
                    val mergeTransaction = transaction.getTransactionMarks(
                        null,
                        centralElement,
                        right,
                        lastPiece.offset,
                        lastPiece.length,
                        lastPiece.marks
                    )
                    transactionManager.handleMergeTransaction(mergeTransaction, lastPiece)
                    RichPieceTransaction.Empty
                }

                else -> RichPieceTransaction.Empty
            }

            transactionManager.add(transactionMarks)
        }

        return transactionManager.getTransactions()
    }

    /**
     * Locates the central, left, and right boundary elements for [range] inside [elements]
     * using binary search — O(log M) instead of O(M).
     *
     * Elements must be in document order (sorted by [TextEditorModel.pieceStart], contiguous),
     * so [TextEditorModel.pieceEnd] is also monotonically non-decreasing.
     */
    private fun findParagraphBoundaries(
        elements: List<TextEditorModel>,
        range: TextRange
    ): ParagraphBoundaries {
        // First index where pieceEnd > range.min (element overlaps the range start).
        var lo = 0
        var hi = elements.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (elements[mid].pieceEnd <= range.min) lo = mid + 1 else hi = mid
        }
        val centralStart = lo
        // Last index where pieceStart < range.max (element overlaps the range end).
        lo = 0
        hi = elements.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (elements[mid].pieceStart < range.max) lo = mid + 1 else hi = mid
        }
        val centralEnd = lo - 1
        return ParagraphBoundaries(
            centralElements = if (centralStart > centralEnd) emptyList() else elements.subList(
                centralStart,
                centralEnd + 1
            ),
            left = if (centralStart > 0) elements[centralStart - 1] else null,
            right = if (centralEnd + 1 < elements.size) elements[centralEnd + 1] else null
        )
    }

    private fun canMergeWithPrevious(
        lastPiece: RichPiece?,
        currentPiece: RichPiece,
        finalMarks: Set<Mark>
    ): Boolean {
        // An atomic token is indivisible: never coalesce it with a neighbor when formatting a
        // multi-piece selection, or its text/identity would be folded into the adjacent piece and
        // lost. This mirrors the token-aware guards in PieceTableProcessor.
        if (lastPiece == null || lastPiece.isToken || currentPiece.isToken) return false
        return Mark.areTheSame(
            lastPiece.marks,
            finalMarks
        ) && currentPiece.source == lastPiece.source && lastPiece.offset + lastPiece.length == currentPiece.offset
    }

    private fun getAdjustedRange(
        piece: RichPiece,
        pieceOffset: Int,
        range: TextRange
    ): TextRange {
        val pieceEnd = pieceOffset + piece.length

        val adjustedStart = maxOf(range.start, pieceOffset)
        val adjustedEnd = minOf(range.end, pieceEnd)

        val relativeStart = adjustedStart - pieceOffset
        val relativeEnd = adjustedEnd - pieceOffset

        return TextRange(relativeStart, relativeEnd)
    }

    private data class ParagraphBoundaries(
        val centralElements: List<TextEditorModel>,
        val left: TextEditorModel?,
        val right: TextEditorModel?
    )
}
