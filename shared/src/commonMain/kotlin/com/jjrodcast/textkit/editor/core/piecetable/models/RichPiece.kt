package com.jjrodcast.textkit.editor.core.piecetable.models

import com.jjrodcast.textkit.editor.core.parser.Mark
import com.jjrodcast.textkit.editor.utils.intersect
import kotlinx.serialization.Serializable

@Serializable
internal data class RichPiece(
    override val source: Source,
    override val offset: Int,
    override val length: Int,
    override val decorator: TextDecoratorModel? = null,
    val marks: Set<Mark> = emptySet(),
    // When non-null this piece is an atomic trigger token (mention, hashtag, …): its visible text is
    // "<triggerKey><label>" and its identity (type + id + label) lives here so it survives the
    // piece-table round-trip and can be serialized back to the right inline node. Selection/editing
    // treats it as indivisible.
    val token: RichToken? = null,
    val isLineBreak: Boolean = false,
    val endsWithLineBreak: Boolean = false
) : Piece() {
    val isDecorator get() = decorator != null

    val isToken get() = token != null

    fun intersect(start: Int, end: Int) = intersect(start, end, offset, offset + length)

    fun hasSameMarksWith(piece: RichPiece): Boolean {
        if (marks.isEmpty() && piece.marks.isEmpty()) return true
        return Mark.areTheSame(piece.marks, marks) && piece.marks.isNotEmpty()
    }
}
