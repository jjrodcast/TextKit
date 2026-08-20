package com.jjrodcast.textkit.editor.core.transactions.text

import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.core.TextKitEditorManager
import com.jjrodcast.textkit.editor.core.parser.Mark
import com.jjrodcast.textkit.editor.core.transactions.TextEditorTransaction
import com.jjrodcast.textkit.editor.core.transactions.lists.models.TextEditorDecoratorTransactionType
import com.jjrodcast.textkit.editor.core.transactions.lists.models.TextEditorListItemTransaction
import com.jjrodcast.textkit.editor.core.transactions.models.TextEditorAction
import com.jjrodcast.textkit.editor.utils.LINE_BREAK
import com.jjrodcast.textkit.editor.utils.isLineBreak

/**
 * This class manage the list item editting using the keyboard.
 */
object TextTransaction {

    fun onTextUpdated(
        actionModel: TextEditorAction,
        manager: TextKitEditorManager
    ): Pair<Boolean, TextRange> = onTextUpdated(actionModel, manager, quoteAware = true)

    /**
     * [quoteAware] gates the two quote-boundary conventions below. They model a *gesture* — a
     * typed Enter, a typed Backspace — so the multiline paste replay, which re-enters this
     * function segment by segment, dispatches with `false`: a pasted blank line inside a quote
     * must survive as a blank quoted line, not get swallowed as an "exit".
     */
    private fun onTextUpdated(
        actionModel: TextEditorAction,
        manager: TextKitEditorManager,
        quoteAware: Boolean
    ): Pair<Boolean, TextRange> {
        if (quoteAware) {
            exitQuoteOnEmptyLine(actionModel, manager)?.let { return Pair(true, it) }
            unwrapQuoteOnBoundaryDelete(actionModel, manager)?.let { return Pair(true, it) }
        }

        // A single insert that carries line breaks amid other text (a paste) must become one
        // paragraph per break — the per-transaction logic below only splits a *lone* line break, and
        // would otherwise leave the breaks embedded in one text node. Replay it as the discrete
        // inserts the same text produces when typed, each through the normal path.
        when (actionModel) {
            is TextEditorAction.TextAdded ->
                if (actionModel.text.hasInnerLineBreak()) {
                    return insertMultiline(actionModel.offset, actionModel.text, actionModel.marks, manager)
                }

            is TextEditorAction.TextUpdated ->
                if (actionModel.text.hasInnerLineBreak()) {
                    // Apply the replacement's removal first, then insert at wherever the removal left
                    // the caret (it can shift the effective offset, e.g. a partially selected
                    // decorator). The paste inherits the marks at that point, matching how the normal
                    // replace path continues the surrounding formatting.
                    var insertOffset = actionModel.offset
                    if (actionModel.removeLength > 0) {
                        val removal = TextEditorAction.TextRemoved(
                            offset = actionModel.offset,
                            length = actionModel.removeLength,
                            selection = TextRange(actionModel.offset),
                        )
                        insertOffset = onTextUpdated(removal, manager, quoteAware = false).second.end
                    }
                    val marks = manager.getSearchMarkType(TextRange(insertOffset)).marks
                    return insertMultiline(insertOffset, actionModel.text, marks, manager)
                }

            else -> Unit
        }

        val (selection, transactions) = when (actionModel) {
            is TextEditorAction.TextAdded -> {
                val lines = manager.transaction.getLineContentWithNeighborParagraphs(actionModel.offset, actionModel.offset)
                TextInsertedTransaction.addText(lines, actionModel)
            }

            is TextEditorAction.TextRemoved -> {
                val lines =
                    manager.transaction.getLineContentWithNeighborParagraphs(actionModel.offset, actionModel.offset + actionModel.length)
                TextDeletedTransaction.deleteText(lines, actionModel, manager)
            }

            is TextEditorAction.TextUpdated -> {
                val lines =
                    manager.transaction.getLineContentWithNeighborParagraphs(
                        actionModel.offset,
                        actionModel.offset + actionModel.removeLength
                    )
                TextUpdateTransaction.updateText(lines, actionModel, manager)
            }

            else -> Pair(TextRange(0), emptyList())
        }

        manager.transaction.commitChanges(transactions)

        // The caret an action reports must lie inside the post-commit document. Some legacy list
        // paths compute it from pre-commit arithmetic that can go stale when the transactions
        // remove more than assumed (an empty nested item's marker deleted whole rather than
        // demoted): callers that chain actions — a multiline paste inserts segment by segment —
        // would then write past the document end and corrupt the piece table.
        val length = manager.text.length
        val bounded = TextRange(
            selection.start.coerceIn(0, length),
            selection.end.coerceIn(0, length),
        )
        return Pair(true, bounded)
    }

