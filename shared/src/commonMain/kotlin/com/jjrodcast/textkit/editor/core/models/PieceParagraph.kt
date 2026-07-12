package com.jjrodcast.textkit.editor.core.models

import com.jjrodcast.textkit.editor.core.piecetable.models.RichPiece
import com.jjrodcast.textkit.editor.core.piecetable.models.TextDecoratorModel.Companion.toTextEditorListItem
import com.jjrodcast.textkit.editor.utils.intersect

internal data class PieceParagraph(
    val pieces: List<TextEditorModel>,
    internal val start: Int,
    internal val end: Int
) {

    val startPiece: RichPiece
        get() = pieces.first().piece

    val endPiece: RichPiece
        get() = pieces.last().piece

    val startText get() = pieces.first().text

    val endText get() = pieces.last().text

    /** Document start offset of the first piece in this paragraph. */
    val startOffset: Int get() = pieces.first().offsetInDocument

    /** Document start offset of the last piece in this paragraph. */
    val endOffset: Int get() = pieces.last().offsetInDocument

    val text
        get() = buildString {
            val pieces = if (isListItem) pieces.drop(1) else pieces
            pieces.forEach { append(it.text) }
        }

    val isSamePiece get() = pieces.size == 1

    // Single lazy pass over pieces; partition produces both halves together so the second
    // access (in or out of range) costs no additional iteration.
    private val piecesPartitioned by lazy {
        pieces.partition { item ->
            val pieceStart = item.offsetInDocument
            val length = item.offsetInDocument + item.piece.length
            intersect(start, end, pieceStart, length)
        }
    }

    val piecesInSelectedRange get() = piecesPartitioned.first

    val piecesOutOfRange get() = piecesPartitioned.second

    val isListItem get() = startPiece.decorator != null

    val paragraphType get() = startPiece.decorator.toTextEditorListItem()

    fun findPiecesInRange(start: Int, end: Int): List<TextEditorModel> {
        return pieces
            .filter { item ->
                val pieceStart = item.offsetInDocument
                val length = item.offsetInDocument + item.piece.length
                intersect(start, end, pieceStart, length)
            }
    }

    fun isAtEndOfParagraph(start: Int, end: Int): Boolean {
        if (start != end) return false
        return endPiece.endsWithLineBreak &&
                end == (endOffset + endPiece.length)
    }
}
