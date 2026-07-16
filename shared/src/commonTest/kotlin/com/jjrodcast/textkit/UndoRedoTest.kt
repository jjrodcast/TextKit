package com.jjrodcast.textkit

import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.components.TextEditorListItem
import com.jjrodcast.textkit.editor.components.TextEditorStyleItem
import com.jjrodcast.textkit.editor.core.TextKitEditorManager
import com.jjrodcast.textkit.editor.core.parser.BoldMark
import com.jjrodcast.textkit.editor.utils.DocumentUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * End-to-end undo/redo over the real engine: every mutation kind (typing, deletion, formatting, list
 * toggles, paragraph split/merge) is captured as a snapshot and reverted/reapplied through
 * [TextKitEditorManager.undo] / [TextKitEditorManager.redo].
 *
 * These drive the same `captureHistoryPoint` + `pushHistory` contract the UI ([TextKitState]) uses:
 * capture a restore point *before* the edit, then commit it once the edit changed the document.
 */
class UndoRedoTest {

    /** Records a restore point for the state *before* the next edit, as the UI does. */
    private fun TextKitEditorManager.checkpoint(selection: TextRange = TextRange.Zero, key: Any? = null) =
        pushHistory(captureHistoryPoint(selection), key)

    @Test
    fun no_history_initially() {
        val editor = editorFrom(SampleDocuments.SINGLE_PARAGRAPH)
        assertFalse(editor.canUndo)
        assertFalse(editor.canRedo)
        assertNull(editor.undo(TextRange.Zero))
        assertNull(editor.redo(TextRange.Zero))
    }

    @Test
    fun undo_reverts_typing_and_redo_reapplies_it() {
        val editor = editorFrom(SampleDocuments.SINGLE_PARAGRAPH)
        val original = editor.text

        editor.checkpoint(TextRange(0))
        editor.typeText(offset = 0, textToAdd = "Hi ")
        assertEquals("Hi Hello world", editor.text)
        assertTrue(editor.canUndo)

        val undoSelection = editor.undo(TextRange(3))
        assertEquals(original, editor.text)
        assertEquals(TextRange(0), undoSelection) // caret restored to where it was before typing
        assertTrue(editor.canRedo)

        val redoSelection = editor.redo(TextRange(0))
        assertEquals("Hi Hello world", editor.text)
        assertEquals(TextRange(3), redoSelection) // caret restored to where it was before the undo
    }

    @Test
    fun undo_reverts_deletion() {
        val editor = editorFrom(SampleDocuments.SINGLE_PARAGRAPH)
        val original = editor.text

        editor.checkpoint(TextRange(0))
        editor.deleteText(offset = 0, length = "Hello ".length)
        assertEquals("world", editor.text)

        editor.undo(TextRange(0))
        assertEquals(original, editor.text)
    }

    @Test
    fun undo_reverts_bold_formatting_including_serialization() {
        val editor = editorFrom(SampleDocuments.SINGLE_PARAGRAPH)
        val range = editor.rangeOf("Hello")
        val jsonBefore = editor.toJson()

        editor.checkpoint(range)
        editor.applyStyle(range, TextEditorStyleItem.Bold)
        assertTrue(editor.marksAt(range).has<BoldMark>())

        editor.undo(range)
        assertFalse(editor.marksAt(range).has<BoldMark>())
        // Undo restores the exact document, so it round-trips to the original JSON.
        assertEquals(jsonBefore, editor.toJson())

        editor.redo(range)
        assertTrue(editor.marksAt(range).has<BoldMark>())
    }

    @Test
    fun undo_reverts_a_list_toggle() {
        val editor = editorFrom(SampleDocuments.SINGLE_PARAGRAPH)
        val range = editor.rangeOf("Hello")

        editor.checkpoint(range)
        editor.toListItem(range, from = TextEditorListItem.None, to = TextEditorListItem.BulletedList)
        val afterToggle = editor.text
        assertTrue(afterToggle != "Hello world") // a bullet decorator marker was inserted

        editor.undo(range)
        assertEquals("Hello world", editor.text)

        editor.redo(range)
        assertEquals(afterToggle, editor.text)
    }

