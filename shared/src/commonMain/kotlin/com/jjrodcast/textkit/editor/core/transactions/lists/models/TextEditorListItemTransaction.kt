package com.jjrodcast.textkit.editor.core.transactions.lists.models

internal data class TextEditorListItemTransaction(
    val offsetInDocument: Int,
    val type: TextEditorDecoratorTransactionType
)
