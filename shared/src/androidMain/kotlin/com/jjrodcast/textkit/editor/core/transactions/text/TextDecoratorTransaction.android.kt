package com.jjrodcast.textkit.editor.core.transactions.text

import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.core.models.MultiPieceParagraph
import com.jjrodcast.textkit.editor.core.models.PieceParagraph
import com.jjrodcast.textkit.editor.core.models.TextEditorModel
import com.jjrodcast.textkit.editor.core.piecetable.models.TextDecoratorModel
import com.jjrodcast.textkit.editor.core.transactions.lists.models.TextEditorListItemTransaction
import com.jjrodcast.textkit.editor.core.transactions.models.TextEditorAction
import com.plangrid.pgfoundation.texteditor.core.validator.ListItemValidator
import com.plangrid.pgfoundation.texteditor.core.validator.TextInputResult

actual object TextDecoratorTransaction {
    internal actual fun getDeleteTransaction(
        paragraph: PieceParagraph,
        lines: MultiPieceParagraph,
        actionModel: TextEditorAction.TextRemoved
    ): Pair<TextRange, List<TextEditorListItemTransaction>> {
        return when (paragraph.startPiece.decorator) {
            is TextDecoratorModel.TaskDecoratorModel -> deleteTaskDecorator(paragraph, actionModel)
            else -> TextTransactionsUtils.getCommonDeleteDecoratorTransactions(paragraph, lines)
        }
    }

    internal actual fun getUpdateDecoratorTransaction(
        inputResult: TextInputResult,
        paragraph: PieceParagraph,
        actionModel: TextEditorAction.TextAdded
    ): TextEditorListItemTransaction {
        val deleteLength = inputResult.model.text.length - (inputResult.model.piece.decorator?.key?.length ?: 0)

        return TextTransactionsUtils.updateTransaction(
            paragraph.startOffset,
            inputResult.model,
            deleteLength
        )
    }

    /**
     * This function deletes a decorator.
     *
     * When we delete text within a decorator we need to validate if the remaining text matches a type of decorator.
     * If it does, we replace it with the new decorator, otherwise we simply delete the decorator and insert the text from the previous decorator as text.
     *
     * @return A Pair with the new cursor position and a list of transactions [TextEditorListItemTransaction].
     */
    private fun deleteTaskDecorator(
        paragraph: PieceParagraph,
        actionModel: TextEditorAction.TextRemoved
    ): Pair<TextRange, List<TextEditorListItemTransaction>> {
        val decoratorOffset = paragraph.startOffset
        val deleteOffset = actionModel.offset - decoratorOffset
        val leftText = paragraph.startText.substring(0, deleteOffset)
        val rigthText = paragraph.startText.substring(deleteOffset + actionModel.length, paragraph.startText.length)
        val newText = leftText + rigthText

        val decoratorInput = ListItemValidator.validateInput(newText)

        return if (decoratorInput != null) {
            val transaction = TextTransactionsUtils.updateTransaction(
                decoratorOffset,
                decoratorInput.model,
                paragraph.startPiece.length
            )

            Pair(TextRange(actionModel.offset), listOf(transaction))
        } else {
            val model = TextEditorModel.create(text = newText)
            val transaction = TextTransactionsUtils.updateTransaction(
                decoratorOffset,
                model,
                paragraph.startPiece.length
            )
            Pair(TextRange(actionModel.offset), listOf(transaction))
        }
    }
}