package com.jjrodcast.textkit.editor.core.transactions.lists.models

import com.jjrodcast.textkit.editor.core.models.TextEditorModel

internal sealed class TextEditorDecoratorTransactionType {
    data class Delete(val length: Int) : TextEditorDecoratorTransactionType()
    data class Update(val model: TextEditorModel, val length: Int) : TextEditorDecoratorTransactionType()
    data class Insert(val model: TextEditorModel) : TextEditorDecoratorTransactionType()
}
