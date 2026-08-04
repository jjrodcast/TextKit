package com.jjrodcast.textkit

import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.core.TextKitEditorManager
import com.jjrodcast.textkit.editor.core.history.EditKind
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Property test for undo/redo over the full editing surface.
 *
 * The example tests pin individual scenarios; this checks the actual contract on random sequences.
 * Every edit from the [EditingStress] op mix is wrapped in the same capture/push protocol the UI
 * uses (`TextKitState.recordBefore`), and a model of the two history stacks — the exact `toJson()`
 * each restore must produce, plus the selection captured with it — is kept alongside. After every
 * step:
 *
 * 1. `undo` restores the exact pre-edit document (`toJson()` equality, not just a valid document)
 *    and returns the selection captured with the restore point.
 * 2. `redo` restores the exact document `undo` left, and returns the selection `undo` was called
 *    with.
 * 3. A coalesced typing run (per-keystroke pushes sharing [EditKind.Typing]) is a single undo step
 *    that restores to before the first keystroke.
 * 4. `canUndo`/`canRedo` mirror the model exactly — including redo invalidation on a new edit and
 *    the oldest-dropped cap of `EditorHistoryManager.DEFAULT_LIMIT` steps.
 * 5. The document that undo/redo restores satisfies every [EditingStress] structural invariant.
 *
 * A failure names the seed, step and operation, so it is an exact repro.
 */
class UndoRedoStressTest {

    @Test
    fun undo_and_redo_restore_exact_documents_across_random_edits() {
        UndoRedoStress.run(SMOKE_SEEDS, SMOKE_OPS_PER_SEED)
    }

    private companion object {
        val SMOKE_SEEDS = 0 until 12
        const val SMOKE_OPS_PER_SEED = 50
    }
}

/** The machinery, shared by the cross-platform smoke run and the JVM-only full sweep. */
internal object UndoRedoStress {

    private const val HISTORY_LIMIT = 100

    /** One expected restore: the document to come back to and the selection stored with it. */
    private class Expected(val json: String, val selection: TextRange)

    fun run(seeds: IntRange, opsPerSeed: Int) {
        for (seed in seeds) {
            val editor = editorFrom(EditingStress.START_DOCS[seed % EditingStress.START_DOCS.size])
            val rng = Random(seed)
            val undoModel = ArrayDeque<Expected>()
            val redoModel = ArrayDeque<Expected>()

            for (step in 0 until opsPerSeed) {
                val where = "seed=$seed step=$step doc=${seed % EditingStress.START_DOCS.size}"
                when (rng.nextInt(10)) {
                    0, 1 -> undoOnce(editor, rng, undoModel, redoModel, "$where undo")
                    2 -> redoOnce(editor, rng, undoModel, redoModel, "$where redo")
                    3 -> typingBurst(editor, rng, undoModel, redoModel, "$where burst")
                    else -> editOnce(editor, rng, undoModel, redoModel, where)
                }
                assertEquals(undoModel.isNotEmpty(), editor.canUndo, "canUndo out of sync at $where")
                assertEquals(redoModel.isNotEmpty(), editor.canRedo, "canRedo out of sync at $where")
            }
        }
    }

    /** One edit from the [EditingStress] mix, recorded the way `recordBefore` records it. */
    private fun editOnce(
        editor: TextKitEditorManager,
        rng: Random,
        undoModel: ArrayDeque<Expected>,
        redoModel: ArrayDeque<Expected>,
        where: String,
    ) {
        val before = editor.toJson()
        val selection = randomSelection(editor, rng)
        val point = editor.captureHistoryPoint(selection)
        val (desc, action) = EditingStress.decideOp(editor, rng)
        try {
            action()
        } catch (t: Throwable) {
            throw AssertionError("threw at $where op '$desc': ${t::class.simpleName}: ${t.message}", t)
        }
        if (editor.toJson() != before) {
            editor.pushHistory(point)
            editor.breakHistoryCoalescing()
            undoModel.push(Expected(before, selection))
            redoModel.clear()
        }
    }

