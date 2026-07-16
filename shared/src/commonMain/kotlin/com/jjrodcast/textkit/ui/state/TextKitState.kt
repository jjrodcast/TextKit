package com.jjrodcast.textkit.ui.state

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.jjrodcast.textkit.editor.components.TextEditorDecoratorItem
import com.jjrodcast.textkit.editor.components.TextEditorListItem
import com.jjrodcast.textkit.editor.core.TextKitEditorManager
import com.jjrodcast.textkit.editor.core.history.EditKind
import com.jjrodcast.textkit.editor.core.parser.BoldMark
import com.jjrodcast.textkit.editor.core.parser.HeadingLevels
import com.jjrodcast.textkit.editor.core.parser.HeadingLevelsValues
import com.jjrodcast.textkit.editor.core.parser.HighlightMark
import com.jjrodcast.textkit.editor.core.parser.ItalicMark
import com.jjrodcast.textkit.editor.core.parser.LinkAttrs
import com.jjrodcast.textkit.editor.core.parser.LinkMark
import com.jjrodcast.textkit.editor.core.parser.Mark
import com.jjrodcast.textkit.editor.core.parser.StrikeMark
import com.jjrodcast.textkit.editor.core.parser.TextStyleAttrs
import com.jjrodcast.textkit.editor.core.parser.TextStyleMark
import com.jjrodcast.textkit.editor.core.parser.UnderlineMark
import com.jjrodcast.textkit.editor.core.piecetable.models.TextDecoratorModel
import com.jjrodcast.textkit.editor.core.piecetable.models.TextDecoratorModel.Companion.createDecoratorString
import com.jjrodcast.textkit.editor.core.transactions.models.TextEditorAction
import com.jjrodcast.textkit.editor.core.transactions.models.TextEditorSelectedMark
import com.jjrodcast.textkit.editor.core.transactions.models.TextEditorTransactionType
import com.jjrodcast.textkit.editor.core.transactions.text.TextTransaction
import com.jjrodcast.textkit.editor.models.MarkSearchType
import com.jjrodcast.textkit.editor.models.TextKitConfiguration
import com.jjrodcast.textkit.editor.models.TextKitTrigger
import com.jjrodcast.textkit.editor.models.createTextKitConfiguration
import com.jjrodcast.textkit.ui.model.TextKitCommand
import com.jjrodcast.textkit.ui.model.TextKitLinkInfo
import com.jjrodcast.textkit.ui.utils.createStyle
import com.jjrodcast.textkit.ui.utils.restore
import com.jjrodcast.textkit.ui.utils.save

/**
 * A state object that can be used to remember the state of a RichText component across recomposition.
 *
 * @param json The JSON string representing the initial state of the RichText component.
 * @param isViewer Whether the component is in read-only viewer mode.
 * @param configuration Configuration object that holds the colors and sizes for UI components.
 */
@Composable
fun rememberTextKitState(
    json: String = "{}",
    isViewer: Boolean = false,
    configuration: TextKitConfiguration = remember { createTextKitConfiguration() }
): TextKitState {
    val state = rememberSaveable(saver = TextKitState.saver(configuration)) {
        TextKitState(json, isViewer, configuration)
            .apply { setup() }
    }
    return state
}

/**
 * TextKitState is a state object that can be used to remember the state of a RichText component storing the initial JSON
 * and the configuration [TextKitConfiguration].
 *
 * @param json The JSON string representing the initial state of the RichText component.
 * @param isViewer Whether the component is in read-only viewer mode.
 * @param configuration The object that manages the configuration of colors and sizes. See [TextKitConfiguration].
 */
