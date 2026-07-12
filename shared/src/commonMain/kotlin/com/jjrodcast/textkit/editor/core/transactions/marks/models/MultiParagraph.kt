package com.jjrodcast.textkit.editor.core.transactions.marks.models

import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.core.models.TextEditorModel
import com.jjrodcast.textkit.editor.utils.fastForEach
import com.jjrodcast.textkit.editor.utils.isLineBreak

internal data class MultiParagraph(
    val range: TextRange,
    val texts: List<TextEditorModel>
) {
    private val fullText
        get() = buildString {
            texts.fastForEach { append(it.text) }
        }

    internal fun isBreakLine() = fullText.isLineBreak()

    internal fun isEmpty() = fullText.isLineBreak() || fullText.isEmpty()

    internal fun isNotEmpty() = !fullText.isLineBreak() && fullText.isNotEmpty()
}