    /**
     * A run of single-character insertions pushed per keystroke under [EditKind.Typing], the way
     * live typing records: the run must coalesce into ONE undo step restoring to before the first
     * keystroke.
     */
    private fun typingBurst(
        editor: TextKitEditorManager,
        rng: Random,
        undoModel: ArrayDeque<Expected>,
        redoModel: ArrayDeque<Expected>,
        where: String,
    ) {
        val before = editor.toJson()
        val firstSelection = randomSelection(editor, rng)
        var at = firstSelection.start
        var selection = firstSelection
        repeat(rng.nextInt(2, 5)) {
            val point = editor.captureHistoryPoint(selection)
            try {
                editor.typeText(at, "x")
            } catch (t: Throwable) {
                throw AssertionError("threw at $where: ${t::class.simpleName}: ${t.message}", t)
            }
            editor.pushHistory(point, EditKind.Typing)
            at++
            selection = TextRange(at)
        }
        editor.breakHistoryCoalescing()
        undoModel.push(Expected(before, firstSelection))
        redoModel.clear()
    }

    private fun undoOnce(
        editor: TextKitEditorManager,
        rng: Random,
        undoModel: ArrayDeque<Expected>,
        redoModel: ArrayDeque<Expected>,
        where: String,
    ) {
        val current = editor.toJson()
        val liveSelection = randomSelection(editor, rng)
        val restoredSelection = editor.undo(liveSelection)
        if (undoModel.isEmpty()) {
            assertNull(restoredSelection, "undo restored something with empty history at $where")
            assertEquals(current, editor.toJson(), "a refused undo still changed the document at $where")
            return
        }
        val expected = undoModel.removeLast()
        assertNotNull(restoredSelection, "undo returned null with ${undoModel.size + 1} steps at $where")
        assertEquals(expected.json, editor.toJson(), "undo did not restore the pre-edit document at $where")
        assertEquals(expected.selection, restoredSelection, "undo did not restore the captured selection at $where")
        redoModel.addLast(Expected(current, liveSelection))
        with(EditingStress) { editor.assertInvariants(where) }
    }

    private fun redoOnce(
        editor: TextKitEditorManager,
        rng: Random,
        undoModel: ArrayDeque<Expected>,
        redoModel: ArrayDeque<Expected>,
        where: String,
    ) {
        val current = editor.toJson()
        val liveSelection = randomSelection(editor, rng)
        val restoredSelection = editor.redo(liveSelection)
        if (redoModel.isEmpty()) {
            assertNull(restoredSelection, "redo restored something with empty redo history at $where")
            assertEquals(current, editor.toJson(), "a refused redo still changed the document at $where")
            return
        }
        val expected = redoModel.removeLast()
        assertNotNull(restoredSelection, "redo returned null with ${redoModel.size + 1} steps at $where")
        assertEquals(expected.json, editor.toJson(), "redo did not restore the undone document at $where")
        assertEquals(expected.selection, restoredSelection, "redo did not restore the selection undo was called with at $where")
        // The manager's redo re-adds to the undo stack WITHOUT the record() cap trim, so the
        // model must not trim here either or the stacks drift apart past the limit.
        undoModel.addLast(Expected(current, liveSelection))
        with(EditingStress) { editor.assertInvariants(where) }
    }

    /** Pushes an undo step onto the model, mirroring the history manager's oldest-dropped cap. */
    private fun ArrayDeque<Expected>.push(expected: Expected) {
        addLast(expected)
        while (size > HISTORY_LIMIT) removeFirst()
    }

    private fun randomSelection(editor: TextKitEditorManager, rng: Random): TextRange {
        val len = editor.text.length
        if (len == 0) return TextRange.Zero
        val a = rng.nextInt(len + 1)
        return if (rng.nextBoolean()) TextRange(a) else TextRange(a, rng.nextInt(a, len + 1))
    }
}
