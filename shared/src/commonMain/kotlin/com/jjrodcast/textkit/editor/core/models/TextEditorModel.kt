package com.jjrodcast.textkit.editor.core.models

import com.jjrodcast.textkit.editor.components.TextEditorDecoratorItem
import com.jjrodcast.textkit.editor.components.TextEditorListItem
import com.jjrodcast.textkit.editor.core.parser.Mark
import com.jjrodcast.textkit.editor.core.piecetable.models.RichPiece
import com.jjrodcast.textkit.editor.core.piecetable.models.Source
import com.jjrodcast.textkit.editor.core.piecetable.models.TextDecoratorModel
import com.jjrodcast.textkit.editor.core.piecetable.models.TextDecoratorModel.Companion.NONE_KEY
import com.jjrodcast.textkit.editor.utils.intersect
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
internal data class TextEditorModel(
    val piece: RichPiece,
    val text: String,
    val paragraphType: TextEditorDecoratorItem = TextEditorListItem.None,
    @Transient val offsetInDocument: Int = 0
) {
    val isDecorator get() = piece.isDecorator
    val pieceStart get() = offsetInDocument
    val pieceEnd get() = offsetInDocument + piece.length
    val pieceLength get() = piece.length
    val isLastOnParagraph get() = piece.endsWithLineBreak

    companion object {

        internal fun TextEditorModel?.getKey(): String = if (this?.piece?.decorator != null) this.piece.decorator.key else NONE_KEY

        fun create(
            text: String,
            piece: RichPiece,
            offsetInDocument: Int = piece.offset
        ): TextEditorModel {
            return TextEditorModel(
                piece = piece,
                text = text,
                paragraphType = TextEditorListItem.None,
                offsetInDocument = offsetInDocument
            )
        }

        fun create(
            piece: RichPiece,
            offsetInDocument: Int = piece.offset
        ): TextEditorModel {
            return TextEditorModel(
                piece = piece,
                text = "^".repeat(piece.length), // Fake string
                paragraphType = TextEditorListItem.None,
                offsetInDocument = offsetInDocument
            )
        }

        fun create(
            text: String,
            marks: Set<Mark> = emptySet(),
            decorator: TextDecoratorModel? = null,
            paragraphType: TextEditorDecoratorItem = TextEditorListItem.None,
            source: Source = Source.ADDED,
            offset: Int = 0,
        ): TextEditorModel {
            return TextEditorModel(
                piece = RichPiece(source = source, offset = offset, length = text.length, marks = marks, decorator = decorator),
                text = text,
                paragraphType = paragraphType
            )
        }

        fun TextEditorModel.update(
            text: String = this.text,
            marks: Set<Mark> = this.piece.marks,
            decorator: TextDecoratorModel? = this.piece.decorator
        ): TextEditorModel {
            return copy(piece = piece.copy(length = text.length, marks = marks, decorator = decorator), text = text)
        }

        fun TextEditorModel.update(
            text: String,
            piece: RichPiece
        ): TextEditorModel {
            return copy(piece = piece.copy(length = text.length), text = text)
        }

        fun TextEditorModel.intersectWithOffset(start: Int, end: Int): Boolean {
            val pieceEnd = offsetInDocument + text.length
            return intersect(start, end, offsetInDocument, pieceEnd)
        }
    }
}
