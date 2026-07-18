package com.jjrodcast.textkit

import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.core.parser.BoldMark
import com.jjrodcast.textkit.editor.models.createTextKitConfiguration
import com.jjrodcast.textkit.ui.state.TextKitState
import kotlin.test.Test
import kotlin.test.assertTrue

class MarkApplyTest {

    private fun stateWith(json: String): TextKitState =
        TextKitState(json, createTextKitConfiguration()).apply { setup() }

    @Test
    fun applyBold_onNonCollapsedSelection_appliesMark() {
        val state = stateWith(SampleDocuments.SINGLE_PARAGRAPH) // "Hello world"
        // select "Hello" (0..5)
        state.onTextFieldChange(state.textFieldValue.copy(selection = TextRange(0, 5)))
        assertTrue(!state.textFieldValue.selection.collapsed, "precondition: selection is not collapsed")

        val applied = state.applyBold(selected = true)

        assertTrue(applied, "applyBold should report the document changed")
        assertTrue(
            state.toJson().contains("\"bold\""),
            "bold mark should be present in the serialized document:\n${state.toJson()}"
        )
        assertTrue(state.lastMarks.any { it is BoldMark }, "caret context should reflect the applied bold")
    }

    @Test
    fun applyBold_afterFocusCollapsedSelection_stillFormatsTheSelection() {
        val state = stateWith(SampleDocuments.SINGLE_PARAGRAPH) // "Hello world"
        // Select "Hello" (0..5)…
        state.onTextFieldChange(state.textFieldValue.copy(selection = TextRange(0, 5)))
        // …then the editor loses focus to the toolbar and re-reports a collapsed caret within it.
        state.onTextFieldChange(state.textFieldValue.copy(selection = TextRange(5)))
        assertTrue(state.textFieldValue.selection.collapsed, "precondition: live selection collapsed")

        val applied = state.applyBold(selected = true)

        assertTrue(applied, "bold should still apply to the remembered selection")
        assertTrue(
            state.toJson().contains("\"bold\""),
            "bold mark should be present:\n${state.toJson()}"
        )
    }

    @Test
    fun applyBold_afterDeliberateCaretOffSelection_doesNotFormatOldRange() {
        val state = stateWith(SampleDocuments.SINGLE_PARAGRAPH) // "Hello world"
        // Select "Hello" (0..5)…
        state.onTextFieldChange(state.textFieldValue.copy(selection = TextRange(0, 5)))
        // …then deliberately place the caret inside "world" (offset 9) — off the old selection.
        state.onTextFieldChange(state.textFieldValue.copy(selection = TextRange(9)))

        val applied = state.applyBold(selected = true)

        // Treated as a collapsed caret: the change is stored, not applied to the stale range.
        assertTrue(applied)
        assertTrue(
            !state.toJson().contains("\"bold\""),
            "bold must NOT be applied to the previous selection:\n${state.toJson()}"
        )
        assertTrue(state.lastMarks.any { it is BoldMark }, "the toggle should be stored for the caret")
    }
}
