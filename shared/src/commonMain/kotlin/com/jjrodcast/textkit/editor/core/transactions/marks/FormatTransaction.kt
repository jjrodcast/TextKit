package com.jjrodcast.textkit.editor.core.transactions.marks

import com.jjrodcast.textkit.editor.core.models.TextEditorModel
import com.jjrodcast.textkit.editor.core.parser.LinkMark
import com.jjrodcast.textkit.editor.core.parser.Mark
import com.jjrodcast.textkit.editor.core.transactions.TextEditorTransaction
import com.jjrodcast.textkit.editor.core.transactions.marks.processors.TextEditorMarkProcessor
import com.jjrodcast.textkit.editor.core.transactions.models.TextEditorRange
import com.jjrodcast.textkit.editor.core.transactions.models.TextEditorTransactionType
import com.jjrodcast.textkit.editor.utils.isLineBreak

/**
 * This object handles the logic to format changes which are applied to the piece table.
 *
 * The format changes comes from the [com.plangrid.pgfoundation.texteditor.components.format.TextEditorFormatComponent] component.
 */
internal object FormatTransaction {

    internal fun updateDocument(
        transaction: TextEditorTransaction,
        prevFormatMarks: Set<Mark>,
        currFormatMarks: Set<Mark>,
        range: TextEditorRange,
        transactionType: TextEditorTransactionType
    ) = modifyMarks(transaction, prevFormatMarks, currFormatMarks, range, transactionType)

    private fun modifyMarks(
        transaction: TextEditorTransaction,
        prevFormatMarks: Set<Mark>,
        currFormatMarks: Set<Mark>,
        range: TextEditorRange,
        transactionType: TextEditorTransactionType
    ): Pair<Boolean, TextEditorRange> {
        val start = range.min
        val end = range.max
        val root = transaction.getLineContentModels(start - 1, end + 1)
        val rootIndex = root.indexOfFirst { model -> model.pieceStart <= start && end <= model.pieceEnd }

        val hasChanges = when (rootIndex) {
            -1 -> {
                MultiPieceFormatTransaction.modifyPieces(transaction, root, prevFormatMarks, currFormatMarks, range, transactionType)
            }

            else -> {
                val newRange = range.getNewRange(root[rootIndex], transactionType)
                val marks = TextEditorMarkProcessor.process(root[rootIndex].piece.marks, prevFormatMarks, currFormatMarks)
                modifyPiece(transaction, root, rootIndex, marks, newRange)
            }
        }
        return Pair(hasChanges, range)
    }

    private fun TextEditorRange.getNewRange(item: TextEditorModel, transactionType: TextEditorTransactionType): TextEditorRange {
        return when (transactionType) {
            TextEditorTransactionType.Format -> this
            is TextEditorTransactionType.Link -> {
                if (item.piece.marks.any { it is LinkMark }) TextEditorRange(item.pieceStart, item.pieceEnd) else this
            }
        }
    }

    private fun modifyPiece(
        transaction: TextEditorTransaction,
        root: List<TextEditorModel>,
        rootIndex: Int,
        currFormatMarks: Set<Mark>,
        range: TextEditorRange
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
        range: TextEditorRange
    ) = transaction.updateMarks(null, element, null, range.min, range.length, marks)

    private fun modifyPieceWithOneNeighbor(
        transaction: TextEditorTransaction,
        elements: List<TextEditorModel>,
        rootIndex: Int,
        marks: Set<Mark>,
        range: TextEditorRange
    ): Boolean {
        val sideIndex = elements.size - rootIndex - 1
        val sideItem = elements[sideIndex]
        val rootItem = elements[rootIndex]

        val hasSameMarks = Mark.areTheSame(sideItem.piece.marks, marks)

        return when {
            sideIndex < rootIndex -> {
                val offset = if (sideItem.piece.isDecorator) range.min else sideItem.pieceStart
                if (hasSameMarks) {
                    transaction.updateMarks(sideItem, rootItem, null, offset, range.length, marks)
                } else {
                    transaction.updateMarks(null, rootItem, null, range.min, range.length, marks)
                }
            }

            else -> {
                if (hasSameMarks) {
                    transaction.updateMarks(null, rootItem, sideItem, range.min, range.length, marks)
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
        range: TextEditorRange
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
