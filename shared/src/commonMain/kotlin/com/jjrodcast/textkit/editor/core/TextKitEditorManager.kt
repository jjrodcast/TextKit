package com.jjrodcast.textkit.editor.core

import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.core.history.EditorHistoryManager
import com.jjrodcast.textkit.editor.core.history.HistorySnapshot
import com.jjrodcast.textkit.editor.core.export.HtmlSerializer
import com.jjrodcast.textkit.editor.core.models.TextEditorModel
import com.jjrodcast.textkit.editor.core.parser.EmbedTokenType
import com.jjrodcast.textkit.editor.core.parser.Mark
import com.jjrodcast.textkit.editor.core.parser.MentionType
import com.jjrodcast.textkit.editor.core.parser.TEXT_EDITOR_JSON
import com.jjrodcast.textkit.editor.core.parser.TextStyleAttrs
import com.jjrodcast.textkit.editor.core.parser.TextStyleMark
import com.jjrodcast.textkit.editor.core.parser.TokenAttrs
import com.jjrodcast.textkit.editor.core.parser.embedType
import com.jjrodcast.textkit.editor.utils.fastForEach
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

    private val history = EditorHistoryManager()

    fun load(json: String, isViewer: Boolean) {
        transaction.loadWith(json, isViewer)
        // The document was replaced; snapshots taken against the previous one are meaningless.
        history.clear()
    }

    /** Whether there is at least one edit that [undo] can revert. */
    val canUndo: Boolean get() = history.canUndo

    /** Whether there is at least one reverted edit that [redo] can reapply. */
    val canRedo: Boolean get() = history.canRedo

    /**
     * Captures the current document state paired with [selection] as a restore point, **before** a
     * mutation. Pair with [pushHistory] once the mutation is known to have changed the document, so
     * no-op edits do not create empty undo steps. O(1).
     */
    internal fun captureHistoryPoint(selection: TextRange): HistorySnapshot =
        HistorySnapshot(transaction.snapshot(), selection)

    /**
     * Commits a restore point captured with [captureHistoryPoint]. [coalesceKey] merges a run of
     * same-kind edits into one undo step (see [EditorHistoryManager.record]); pass `null` for a
     * discrete step.
     */
    internal fun pushHistory(point: HistorySnapshot, coalesceKey: Any? = null) =
        history.record(point, coalesceKey)

    /** Ends the current coalescing run so the next [pushHistory] starts a fresh undo step. */
    internal fun breakHistoryCoalescing() = history.breakCoalescing()

    /**
     * Reverts the last recorded edit. [currentSelection] is the live caret/selection, stored so
     * [redo] can restore it. Returns the selection to place the caret at, or `null` when there is
     * nothing to undo.
     */
    fun undo(currentSelection: TextRange): TextRange? {
        val restored = history.undo(HistorySnapshot(transaction.snapshot(), currentSelection))
            ?: return null
        transaction.restore(restored.document)
        return restored.selection
    }

    /**
     * Reapplies the last undone edit. [currentSelection] is the live caret/selection, stored so a
     * following [undo] can restore it. Returns the selection to place the caret at, or `null` when
     * there is nothing to redo.
     */
    fun redo(currentSelection: TextRange): TextRange? {
        val restored = history.redo(HistorySnapshot(transaction.snapshot(), currentSelection))
            ?: return null
        transaction.restore(restored.document)
        return restored.selection
    }

    /** Drops all undo/redo history. */
    fun clearHistory() = history.clear()

    val text get() = transaction.text

    fun toJson() = transaction.json

    /**
     * Exports the current document as semantic HTML. Export only — the editor is still loaded from,
     * and persisted as, the JSON produced by [toJson].
     */
    fun toHtml(): String = HtmlSerializer().serialize(transaction.document)

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

    // region Embedded blocks (tables, images, documents, …)

    /**
     * A placeholder currently in the document: its [range], the block [rawJson], its [embedType] and
     * the visible [label] shown in the editor (e.g. "📊 Table").
     */
    data class EmbedInfo(
        val range: TextRange,
        val embedType: String,
        val rawJson: String,
        val label: String,
    )

    private var embedSeq = 0

    /**
     * Inserts an embedded block ([rawJson], e.g. a `table`) at [at] as its **own paragraph**, shown as
     * the one-line placeholder [label]. If [at] falls mid-paragraph the paragraph is split so the embed
     * stays isolated (left text · embed · right text). Returns the placeholder's range.
     */
    fun insertEmbed(embedType: String, rawJson: String, label: String, at: TextRange): TextRange {
        val start = at.min
        if (at.length > 0) transaction.delete(start, at.length)
        val fullText = text
        var pos = start
        // Close the current paragraph on the left when the caret sits mid-line.
        if (pos > 0 && fullText.getOrNull(pos - 1) != '\n') {
            transaction.insert(TextEditorModel.create(text = LINE_BREAK), pos)
            pos += 1
        }
        // Terminate the embed's own paragraph with a line break, unless the right side already starts
        // one (avoids an extra empty line) or we're at the end of the document.
        val rightChar = fullText.getOrNull(start)
        val placeholderText = if (rightChar != null && rightChar != '\n') label + LINE_BREAK else label
        val model = TextEditorModel.create(
            text = placeholderText,
            token = RichToken(
                type = EmbedTokenType,
                attrs = TokenAttrs(id = "embed-${embedSeq++}", label = label),
                payload = rawJson
            )
        )
        transaction.insert(model, pos)
        return TextRange(pos, pos + placeholderText.length)
    }

    /** Replaces the block JSON of the placeholder at [range] (its visible label is kept). */
    fun updateEmbedAt(range: TextRange, rawJson: String): Boolean {
        val safeMin = range.min.coerceIn(0, text.length)
        val safeMax = range.max.coerceIn(safeMin, text.length)
        val current = text.substring(safeMin, safeMax)
        val label = current.removeSuffix(LINE_BREAK)
        val model = TextEditorModel.create(
            text = current,
            token = RichToken(
                type = EmbedTokenType,
                attrs = TokenAttrs(id = "embed-${embedSeq++}", label = label),
                payload = rawJson
            )
        )
        return transaction.update(safeMin, safeMax - safeMin, model)
    }

    /** Removes the placeholder at [range] (and its line break) → the block disappears from the JSON. */
    fun removeEmbedAt(range: TextRange): Boolean {
        if (range.length <= 0) return false
        return transaction.delete(range.min, range.length)
    }

    /** The embed placeholder at document [offset], or null when [offset] is not on one. */
    fun embedAt(offset: Int): EmbedInfo? {
        getParagraphs().fastForEach { paragraph ->
            paragraph.children.fastForEach { child ->
                if (child.isEmbed && offset >= child.start && offset < child.end) {
                    val payload = child.embedPayload ?: return@fastForEach
                    return EmbedInfo(
                        range = TextRange(child.start, child.end),
                        embedType = embedTypeOf(payload),
                        rawJson = payload,
                        label = child.embedLabel ?: child.text.trimEnd('\n')
                    )
                }
            }
        }
        return null
    }

    private fun embedTypeOf(payload: String): String =
        TEXT_EDITOR_JSON.parseToJsonElement(payload).embedType()

    // endregion
}

private const val LINE_BREAK = "\n"
