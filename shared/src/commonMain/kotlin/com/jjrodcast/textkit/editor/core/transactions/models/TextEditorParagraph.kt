package com.jjrodcast.textkit.editor.core.transactions.models

import com.jjrodcast.textkit.editor.core.models.TextEditorModel
import com.jjrodcast.textkit.editor.core.parser.Mark
import com.jjrodcast.textkit.editor.core.piecetable.models.TextDecoratorModel

class TextEditorParagraph(val children: List<TextEditorItem>)

class TextEditorItem(
    val text: String,
    val start: Int,
    val end: Int,
    val decorator: TextDecoratorModel? = null,
    val marks: List<Mark>
) {
    companion object {
        internal fun from(model: TextEditorModel) = TextEditorItem(
            text = model.text,
            decorator = model.piece.decorator,
            start = model.offsetInDocument,
            end = model.offsetInDocument + model.piece.length,
            marks = model.piece.marks.toList()
        )
    }
}
