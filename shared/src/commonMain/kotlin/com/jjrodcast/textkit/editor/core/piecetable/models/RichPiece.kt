package com.jjrodcast.textkit.editor.core.piecetable.models

import com.jjrodcast.textkit.editor.core.parser.Mark
import com.jjrodcast.textkit.editor.core.parser.MentionAttrs
import com.jjrodcast.textkit.editor.utils.intersect
import kotlinx.serialization.Serializable

@Serializable
internal data class RichPiece(
    override val source: Source,
    override val offset: Int,
    override val length: Int,
    override val decorator: TextDecoratorModel? = null,
    val marks: Set<Mark> = emptySet(),
    // When non-null this piece is an atomic mention: its visible text is "<triggerKey><label>"
    // and its identity (id + label) lives here so it survives the piece-table round-trip and can
    // be serialized back to a `mention` node. Selection/editing treats it as indivisible.
    val mention: MentionAttrs? = null,
    val isLineBreak: Boolean = false,
    val endsWithLineBreak: Boolean = false
) : Piece() {
    val isDecorator get() = decorator != null

    val isMention get() = mention != null

    fun intersect(start: Int, end: Int) = intersect(start, end, offset, offset + length)

    fun hasSameMarksWith(piece: RichPiece): Boolean {
        if (marks.isEmpty() && piece.marks.isEmpty()) return true
        return Mark.areTheSame(piece.marks, marks) && piece.marks.isNotEmpty()
    }
}