    /**
     * Enter on an EMPTY quoted line exits the quote instead of extending it (#126): the empty
     * paragraph drops its quote attribute and the break is swallowed, the same way Enter on an
     * empty list item removes the marker rather than opening yet another item. Returns the caret
     * (unchanged — the line the user is on just turned plain), or `null` when the convention does
     * not apply and the ordinary insert should run.
     */
    private fun exitQuoteOnEmptyLine(
        actionModel: TextEditorAction,
        manager: TextKitEditorManager
    ): TextRange? {
        if (actionModel !is TextEditorAction.TextAdded || !actionModel.text.isLineBreak()) return null
        val offset = actionModel.offset
        // At the document end there is no paragraph at the offset to be empty AND quoted — the
        // trailing line past the last break has no pieces, and its export is already plain.
        if (offset >= manager.text.length) return null
        if (manager.transaction.quoteDecoratorAt(offset) == null) return null
        // The forward window [offset, offset+1) is the same resolution quoteDecoratorAt used, so
        // this is the paragraph whose quote was found — empty means it is just its own terminator.
        val line = manager.transaction.getLineContent(offset, offset + 1)
            .paragraphsInSelectedRange.firstOrNull() ?: return null
        if (!line.text.isLineBreak()) return null
        manager.transaction.updateBlockquote(TextRange(offset, offset + 1), quoted = false)
        return TextRange(offset)
    }

    /**
     * Backspace at the start of a quote unwraps its first line instead of merging (#126): deleting
     * the lone break between an unquoted line and a quoted one would pull the previous line INTO
     * the quote. The quote's first paragraph steps out and the break survives; the next backspace
     * then merges two plain lines the ordinary way. (A forward Delete at the end of the previous
     * line produces the same action and gets the same treatment — the two are indistinguishable
     * here, and unwrap-first is the safer of the two merges.) Returns the caret at the now-plain
     * line's start, or `null` when the ordinary delete should run.
     */
    private fun unwrapQuoteOnBoundaryDelete(
        actionModel: TextEditorAction,
        manager: TextKitEditorManager
    ): TextRange? {
        if (actionModel !is TextEditorAction.TextRemoved || actionModel.length != 1) return null
        val offset = actionModel.offset
        if (manager.text.getOrNull(offset)?.isLineBreak() != true) return null
        if (offset + 1 >= manager.text.length) return null
        val transaction = manager.transaction
        // Only at the quote's first line: a break already inside the quote merges normally.
        if (transaction.quoteDecoratorAt(offset) != null) return null
        if (transaction.quoteDecoratorAt(offset + 1) == null) return null
        transaction.updateBlockquote(TextRange(offset + 1, offset + 2), quoted = false)
        return TextRange(offset + 1)
    }

    /**
     * Replays [text] as the discrete inserts it would produce when typed: each run of ordinary text
     * and each line break is inserted on its own, in order, through [onTextUpdated] — so every break
     * splits a paragraph the way a lone typed break already does. The offset advances by each
     * segment's length; the caret ends where the final segment lands.
     */
    private fun insertMultiline(
        startOffset: Int,
        text: String,
        marks: Set<Mark>,
        manager: TextKitEditorManager
    ): Pair<Boolean, TextRange> {
        var offset = startOffset
        var changed = false
        var selection = TextRange(startOffset)
        segmentsOf(text).forEach { segment ->
            val action = TextEditorAction.TextAdded(
                text = segment,
                marks = marks,
                offset = offset,
                selection = TextRange(offset + segment.length),
            )
            val (applied, resultSelection) = onTextUpdated(action, manager, quoteAware = false)
            changed = changed || applied
            selection = resultSelection
            // Advance to where the engine actually left the caret rather than assuming
            // `offset + length`: a break split or a decorator conversion can move it.
            offset = resultSelection.end
        }
        return Pair(changed, selection)
    }

    /** Splits [text] into alternating ordinary-text runs and lone line breaks, in order. */
    private fun segmentsOf(text: String): List<String> = buildList {
        val run = StringBuilder()
        text.forEach { char ->
            if (char.isLineBreak()) {
                if (run.isNotEmpty()) {
                    add(run.toString())
                    run.clear()
                }
                add(LINE_BREAK.toString())
            } else {
                run.append(char)
            }
        }
        if (run.isNotEmpty()) add(run.toString())
    }

    /** True when [this] carries a line break alongside other content — i.e. not a lone break. */
    private fun String.hasInnerLineBreak(): Boolean = contains(LINE_BREAK) && !isLineBreak()

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
