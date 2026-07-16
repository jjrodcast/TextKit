package com.jjrodcast.textkit.editor.core.piecetable.processor

import com.jjrodcast.textkit.editor.core.models.TextEditorModel
import com.jjrodcast.textkit.editor.core.parser.Mark
import com.jjrodcast.textkit.editor.core.piecetable.models.RichPiece
import com.jjrodcast.textkit.editor.core.piecetable.models.RichPieceTransaction
import com.jjrodcast.textkit.editor.core.piecetable.models.Source

internal object PieceTableProcessor {

    internal fun getRightPieceTransaction(
        rightModel: TextEditorModel,
        centralModel: TextEditorModel,
        indexOfCentralPiece: Int,
        offset: Int,
        length: Int,
        marks: Set<Mark>
    ): RichPieceTransaction {
        if (length <= 0) return RichPieceTransaction.Empty
        // O-O
        // A-A
        val rightPiece = rightModel.piece
        val centralPiece = centralModel.piece
        return if (rightPiece.source == centralPiece.source) {
            if (rightPiece.isDecorator || rightPiece.isMention || centralPiece.isMention) {
                // A mention is atomic — never coalesce it with a neighbor; just (re)mark the central
                // piece, exactly like decorators are kept out of merges.
                getCentralPieceTransaction(centralModel, indexOfCentralPiece, offset, length, marks)
            } else if (centralModel.isLastOnParagraph || rightPiece.offset < centralPiece.offset) {
                getCentralPieceTransaction(centralModel, indexOfCentralPiece, offset, length, marks)
            } else {
                val isCloser = centralPiece.offset + centralPiece.length == rightPiece.offset
                if (isCloser) {
                    val newPieces = buildList(2) {
                        val head = centralPiece.copy(
                            offset = centralPiece.offset,
                            length = centralPiece.length - length
                        )
                        if (head.length > 0) add(head)
                        add(
                            centralPiece.copy(
                                offset = centralPiece.offset + centralPiece.length - length,
                                length = length + rightPiece.length,
                                marks = marks
                            )
                        )
                    }
                    return RichPieceTransaction(
                        insertAtIndex = indexOfCentralPiece,
                        removedPieces = ArrayDeque<RichPiece>(2).apply {
                            add(centralPiece); add(
                            rightPiece
                        )
                        },
                        insertedPieces = ArrayDeque(newPieces)
                    )
                } else {
                    getCentralPieceTransaction(
                        centralModel,
                        indexOfCentralPiece,
                        offset,
                        length,
                        marks
                    )
                }
            }
        } else {
            // O-A
            // A-O
            val newPieces = buildList(2) {
                val head = centralPiece.copy(
                    offset = centralPiece.offset,
                    length = centralPiece.length - length
                )
                if (head.length > 0) add(head)
                add(
                    centralPiece.copy(
                        offset = centralPiece.offset + centralPiece.length - length,
                        length = length,
                        marks = marks
                    )
                )
            }
            return RichPieceTransaction(
                insertAtIndex = indexOfCentralPiece,
                removedPieces = ArrayDeque<RichPiece>(1).apply { add(centralPiece) },
                insertedPieces = ArrayDeque(newPieces)
            )
        }
    }

