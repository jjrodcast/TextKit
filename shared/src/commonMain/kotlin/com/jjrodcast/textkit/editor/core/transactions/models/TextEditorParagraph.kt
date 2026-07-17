package com.jjrodcast.textkit.editor.core.transactions.models

import com.jjrodcast.textkit.editor.core.models.TextEditorModel
import com.jjrodcast.textkit.editor.core.parser.EmbedTokenType
import com.jjrodcast.textkit.editor.core.parser.Mark
import com.jjrodcast.textkit.editor.core.parser.MentionType
import com.jjrodcast.textkit.editor.core.piecetable.models.RichToken
import com.jjrodcast.textkit.editor.core.piecetable.models.TextDecoratorModel

class TextEditorParagraph(val children: List<TextEditorItem>)

class TextEditorItem internal constructor(
    val text: String,
    val start: Int,
    val end: Int,
    val decorator: TextDecoratorModel? = null,
    internal val token: RichToken? = null,
    val marks: List<Mark>
) {
    /** True for any atomic trigger token (mention, hashtag, …). */
    val isToken get() = token != null

    /** The persisted node type of this token (`"mention"`, `"hashtag"`, …), or null when not a token. */
    val tokenType get() = token?.type

    /** True specifically for a mention token. */
    val isMention get() = token?.type == MentionType.Mention

    /** True for an embedded-block placeholder (a table/image/document rendered as a one-line chip). */
    val isEmbed get() = token?.type == EmbedTokenType

    /** The embedded block's JSON (verbatim), or null when this item is not an embed. */
    val embedPayload get() = if (isEmbed) token?.payload else null

    companion object {
        internal fun from(model: TextEditorModel) = TextEditorItem(
            text = model.text,
            decorator = model.piece.decorator,
            token = model.piece.token,
            start = model.offsetInDocument,
            end = model.offsetInDocument + model.piece.length,
            marks = model.piece.marks.toList()
        )
    }
}
