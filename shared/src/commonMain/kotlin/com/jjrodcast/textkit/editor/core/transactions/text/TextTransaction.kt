package com.jjrodcast.textkit.editor.core.transactions.text

import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.core.TextKitEditorManager
import com.jjrodcast.textkit.editor.core.transactions.TextEditorTransaction
import com.jjrodcast.textkit.editor.core.transactions.lists.models.TextEditorDecoratorTransactionType
import com.jjrodcast.textkit.editor.core.transactions.lists.models.TextEditorListItemTransaction
import com.jjrodcast.textkit.editor.core.transactions.models.TextEditorAction

/**
 * This class manage the list item editting using the keyboard.
 */
object TextTransaction {

    fun onTextUpdated(
        actionModel: TextEditorAction,
        manager: TextKitEditorManager
    ): Pair<Boolean, TextRange> {
        val (selection, transactions) = when (actionModel) {
            is TextEditorAction.TextAdded -> {
                val lines = manager.transaction.getLineContentWithNeigborParagraphs(actionModel.offset, actionModel.offset)
                TextInsertedTransaction.addText(lines, actionModel)
            }

            is TextEditorAction.TextRemoved -> {
                val lines =
                    manager.transaction.getLineContentWithNeigborParagraphs(actionModel.offset, actionModel.offset + actionModel.length)
                TextDeletedTransaction.deleteText(lines, actionModel, manager)
            }

            is TextEditorAction.TextUpdated -> {
                val lines =
                    manager.transaction.getLineContentWithNeigborParagraphs(
                        actionModel.offset,
                        actionModel.offset + actionModel.removeLength
                    )
                TextUpdateTransaction.updateText(lines, actionModel, manager)
            }

            else -> Pair(TextRange(0), emptyList())
        }

        manager.transaction.commitChanges(transactions)

        return Pair(true, selection)
    }

    private fun TextEditorTransaction.commitChanges(transactions: List<TextEditorListItemTransaction>): Boolean {
        transactions
            .sortedByDescending { it.offsetInDocument }
            .forEach { transaction ->
                when (val type = transaction.type) {
                    is TextEditorDecoratorTransactionType.Insert -> insert(type.model, transaction.offsetInDocument)
                    is TextEditorDecoratorTransactionType.Update -> update(transaction.offsetInDocument, type.length, type.model)
                    is TextEditorDecoratorTransactionType.Delete -> delete(transaction.offsetInDocument, type.length)
                }
            }
        return transactions.isNotEmpty()
    }
}