    internal fun getLeftPieceTransaction(
        leftModel: TextEditorModel,
        indexOfLeftPiece: Int,
        centralModel: TextEditorModel,
        indexOfCentralPiece: Int,
        offset: Int,
        length: Int,
        marks: Set<Mark>
    ): RichPieceTransaction {
        if (length <= 0) return RichPieceTransaction.Empty
        // A-A
        // O-O
        val leftPiece = leftModel.piece
        val centralPiece = centralModel.piece
        return if (leftPiece.source == centralPiece.source) {
            if (leftPiece.isDecorator || leftPiece.isMention || centralPiece.isMention) {
                // A mention is atomic — never coalesce it with a neighbor; just (re)mark the central
                // piece, exactly like decorators are kept out of merges.
                getCentralPieceTransaction(centralModel, indexOfCentralPiece, offset, length, marks)
            } else if (leftModel.isLastOnParagraph || centralPiece.offset < leftPiece.offset) {
                getCentralPieceTransaction(centralModel, indexOfCentralPiece, offset, length, marks)
            } else {
                val isCloser = leftPiece.offset + leftPiece.length == centralPiece.offset
                if (isCloser) {
                    val newPieces = buildList(2) {
                        add(
                            leftPiece.copy(
                                offset = leftPiece.offset,
                                length = leftPiece.length + length,
                                marks = marks
                            )
                        )
                        val tail = centralPiece.copy(
                            offset = leftPiece.offset + leftPiece.length + length,
                            length = centralPiece.length - length
                        )
                        if (tail.length > 0) add(tail)
                    }
                    return RichPieceTransaction(
                        insertAtIndex = indexOfLeftPiece,
                        removedPieces = ArrayDeque<RichPiece>(2).apply {
                            add(leftPiece); add(
                            centralPiece
                        )
                        },
                        insertedPieces = ArrayDeque(newPieces)
                    )
                } else {
                    getCentralPieceTransaction(
                        centralModel,
                        indexOfCentralPiece,
                        offset,
                        length,
                        marks
                    )
                }
            }
        } else {
            // O-A
            // A-O
            val newPieces = buildList(2) {
                add(centralPiece.copy(offset = centralPiece.offset, length = length, marks = marks))
                val tail = centralPiece.copy(
                    offset = centralPiece.offset + length,
                    length = centralPiece.length - length
                )
                if (tail.length > 0) add(tail)
            }
            return RichPieceTransaction(
                insertAtIndex = indexOfCentralPiece,
                removedPieces = ArrayDeque<RichPiece>(1).apply { add(centralPiece) },
                insertedPieces = ArrayDeque(newPieces)
            )
        }
    }

    internal fun getCentralPieceTransaction(
        centralModel: TextEditorModel,
        indexOfCentralPiece: Int,
        offset: Int,
        length: Int,
        marks: Set<Mark>
    ): RichPieceTransaction {
        if (length <= 0) return RichPieceTransaction.Empty
        val centralPiece = centralModel.piece
        val delta = offset - centralModel.offsetInDocument
        val newPieces = buildList(3) {
            val head = centralPiece.copy(
                source = centralPiece.source,
                offset = centralPiece.offset,
                length = delta
            )
            if (head.length > 0) add(head)
            add(
                centralPiece.copy(
                    source = centralPiece.source,
                    offset = centralPiece.offset + delta,
                    length = length,
                    marks = marks
                )
            )
            val tail = centralPiece.copy(
                source = centralPiece.source,
                offset = centralPiece.offset + delta + length,
                length = (centralModel.offsetInDocument + centralPiece.length) - (offset + length)
            )
            if (tail.length > 0) add(tail)
        }

        return RichPieceTransaction(
            insertAtIndex = indexOfCentralPiece,
            removedPieces = ArrayDeque<RichPiece>(1).apply { add(centralPiece) },
            insertedPieces = ArrayDeque(newPieces)
        )
    }

