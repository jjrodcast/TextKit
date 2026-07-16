package com.jjrodcast.textkit.editor.core.piecetable

import com.jjrodcast.textkit.editor.core.models.TextEditorModel
import com.jjrodcast.textkit.editor.core.piecetable.models.RichPiece
import com.jjrodcast.textkit.editor.core.piecetable.models.Source
import com.jjrodcast.textkit.editor.utils.endsWithLineBreak
import com.jjrodcast.textkit.editor.utils.isLineBreak

internal class RichTextEditorPieceTable : RichTextEditorBasePieceTable() {

    override fun insert(model: TextEditorModel, offset: Int): Boolean {
        if (model.text.isEmpty()) return false

        val addBufferOffset = addedBuffer.length
        addedBuffer.append(model.text)

        val (pieceIndex, bufferOffset) = getPieceIndexAndOffset(offset)
        val prevOriginalPiece = rope.get(pieceIndex)
        val newOriginalPiece: RichPiece
        if (isSamePiece(
                prevOriginalPiece,
                bufferOffset,
                addBufferOffset,
                model,
                prevOriginalPiece.isParagraph(addedBuffer, originalBuffer)
            )
        ) {
            // Fast path: extend the existing ADDED piece — O(log P), no structural split needed.
            // isLineBreak stays false because isSamePiece guards against paragraph pieces.
            newOriginalPiece = prevOriginalPiece.copy(
                length = prevOriginalPiece.length + model.text.length,
                endsWithLineBreak = model.text.endsWithLineBreak()
            )
            rope.replaceAt(pieceIndex, newOriginalPiece)
            patchCache(offset, 0, model.text)
            return false
        } else {
            newOriginalPiece = prevOriginalPiece
        }
        val insertPieces = buildList(3) {
            val headLength = bufferOffset - newOriginalPiece.offset
            val head = RichPiece(
                source = newOriginalPiece.source,
                offset = newOriginalPiece.offset,
                length = headLength,
                marks = newOriginalPiece.marks,
                decorator = newOriginalPiece.decorator,
                // Edits are token-boundary aligned, so an atomic token piece is never split:
                // headLength is either 0 (dropped) or its full length, so carrying the token here is
                // safe.
                token = newOriginalPiece.token,
                isLineBreak = newOriginalPiece.isLineBreak,
                endsWithLineBreak = charAtEndIsLineBreak(newOriginalPiece.source, newOriginalPiece.offset, headLength)
            )
            if (head.length > 0) add(head)
            add(
                RichPiece(
                    source = Source.ADDED,
                    offset = addBufferOffset,
                    length = model.text.length,
                    marks = model.piece.marks,
                    decorator = model.piece.decorator,
                    token = model.piece.token,
                    isLineBreak = model.text.isLineBreak(),
                    endsWithLineBreak = model.text.endsWithLineBreak()
                )
            )
            val remaining = createRemainingPiece(newOriginalPiece, bufferOffset)
            if (remaining.length > 0) add(remaining)
        }
        // O(log P): splice replaces the one piece at pieceIndex with insertPieces
        rope.splice(pieceIndex, pieceIndex + 1, insertPieces)
        patchCache(offset, 0, model.text)
        return true
    }

