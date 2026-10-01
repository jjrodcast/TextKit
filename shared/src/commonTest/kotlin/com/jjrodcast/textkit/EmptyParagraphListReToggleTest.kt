package com.jjrodcast.textkit

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.jjrodcast.textkit.editor.models.createTextKitConfiguration
import com.jjrodcast.textkit.editor.utils.BULLET_DECORATOR_LEVEL_ONE
import com.jjrodcast.textkit.editor.utils.TABS
import com.jjrodcast.textkit.ui.state.TextKitState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Removing a list from an item must shift the caret by the decorator's full flat width (indentation
 * tabs + marker). It only subtracted the marker, leaving the caret past the end of an emptied
 * trailing item, so toggling the list back on inserted out of bounds and crashed with
 * `StringIndexOutOfBoundsException`.
 */
class EmptyParagraphListReToggleTest {

    private fun stateWith(json: String): TextKitState =
        TextKitState(json, createTextKitConfiguration()).apply { setup() }

    private fun TextKitState.type(text: String) {
        val value = textFieldValue
        val at = value.selection.max
        onTextFieldChange(
            TextFieldValue(
                text = value.text.substring(0, at) + text + value.text.substring(at),
                selection = TextRange(at + text.length),
            )
        )
    }

    private fun reToggle(toggle: TextKitState.(Boolean) -> Boolean, listType: String) {
        val state = stateWith("{}")
        state.type("abc")
        state.type("\n")

        assertTrue(state.toggle(true))
        assertTrue(state.toggle(false))
        assertEquals(TextRange(4), state.selection, "caret must return to the emptied paragraph")

        assertTrue(state.toggle(true))
        assertTrue(state.toJson().contains(listType))
    }

    @Test
    fun ordered_list_can_be_re_toggled_on_an_empty_trailing_paragraph() =
        reToggle({ toggleOrderedList(it) }, "orderedList")

    @Test
    fun bullet_list_can_be_re_toggled_on_an_empty_trailing_paragraph() =
        reToggle({ toggleUnorderedList(it) }, "bulletList")

    @Test
    fun removing_a_list_keeps_the_caret_at_the_end_of_the_item_text() {
        val state = stateWith(SampleDocuments.SINGLE_PARAGRAPH)
        state.onTextFieldChange(state.textFieldValue.copy(selection = TextRange(11)))

        assertTrue(state.toggleUnorderedList(true))
        // The decorator's flat width is platform-dependent (`TABS` is shorter on iOS).
        val decoratorWidth = TABS.length + BULLET_DECORATOR_LEVEL_ONE.length
        assertEquals(TextRange(11 + decoratorWidth), state.selection, "caret must stay after the item text")
        assertTrue(state.toggleUnorderedList(false))

        assertEquals(TextRange(11), state.selection)
    }
}