    internal fun getBothPiecesTransaction(
        leftModel: TextEditorModel,
        indexOfLeftPiece: Int,
        centralModel: TextEditorModel,
        indexOfCentralPiece: Int,
        rightModel: TextEditorModel,
        offset: Int,
        length: Int,
        marks: Set<Mark>
    ): RichPieceTransaction {
        val centralPiece = centralModel.piece
        val leftPiece = leftModel.piece
        val rightPiece = rightModel.piece
        return when {
            // A-A-A
            // O-O-O
            leftPiece.source == centralPiece.source && centralPiece.source == rightPiece.source -> {
                if (centralPiece.source == Source.ADDED) {
                    mergeIfPossible(
                        leftModel,
                        indexOfLeftPiece,
                        centralModel,
                        indexOfCentralPiece,
                        rightModel,
                        offset,
                        length,
                        marks
                    )
                } else if (leftModel.isLastOnParagraph) {
                    // left is in another paragraph
                    // we need to check if we can merge just central and right
                    getRightPieceTransaction(
                        rightModel,
                        centralModel,
                        indexOfCentralPiece,
                        offset,
                        length,
                        marks
                    )
                } else if (centralModel.isLastOnParagraph) {
                    // Left and central are in the same paragraph
                    // we need to check if we can merge just left and central
                    getLeftPieceTransaction(
                        leftModel,
                        indexOfLeftPiece,
                        centralModel,
                        indexOfCentralPiece,
                        offset,
                        length,
                        marks
                    )
                } else {
                    mergeAllPieces(
                        leftModel,
                        indexOfLeftPiece,
                        centralModel,
                        indexOfCentralPiece,
                        rightModel,
                        marks
                    )
                }
            }
            // A-A-O
            // O-O-A
            leftPiece.source == centralPiece.source && centralPiece.source != rightPiece.source -> {
                if (centralPiece.source == Source.ADDED) {
                    getLeftPieceTransaction(
                        leftModel,
                        indexOfLeftPiece,
                        centralModel,
                        indexOfCentralPiece,
                        offset,
                        length,
                        marks
                    )
                } else if (leftModel.isLastOnParagraph) {
                    getCentralPieceTransaction(
                        centralModel,
                        indexOfCentralPiece,
                        centralPiece.offset,
                        centralPiece.length,
                        marks
                    )
                } else {
                    // Merge left and central if possible
                    getLeftPieceTransaction(
                        leftModel,
                        indexOfLeftPiece,
                        centralModel,
                        indexOfCentralPiece,
                        offset,
                        length,
                        marks
                    )
                }
            }
            // A-O-O
            // O-A-A
            leftPiece.source != centralPiece.source && centralPiece.source == rightPiece.source -> {
                if (centralPiece.source == Source.ADDED) {
                    getRightPieceTransaction(
                        rightModel,
                        centralModel,
                        indexOfCentralPiece,
                        offset,
                        length,
                        marks
                    )
                } else if (centralModel.isLastOnParagraph) {
                    getCentralPieceTransaction(
                        centralModel,
                        indexOfCentralPiece,
                        centralPiece.offset,
                        centralPiece.length,
                        marks
                    )
                } else {
                    // Try to merge central and right
                    getRightPieceTransaction(
                        rightModel,
                        centralModel,
                        indexOfCentralPiece,
                        offset,
                        length,
                        marks
                    )
                }
            }
            // A-O-A
            // O-A-O
            else -> {
                // We can't merge the pieces so we apply the marks to the current selection.
                val newPiece = centralPiece.copy(marks = marks)
                RichPieceTransaction(
                    insertAtIndex = indexOfCentralPiece,
                    removedPieces = ArrayDeque<RichPiece>(1).apply { add(centralPiece) },
                    insertedPieces = ArrayDeque<RichPiece>(1).apply { add(newPiece) }
                )
            }
        }
    }

