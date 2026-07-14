package com.jjrodcast.textkit.editor.core.transactions.marks

import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.core.models.TextEditorModel
import com.jjrodcast.textkit.editor.core.parser.LinkMark
import com.jjrodcast.textkit.editor.core.parser.Mark
import com.jjrodcast.textkit.editor.core.transactions.TextEditorTransaction
import com.jjrodcast.textkit.editor.core.transactions.marks.processors.TextEditorMarkProcessor
import com.jjrodcast.textkit.editor.core.transactions.models.TextEditorTransactionType
import com.jjrodcast.textkit.editor.models.TextKitConfiguration
import com.jjrodcast.textkit.editor.utils.isLineBreak

/**
 * This object handles the logic to format changes which are applied to the piece table.
 **/
internal object FormatTransaction {

    internal fun updateDocument(
        transaction: TextEditorTransaction,
        prevFormatMarks: Set<Mark>,
        currFormatMarks: Set<Mark>,
        range: TextRange,
        configuration: TextKitConfiguration,
        transactionType: TextEditorTransactionType
    ) = modifyMarks(
        transaction,
        prevFormatMarks,
        currFormatMarks,
        range,
        configuration,
        transactionType
    )

    private fun modifyMarks(
        transaction: TextEditorTransaction,
        prevFormatMarks: Set<Mark>,
        currFormatMarks: Set<Mark>,
        range: TextRange,
        configuration: TextKitConfiguration,
        transactionType: TextEditorTransactionType
    ): Pair<Boolean, TextRange> {
        val start = range.min
        val end = range.max
        val root = transaction.getLineContentModels(start - 1, end + 1)
        val rootIndex =
            root.indexOfFirst { model -> model.pieceStart <= start && end <= model.pieceEnd }

        val hasChanges = when (rootIndex) {
            -1 -> {
                MultiPieceFormatTransaction.modifyPieces(
                    transaction,
                    root,
                    prevFormatMarks,
                    currFormatMarks,
                    range,
                    configuration,
                    transactionType
                )
            }

            else -> {
                val newRange = range.getNewRange(root[rootIndex], transactionType)
                val marks = TextEditorMarkProcessor.process(
                    root[rootIndex].piece.marks,
                    prevFormatMarks,
                    currFormatMarks,
                    configuration
                )
                modifyPiece(transaction, root, rootIndex, marks, newRange)
            }
        }
        return Pair(hasChanges, range)
    }

    private fun TextRange.getNewRange(
        item: TextEditorModel,
        transactionType: TextEditorTransactionType
    ): TextRange {
        return when (transactionType) {
            TextEditorTransactionType.Format -> this
            is TextEditorTransactionType.Color -> this
            is TextEditorTransactionType.Link -> {
                if (item.piece.marks.any { it is LinkMark }) TextRange(
                    item.pieceStart,
                    item.pieceEnd
                ) else this
            }
        }
    }

    private fun modifyPiece(
        transaction: TextEditorTransaction,
        root: List<TextEditorModel>,
        rootIndex: Int,
        currFormatMarks: Set<Mark>,
        range: TextRange
    ): Boolean {
        val text = root[rootIndex].text
        val isDecorator = root[rootIndex].isDecorator
        val isEmpty = text.isEmpty() && text.isLineBreak()
        if (isDecorator || isEmpty) return false
        return when (root.size) {
            1 -> modifyPieceWithoutNeighbor(transaction, root[rootIndex], currFormatMarks, range)
            2 -> modifyPieceWithOneNeighbor(transaction, root, rootIndex, currFormatMarks, range)
            3 -> modifyPieceWithBothNeighbors(transaction, root, currFormatMarks, range)
            else -> false
        }
    }

    private fun modifyPieceWithoutNeighbor(
        transaction: TextEditorTransaction,
        element: TextEditorModel,
        marks: Set<Mark>,
        range: TextRange
    ) = transaction.updateMarks(null, element, null, range.min, range.length, marks)

    private fun modifyPieceWithOneNeighbor(
        transaction: TextEditorTransaction,
        elements: List<TextEditorModel>,
        rootIndex: Int,
        marks: Set<Mark>,
        range: TextRange
    ): Boolean {
        val sideIndex = elements.size - rootIndex - 1
        val sideItem = elements[sideIndex]
        val rootItem = elements[rootIndex]

        val hasSameMarks = Mark.areTheSame(sideItem.piece.marks, marks)

        return when {
            sideIndex < rootIndex -> {
                // Only coalesce with the left neighbor when it lives in the SAME paragraph. If it
                // ends a paragraph (trailing line break) the pieces belong to different paragraphs;
                // merging them feeds a left-based offset into the central-piece transaction, which
                // computes a negative delta and corrupts the piece boundaries (e.g. removing a link
                // in its own paragraph would splice it into the previous paragraph's text).
                val canMerge = hasSameMarks && !sideItem.isLastOnParagraph
                val offset = if (sideItem.piece.isDecorator) range.min else sideItem.pieceStart
                if (canMerge) {
                    transaction.updateMarks(sideItem, rootItem, null, offset, range.length, marks)
                } else {
                    transaction.updateMarks(null, rootItem, null, range.min, range.length, marks)
                }
            }

            else -> {
                // Symmetric guard: only coalesce with the right neighbor when the root piece does
                // not end its paragraph (otherwise the right neighbor is in the next paragraph).
                val canMerge = hasSameMarks && !rootItem.isLastOnParagraph
                if (canMerge) {
                    transaction.updateMarks(
                        null,
                        rootItem,
                        sideItem,
                        range.min,
                        range.length,
                        marks
                    )
                } else {
                    transaction.updateMarks(null, rootItem, null, range.min, range.length, marks)
                }
            }
        }
    }

    private fun modifyPieceWithBothNeighbors(
        transaction: TextEditorTransaction,
        elements: List<TextEditorModel>,
        formatMarks: Set<Mark>,
        range: TextRange
    ): Boolean {
        val start = range.min
        val end = range.max
        val (left, root, right) = elements
        val leftMarks = left.piece.marks
        val rightMarks = right.piece.marks

        return when {
            Mark.areTheSame(formatMarks, leftMarks) && Mark.areTheSame(formatMarks, rightMarks) -> {
                transaction.updateMarks(left, root, right, start, end - start, formatMarks)
            }

            Mark.areTheSame(formatMarks, leftMarks) -> {
                transaction.updateMarks(left, root, null, start, end - start, formatMarks)
            }

            Mark.areTheSame(formatMarks, rightMarks) -> {
                transaction.updateMarks(null, root, right, start, end - start, formatMarks)
            }

            else -> {
                transaction.updateMarks(null, root, null, start, end - start, formatMarks)
            }
        }
    }
}