    @Test
    fun undo_merges_a_split_paragraph_back() {
        val editor = editorFrom(SampleDocuments.SINGLE_PARAGRAPH)

        editor.checkpoint(TextRange(editor.offsetOf("world")))
        editor.typeText(offset = editor.offsetOf("world"), textToAdd = "\n")
        assertEquals(2, editor.getParagraphs().size)

        editor.undo(TextRange.Zero)
        assertEquals("Hello world", editor.text)
        assertEquals(1, editor.getParagraphs().size)
    }

    @Test
    fun multiple_sequential_undos_walk_back_the_history() {
        val editor = editorFrom(DocumentUtils.emptyDocument)

        editor.checkpoint(TextRange(0))
        editor.typeText(0, "one")
        editor.checkpoint(TextRange(editor.text.length))
        editor.typeText(editor.text.length, "two")
        editor.checkpoint(TextRange(editor.text.length))
        editor.typeText(editor.text.length, "three")
        assertEquals("onetwothree", editor.text)

        editor.undo(TextRange(editor.text.length))
        assertEquals("onetwo", editor.text)
        editor.undo(TextRange(editor.text.length))
        assertEquals("one", editor.text)
        editor.undo(TextRange(editor.text.length))
        assertEquals("", editor.text)
        assertFalse(editor.canUndo)
    }

    @Test
    fun a_new_edit_after_undo_invalidates_redo() {
        val editor = editorFrom(SampleDocuments.SINGLE_PARAGRAPH)

        editor.checkpoint(TextRange(0))
        editor.typeText(0, "A")
        editor.undo(TextRange(1))
        assertTrue(editor.canRedo)

        editor.checkpoint(TextRange(0))
        editor.typeText(0, "B")
        assertFalse(editor.canRedo)
        assertEquals("BHello world", editor.text)
    }

    @Test
    fun redo_stack_survives_across_undo_redo_cycles() {
        val editor = editorFrom(SampleDocuments.SINGLE_PARAGRAPH)

        editor.checkpoint(TextRange(0))
        editor.typeText(0, "X ")
        assertEquals("X Hello world", editor.text)

        editor.undo(TextRange(2))
        assertEquals("Hello world", editor.text)
        editor.redo(TextRange(0))
        assertEquals("X Hello world", editor.text)
        editor.undo(TextRange(2))
        assertEquals("Hello world", editor.text)
    }

    @Test
    fun load_clears_history() {
        val editor = editorFrom(SampleDocuments.SINGLE_PARAGRAPH)
        editor.checkpoint(TextRange(0))
        editor.typeText(0, "junk")
        assertTrue(editor.canUndo)

        editor.load(SampleDocuments.TWO_PARAGRAPHS, isViewer = false)

        assertFalse(editor.canUndo)
        assertFalse(editor.canRedo)
        assertNull(editor.undo(TextRange.Zero))
    }

    @Test
    fun clearHistory_drops_all_steps() {
        val editor = editorFrom(SampleDocuments.SINGLE_PARAGRAPH)
        editor.checkpoint(TextRange(0))
        editor.typeText(0, "a")

        editor.clearHistory()

        assertFalse(editor.canUndo)
        assertFalse(editor.canRedo)
    }

    @Test
    fun undo_then_edit_then_undo_restores_intermediate_and_original() {
        val editor = editorFrom(DocumentUtils.emptyDocument)

        editor.checkpoint(TextRange(0))
        editor.typeText(0, "hello")
        assertEquals("hello", editor.text)

        // Revert once, then make a different edit; the old redo is gone but undo still reaches "".
        editor.undo(TextRange(5))
        assertEquals("", editor.text)

        editor.checkpoint(TextRange(0))
        editor.typeText(0, "world")
        assertEquals("world", editor.text)

        editor.undo(TextRange(5))
        assertEquals("", editor.text)
        assertFalse(editor.canUndo)
    }
}
