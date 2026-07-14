package com.jjrodcast.textkit

import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.models.createTextKitConfiguration
import com.jjrodcast.textkit.ui.state.TextKitState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LinkPopupSelectionTest {

    private fun stateWith(json: String): TextKitState =
        TextKitState(json, false, createTextKitConfiguration()).apply { setup() }

    @Test
    fun openLinkEditor_opensForSelectedText() {
        val state = stateWith(SampleDocuments.SINGLE_PARAGRAPH)
        // text == "Hello world"; select "world" (6..11)
        state.onTextFieldChange(state.textFieldValue.copy(selection = TextRange(6, 11)))

        assertEquals(TextRange(6, 11), state.textFieldValue.selection)

        state.openLinkEditorForSelection()

        val link = state.activeLink
        assertNotNull(link, "activeLink should be set after opening the editor for a selection")
        assertEquals("world", link.text)
        assertEquals("", link.url)
        assertEquals(TextRange(6, 11), link.range)
    }

    @Test
    fun openLinkEditor_collapsedCaret_usesWordUnderCaret() {
        val state = stateWith(SampleDocuments.SINGLE_PARAGRAPH)
        // caret inside "Hello" (index 2)
        state.onTextFieldChange(state.textFieldValue.copy(selection = TextRange(2)))

        state.openLinkEditorForSelection()

        val link = state.activeLink
        assertNotNull(link, "collapsed caret inside a word should open the editor for that word")
        assertEquals("Hello", link.text)
        assertEquals(TextRange(0, 5), link.range)
    }

    @Test
    fun openLinkEditor_staysOpenWhenSelectionCollapsesWithinRange() {
        val state = stateWith(SampleDocuments.SINGLE_PARAGRAPH)
        state.onTextFieldChange(state.textFieldValue.copy(selection = TextRange(6, 11)))
        state.openLinkEditorForSelection()
        assertNotNull(state.activeLink)

        // Editor loses focus to the popup's URL field → it reports a collapsed selection inside the
        // range the popup was opened for. The popup must NOT close.
        state.onTextFieldChange(state.textFieldValue.copy(selection = TextRange(11)))

        assertNotNull(state.activeLink, "popup should stay open while the caret is within its range")
    }

    @Test
    fun openLinkEditor_closesWhenSelectionMovesOffRange() {
        val state = stateWith(SampleDocuments.SINGLE_PARAGRAPH)
        state.onTextFieldChange(state.textFieldValue.copy(selection = TextRange(6, 11)))
        state.openLinkEditorForSelection()
        assertNotNull(state.activeLink)

        // Caret genuinely moves off the opened range → the popup dismisses.
        state.onTextFieldChange(state.textFieldValue.copy(selection = TextRange(2)))

        assertNull(state.activeLink)
    }

    @Test
    fun openLinkEditor_prefillsExistingLinkUrl() {
        val state = stateWith(SampleDocuments.PARAGRAPH_WITH_LINK)
        // text == "visit autodesk now"; select "autodesk" (6..14)
        state.onTextFieldChange(state.textFieldValue.copy(selection = TextRange(6, 14)))

        state.openLinkEditorForSelection()

        val link = state.activeLink
        assertNotNull(link)
        assertEquals("autodesk", link.text)
        assertEquals("https://autodesk.com", link.url)
    }
}