    override fun delete(offset: Int, length: Int): Boolean {
        if (length == 0) return false
        if (length < 0) {
            return delete(offset + length, -length)
        }
        if (offset < 0) throw IndexOutOfBoundsException("Index out of bounds:delete: $offset")

        val (initialAffectedPieceIndex, initialBufferOffset) = getPieceIndexAndOffset(offset)
        val (finalAffectedPieceIndex, finalBufferOffset) = getPieceIndexAndOffset(offset + length)
        val newPiece: RichPiece

        if (initialAffectedPieceIndex == finalAffectedPieceIndex) {
            val piece = rope.get(initialAffectedPieceIndex)
            if (initialBufferOffset == piece.offset) {
                // Deleting from the start — the END of the piece is unchanged.
                val newLength = piece.length - length
                newPiece = piece.copy(
                    offset = piece.offset + length,
                    length = newLength,
                    decorator = if (newLength == 0) null else piece.decorator,
                    endsWithLineBreak = piece.endsWithLineBreak && newLength > 0
                )
                rope.replaceAt(initialAffectedPieceIndex, newPiece)
                patchCache(offset, length, "")
                return false
            } else if (finalBufferOffset == piece.offset + piece.length) {
                // Deleting from the end — the START is unchanged but the END moves.
                val newLength = piece.length - length
                newPiece = piece.copy(
                    length = newLength,
                    decorator = if (newLength == 0) null else piece.decorator,
                    endsWithLineBreak = charAtEndIsLineBreak(piece.source, piece.offset, newLength)
                )
                rope.replaceAt(initialAffectedPieceIndex, newPiece)
                patchCache(offset, length, "")
                return false
            }
        }

        val initialPiece = rope.get(initialAffectedPieceIndex)
        val finalPiece = rope.get(finalAffectedPieceIndex)
        val deletePieces = buildList(2) {
            val leftLength = initialBufferOffset - initialPiece.offset
            val left = RichPiece(
                source = initialPiece.source,
                offset = initialPiece.offset,
                length = leftLength,
                marks = initialPiece.marks,
                decorator = initialPiece.decorator,
                token = initialPiece.token,
                isLineBreak = leftLength > 0 && initialPiece.isLineBreak,
                endsWithLineBreak = charAtEndIsLineBreak(initialPiece.source, initialPiece.offset, leftLength)
            )
            if (left.length > 0) add(left)
            val rightLength = finalPiece.length - (finalBufferOffset - finalPiece.offset)
            val right = RichPiece(
                source = finalPiece.source,
                offset = finalBufferOffset,
                length = rightLength,
                marks = finalPiece.marks,
                decorator = finalPiece.decorator,
                token = finalPiece.token,
                isLineBreak = rightLength > 0 && finalPiece.isLineBreak,
                // Right piece shares the same buffer end as finalPiece.
                endsWithLineBreak = rightLength > 0 && finalPiece.endsWithLineBreak
            )
            if (right.length > 0) add(right)
        }
        // O(log P): splice replaces [initialAffectedPieceIndex, finalAffectedPieceIndex] with deletePieces
        rope.splice(initialAffectedPieceIndex, finalAffectedPieceIndex + 1, deletePieces)
        patchCache(offset, length, "")
        return true
    }

    private fun isSamePiece(
        piece: RichPiece,
        offset: Int,
        bufferOffset: Int,
        model: TextEditorModel,
        isParagraph: Boolean
    ) = piece.source == Source.ADDED &&
        offset == piece.offset + piece.length &&
        piece.offset + piece.length == bufferOffset &&
        piece.hasSameMarksWith(model.piece) && !isParagraph &&
        piece.decorator == null && !model.isDecorator &&
        // An atomic token is indivisible: never absorb typed text into it, and never grow it by
        // appending another piece — both would silently drop characters on serialization (which
        // reads the stored label, not the piece text).
        !piece.isToken && !model.piece.isToken

    private fun RichPiece.isParagraph(addedText: StringBuilder, originalDocumentText: String): Boolean {
        val text = if (source == Source.ADDED) {
            addedText.substring(offset, offset + length)
        } else {
            originalDocumentText.substring(offset, offset + length)
        }
        return text.endsWithLineBreak()
    }

    private fun createRemainingPiece(originalPiece: RichPiece, bufferOffset: Int): RichPiece {
        val remainingLength = originalPiece.length - (bufferOffset - originalPiece.offset)
        val remainingSource = if (originalPiece.source == Source.ADDED) addedBuffer else originalBuffer
        val remainingText = remainingSource.substring(bufferOffset, bufferOffset + remainingLength)
        val remainingMarks = when (remainingText.isLineBreak()) {
            true -> emptySet()
            else -> originalPiece.marks
        }
        return RichPiece(
            source = originalPiece.source,
            offset = bufferOffset,
            length = remainingLength,
            marks = remainingMarks,
            decorator = originalPiece.decorator,
            token = originalPiece.token,
            isLineBreak = remainingText.isLineBreak(),
            endsWithLineBreak = remainingText.endsWithLineBreak()
        )
    }

    /**
     * Returns `true` if the character at position `offset + length - 1` in the
     * backing buffer (selected by [source]) is a line break.
     */
    private fun charAtEndIsLineBreak(source: Source, offset: Int, length: Int): Boolean {
        if (length <= 0) return false
        val buf: CharSequence = if (source == Source.ADDED) addedBuffer else originalBuffer
        return buf[offset + length - 1].isLineBreak()
    }
}
