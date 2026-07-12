package com.jjrodcast.textkit.editor.core.transactions.text

import com.jjrodcast.textkit.editor.core.models.MultiPieceParagraph
import com.jjrodcast.textkit.editor.core.models.PieceParagraph
import com.jjrodcast.textkit.editor.core.transactions.lists.models.TextEditorListItemTransaction
import com.jjrodcast.textkit.editor.core.transactions.models.TextEditorAction
import com.jjrodcast.textkit.editor.core.transactions.models.TextEditorRange
import com.plangrid.pgfoundation.texteditor.core.validator.TextInputResult

expect object TextDecoratorTransaction {
    /**
     * Gets a list of transactions and the new cursor position when a delete action is performed on a decorator.
     *
     *  @param paragraph The current paragraph on which the deletion is being perfommed.
     *  @param lines a [MultiPieceParagraph] with all the neighbor paragraphs.
     *  @param actionModel A [TextEditorAction.TextRemoved] with the cursor position and the length of the deleted text.
     *
     * @return A Pair with the new cursor position and a list of transactions [TextEditorListItemTransaction].
     */
    internal fun getDeleteTransaction(
        paragraph: PieceParagraph,
        lines: MultiPieceParagraph,
        actionModel: TextEditorAction.TextRemoved
    ): Pair<TextEditorRange, List<TextEditorListItemTransaction>>

    internal fun getUpdateDecoratorTransaction(
        inputResult: TextInputResult,
        paragraph: PieceParagraph,
        actionModel: TextEditorAction.TextAdded
    ): TextEditorListItemTransaction
}
