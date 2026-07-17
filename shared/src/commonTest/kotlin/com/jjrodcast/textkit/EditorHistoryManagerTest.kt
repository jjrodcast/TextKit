package com.jjrodcast.textkit

import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.core.history.EditKind
import com.jjrodcast.textkit.editor.core.history.EditorHistoryManager
import com.jjrodcast.textkit.editor.core.history.HistorySnapshot
import com.jjrodcast.textkit.editor.core.piecetable.PieceTableSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Pure unit tests for the undo/redo stack + coalescing logic in [EditorHistoryManager], exercised
 * with dummy [HistorySnapshot]s (the document payload is irrelevant to the stack behavior).
 */
class EditorHistoryManagerTest {

    private fun snap(selection: Int = 0): HistorySnapshot =
        HistorySnapshot(PieceTableSnapshot(root = null, cachedText = null), TextRange(selection))

    @Test
    fun starts_empty() {
        val history = EditorHistoryManager()
        assertFalse(history.canUndo)
        assertFalse(history.canRedo)
        assertEquals(0, history.undoCount)
        assertEquals(0, history.redoCount)
    }

    @Test
    fun recording_makes_undo_available() {
        val history = EditorHistoryManager()
        history.record(snap())
        assertTrue(history.canUndo)
        assertEquals(1, history.undoCount)
    }

    @Test
    fun same_key_run_coalesces_into_one_step() {
        val history = EditorHistoryManager()
        history.record(snap(0), EditKind.Typing)
        history.record(snap(1), EditKind.Typing)
        history.record(snap(2), EditKind.Typing)
        assertEquals(1, history.undoCount)
    }

    @Test
    fun break_coalescing_starts_a_new_step() {
        val history = EditorHistoryManager()
        history.record(snap(0), EditKind.Typing)
        history.record(snap(1), EditKind.Typing)
        history.breakCoalescing()
        history.record(snap(2), EditKind.Typing)
        assertEquals(2, history.undoCount)
    }

    @Test
    fun null_key_never_coalesces() {
        val history = EditorHistoryManager()
        history.record(snap(0), coalesceKey = null)
        history.record(snap(1), coalesceKey = null)
        assertEquals(2, history.undoCount)
    }

    @Test
    fun different_keys_do_not_coalesce() {
        val history = EditorHistoryManager()
        history.record(snap(0), EditKind.Typing)
        history.record(snap(1), EditKind.Deleting)
        assertEquals(2, history.undoCount)
    }

    @Test
    fun new_record_invalidates_redo() {
        val history = EditorHistoryManager()
        history.record(snap(0))
        history.undo(snap(9))
        assertTrue(history.canRedo)

        history.record(snap(1))
        assertFalse(history.canRedo)
    }

    @Test
    fun undo_returns_the_recorded_point_and_stores_current_for_redo() {
        val history = EditorHistoryManager()
        val recorded = snap(0)
        history.record(recorded)

        val current = snap(5)
        val restored = history.undo(current)

        assertSame(recorded, restored)
        assertFalse(history.canUndo)
        assertTrue(history.canRedo)
    }

    @Test
    fun redo_returns_the_state_that_was_current_at_undo() {
        val history = EditorHistoryManager()
        history.record(snap(0))
        val current = snap(5)
        history.undo(current)

        val redone = history.redo(snap(7))

        assertSame(current, redone)
        assertTrue(history.canUndo)
        assertFalse(history.canRedo)
    }

    @Test
    fun undo_and_redo_are_null_when_empty() {
        val history = EditorHistoryManager()
        assertNull(history.undo(snap()))
        assertNull(history.redo(snap()))
    }

    @Test
    fun limit_drops_the_oldest_entries() {
        val history = EditorHistoryManager(limit = 3)
        repeat(5) { history.record(snap(it)) }
        assertEquals(3, history.undoCount)
    }

    @Test
    fun clear_empties_both_stacks() {
        val history = EditorHistoryManager()
        history.record(snap(0))
        history.undo(snap(1))
        assertTrue(history.canRedo)

        history.clear()

        assertFalse(history.canUndo)
        assertFalse(history.canRedo)
    }

    @Test
    fun undo_ends_the_coalescing_run() {
        val history = EditorHistoryManager()
        history.record(snap(0), EditKind.Typing)
        history.undo(snap(9))
        // After an undo, a following same-key record must not merge into the (now redone) run.
        history.record(snap(1), EditKind.Typing)
        history.record(snap(2), EditKind.Typing)
        assertEquals(1, history.undoCount) // the two Typing records after undo coalesce together
        assertFalse(history.canRedo)       // ...and the fresh record invalidated redo
    }
}
