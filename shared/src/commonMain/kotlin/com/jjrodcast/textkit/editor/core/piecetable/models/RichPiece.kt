package com.jjrodcast.textkit.editor.core.piecetable.models

import com.jjrodcast.textkit.editor.core.parser.Mark
import com.jjrodcast.textkit.editor.core.parser.TextAlign
import com.jjrodcast.textkit.editor.utils.intersect
import kotlinx.serialization.Serializable

@Serializable
internal data class RichPiece(
    override val source: Source,
    override val offset: Int,
    override val length: Int,
    override val decorator: TextDecoratorModel? = null,
    val marks: Set<Mark> = emptySet(),
    // Paragraph-level horizontal alignment carried on every piece of the paragraph. It is a block
    // attribute (all pieces of a paragraph share the same value), stored per-piece so it survives the
    // rope's split/merge/splice operations via `copy()`. Read back on serialization from the
    // paragraph's pieces (see PieceTableConverter).
    val textAlign: TextAlign = TextAlign.Left,
    // When non-null this piece is an atomic trigger token (mention, hashtag, …): its visible text is
    // "<triggerKey><label>" and its identity (type + id + label) lives here so it survives the
    // piece-table round-trip and can be serialized back to the right inline node. Selection/editing
    // treats it as indivisible.
    val token: RichToken? = null,
    val isLineBreak: Boolean = false,
    val endsWithLineBreak: Boolean = false
) : Piece() {
    // Marker pieces only: a blockquote decorator is a paragraph attribute on ordinary content
    // pieces, not an atomic marker — the editing paths' decorator handling must not apply to it.
    val isDecorator get() = decorator?.isMarker == true

    val isToken get() = token != null

    fun intersect(start: Int, end: Int) = intersect(start, end, offset, offset + length)

    fun hasSameMarksWith(piece: RichPiece): Boolean {
        if (marks.isEmpty() && piece.marks.isEmpty()) return true
        return Mark.areTheSame(piece.marks, marks) && piece.marks.isNotEmpty()
    }
}
