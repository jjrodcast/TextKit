package com.jjrodcast.textkit.editor.core.piecetable.models

internal data class RichPieceTransaction(
    val insertAtIndex: Int = -1,
    val removedPieces: ArrayDeque<RichPiece> = ArrayDeque(),
    val insertedPieces: ArrayDeque<RichPiece> = ArrayDeque()
) {

    companion object {
        val Empty = RichPieceTransaction()
    }
}
