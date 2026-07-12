package com.jjrodcast.textkit.editor.models

import com.jjrodcast.textkit.editor.components.TextEditorDecoratorItem
import com.jjrodcast.textkit.editor.components.TextEditorListItem
import com.jjrodcast.textkit.editor.core.parser.LinkMark
import com.jjrodcast.textkit.editor.core.parser.Mark
import com.jjrodcast.textkit.editor.core.transactions.models.TextEditorRange

data class MarkSearchType(
    val marks: Set<Mark> = emptySet(),
    val listItem: TextEditorDecoratorItem = TextEditorListItem.None,
    val range: TextEditorRange = TextEditorRange.Zero,
    val text: String = ""
) {
    val hasLink get() = marks.any { it is LinkMark }

    val isEmpty
        get() = marks.isEmpty() && text.isEmpty() &&
                listItem == TextEditorListItem.None &&
                range == TextEditorRange.Zero
}