    private fun mergeAllPieces(
        leftModel: TextEditorModel,
        indexOfLeftPiece: Int,
        centralModel: TextEditorModel,
        indexOfCentralPiece: Int,
        rightModel: TextEditorModel,
        marks: Set<Mark>
    ): RichPieceTransaction {
        val leftPiece = leftModel.piece
        val rightPiece = rightModel.piece
        val centralPiece = centralModel.piece

        // A mention is atomic: never coalesce it with its neighbors. This path is only reached when
        // the neighbors already carry the target marks, so it is enough to (re)mark the central piece
        // on its OWN range. Merging would feed a left-spanning offset into getCentralPieceTransaction
        // and compute a negative buffer offset (crash) plus a piece that swallows the neighbor's text.
        if (leftPiece.isMention || centralPiece.isMention || rightPiece.isMention) {
            return getCentralPieceTransaction(
                centralModel,
                indexOfCentralPiece,
                centralModel.offsetInDocument,
                centralPiece.length,
                marks
            )
        }

        val leftLengthTest = leftPiece.offset + leftPiece.length
        val centralLengthTest = centralPiece.offset + centralPiece.length
        return when {
            leftLengthTest == centralPiece.offset && centralLengthTest == rightPiece.offset -> {
                val newLeftPiece = if (leftPiece.isDecorator) centralPiece else leftPiece
                val leftLength = if (leftPiece.isDecorator) 0 else leftPiece.length
                val rightLength = if (rightPiece.isDecorator) 0 else rightPiece.length
                val indexOfPiece =
                    if (leftPiece.isDecorator) indexOfCentralPiece else indexOfLeftPiece

                val fullLength = leftLength + centralPiece.length + rightLength
                val newPiece = newLeftPiece.copy(length = fullLength, marks = marks)

                RichPieceTransaction(
                    insertAtIndex = indexOfPiece,
                    removedPieces = ArrayDeque<RichPiece>(3).apply {
                        if (!leftPiece.isDecorator) add(leftPiece)
                        add(centralPiece)
                        if (!rightPiece.isDecorator) add(rightPiece)
                    },
                    insertedPieces = ArrayDeque<RichPiece>(1).apply { add(newPiece) }
                )
            }

            leftLengthTest == centralPiece.offset -> {
                val length = leftPiece.length + centralPiece.length
                getLeftPieceTransaction(
                    leftModel,
                    indexOfLeftPiece,
                    centralModel,
                    indexOfCentralPiece,
                    leftPiece.offset,
                    length,
                    marks
                )
            }

            centralLengthTest == rightPiece.offset -> {
                val length = centralPiece.length + rightPiece.length
                getRightPieceTransaction(
                    rightModel,
                    centralModel,
                    indexOfCentralPiece,
                    centralPiece.offset,
                    length,
                    marks
                )
            }

            else -> RichPieceTransaction.Empty
        }
    }

    private fun mergeIfPossible(
        leftModel: TextEditorModel,
        indexOfLeftPiece: Int,
        centralModel: TextEditorModel,
        indexOfCentralPiece: Int,
        rightModel: TextEditorModel,
        offset: Int,
        length: Int,
        marks: Set<Mark>
    ): RichPieceTransaction {
        val leftPiece = leftModel.piece
        val rightPiece = rightModel.piece
        val centralPiece = centralModel.piece

        /**
         * 0 1 2    left<central<right = 0-1-2 = -3
         * 1 2 0    central<right<left = 1-2-0 = -1
         * 2 0 1    right<left<central = 2-0-1 =  2
         *
         * 0 2 1    left<right<central = 0-2-1 = -3
         * 1 0 2    central<left<right = 1-0-2 =  1
         * 2 1 0    right<central<left = 2-1-0 =  1
         */

        return when {
            leftPiece.offset < centralPiece.offset && centralPiece.offset < rightPiece.offset -> {
                // left<central<right
                if (leftModel.isLastOnParagraph) {
                    getRightPieceTransaction(
                        rightModel,
                        centralModel,
                        indexOfCentralPiece,
                        offset,
                        length,
                        marks
                    )
                } else if (centralModel.isLastOnParagraph) {
                    getLeftPieceTransaction(
                        leftModel,
                        indexOfLeftPiece,
                        centralModel,
                        indexOfCentralPiece,
                        offset,
                        length,
                        marks
                    )
                } else {
                    mergeAllPieces(
                        leftModel,
                        indexOfLeftPiece,
                        centralModel,
                        indexOfCentralPiece,
                        rightModel,
                        marks
                    )
                }
            }

            centralPiece.offset < rightPiece.offset && rightPiece.offset < leftPiece.offset -> {
                // central<right<left 1<2<0
                getRightPieceTransaction(
                    rightModel,
                    centralModel,
                    indexOfCentralPiece,
                    offset,
                    length,
                    marks
                )
            }

            rightPiece.offset < leftPiece.offset && leftPiece.offset < centralPiece.offset -> {
                // right<left<central
                getLeftPieceTransaction(
                    leftModel,
                    indexOfLeftPiece,
                    centralModel,
                    indexOfCentralPiece,
                    offset,
                    length,
                    marks
                )
            }

            else -> getCentralPieceTransaction(
                centralModel,
                indexOfCentralPiece,
                offset,
                length,
                marks
            )
        }
    }
}
