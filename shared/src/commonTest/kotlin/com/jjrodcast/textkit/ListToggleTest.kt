package com.jjrodcast.textkit

import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.components.TextEditorListItem
import com.jjrodcast.textkit.editor.models.createTextKitConfiguration
import com.jjrodcast.textkit.ui.state.TextKitState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ListToggleTest {

    private fun stateWith(json: String): TextKitState =
        TextKitState(json, false, createTextKitConfiguration()).apply { setup() }

    private fun TextKitState.caretAt(offset: Int) =
        onTextFieldChange(textFieldValue.copy(selection = TextRange(offset)))

    @Test
    fun toggleUnorderedList_collapsedCaret_convertsParagraph() {
        val state = stateWith(SampleDocuments.SINGLE_PARAGRAPH)
        state.caretAt(3)

        val changed = state.toggleUnorderedList(selected = true)

        assertTrue(changed)
        assertEquals(TextEditorListItem.BulletedList, state.lastListItem)
    }

    @Test
    fun toggleOrderedList_collapsedCaret_convertsParagraph() {
        val state = stateWith(SampleDocuments.SINGLE_PARAGRAPH)
        state.caretAt(3)

        val changed = state.toggleOrderedList(selected = true)

        assertTrue(changed)
        assertEquals(TextEditorListItem.NumberedList, state.lastListItem)
    }

    @Test
    fun toggleUnorderedList_off_removesList() {
        val state = stateWith(SampleDocuments.SINGLE_PARAGRAPH)
        state.caretAt(3)
        state.toggleUnorderedList(selected = true)
        assertEquals(TextEditorListItem.BulletedList, state.lastListItem)

        val changed = state.toggleUnorderedList(selected = false)

        assertTrue(changed)
        assertEquals(TextEditorListItem.None, state.lastListItem)
    }

    @Test
    fun toggleOrderedList_whileBulleted_switchesKind() {
        val state = stateWith(SampleDocuments.SINGLE_PARAGRAPH)
        state.caretAt(3)
        state.toggleUnorderedList(selected = true)
        assertEquals(TextEditorListItem.BulletedList, state.lastListItem)

        val changed = state.toggleOrderedList(selected = true)

        assertTrue(changed)
        assertEquals(TextEditorListItem.NumberedList, state.lastListItem)
    }

    @Test
    fun toggleOrderedList_alreadyPlain_isNoop() {
        val state = stateWith(SampleDocuments.SINGLE_PARAGRAPH)
        state.caretAt(3)

        // selected = false with a plain paragraph -> target None == current None -> no change
        val changed = state.toggleOrderedList(selected = false)

        assertFalse(changed)
        assertEquals(TextEditorListItem.None, state.lastListItem)
    }
}
