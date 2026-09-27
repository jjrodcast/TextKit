package com.jjrodcast.textkit.editor.core.transactions.models

import com.jjrodcast.textkit.editor.core.models.TextEditorModel
import com.jjrodcast.textkit.editor.core.parser.EmbedTokenType
import com.jjrodcast.textkit.editor.core.parser.Mark
import com.jjrodcast.textkit.editor.core.parser.MentionType
import com.jjrodcast.textkit.editor.core.parser.TextAlign
import com.jjrodcast.textkit.editor.core.parser.TextDirection
import com.jjrodcast.textkit.editor.core.piecetable.models.RichToken
import com.jjrodcast.textkit.editor.core.piecetable.models.TextDecoratorModel

class TextEditorParagraph(val children: List<TextEditorItem>) {
    /**
     * Horizontal alignment of the paragraph, resolved from its children: the first non-default value
     * wins (an edit can leave a freshly typed piece at [TextAlign.Left] while its neighbours keep the
     * paragraph's value), falling back to [TextAlign.Left]. Use it to build the paragraph's
     * `ParagraphStyle` when rendering.
     */
    val textAlign: TextAlign
        get() = children.firstOrNull { it.textAlign != TextAlign.Left }?.textAlign ?: TextAlign.Left

    /**
     * Writing direction of the paragraph, resolved from its children like [textAlign]. Use it to set
     * the `textDirection` of the paragraph's `ParagraphStyle` when rendering.
     */
    val textDirection: TextDirection
        get() = if (children.any { it.textDirection == TextDirection.Rtl }) TextDirection.Rtl
        else TextDirection.Ltr
}

class TextEditorItem internal constructor(
    val text: String,
    val start: Int,
    val end: Int,
    val decorator: TextDecoratorModel? = null,
    internal val token: RichToken? = null,
    val marks: List<Mark>,
    val textAlign: TextAlign = TextAlign.Left,
    val textDirection: TextDirection = TextDirection.Ltr
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

    /** The embed placeholder's visible label (e.g. "📊 Table"), or null when this item is not an embed. */
    val embedLabel get() = if (isEmbed) token?.attrs?.label else null

    companion object {
        internal fun from(model: TextEditorModel) = TextEditorItem(
            text = model.text,
            decorator = model.piece.decorator,
            token = model.piece.token,
            start = model.offsetInDocument,
            end = model.offsetInDocument + model.piece.length,
            marks = model.piece.marks.toList(),
            textAlign = model.piece.textAlign,
            textDirection = model.piece.textDirection
        )
    }
}
