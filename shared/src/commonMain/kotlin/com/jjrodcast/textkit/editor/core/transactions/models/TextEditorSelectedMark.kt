package com.jjrodcast.textkit.editor.core.transactions.models

import com.jjrodcast.textkit.editor.components.TextEditorListItem
import com.jjrodcast.textkit.editor.core.parser.Mark

data class TextEditorSelectedMark(
    private val listItemSelectedValue: TextEditorListItem? = null,
    val marks: Set<Mark> = emptySet()
) {
    val listItemSelected get() = listItemSelectedValue ?: TextEditorListItem.None
}