package com.jjrodcast.textkit.ui.state

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.core.TextKitEditorManager
import com.jjrodcast.textkit.editor.core.parser.Mark
import com.jjrodcast.textkit.editor.models.TextKitConfiguration

/**
 * Owns all mention (`@`) behavior, split out of [TextKitState] to keep responsibilities separate:
 * detecting the trigger char, tracking the in-progress [query], keeping mentions atomic (cursor
 * snapping + whole-mention deletion), and committing a picked mention through the engine.
 *
 * This class holds only mention-domain state and drives the [TextKitEditorManager]; it never touches
 * the visible `TextFieldValue`. [TextKitState] passes in the current text/selection/marks and applies
 * the caret each method returns, so the two concerns stay decoupled.
 */
@Stable
internal class TextKitMentionState(
    private val manager: TextKitEditorManager,
    configuration: TextKitConfiguration,
) {

    /** Configured trigger char (e.g. `@`), or null when mentions are not enabled. */
    private val triggerChar: Char? = configuration.mentionTrigger?.triggerKey

    /** Whether the editor has a mention trigger configured at all. */
    val isEnabled: Boolean get() = triggerChar != null

    /**
     * Text typed after the trigger char, or null when no mention is being composed. Observe this to
     * show/filter the mention popup; it updates on every keystroke and clears once committed,
     * dismissed, or invalidated.
     */
    var query by mutableStateOf<String?>(null)
        private set

    /** Document offset of the active trigger char, or -1 when inactive. */
    private var anchor: Int = -1

    /**
     * Offset of a trigger the user explicitly dismissed; the popup stays closed for it until the
     * caret leaves that span, so a dismissed popup does not immediately re-open. -1 when nothing is
     * suppressed.
     */
    private var suppressedAnchor: Int = -1

    /** Hard reset — clears the in-progress mention and any dismissal (used after commit/delete). */
    fun reset() {
        anchor = -1
        query = null
        suppressedAnchor = -1
    }

    /** Closes the popup and suppresses re-opening for the current trigger until the caret leaves it. */
    fun dismiss() {
        if (anchor >= 0) suppressedAnchor = anchor
        anchor = -1
        query = null
    }

    /**
     * (Re)derives the active mention from the [caret]: it is active whenever the collapsed caret sits
     * inside a `@query` span (trigger char at a word boundary, no whitespace between it and the
     * caret) and inactive otherwise. Deriving it from the caret — instead of only reacting to the
     * keystroke that typed the trigger — is what lets the popup re-open when the caret is moved back
     * next to an existing, still-empty `@`.
     */
    fun refreshQuery(text: String, caret: TextRange) {
        val char = triggerChar
        if (char == null || !caret.collapsed) {
            clearActive()
            return
        }
        val start = findTriggerStart(text, char, caret.start)
        if (start < 0) {
            // Caret is outside any pending token span — drop the mention and forget any dismissal so
            // returning to a trigger later re-opens the popup.
            clearActive()
            suppressedAnchor = -1
            return
        }
        if (start == suppressedAnchor) {
            // Explicitly dismissed for this exact trigger; keep it closed until the caret leaves.
            clearActive()
            return
        }
        suppressedAnchor = -1
        anchor = start
        query = text.substring(start + 1, caret.start)
    }

    private fun clearActive() {
        anchor = -1
        query = null
    }

    /**
     * Offset of the trigger char opening the pending token the [caretStart] sits in, or -1. Walks
     * left from the caret over query chars (stopping at whitespace); a match must sit at a word
     * boundary and must not belong to an already-committed (atomic) token.
     */
    private fun findTriggerStart(text: String, char: Char, caretStart: Int): Int {
        var i = caretStart - 1
        while (i >= 0) {
            val ch = text[i]
            if (ch == '\n' || ch == ' ') return -1
            if (ch == char) {
                val atBoundary = i == 0 || text[i - 1] == ' ' || text[i - 1] == '\n'
                if (!atBoundary || isInsideCommittedToken(i)) return -1
                return i
            }
            i--
        }
        return -1
    }

    private fun isInsideCommittedToken(offset: Int): Boolean =
        mentionRanges().any { offset >= it.min && offset < it.max }

    /** Document ranges of every mention currently in the document, in ascending order. */
    fun mentionRanges(): List<TextRange> =
        manager.getParagraphs()
            .flatMap { it.children }
            .filter { it.isMention }
            .map { TextRange(it.start, it.end) }

    /** Snaps a collapsed caret out of a mention's interior to the nearest boundary. */
    fun snapCaretOutOfMention(sel: TextRange): TextRange {
        if (!sel.collapsed) return sel
        val mention = mentionRanges().firstOrNull { sel.start > it.min && sel.start < it.max }
            ?: return sel
        val target = if (sel.start - mention.min <= mention.max - sel.start) mention.min else mention.max
        return TextRange(target)
    }

    /**
     * Bounding box (text-field local coordinates) of the active trigger char, used to anchor the
     * popup. Null when no mention is being composed or the [layout] is not ready.
     */
    fun anchorBounds(layout: TextLayoutResult?): Rect? {
        if (anchor < 0 || layout == null) return null
        if (anchor !in 0..layout.layoutInput.text.length) return null
        return layout.getBoundingBox(anchor)
    }

    /**
     * Commits the mention picked for ([id], [label]) by replacing the `@query` span with an atomic
     * mention node carrying [marks], then resets. Returns the caret to place after the mention, or
     * null when no mention is active.
     */
    fun commitSelection(id: String, label: String, caret: TextRange, marks: Set<Mark>): TextRange? {
        if (anchor < 0) return null
        val end = caret.start.coerceAtLeast(anchor)
        val newCaret = manager.insertMention(id, label, TextRange(anchor, end), marks)
        reset()
        return newCaret
    }

    /**
     * If going from [oldText] to [newText] would clip a mention rather than remove it whole, deletes
     * the whole mention range through the engine and returns the resulting caret; otherwise returns
     * null so the caller's normal removal path runs.
     */
    fun deletePartialMention(oldText: String, newText: String): TextRange? {
        val removed = removedSpan(oldText, newText)
        if (removed.collapsed) return null
        val partials = mentionRanges().filter { m ->
            val overlaps = removed.min < m.max && m.min < removed.max
            val fullyInside = removed.min <= m.min && m.max <= removed.max
            overlaps && !fullyInside
        }
        if (partials.isEmpty()) return null
        val delStart = (partials.map { it.min } + removed.min).min()
        val delEnd = (partials.map { it.max } + removed.max).max()
        manager.deleteRange(TextRange(delStart, delEnd))
        reset()
        return TextRange(delStart)
    }

    /**
     * The contiguous span removed when going from [oldText] to [newText], in [oldText] coordinates,
     * derived from the common prefix/suffix. Collapsed when nothing was removed.
     */
    private fun removedSpan(oldText: String, newText: String): TextRange {
        val minLen = minOf(oldText.length, newText.length)
        var prefix = 0
        while (prefix < minLen && oldText[prefix] == newText[prefix]) prefix++
        var suffix = 0
        while (suffix < minLen - prefix &&
            oldText[oldText.length - 1 - suffix] == newText[newText.length - 1 - suffix]
        ) suffix++
        return TextRange(prefix, oldText.length - suffix)
    }
}
