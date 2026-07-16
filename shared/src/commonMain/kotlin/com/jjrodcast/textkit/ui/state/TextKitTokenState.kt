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
import com.jjrodcast.textkit.editor.models.TextKitTrigger

/**
 * Owns all trigger-token behavior (`@` mentions, `#` hashtags, `/` slash commands, …), split out of
 * [TextKitState] to keep responsibilities separate: detecting any configured trigger char, tracking
 * the in-progress [query] + [activeTrigger], keeping atomic tokens indivisible (cursor snapping +
 * whole-token deletion), and committing a picked suggestion through the engine.
 *
 * This class holds only trigger-domain state and drives the [TextKitEditorManager]; it never touches
 * the visible `TextFieldValue`. [TextKitState] passes in the current text/selection/marks and applies
 * the caret each method returns, so the two concerns stay decoupled. Generalized from the former
 * mention-only state so a new trigger is just configuration + candidates.
 */
@Stable
internal class TextKitTokenState(
    private val manager: TextKitEditorManager,
    private val configuration: TextKitConfiguration,
) {

    /** Whether the editor has at least one trigger configured. */
    val isEnabled: Boolean get() = configuration.triggers.isNotEmpty()

    /**
     * Text typed after the active trigger char, or null when no token is being composed. Observe this
     * (together with [activeTrigger]) to show/filter the popup; it updates on every keystroke and
     * clears once committed, dismissed, or invalidated.
     */
    var query by mutableStateOf<String?>(null)
        private set

    /** The trigger currently being composed (drives which candidate set the popup shows), or null. */
    var activeTrigger by mutableStateOf<TextKitTrigger?>(null)
        private set

    /** Document offset of the active trigger char, or -1 when inactive. */
    private var anchor: Int = -1

    /**
     * Offset of a trigger the user explicitly dismissed; the popup stays closed for it until the
     * caret leaves that span, so a dismissed popup does not immediately re-open. -1 when nothing is
     * suppressed.
     */
    private var suppressedAnchor: Int = -1

    /** Hard reset — clears the in-progress token and any dismissal (used after commit/delete). */
    fun reset() {
        anchor = -1
        query = null
        activeTrigger = null
        suppressedAnchor = -1
    }

    /** Closes the popup and suppresses re-opening for the current trigger until the caret leaves it. */
    fun dismiss() {
        if (anchor >= 0) suppressedAnchor = anchor
        anchor = -1
        query = null
        activeTrigger = null
    }

    /**
     * (Re)derives the active token from the [caret]: it is active whenever the collapsed caret sits
     * inside a `<char>query` span (a configured trigger char at a word boundary, no whitespace
     * between it and the caret) and inactive otherwise. Deriving it from the caret — instead of only
     * reacting to the keystroke that typed the trigger — is what lets the popup re-open when the caret
     * is moved back next to an existing, still-empty trigger char.
     */
    fun refreshQuery(text: String, caret: TextRange) {
        if (!caret.collapsed || configuration.triggers.isEmpty()) {
            clearActive()
            return
        }
        val start = findTriggerStart(text, caret.start)
        if (start < 0) {
            // Caret is outside any pending token span — drop the token and forget any dismissal so
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
        activeTrigger = configuration.triggerFor(text[start])
        query = text.substring(start + 1, caret.start)
    }

    private fun clearActive() {
        anchor = -1
        query = null
        activeTrigger = null
    }

    /**
     * Offset of the trigger char opening the pending token the [caretStart] sits in, or -1. Walks
     * left from the caret over query chars (stopping at whitespace); a match must sit at a word
     * boundary and must not belong to an already-committed (atomic) token.
     */
    private fun findTriggerStart(text: String, caretStart: Int): Int {
        var i = caretStart - 1
        while (i >= 0) {
            val ch = text[i]
            if (ch == '\n' || ch == ' ') return -1
            if (configuration.triggerFor(ch) != null) {
                val atBoundary = i == 0 || text[i - 1] == ' ' || text[i - 1] == '\n'
                if (!atBoundary || isInsideCommittedToken(i)) return -1
                return i
            }
            i--
        }
        return -1
    }

    private fun isInsideCommittedToken(offset: Int): Boolean =
        tokenRanges().any { offset >= it.min && offset < it.max }

    /** Document ranges of every atomic token currently in the document, in ascending order. */
    fun tokenRanges(): List<TextRange> =
        manager.getParagraphs()
            .flatMap { it.children }
            .filter { it.isToken }
            .map { TextRange(it.start, it.end) }

    /** Snaps a collapsed caret out of an atomic token's interior to the nearest boundary. */
    fun snapCaretOutOfToken(sel: TextRange): TextRange {
        if (!sel.collapsed) return sel
        val token = tokenRanges().firstOrNull { sel.start > it.min && sel.start < it.max }
            ?: return sel
        val target = if (sel.start - token.min <= token.max - sel.start) token.min else token.max
        return TextRange(target)
    }

    /**
     * Bounding box (text-field local coordinates) of the active trigger char, used to anchor the
     * popup. Null when no token is being composed or the [layout] is not ready.
     */
    fun anchorBounds(layout: TextLayoutResult?): Rect? {
        if (anchor < 0 || layout == null) return null
        // getBoundingBox needs a valid character index in [0, length). When the trigger char was just
        // typed at the very end of the document the layout is momentarily one frame behind (its text
        // is still the pre-insert, shorter string), so anchor can equal its length — skip this frame
        // instead of crashing; recomposition re-runs anchorBounds once the updated layout arrives.
        if (anchor >= layout.layoutInput.text.length) return null
        return layout.getBoundingBox(anchor)
    }

    /**
     * Commits the suggestion picked for ([id], [label]) by replacing the `<char>query` span through
     * the engine, then resets. For an atomic trigger this inserts a token node carrying [marks]; for
     * an ephemeral trigger (`nodeType == null`) it inserts plain text. Returns the caret to place
     * after the result, or null when no token is active.
     */
    fun commitSelection(id: String, label: String, caret: TextRange, marks: Set<Mark>): TextRange? {
        val trigger = activeTrigger ?: return null
        if (anchor < 0) return null
        val end = caret.start.coerceAtLeast(anchor)
        val newCaret = manager.insertToken(trigger.nodeType, id, label, TextRange(anchor, end), marks)
        reset()
        return newCaret
    }

    /**
     * Commits an ephemeral command trigger (e.g. `/`): removes the `<char>query` span the user typed
     * (with NO insertion — the command itself performs the effect) and resets. Returns the caret at
     * the trigger start, or null when the active trigger is not an ephemeral command.
     */
    fun commitCommand(caret: TextRange): TextRange? {
        val trigger = activeTrigger ?: return null
        if (trigger.isToken) return null
        if (anchor < 0) return null
        val end = caret.start.coerceAtLeast(anchor)
        val newCaret = manager.deleteRange(TextRange(anchor, end))
        reset()
        return newCaret
    }

    /**
     * If going from [oldText] to [newText] would clip an atomic token rather than remove it whole,
     * deletes the whole token range through the engine and returns the resulting caret; otherwise
     * returns null so the caller's normal removal path runs.
     */
    fun deletePartialToken(oldText: String, newText: String): TextRange? {
        val removed = removedSpan(oldText, newText)
        if (removed.collapsed) return null
        val partials = tokenRanges().filter { m ->
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

/** Backward-compatible alias for the former mention-only state (now generalized). */
internal typealias TextKitMentionState = TextKitTokenState
