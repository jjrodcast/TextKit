package com.jjrodcast.textkit.editor.core.converters.models

import com.jjrodcast.textkit.editor.core.models.TextEditorModel
import com.jjrodcast.textkit.editor.core.parser.BaseText
import com.jjrodcast.textkit.editor.core.parser.Text
import com.jjrodcast.textkit.editor.utils.isLineBreak
import com.jjrodcast.textkit.editor.utils.removeLineBreakSuffix

/**
 * Class to store paragraphs content using positions.
 *
 * @property index Represents the positions of the paragraph in the document.
 * @property level We store the level of the element. If this is a normal paragraph we store 0 but if we are storing a list item
 * the value depends on the level of the item (if the item is a nested item the value is greater).
 * @property key Key represents the type of list item we are trying to store.
 * For lists, we use:
 * - NUMBERED_LIST_KEY
 * - BULLETED_LIST_KEY
 * - TASK_LIST_KEY
 *
 * For paragraphs, we use:
 * - NONE_KEY
 */
internal data class PositionalParagraph(
    val index: Int,
    val level: Int,
    val key: String,
    val textStyled: List<TextEditorModel> = emptyList(),
    val positionalParagraphs: ArrayList<PositionalParagraph> = arrayListOf()
) {
    var isVisited: Boolean = false

    internal fun getParagraphContent(): List<BaseText> {
        return if (textStyled.any { it.text.isNotEmpty() }) {
            textStyled.mapNotNull {
                if (it.text.isLineBreak()) null
                else Text(text = it.text.removeLineBreakSuffix(), marks = it.piece.marks)
            }
        } else {
            emptyList()
        }
    }
}
