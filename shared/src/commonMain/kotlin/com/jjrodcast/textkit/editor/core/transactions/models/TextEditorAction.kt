package com.jjrodcast.textkit.editor.core.transactions.models

import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.core.parser.Mark

sealed class TextEditorAction(open val offset: Int, open val selection: TextRange) {

    data class TextAdded(
        val text: String,
        val marks: Set<Mark> = emptySet(),
        override val offset: Int,
        override val selection: TextRange
    ) : TextEditorAction(offset, selection)

    data class TextRemoved(
        override val offset: Int,
        val length: Int,
        override val selection: TextRange
    ) : TextEditorAction(offset, selection)

    data class TextUpdated(
        val removeLength: Int,
        val text: String,
        override val offset: Int,
        override val selection: TextRange
    ) : TextEditorAction(offset, selection)

    data object None : TextEditorAction(0, TextRange.Zero)
}
