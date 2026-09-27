package com.jjrodcast.textkit.editor.models

import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.components.TextEditorDecoratorItem
import com.jjrodcast.textkit.editor.components.TextEditorListItem
import com.jjrodcast.textkit.editor.core.parser.LinkMark
import com.jjrodcast.textkit.editor.core.parser.Mark
import com.jjrodcast.textkit.editor.core.parser.TextAlign
import com.jjrodcast.textkit.editor.core.parser.TextDirection

data class MarkSearchType(
    val marks: Set<Mark> = emptySet(),
    val listItem: TextEditorDecoratorItem = TextEditorListItem.None,
    val range: TextRange = TextRange.Zero,
    val text: String = "",
    // Alignment shared by the paragraph(s) the selection touches, or null when it spans paragraphs
    // with differing alignment (a "mixed" selection) or there is nothing selected.
    val textAlign: TextAlign? = null,
    // Writing direction shared by the paragraph(s) the selection touches; null when mixed/empty.
    val textDirection: TextDirection? = null
) {
    val hasLink get() = marks.any { it is LinkMark }

    val isEmpty
        get() = marks.isEmpty() && text.isEmpty() &&
                listItem == TextEditorListItem.None &&
                range == TextRange.Zero
}
