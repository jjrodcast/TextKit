package com.jjrodcast.textkit.editor.core.transactions.lists.models

import com.jjrodcast.textkit.editor.components.TextEditorListItem
import com.jjrodcast.textkit.editor.core.piecetable.models.RichPiece

internal data class TextEditorDecoratorLine(
    val piece: RichPiece,
    val offsetInDocument: Int,
    val type: TextEditorListItem
)
