package com.jjrodcast.textkit.editor.models

import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.components.TextEditorDecoratorItem
import com.jjrodcast.textkit.editor.components.TextEditorListItem
import com.jjrodcast.textkit.editor.core.parser.LinkMark
import com.jjrodcast.textkit.editor.core.parser.Mark

data class MarkSearchType(
    val marks: Set<Mark> = emptySet(),
    val listItem: TextEditorDecoratorItem = TextEditorListItem.None,
    val range: TextRange = TextRange.Zero,
    val text: String = ""
) {
    val hasLink get() = marks.any { it is LinkMark }

    val isEmpty
        get() = marks.isEmpty() && text.isEmpty() &&
                listItem == TextEditorListItem.None &&
                range == TextRange.Zero
}
