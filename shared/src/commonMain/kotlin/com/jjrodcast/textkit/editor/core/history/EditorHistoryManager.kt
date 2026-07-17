package com.jjrodcast.textkit.editor.core.history

import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.core.piecetable.PieceTableSnapshot

/**
 * A single undo/redo restore point: the document state ([document]) plus the [selection] to put the
 * caret back to when this point is restored.
 */
internal class HistorySnapshot(
    val document: PieceTableSnapshot,
    val selection: TextRange,
)

/**
 * The kind of edit, used only as a coalescing key so a run of same-kind edits (e.g. typing
 * character by character) collapses into a single undo step. Discrete edits (formatting, list
 * toggles, token insertions, …) pass `null` instead, which always starts a new, un-mergeable entry.
 */
internal enum class EditKind {
    Typing,
    Deleting,
}

/**
 * Stack-based undo/redo history over immutable [PieceTableSnapshot]s.
 *
 * Because a snapshot is an O(1) capture of a persistent rope (see [PieceTableSnapshot]), a single
 * snapshot type uniformly covers **every** kind of document mutation (typing, deleting, formatting,
 * lists, tokens): recording is just pushing the pre-edit snapshot, and undo/redo is swapping it back.
 *
 * Coalescing: [record] merges consecutive edits that share a non-null [coalesceKey], so typing a
 * word is one undo step rather than one per keystroke. [breakCoalescing] force-ends the current run
 * (e.g. after a word boundary) so the next edit starts a fresh step.
 *
 * The undo stack is capped at [limit]; the oldest entries are dropped past it. Redo is invalidated by
 * any new [record].
 */
internal class EditorHistoryManager(private val limit: Int = DEFAULT_LIMIT) {

    private val undoStack = ArrayDeque<HistorySnapshot>()
    private val redoStack = ArrayDeque<HistorySnapshot>()

    /** Coalescing key of the most recently recorded edit, or `null` at a boundary. */
    private var lastKey: Any? = null

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /** Number of undo steps currently retained. Exposed for tests. */
    internal val undoCount: Int get() = undoStack.size

    /** Number of redo steps currently retained. Exposed for tests. */
    internal val redoCount: Int get() = redoStack.size

    /**
     * Records [snapshot] as a restore point (the state **before** the edit it guards). Any new record
     * invalidates the redo stack.
     *
     * When [coalesceKey] is non-null and equal to the previous record's key, the edits are merged:
     * this snapshot is dropped so the earlier boundary remains the restore point — this is what turns
     * a run of keystrokes into one undo step. A `null` key always starts a new, un-mergeable entry.
     */
    fun record(snapshot: HistorySnapshot, coalesceKey: Any? = null) {
        redoStack.clear()
        val mergeable = coalesceKey != null && coalesceKey == lastKey && undoStack.isNotEmpty()
        lastKey = coalesceKey
        if (mergeable) return
        undoStack.addLast(snapshot)
        while (undoStack.size > limit) undoStack.removeFirst()
    }

    /**
     * Ends the current coalescing run so the next [record] starts a fresh undo step even if it shares
     * the previous key. Called e.g. right after typing a word-boundary character.
     */
    fun breakCoalescing() {
        lastKey = null
    }

    /**
     * Pops the last restore point and returns it, pushing [current] (the live state) onto the redo
     * stack so the move can be reversed. Returns `null` when there is nothing to undo.
     */
    fun undo(current: HistorySnapshot): HistorySnapshot? {
        val previous = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(current)
        lastKey = null
        return previous
    }

    /**
     * Pops the last redone point and returns it, pushing [current] (the live state) back onto the
     * undo stack. Returns `null` when there is nothing to redo.
     */
    fun redo(current: HistorySnapshot): HistorySnapshot? {
        val next = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(current)
        lastKey = null
        return next
    }

    /** Clears all history (both stacks). Called when the document is replaced (e.g. `load`). */
    fun clear() {
        undoStack.clear()
        redoStack.clear()
        lastKey = null
    }

    companion object {
        const val DEFAULT_LIMIT = 100
    }
}
