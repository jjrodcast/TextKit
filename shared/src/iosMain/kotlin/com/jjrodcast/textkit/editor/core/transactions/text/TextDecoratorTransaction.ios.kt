package com.jjrodcast.textkit.editor.core.transactions.text

import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.core.models.MultiPieceParagraph
import com.jjrodcast.textkit.editor.core.models.PieceParagraph
import com.jjrodcast.textkit.editor.core.piecetable.models.TextDecoratorModel
import com.jjrodcast.textkit.editor.core.transactions.lists.models.TextEditorListItemTransaction
import com.jjrodcast.textkit.editor.core.transactions.models.TextEditorAction
import com.jjrodcast.textkit.editor.utils.TASK_DECORATOR_COMMON
import com.jjrodcast.textkit.editor.utils.TASK_DECORATOR_UNCHECKED_COMMON
import com.plangrid.pgfoundation.texteditor.core.validator.TextInputResult

actual object TextDecoratorTransaction {
    internal actual fun getDeleteTransaction(
        paragraph: PieceParagraph,
        lines: MultiPieceParagraph,
        actionModel: TextEditorAction.TextRemoved
    ): Pair<TextRange, List<TextEditorListItemTransaction>> {
        return TextTransactionsUtils.getCommonDeleteDecoratorTransactions(paragraph, lines)
    }

    internal actual fun getUpdateDecoratorTransaction(
        inputResult: TextInputResult,
        paragraph: PieceParagraph,
        actionModel: TextEditorAction.TextAdded
    ): TextEditorListItemTransaction {
        val deleteLength = getDeleteLength(inputResult, actionModel.text.length)

        return TextTransactionsUtils.updateTransaction(
            paragraph.startOffset,
            inputResult.model,
            deleteLength
        )
    }

    private fun getDeleteLength(inputResult: TextInputResult, textLength: Int): Int {
        return when (val decorator = inputResult.model.piece.decorator) {
            is TextDecoratorModel.TaskDecoratorModel -> {
                if (decorator.checked) {
                    TASK_DECORATOR_COMMON.length - textLength
                } else {
                    TASK_DECORATOR_UNCHECKED_COMMON.length - textLength
                }
            }

            else -> inputResult.model.text.length - (inputResult.model.piece.decorator?.key?.length ?: 0)
        }
    }
}
