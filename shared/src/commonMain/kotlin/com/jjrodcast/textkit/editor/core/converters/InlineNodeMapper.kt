package com.jjrodcast.textkit.editor.core.converters

import com.jjrodcast.textkit.editor.core.models.TextEditorModel
import com.jjrodcast.textkit.editor.core.parser.BaseText
import com.jjrodcast.textkit.editor.core.parser.Hashtag
import com.jjrodcast.textkit.editor.core.parser.HashtagType
import com.jjrodcast.textkit.editor.core.parser.Mention
import com.jjrodcast.textkit.editor.core.parser.MentionType
import com.jjrodcast.textkit.editor.core.parser.Text
import com.jjrodcast.textkit.editor.core.piecetable.models.RichToken
import com.jjrodcast.textkit.editor.utils.removeLineBreakSuffix

/**
 * Converts a single flat [TextEditorModel] back into its inline [BaseText] node when serializing the
 * piece table to a document. A piece carrying a [RichToken] becomes the matching atomic node (its
 * id/label come from the token metadata, never from the visible text); everything else becomes a
 * [Text].
 *
 * Callers are responsible for filtering out decorator and line-break pieces beforehand.
 */
internal fun TextEditorModel.toInlineNode(): BaseText {
    val token = piece.token
    return if (token != null) {
        token.toInlineNode(piece.marks.toSet())
    } else {
        Text(text = text.removeLineBreakSuffix(), marks = piece.marks.toSet())
    }
}

/** Rebuilds the concrete inline node for [RichToken.type]; unknown types fall back to a [Mention]. */
private fun RichToken.toInlineNode(marks: Set<com.jjrodcast.textkit.editor.core.parser.Mark>): BaseText =
    when (type) {
        HashtagType.Hashtag -> Hashtag(attrs = attrs, marks = marks)
        MentionType.Mention -> Mention(attrs = attrs, marks = marks)
        else -> Mention(attrs = attrs, marks = marks)
    }
