package com.jjrodcast.textkit

import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.components.TextEditorListItem
import com.jjrodcast.textkit.editor.models.createTextKitConfiguration
import com.jjrodcast.textkit.editor.utils.DocumentUtils
import com.jjrodcast.textkit.ui.state.TextKitState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ListToggleTest {

    private fun stateWith(json: String): TextKitState =
        TextKitState(json, createTextKitConfiguration()).apply { setup() }

    private fun TextKitState.caretAt(offset: Int) =
        onTextFieldChange(textFieldValue.copy(selection = TextRange(offset)))

    private fun TextKitState.selectAll() =
        onTextFieldChange(textFieldValue.copy(selection = TextRange(0, textFieldValue.text.length)))

    private val uniformNumberedList = """
        {"type":"doc","content":[{"type":"orderedList","attrs":{"start":1},"content":[
          {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"aaa"}]}]},
          {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"bbb"}]}]}
        ]}]}
    """

    @Test
    fun applying_a_list_over_full_selection_keeps_whole_document_selected() {
        val state = stateWith(DocumentUtils.complexJsonV2)
        state.selectAll()
        assertTrue(state.toggleOrderedList(selected = true))

        // The selection must cover the whole document, including the freshly inserted decorators —
        // regression: it clamped to the pre-decorator length and stopped short of the new end.
        assertEquals(0, state.textFieldValue.selection.start)
        assertEquals(state.textFieldValue.text.length, state.textFieldValue.selection.end)
    }

    @Test
    fun removing_a_list_over_full_selection_keeps_whole_document_selected() {
        val state = stateWith(uniformNumberedList)
        state.selectAll()
        assertTrue(state.toggleOrderedList(selected = false))

        assertEquals(0, state.textFieldValue.selection.start)
        assertEquals(state.textFieldValue.text.length, state.textFieldValue.selection.end)
    }

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

    @Test
    fun toggling_a_list_on_then_off_over_the_whole_document_does_not_crash() {
        // Regression: over a full-document selection, removing list decorators shifts content left,
        // which drove the restored selection range negative (start + firstItemDiff < 0) and crashed
        // when building the TextRange. The range is now coerced to valid bounds.
        val editor = editorFrom(DocumentUtils.complexJsonV2)

        editor.toListItem(
            TextRange(0, editor.text.length),
            from = TextEditorListItem.None,
            to = TextEditorListItem.NumberedList,
        )
        // Toggle the same type again (removes decorators — the branch from the reported stack trace).
        editor.toListItem(
            TextRange(0, editor.text.length),
            from = TextEditorListItem.NumberedList,
            to = TextEditorListItem.NumberedList,
        )
        // And explicitly back to plain paragraphs.
        editor.toListItem(
            TextRange(0, editor.text.length),
            from = TextEditorListItem.NumberedList,
            to = TextEditorListItem.None,
        )

        // Document is still readable after the round-trip (no crash, valid state).
        assertTrue(editor.getParagraphs().isNotEmpty())
    }
}
