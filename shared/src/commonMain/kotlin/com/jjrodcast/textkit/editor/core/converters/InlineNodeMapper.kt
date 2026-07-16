package com.jjrodcast.textkit.editor.core.converters

import com.jjrodcast.textkit.editor.core.models.TextEditorModel
import com.jjrodcast.textkit.editor.core.parser.BaseText
import com.jjrodcast.textkit.editor.core.parser.Mention
import com.jjrodcast.textkit.editor.core.parser.Text
import com.jjrodcast.textkit.editor.utils.removeLineBreakSuffix

/**
 * Converts a single flat [TextEditorModel] back into its inline [BaseText] node when serializing the
 * piece table to a document. A piece carrying a [com.jjrodcast.textkit.editor.core.parser.MentionAttrs]
 * becomes a [Mention] (its id/label come from the piece metadata, never from the visible text);
 * everything else becomes a [Text].
 *
 * Callers are responsible for filtering out decorator and line-break pieces beforehand.
 */
internal fun TextEditorModel.toInlineNode(): BaseText {
    val mention = piece.mention
    return if (mention != null) {
        Mention(attrs = mention, marks = piece.marks.toSet())
    } else {
        Text(text = text.removeLineBreakSuffix(), marks = piece.marks.toSet())
    }
}