@Stable
class TextKitState(
    private val json: String,
    private val isViewer: Boolean,
    private val configuration: TextKitConfiguration
) {

    internal var onUrlClicked: ((url: String, text: String, range: TextRange) -> Unit)? = null

    private val manager by lazy { TextKitEditorManager(configuration) }

    internal var visualTransformation by mutableStateOf(VisualTransformation.None)

    var textFieldValue by mutableStateOf(TextFieldValue())
        private set

    internal var selection by mutableStateOf(TextRange.Zero)
        private set

    /** Whether an [undo] is available. Observable so the formatting bar's undo button can enable/disable. */
    var canUndo by mutableStateOf(false)
        private set

    /** Whether a [redo] is available. Observable so the formatting bar's redo button can enable/disable. */
    var canRedo by mutableStateOf(false)
        private set

    /**
     * Re-entrancy guard for [recordBefore]. A compound action (e.g. a link text+URL edit that swaps
     * text and then applies a link) calls other recording methods internally; only the outermost call
     * should capture a restore point, so the whole action is a single undo step.
     */
    private var historyDepth = 0

    /**
     * Marks active at the collapsed caret, captured when the selection moves. Text typed next
     * inherits these ("stored marks"), so formatting continues across the caret without a selection.
     */
    var lastMarks by mutableStateOf<Set<Mark>>(emptySet())
        private set

    /**
     * List-item type active at the collapsed caret, captured alongside [lastMarks] when the
     * selection moves. Lets the formatting bar reflect whether the caret sits inside a numbered,
     * bulleted, or task list (or none) without re-querying the document.
     */
    var lastListItem by mutableStateOf<TextEditorDecoratorItem>(TextEditorListItem.None)
        private set

    internal var textLayoutResult: TextLayoutResult? by mutableStateOf(null)
        private set

    /**
     * Link currently under the caret / selection, or null. Observe this to show a link popup; it
     * updates as the selection moves and is cleared by [dismissLinkPopup] or when leaving the link.
     */
    var activeLink by mutableStateOf<TextKitLinkInfo?>(null)
        private set

    /** Owns all trigger-token behavior (`@`, `#`, `/`, …): detection, query, atomicity, insertion. */
    private val tokenState by lazy { TextKitTokenState(manager, configuration) }

    /**
     * The text typed after the active trigger char (e.g. after `@`/`#`/`/`), or null when no picker
     * is active. Observe this together with [activeTrigger] to show a popup and filter candidates; it
     * updates on every keystroke and is cleared once committed ([selectToken]), dismissed
     * ([dismissToken]) or invalidated (caret leaves the query, whitespace typed, …).
     */
    val tokenQuery: String? get() = tokenState.query

    /** The trigger currently being composed, or null. Drives which candidate set the popup shows. */
    val activeTrigger: TextKitTrigger? get() = tokenState.activeTrigger

    val composition get() = textFieldValue.composition

    private var prevTextFieldValue = textFieldValue.copy(text = manager.text)

    /**
     * Range of the link currently under a collapsed caret, painted as a background highlight
     * instead of a real text selection so it reads like a mobile highlight (no selection handles).
     * Null when the caret is not inside a link.
     */
    private var linkSelectionRange by mutableStateOf<TextRange?>(null)

    /**
     * Range the link popup is currently anchored to, or null when no popup is showing. While it is
     * set, a selection change that stays within this range keeps the popup open — this is what lets
     * the popup survive the editor collapsing / re-reporting its selection when focus moves to the
     * popup's own text fields. Moving the caret off the range dismisses it.
     */
    private var pinnedLinkRange: TextRange? = null

    /**
     * Last non-collapsed selection seen. When a formatting action runs while the live [selection]
     * has collapsed onto (or within) this range — which happens when the editor hands focus to the
     * formatting bar and re-reports a collapsed caret — the action targets this range instead of
     * being treated as "no selection". Reset on text edits so it never points at a stale range.
     */
    private var lastRangeSelection: TextRange = TextRange.Zero

    private var annotatedString by mutableStateOf(AnnotatedString(text = ""))

    private val viewerAnnotatedStringState = derivedStateOf {
        annotatedString // establishes snapshot state dependency
        createViewerAnnotatedString()
    }

    val annotatedStringForViewer get() = viewerAnnotatedStringState.value

    private val isTyping get() = prevTextFieldValue.selection.collapsed && textFieldValue.selection.collapsed

    private val isUsingClipboard get() = prevTextFieldValue.selection.collapsed && !textFieldValue.selection.collapsed

    internal val paragraphs get() = manager.getParagraphs()

    /**
     * Whether the character under [position] (in the text field's local coordinates) sits on a
     * link. Used by the editor to swap the pointer to a hand cursor while hovering a link, since
     * the editable field renders links as plain spans rather than clickable [LinkAnnotation]s.
     * Returns false until the first [onTextLayout] has run.
     */
    internal fun isLinkAtPosition(position: Offset): Boolean {
        val layout = textLayoutResult ?: return false

        val line = layout.getLineForVerticalPosition(position.y)
        val outsideText = position.y > layout.getLineBottom(layout.lineCount - 1) ||
                position.x < layout.getLineLeft(line) ||
                position.x > layout.getLineRight(line)
        if (outsideText) return false

        val offset = layout.getOffsetForPosition(position).coerceIn(0, textFieldValue.text.length)
        val (href, _) = manager.getLink(offset, offset)
        return href != null
    }

    internal fun setup() {
        manager.load(json, isViewer)
        val text = manager.text
        textFieldValue = textFieldValue.copy(text = text, selection = TextRange.Zero)
        updateAnnotatedString(textFieldValue.selection)
        syncHistoryAvailability()
    }

    /**
     * Reverts the last edit (typing, deletion, formatting, list toggle, token insertion, …) and
     * restores the caret to where it was before that edit. No-op — returns false — when there is
     * nothing to undo. Wire it to a toolbar button ([canUndo]) or a keyboard shortcut (Ctrl/Cmd+Z).
     */
    fun undo(): Boolean {
        val restored = manager.undo(selection) ?: return false
        applyRestoredHistory(restored)
        return true
    }

    /**
     * Reapplies the last undone edit and restores its caret. No-op — returns false — when there is
     * nothing to redo. Wire it to a toolbar button ([canRedo]) or a keyboard shortcut
     * (Ctrl/Cmd+Shift+Z, or Ctrl+Y).
     */
    fun redo(): Boolean {
        val restored = manager.redo(selection) ?: return false
        applyRestoredHistory(restored)
        return true
    }

    /** Re-renders the field after an undo/redo restore and re-syncs the caret-dependent UI state. */
    private fun applyRestoredHistory(restoredSelection: TextRange) {
        // A restore can shrink or grow the document; any query/token popup that was open no longer
        // matches the restored text, and a remembered range is stale.
        tokenState.dismiss()
        lastRangeSelection = TextRange.Zero
        selection = restoredSelection
        updateAnnotatedString(restoredSelection)
        readSelectionContext()
        syncHistoryAvailability()
    }

    private fun syncHistoryAvailability() {
        canUndo = manager.canUndo
        canRedo = manager.canRedo
    }

    /**
     * Runs a document-mutating [block] as one undo step. Captures a restore point (the pre-edit
     * state + current [selection]) and, if [block] reports a change, commits it with [coalesceKey]
     * (see [EditorHistoryManager][com.jjrodcast.textkit.editor.core.history.EditorHistoryManager]);
     * [breakAfter] then ends the coalescing run so the next edit starts fresh.
     *
     * Nested calls (a compound action calling other recording methods) reuse the outermost restore
     * point via [historyDepth], so the whole action is a single undo step.
     */
    private inline fun recordBefore(
        coalesceKey: Any? = null,
        breakAfter: Boolean = true,
        block: () -> Boolean
    ): Boolean {
        val point = if (historyDepth == 0) manager.captureHistoryPoint(selection) else null
        historyDepth++
        val changed = try {
            block()
        } finally {
            historyDepth--
        }
        if (changed && point != null) {
            manager.pushHistory(point, coalesceKey)
            if (breakAfter) manager.breakHistoryCoalescing()
            syncHistoryAvailability()
        }
        return changed
    }

    /**
     * Serializes the current document to the ProseMirror-style JSON string (the same format
     * [rememberTextKitState] accepts as `json`). Use it to persist the editor's contents.
     */
    fun toJson() = manager.toJson()

    /**
     * Receives the editor's latest [TextLayoutResult]; wire it to the text field's `onTextLayout`.
     * It backs coordinate-based lookups such as link hit-testing and [linkBoundingBox], so those
     * return nothing until the first layout pass has run.
     */
    fun onTextLayout(textLayoutResult: TextLayoutResult) {
        this.textLayoutResult = textLayoutResult
    }

    /**
     * Toggles **bold** on the current selection, matching [selected] (true = on, false = off). With
     * a collapsed caret it stores the change in [lastMarks] so the next typed text inherits it.
     * Returns whether the state changed.
     */
    fun applyBold(selected: Boolean): Boolean = applyMark(BoldMark(), selected)

    /**
     * Toggles *italic* on the current selection, matching [selected] (true = on, false = off). With
     * a collapsed caret it stores the change in [lastMarks] so the next typed text inherits it.
     * Returns whether the state changed.
     */
    fun applyItalic(selected: Boolean): Boolean = applyMark(ItalicMark(), selected)

    /**
     * Toggles the highlight mark on the current selection, matching [selected] (true = on, false =
     * off). With a collapsed caret it stores the change in [lastMarks] so the next typed text
     * inherits it. Returns whether the state changed.
     */
    fun applyHighlight(selected: Boolean): Boolean = applyMark(HighlightMark(), selected)

    /**
     * Toggles underline on the current selection, matching [selected] (true = on, false = off).
     * With a collapsed caret it stores the change in [lastMarks] so the next typed text inherits it.
     * Returns whether the state changed.
     */
    fun applyUnderline(selected: Boolean): Boolean = applyMark(UnderlineMark(), selected)

    /**
     * Toggles strike-through on the current selection, matching [selected] (true = on, false = off).
     * With a collapsed caret it stores the change in [lastMarks] so the next typed text inherits it.
     * Returns whether the state changed.
     */
    fun applyStrikeThrough(selected: Boolean): Boolean = applyMark(StrikeMark(), selected)

    /**
     * Applies a text-style mark — [fontSize] (in the document's font-size units) and a [color] hex
     * string such as `#FF0000`, or null to leave the color unset — over the current selection,
     * replacing any existing text style there. With a collapsed caret it is stored for the next
     * typed text. Returns whether the state changed.
     */
    fun applyTextStyle(fontSize: Int, color: String?): Boolean =
        applyMark(
            TextStyleMark(TextStyleAttrs(fontSize = fontSize, color = color)),
            selected = true
        )

    /**
     * Applies a heading of [level] (1..6) by setting the matching heading font size (see
     * [HeadingLevelsValues]); any other [level] resets to the configured body [TextKitConfiguration.fontSize].
     * Over a selection it restyles that text; with a collapsed caret it stores the style so the next
     * typed characters inherit it (matching how the other mark toggles behave). Returns whether the
     * document changed.
     */
    fun applyHeading(level: Int): Boolean {
        val fontSize = when (level) {
            HeadingLevels.H1 -> HeadingLevelsValues.H1
            HeadingLevels.H2 -> HeadingLevelsValues.H2
            HeadingLevels.H3 -> HeadingLevelsValues.H3
            HeadingLevels.H4 -> HeadingLevelsValues.H4
            HeadingLevels.H5 -> HeadingLevelsValues.H5
            HeadingLevels.H6 -> HeadingLevelsValues.H6
            else -> configuration.fontSize
        }
        return applyTextStyle(fontSize = fontSize, color = null)
    }

    /**
     * Toggles an ordered (numbered) list over the paragraph(s) the current selection touches,
     * matching [selected] (true = convert to a numbered list, false = back to a plain paragraph).
     * If those paragraphs are already another list kind it switches them in place. Works with a
     * collapsed caret (acts on the caret's own paragraph). Returns whether the document changed.
     */
    fun toggleOrderedList(selected: Boolean): Boolean =
        updateListItem(if (selected) TextEditorListItem.NumberedList else TextEditorListItem.None)

    /**
     * Toggles an unordered (bulleted) list over the paragraph(s) the current selection touches,
     * matching [selected] (true = convert to a bulleted list, false = back to a plain paragraph).
     * If those paragraphs are already another list kind it switches them in place. Works with a
     * collapsed caret (acts on the caret's own paragraph). Returns whether the document changed.
     */
    fun toggleUnorderedList(selected: Boolean): Boolean =
        updateListItem(if (selected) TextEditorListItem.BulletedList else TextEditorListItem.None)

    /**
     * Converts the paragraph(s) in the current [selection] from the caret's current list kind
     * ([lastListItem]) to [target] ([TextEditorListItem.None] removes the list). Routed as a
     * list-item change (not a mark change), so it applies to whole paragraphs even with a collapsed
     * caret. No-op — returns false — when the target already matches the current kind.
     */
    private fun updateListItem(target: TextEditorListItem): Boolean {
        if (lastListItem == target) return false
        return updateDocument(
            selection,
            TextEditorSelectedMark(marks = lastMarks, listItemSelectedValue = lastListItem),
            TextEditorSelectedMark(marks = lastMarks, listItemSelectedValue = target),
            transactionType = TextEditorTransactionType.Format
        )
    }

    /**
     * Toggles a single [mark] on/off while keeping the other marks active at the caret
     * ([lastMarks]) intact, then applies the resulting full set.
     *
     * Passing only the toggled mark used to reset the whole set — e.g. turning italic off while
     * bold was active cleared bold too — because [TextEditorMarkProcessor] diffs the previous set
     * against the current one by size to decide add vs remove. It needs the complete post-toggle
     * set, not just the button that changed, so we rebuild it here (matching marks by [Mark.type]
     * so attribute-carrying marks like text style/link replace rather than duplicate).
     */
    private fun applyMark(mark: Mark, selected: Boolean): Boolean {
        val withoutType = lastMarks.filterNotTo(mutableSetOf()) { it.type == mark.type }
        val marks = if (selected) withoutType + mark else withoutType
        return applyMarks(marks)
    }

    private fun applyMarks(marks: Set<Mark>): Boolean {
        val target = markTarget()
        // With no usable selection there is no text to reformat; store the marks so the next typed
        // characters inherit them (and the formatting bar reflects the toggle).
        if (target.collapsed) {
            lastMarks = marks
            return true
        }
        return updateDocument(
            target,
            TextEditorSelectedMark(
                marks = lastMarks, listItemSelectedValue = lastListItem
            ),
            currentMarks = TextEditorSelectedMark(
                marks = marks, listItemSelectedValue = lastListItem
            ),
            transactionType = TextEditorTransactionType.Format
        )
    }

    /**
     * The range a formatting action should target. Normally the live [selection]; but when that has
     * collapsed onto (or within) [lastRangeSelection] — the editor re-reports a collapsed caret when
     * it hands focus to the formatting bar — we format that remembered range instead, so a toolbar
     * click still styles the text the user had selected. A caret that lands off the remembered range
     * (a deliberate move) is treated as a genuine collapsed selection.
     */
    private fun markTarget(): TextRange {
        if (!selection.collapsed) return selection
        val pending = lastRangeSelection
        return if (!pending.collapsed && selection.min in pending.min..pending.max) pending
        else selection
    }

    private fun updateDocument(
        selection: TextRange,
        previousMarks: TextEditorSelectedMark,
        currentMarks: TextEditorSelectedMark,
        transactionType: TextEditorTransactionType
    ): Boolean = recordBefore(coalesceKey = null) {
        val (updated, resultSelection) = manager.updateDocument(
            selection,
            previousMarks,
            currentMarks,
            transactionType
        )

        if (updated) {
            this.selection = resultSelection
            updateAnnotatedString(resultSelection)
            // Refresh the caret context so formatting-bar toggles reflect the change just applied.
            readSelectionContext()
        }
        updated
    }

    /**
     * Adds or updates a link with [url] over [range] (e.g. the range from [activeLink]). Passing an
     * empty [url] removes the link, keeping any other marks (bold, italic, …) on that range.
     * Returns whether the document changed. On success the caret is collapsed at the end of [range]
     * and the popup is closed (see [finishLinkEditAt]), so no selection or handles linger.
     *
     * `prev` and `curr` both carry a single [LinkMark] on purpose: the mark processor only takes
     * its "replace/remove link" branch when the two sets have the same size. With an empty `prev`
     * it would instead take the "add" branch, which re-adds the existing link and never removes it.
     */
    fun updateLink(url: String, range: TextRange): Boolean {
        val updated = updateDocument(
            range,
            TextEditorSelectedMark(marks = setOf(LinkMark(LinkAttrs("")))),
            TextEditorSelectedMark(marks = setOf(LinkMark(LinkAttrs(url)))),
            TextEditorTransactionType.Link(url),
        )
        // Commit leaves a plain caret at the end of the affected text — no lingering selection or
        // its handles — and closes the popup now that the edit is done.
        if (updated) finishLinkEditAt(range.max)
        return updated
    }

    /** Removes the link over [range] (e.g. the range from [activeLink]). */
    fun removeLink(range: TextRange): Boolean = updateLink(url = "", range = range)

    /**
     * Sets [url] on [range], replacing the range's visible text with [newText] first when it
     * changed, and collapses the caret at the end of the resulting text. Meant for a link popup
     * where both the text and the URL are editable — including adding a link to a fresh selection
     * (leave [newText] as the selected text, and it only attaches the URL).
     *
     * When [newText] equals the current text over [range], the swap is skipped and only [updateLink]
     * runs, so per-character marks in the range survive (a full text replace would flatten them to
     * the marks at the range start). When it differs, the text is swapped — inheriting the range
     * start's marks — and then the link is (re)applied over the new span. Passing an empty [newText]
     * is a no-op (nothing to show); use [removeLink] to drop the link instead.
     */
    fun updateLinkText(newText: String, url: String, range: TextRange): Boolean {
        if (newText.isEmpty()) return false
        // Text swap + link apply is one logical action; wrap so it is a single undo step (the nested
        // updateLink reuses this restore point).
        return recordBefore { updateLinkTextInternal(newText, url, range) }
    }

    private fun updateLinkTextInternal(newText: String, url: String, range: TextRange): Boolean {
        val min = range.min.coerceIn(0, textFieldValue.text.length)
        val max = range.max.coerceIn(min, textFieldValue.text.length)
        val currentText = textFieldValue.text.substring(min, max)

        val end = if (newText == currentText) {
            // Text unchanged (e.g. just attaching a URL to a selection): don't rewrite the span, so
            // any per-character formatting inside it is preserved.
            max
        } else {
            val action = TextEditorAction.TextUpdated(
                removeLength = range.length,
                text = newText,
                offset = range.min,
                selection = TextRange(range.min + newText.length),
            )
            val (updated, _) = TextTransaction.onTextUpdated(action, manager)
            if (!updated) return false
            val newEnd = range.min + newText.length
            // Refresh the rendered text after the swap so the field reflects it even if the link
            // re-apply below is a no-op (unchanged URL).
            selection = TextRange(newEnd)
            updateAnnotatedString(TextRange(newEnd))
            newEnd
        }

        // (Re)apply the link over the resulting span. updateLink finalizes with a collapsed caret at
        // the end of the span and closes the popup, so the edit ends without a lingering selection.
        return updateLink(url = url, range = TextRange(range.min, end))
    }

    /**
     * Finalizes a link add / update / remove: clears the popup and the link's background highlight
     * and leaves a plain collapsed caret at [offset] (the end of the affected text). This is what
     * keeps a committed edit from leaving a full-range selection (and its drag handles) behind.
     */
    private fun finishLinkEditAt(offset: Int) {
        val caret = offset.coerceIn(0, textFieldValue.text.length)
        linkSelectionRange = null
        activeLink = null
        pinnedLinkRange = null
        selection = TextRange(caret)
        updateAnnotatedString(TextRange(caret))
        // Sync the formatting-bar context to the new caret WITHOUT going through
        // readSelectionContext, which would re-open the popup and re-paint the link highlight.
        val searchType = getSelectedMarksWithType()
        lastMarks = searchType.marks
        lastListItem = searchType.listItem
    }

    /**
     * Opens the link popup so a URL can be attached to text — the action behind a formatting bar's
     * link button. Uses the current selection when there is one; with a collapsed caret it falls
     * back to the word under the caret, so it also works when the user just clicks inside a word
     * (or focus collapsed the selection). No-op when there is nothing to link (e.g. the caret sits
     * on whitespace). If the target already carries a link its URL is pre-filled, so the popup edits
     * the existing link instead of stacking a new one.
     *
     * Wire this to `onLinkClick`; confirming the popup routes back through [updateLinkText] (see the
     * `onEdit` handler wired next to [TextKitLinkPopup]). Dismiss it with [dismissLinkPopup].
     */
    fun applyLink() {
        openLinkEditor()
    }

    private fun openLinkEditor() {
        val text = textFieldValue.text
        val current = selection
        val (min, max) = if (!current.collapsed) {
            val lo = current.min.coerceIn(0, text.length)
            lo to current.max.coerceIn(lo, text.length)
        } else {
            wordBoundsAt(text, current.min.coerceIn(0, text.length))
        }
        if (min >= max) return
        val (href, _) = manager.getLink(min, max)
        val range = TextRange(min, max)
        activeLink =
            TextKitLinkInfo(text = text.substring(min, max), url = href.orEmpty(), range = range)
        // Pin so focusing the popup's fields (which makes the editor re-report its selection) does
        // not immediately dismiss it — there is no real link here yet to keep it open otherwise.
        pinnedLinkRange = range
    }

    /**
     * Word (non-whitespace run) surrounding [offset], as a `min..max` pair. Looks left first so a
     * caret resting right after a word still selects it; returns a collapsed pair (min == max) when
     * [offset] is not adjacent to any word character.
     */
    private fun wordBoundsAt(text: String, offset: Int): Pair<Int, Int> {
        fun isWordChar(index: Int) = index in text.indices && !text[index].isWhitespace()
        var start = offset
        var end = offset
        while (start > 0 && isWordChar(start - 1)) start--
        while (end < text.length && isWordChar(end)) end++
        return start to end
    }

    private fun getSelectedMarksWithType(): MarkSearchType {
        return manager.getSearchMarkType(selection)
    }

    /**
     * Refreshes the stored caret context ([lastMarks] + [lastListItem]) from a single document
     * query so both stay in sync with the current selection.
     *
     * When the caret lands inside a link with a collapsed selection, the whole link is marked with
     * a background highlight (see [linkSelectionRange]) rather than a real text selection, so it
     * reads like a mobile highlight without the two selection handles + caret.
     */
    private fun readSelectionContext() {
        // Remember the live selection while it is a real range, so a formatting action fired after
        // the editor collapses it (on focus loss to the toolbar) can still target it.
        if (!selection.collapsed) lastRangeSelection = selection
        val searchType = getSelectedMarksWithType()
        lastMarks = searchType.marks
        lastListItem = searchType.listItem
        updateLinkHighlight(searchType)
        notifyLinkAtSelection(searchType)
    }

    private fun updateLinkHighlight(searchType: MarkSearchType) {
        val range = searchType.range.takeIf {
            selection.collapsed && searchType.hasLink && !it.collapsed
        }
        if (range != linkSelectionRange) {
            linkSelectionRange = range
            // Rebuild so the background span is added/removed; keep the caret where it is.
            updateAnnotatedString(textFieldValue.selection)
        }
    }

    /**
     * Fires [onUrlClicked] when the caret / selection sits on a link and auto-opens the link popup —
     * but only for a **collapsed** caret resting on the link. A selection spanning a link does not
     * pop it up; adding a link to a selection goes through [applyLink] (the
     * formatting-bar action) instead. Leaving the link closes the popup.
     */
    private fun notifyLinkAtSelection(searchType: MarkSearchType) {
        val href = searchType.marks.filterIsInstance<LinkMark>().firstOrNull()?.attrs?.href
        if (searchType.hasLink && !href.isNullOrEmpty()) {
            onUrlClicked?.invoke(href, searchType.text, searchType.range)
            // Only a collapsed caret on the link opens it; a selection over the link must not.
            if (selection.collapsed) {
                activeLink = TextKitLinkInfo(searchType.text, href, searchType.range)
                pinnedLinkRange = searchType.range
                return
            }
        }
        // Keep an open popup alive while the selection is still within the range it was opened for:
        // focusing the popup's text fields makes the editor re-report a (usually collapsed)
        // selection, which must not dismiss the popup. Only close once the caret actually moves off
        // that range.
        val pinned = pinnedLinkRange
        if (pinned != null && selection.min >= pinned.min && selection.max <= pinned.max) return
        activeLink = null
        pinnedLinkRange = null
    }

    /** Hides the link popup (clears [activeLink]) until the selection next lands on a link. */
    fun dismissLinkPopup() {
        activeLink = null
        pinnedLinkRange = null
    }

    /**
     * Bounding box of the link at [range] in the text field's local coordinates, or null if the
     * layout is not available yet. Used to anchor the link popup next to the link. For a multi-line
     * link it returns the first line's box.
     */
    fun linkBoundingBox(range: TextRange): Rect? {
        val layout = textLayoutResult ?: return null
        val length = layout.layoutInput.text.length
        if (range.min !in 0..length || range.max !in 0..length || range.min >= range.max) return null
        val start = layout.getBoundingBox(range.min)
        val end = layout.getBoundingBox((range.max - 1).coerceAtLeast(range.min))
        return if (start.top == end.top) Rect(start.left, start.top, end.right, end.bottom)
        else start
    }

    private fun checkDecorator(start: Int, end: Int) {
        val (hasDecorator, range) = manager.checkDecorator(start, end)
        if (start == end && hasDecorator) {
            textFieldValue = textFieldValue.copy(selection = TextRange(range.end))
        }
    }

    /**
     * Entry point for every text-field edit; wire it to the field's `onValueChange`. It diffs
     * [newTextFieldValue] against the current value and routes the change to the piece table as an
     * insert, a deleted, a clipboard replace, or a pure selection move, keeping marks, decorators and
     * the rendered [textFieldValue] consistent.
     */
    fun onTextFieldChange(newTextFieldValue: TextFieldValue = prevTextFieldValue) {
        prevTextFieldValue = newTextFieldValue

        if (prevTextFieldValue.text.length > textFieldValue.text.length) {
            handleAddingText()
        } else if (prevTextFieldValue.text.length < textFieldValue.text.length) {
            handleRemovingText()
        } else {
            if (prevTextFieldValue.text == textFieldValue.text &&
                prevTextFieldValue.selection != textFieldValue.selection
            ) {
                // Update selection
                textFieldValue = prevTextFieldValue
                selection = textFieldValue.selection
                // Atomicity: a collapsed caret may not rest inside an atomic token — snap it out.
                val snapped = tokenState.snapCaretOutOfToken(selection)
                if (snapped != selection) {
                    selection = snapped
                    textFieldValue = textFieldValue.copy(selection = snapped)
                }
                readSelectionContext()
                checkDecorator(textFieldValue.selection.start, textFieldValue.selection.end)
                tokenState.refreshQuery(textFieldValue.text, selection)
            } else {
                // Replacing the same length of characters using the clipboard
                updateAnnotatedString(prevTextFieldValue.selection)
            }
        }
        prevTextFieldValue = TextFieldValue()
    }

    private fun handleAddingText(marks: Set<Mark> = lastMarks) {
        val action = createAddAction(marks)
        val added = (action as? TextEditorAction.TextAdded)?.text.orEmpty()
        // A single typed character coalesces into the current word; a word boundary (space/newline)
        // still joins the run but ends it so the next word is a separate undo step. Anything else
        // (a paste-like multi-char insert) is its own discrete step.
        val singleChar = added.length == 1
        val boundary = added.any { it == ' ' || it == '\n' }
        recordBefore(
            coalesceKey = if (singleChar) EditKind.Typing else null,
            breakAfter = !singleChar || boundary
        ) {
            val (result, range) = TextTransaction.onTextUpdated(action, manager)
            if (result) {
                selection = range
                updateAnnotatedString(selection)
                tokenState.refreshQuery(textFieldValue.text, selection)
                // Editing shifts offsets, so a remembered selection is no longer valid.
                lastRangeSelection = TextRange.Zero
            }
            result
        }
    }

    private fun handleRemovingText() {
        recordBefore(coalesceKey = EditKind.Deleting, breakAfter = false) {
            // A partial deletion of an atomic token (e.g. Backspace at its trailing edge) must remove
            // the whole token instead of clipping a character off its label.
            if (handleAtomicTokenDeletion()) return@recordBefore true
            val action = createRemoveAction()
            val (result, range) = TextTransaction.onTextUpdated(action, manager)
            if (result) {
                selection = range
                updateAnnotatedString(selection)
                tokenState.refreshQuery(textFieldValue.text, selection)
                // Editing shifts offsets, so a remembered selection is no longer valid.
                lastRangeSelection = TextRange.Zero
            }
            result
        }
    }

    // region Trigger tokens — thin glue over TextKitTokenState (which owns the logic)

    /** Dismisses the token popup without inserting anything (e.g. on Escape or outside tap). */
    fun dismissToken() = tokenState.dismiss()

    /**
     * Commits the active token: replaces the `<char>query` the user typed for ([id], [label]) and
     * drops the caret right after it. No-op when no token is being composed. For an atomic trigger
     * (`@`, `#`) this inserts a token node inheriting the marks active at the caret ([lastMarks]); for
     * an ephemeral trigger (`/`) it inserts the label as plain text.
     */
    fun selectToken(id: String, label: String) {
        recordBefore {
            val caret = tokenState.commitSelection(id, label, selection, lastMarks)
                ?: return@recordBefore false
            selection = caret
            updateAnnotatedString(caret)
            true
        }
    }

    /**
     * Bounding box (in the text field's local coordinates) of the active trigger char, used to
     * anchor the popup. Null when no token is being composed or the layout is not ready.
     */
    fun tokenAnchorBounds(): Rect? = tokenState.anchorBounds(textLayoutResult)

    /** Backward-compatible alias of [dismissToken]. */
    fun dismissMention() = dismissToken()

    /** Backward-compatible alias of [selectToken]. */
    fun selectMention(id: String, label: String) = selectToken(id, label)

    /** Backward-compatible alias of [tokenAnchorBounds]. */
    fun mentionAnchorBounds(): Rect? = tokenAnchorBounds()

    /**
     * Runs a slash [command] (or any ephemeral-trigger command): removes the `/query` the user typed
     * and then invokes [TextKitCommand.onSelect] at the resulting caret. No-op when no ephemeral
     * command trigger is active. Use this from a slash-command popup (see `TextKitSlashCommandPopup`).
     */
    fun runCommand(command: TextKitCommand) {
        recordBefore {
            val caret = tokenState.commitCommand(selection) ?: return@recordBefore false
            selection = caret
            updateAnnotatedString(caret)
            // The command may mutate further (e.g. insertText / applyHeading); those calls nest under
            // this restore point, so the whole command is a single undo step.
            command.onSelect(this)
            true
        }
    }

    /**
     * Inserts [text] as plain text at the current caret (inheriting the marks active there),
     * collapsing the caret right after it. Handy for custom slash commands (e.g. inserting a date or
     * snippet) via [TextKitCommand.custom].
     */
    fun insertText(text: String) {
        if (text.isEmpty()) return
        recordBefore {
            val caret = manager.insertToken(
                nodeType = null,
                id = "",
                label = text,
                replaceRange = TextRange(selection.min, selection.max),
                marks = lastMarks
            )
            selection = caret
            updateAnnotatedString(caret)
            true
        }
    }

    /**
     * When the pending deletion clips an atomic token rather than removing it whole, removes the
     * whole token through the engine and syncs the view. Returns true when it handled the deletion,
     * so the normal removal path is skipped.
     */
    private fun handleAtomicTokenDeletion(): Boolean {
        val caret = tokenState.deletePartialToken(textFieldValue.text, prevTextFieldValue.text)
            ?: return false
        selection = caret
        updateAnnotatedString(caret)
        return true
    }

    // endregion

    private fun updateAnnotatedString(selection: TextRange) {
        annotatedString = defaultAnnotatedStringFormatting().withLinkHighlight()
        val text = annotatedString.text
        // Build the field value with the NEW text and the selection together so the selection is
        // validated against the new length. Applying it via `copy(selection = …)` on the old (shorter)
        // value clamps it to the OLD length first — e.g. after adding list decorators the selection
        // would stop short of the new end instead of covering the whole (now longer) document.
        val coerced = TextRange(
            start = selection.start.coerceIn(0, text.length),
            end = selection.end.coerceIn(0, text.length),
        )
        textFieldValue = TextFieldValue(text = text, selection = coerced)
        visualTransformation =
            VisualTransformation { _ -> TransformedText(annotatedString, OffsetMapping.Identity) }
    }

    /**
     * Paints the active [linkSelectionRange] as a translucent background so a tapped link reads
     * like a highlight (no selection handles). Returns the string unchanged when no link is active.
     */
    private fun AnnotatedString.withLinkHighlight(): AnnotatedString {
        val range = linkSelectionRange ?: return this
        if (range.min >= range.max || range.max > text.length) return this
        return buildAnnotatedString {
            append(this@withLinkHighlight)
            addStyle(
                SpanStyle(background = configuration.linkColor.copy(alpha = 0.20f)),
                range.min,
                range.max
            )
        }
    }

    private fun defaultAnnotatedStringFormatting(): AnnotatedString {
        return buildAnnotatedString {
            // A single ParagraphStyle wraps the whole document. Wrapping each paragraph in its own
            // ParagraphStyle turned every trailing '\n' into a full-height empty line (visible as
            // large gaps between paragraphs); keeping one paragraph block makes the '\n' plain line
            // breaks (single spacing) while every character — and thus the offset mapping — is kept.
            withStyle(DefaultParagraphStyle) {
                paragraphs.forEach { paragraph ->
                    paragraph.children.forEach { child ->
                        if (child.text.endsWith(lineBreak)) {
                            val text = child.text.dropLast(1)
                            withStyle(child.createStyle(manager.configuration)) {
                                append(text)
                            }
                            append(lineBreak)
                        } else {
                            withStyle(child.createStyle(manager.configuration)) {
                                append(child.text)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun createViewerAnnotatedString(): Pair<AnnotatedString, Map<String, InlineTextContent>> {
        val inlineContent = mutableMapOf<String, InlineTextContent>()
        val annotatedString = buildAnnotatedString {
            paragraphs.forEach { paragraph ->
                withStyle(DefaultParagraphStyle) {
                    paragraph.children.forEach { child ->
                        val id = "${child.start}-${child.end}"
                        val text = if (child.text.endsWith(lineBreak)) {
                            child.text
                        } else child.text
                        if (child.decorator is TextDecoratorModel.TaskDecoratorModel) {
                            val taskDecorator = child.decorator
                            appendInlineContent(id, taskDecorator.createDecoratorString())
                            inlineContent[id] = InlineTextContent(
                                Placeholder(
                                    width = (1.8).em,
                                    height = (1.9).em,
                                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                                )
                            ) {
                                Checkbox(
                                    checked = taskDecorator.checked,
                                    onCheckedChange = {},
                                    modifier = Modifier.padding(start = 8.dp, end = 16.dp)
                                )
                            }
                        } else {
                            pushStringAnnotation(id, text)
                            if (child.marks.any { it is LinkMark }) {
                                withLink(
                                    link = LinkAnnotation.Url(
                                        url = child.marks.filterIsInstance<LinkMark>()
                                            .first().attrs.href,
                                        styles = TextLinkStyles(child.createStyle(manager.configuration)),
                                        linkInteractionListener = {
                                            val url = (it as LinkAnnotation.Url).url
                                            onUrlClicked?.invoke(
                                                url,
                                                child.text,
                                                TextRange(child.start, child.end)
                                            )
                                        }
                                    )
                                ) {
                                    append(text)
                                }
                            } else {
                                append(text)
                                addStyle(
                                    style = child.createStyle(manager.configuration),
                                    start = child.start,
                                    end = child.end
                                )
                            }
                            pop()
                        }
                    }
                }
            }
        }

        return Pair(annotatedString, inlineContent)
    }

    private fun createAddAction(marks: Set<Mark> = emptySet()): TextEditorAction {
        return when {
            isTyping -> {
                val typedTextCount = prevTextFieldValue.text.length - textFieldValue.text.length
                val start = prevTextFieldValue.selection.min - typedTextCount
                val typedText = prevTextFieldValue.text.substring(start, start + typedTextCount)
                TextEditorAction.TextAdded(
                    text = typedText,
                    marks = marks,
                    offset = start,
                    selection = prevTextFieldValue.selection
                )
            }

            isUsingClipboard -> {
                TextEditorAction.TextUpdated(
                    removeLength = textFieldValue.selection.length,
                    text = prevTextFieldValue.text.substring(
                        textFieldValue.selection.start,
                        prevTextFieldValue.selection.end
                    ),
                    offset = textFieldValue.selection.start,
                    selection = prevTextFieldValue.selection
                )
            }

            else -> {
                val typedTextCount = prevTextFieldValue.text.length - textFieldValue.text.length
                val start = prevTextFieldValue.selection.min - typedTextCount
                val typedText = prevTextFieldValue.text.substring(start, start + typedTextCount)
                TextEditorAction.TextAdded(
                    text = typedText,
                    offset = start,
                    selection = prevTextFieldValue.selection
                )
            }
        }
    }

    private fun createRemoveAction(): TextEditorAction {
        return when {
            isTyping -> {
                TextEditorAction.TextRemoved(
                    offset = prevTextFieldValue.selection.min,
                    length = textFieldValue.text.length - prevTextFieldValue.text.length,
                    selection = textFieldValue.selection
                )
            }

            isUsingClipboard -> {
                TextEditorAction.TextUpdated(
                    removeLength = textFieldValue.selection.length,
                    text = prevTextFieldValue.text.substring(
                        textFieldValue.selection.start,
                        prevTextFieldValue.selection.end
                    ),
                    offset = textFieldValue.selection.start,
                    selection = prevTextFieldValue.selection
                )
            }

            else -> TextEditorAction.TextRemoved(
                offset = prevTextFieldValue.selection.min,
                length = textFieldValue.text.length - prevTextFieldValue.text.length,
                selection = textFieldValue.selection
            )
        }
    }

    companion object {

        /**
         * Builds the [rememberSaveable] saver for a [TextKitState]. The [configuration] (colors,
         * sizes and the full set of triggers) is supplied by the caller on every recreation —
         * including after process death — so it is taken live from here instead of being serialized
         * and rebuilt. This keeps the triggers fully dynamic: whatever the caller configured (custom
         * subclasses included) is restored as-is, with no hardcoded per-char reconstruction. Only the
         * editing state (text, selection, document, viewer flag) is persisted.
         */
        fun saver(configuration: TextKitConfiguration) = Saver<TextKitState, Any>(
            save = {
                arrayListOf(
                    save(it.textFieldValue.text),
                    save(it.selection, TextRangeSaver, this),
                    save(it.toJson()),
                    save(it.isViewer),
                )
            },
            restore = {
                @Suppress("UNCHECKED_CAST")
                val list = it as List<Any>
                val textFieldValue = TextFieldValue(
                    text = restore(list[0])!!,
                    selection = restore(list[1], TextRangeSaver)!!
                )
                val json: String = restore(list[2])!!
                val isViewer: Boolean = restore(list[3])!!

                val state = TextKitState(json, isViewer, configuration)
                    .also { state ->
                        state.textFieldValue = textFieldValue
                        state.updateAnnotatedString(state.textFieldValue.selection)
                    }
                state
            }
        )

        internal val DefaultParagraphStyle = ParagraphStyle(
            textAlign = TextAlign.Left,
            lineBreak = LineBreak.Paragraph,
            lineHeight = 1.5.em,
            // Trim.None keeps full line height on every line — including the empty line created
            // right after pressing Enter — so the caret lands at the correct vertical position
            // immediately instead of appearing on the previous paragraph's line and then jumping
            // down once the layout re-measures. Center keeps the glyph centered in the line box.
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.None,
            ),
        )

        private val TextRangeSaver = Saver<TextRange, Any>(
            save = { arrayListOf(save(it.start), save(it.end)) },
            restore = {
                @Suppress("UNCHECKED_CAST")
                val list = it as List<Any>
                TextRange(restore(list[0])!!, restore(list[1])!!)
            }
        )
    }

    private val lineBreak = "\n"
}
