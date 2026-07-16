package com.jjrodcast.textkit.editor.core

import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.core.models.TextEditorModel
import com.jjrodcast.textkit.editor.core.parser.Mark
import com.jjrodcast.textkit.editor.core.parser.MentionType
import com.jjrodcast.textkit.editor.core.parser.TextStyleAttrs
import com.jjrodcast.textkit.editor.core.parser.TextStyleMark
import com.jjrodcast.textkit.editor.core.parser.TokenAttrs
import com.jjrodcast.textkit.editor.core.piecetable.models.RichToken
import com.jjrodcast.textkit.editor.core.transactions.TextEditorTransaction
import com.jjrodcast.textkit.editor.core.transactions.models.TextEditorSelectedMark
import com.jjrodcast.textkit.editor.core.transactions.models.TextEditorTransactionType
import com.jjrodcast.textkit.editor.models.MarkSearchType
import com.jjrodcast.textkit.editor.models.TextKitConfiguration
import com.jjrodcast.textkit.editor.models.createTextKitConfiguration
import com.jjrodcast.textkit.editor.utils.toHex

class TextKitEditorManager(val configuration: TextKitConfiguration = createTextKitConfiguration()) {

    internal val transaction by lazy { TextEditorTransaction(configuration) }

    fun load(json: String, isViewer: Boolean) {
        transaction.loadWith(json, isViewer)
    }

    val text get() = transaction.text

    fun toJson() = transaction.json

    val isViewer get() = transaction.isViewer

    fun getLink(start: Int, end: Int) = transaction.getLink(start, end, configuration)

    /**
     * Single entry point for every document format change: marks, list items, links and colors.
     *
     * - **Marks / list items:** pass the previous and current [TextEditorSelectedMark] and leave
     *   [transactionType] as [TextEditorTransactionType.Format].
     * - **Links:** put the [com.jjrodcast.textkit.editor.core.parser.LinkMark] in the current
     *   selection's marks and pass [TextEditorTransactionType.Link].
     * - **Colors:** pass [TextEditorTransactionType.Color]; [prevSelectedMark] / [currSelectedMark]
     *   are ignored and the color marks are resolved from the current selection by [updateColor].
     *
     * @return whether the edit was applied, plus the resulting range.
     */
    fun updateDocument(
        selection: TextRange,
        prevSelectedMark: TextEditorSelectedMark,
        currSelectedMark: TextEditorSelectedMark,
        transactionType: TextEditorTransactionType = TextEditorTransactionType.Format
    ): Pair<Boolean, TextRange> = when (transactionType) {
        is TextEditorTransactionType.Color -> updateColor(selection, transactionType.color)
        else -> transaction.updateDocument(
            prevMarks = prevSelectedMark.marks,
            currMarks = currSelectedMark.marks,
            prevListItem = prevSelectedMark.listItemSelected,
            currListItem = currSelectedMark.listItemSelected,
            range = selection,
            transactionType = transactionType
        )
    }

    private fun updateColor(
        selection: TextRange,
        color: String?
    ): Pair<Boolean, TextRange> {
        val prevFormatMarks =
            transaction.getMarksWithType(selection.start, selection.end, configuration)

        // Keep the current font size and only change the color. Removing the color (color == null)
        // falls back to the configured default color instead of dropping the text-style mark.
        val (prevMarks, prevTextStyle) = prevFormatMarks.marks.partition { it !is TextStyleMark }
        val prevTextStyleMark = prevTextStyle.firstOrNull() as? TextStyleMark
        val fontSize = prevTextStyleMark?.attrs?.fontSize ?: configuration.fontSize
        val resolvedColor = color ?: configuration.textColor.toHex()
        val formatMarks = prevMarks
            .plus(TextStyleMark(TextStyleAttrs(color = resolvedColor, fontSize = fontSize)))
            .toSet()

        return transaction.updateDocument(
            prevMarks = prevFormatMarks.marks,
            currMarks = formatMarks,
            prevListItem = prevFormatMarks.listItem,
            currListItem = prevFormatMarks.listItem,
            range = selection,
            transactionType = TextEditorTransactionType.Format
        )
    }

    fun getSearchMarkType(selection: TextRange): MarkSearchType {
        return transaction.getMarksWithType(selection.min, selection.max, configuration)
    }

    fun checkDecorator(start: Int, end: Int) = transaction.containsDecorator(start, end)

    fun getParagraphs() = transaction.getParagraphs()

    fun onDecoratorChange(offset: Int) = transaction.onDecoratorChange(offset)

    /**
     * Inserts a trigger token, replacing [replaceRange] (typically the `<char>query` text the user
     * was typing).
     *
     * - When [nodeType] is non-null (an atomic token like `"mention"`/`"hashtag"`) the inserted
     *   piece's visible text is `<triggerKey><label>` and it carries the token's type/id/label so it
     *   serializes back to that node.
     * - When [nodeType] is null (an ephemeral command like `/`) the [label] is inserted as plain text
     *   and nothing is persisted as a token.
     *
     * @return the collapsed [TextRange] where the caret should land (right after the inserted text).
     */
    fun insertToken(
        nodeType: String?,
        id: String,
        label: String,
        replaceRange: TextRange,
        marks: Set<Mark> = emptySet()
    ): TextRange {
        val text = if (nodeType != null) {
            val triggerChar = configuration.triggerForType(nodeType)?.triggerKey
                ?: MentionType.DEFAULT_MENTION_CHAR
            triggerChar + label
        } else {
            // Ephemeral command: no trigger char, no persisted token — just the plain-text result.
            label
        }
        val model = TextEditorModel.create(
            text = text,
            marks = marks,
            token = nodeType?.let { RichToken(type = it, attrs = TokenAttrs(id = id, label = label)) }
        )
        val start = replaceRange.min
        val length = replaceRange.length
        if (length > 0) transaction.update(start, length, model) else transaction.insert(model, start)
        return TextRange(start + text.length)
    }

    /**
     * Inserts an atomic mention. Thin wrapper over [insertToken] with the `mention` node type, kept
     * for a convenient, type-specific API.
     *
     * @return the collapsed [TextRange] where the caret should land (right after the mention).
     */
    fun insertMention(
        id: String,
        label: String,
        replaceRange: TextRange,
        marks: Set<Mark> = emptySet()
    ): TextRange = insertToken(MentionType.Mention, id, label, replaceRange, marks)

    /**
     * Deletes the characters in [range]. Used to remove an atomic mention as a whole. Returns the
     * collapsed [TextRange] where the caret should land (the start of the deleted range).
     */
    fun deleteRange(range: TextRange): TextRange {
        if (range.length > 0) transaction.delete(range.min, range.length)
        return TextRange(range.min)
    }
}
