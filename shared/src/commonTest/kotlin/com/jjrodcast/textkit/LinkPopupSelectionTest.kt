package com.jjrodcast.textkit

import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.models.createTextKitConfiguration
import com.jjrodcast.textkit.ui.state.TextKitState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LinkPopupSelectionTest {

    private fun stateWith(json: String): TextKitState =
        TextKitState(json, false, createTextKitConfiguration()).apply { setup() }

    /** "visit test now" with the word "test" (offsets 6..10) linked to https://test.com. */
    private val paragraphWithLink = """
        {"type":"doc","content":[
          {"type":"paragraph","content":[
            {"type":"text","text":"visit "},
            {"type":"text","marks":[{"type":"link","attrs":{"href":"https://test.com","target":"_blank"}}],"text":"test"},
            {"type":"text","text":" now"}
          ]}
        ]}
    """

    @Test
    fun openLinkEditor_opensForSelectedText() {
        val state = stateWith(SampleDocuments.SINGLE_PARAGRAPH)
        // text == "Hello world"; select "world" (6..11)
        state.onTextFieldChange(state.textFieldValue.copy(selection = TextRange(6, 11)))

        assertEquals(TextRange(6, 11), state.textFieldValue.selection)

        state.applyLink()

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

        state.applyLink()

        val link = state.activeLink
        assertNotNull(link, "collapsed caret inside a word should open the editor for that word")
        assertEquals("Hello", link.text)
        assertEquals(TextRange(0, 5), link.range)
    }

    @Test
    fun openLinkEditor_staysOpenWhenSelectionCollapsesWithinRange() {
        val state = stateWith(SampleDocuments.SINGLE_PARAGRAPH)
        state.onTextFieldChange(state.textFieldValue.copy(selection = TextRange(6, 11)))
        state.applyLink()
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
        state.applyLink()
        assertNotNull(state.activeLink)

        // Caret genuinely moves off the opened range → the popup dismisses.
        state.onTextFieldChange(state.textFieldValue.copy(selection = TextRange(2)))

        assertNull(state.activeLink)
    }

    @Test
    fun openLinkEditor_prefillsExistingLinkUrl() {
        val state = stateWith(paragraphWithLink)
        // text == "visit test now"; select "test" (6..10)
        state.onTextFieldChange(state.textFieldValue.copy(selection = TextRange(6, 10)))

        state.applyLink()

        val link = state.activeLink
        assertNotNull(link)
        assertEquals("test", link.text)
        assertEquals("https://test.com", link.url)
    }

    @Test
    fun caretOnLink_collapsed_opensPopup() {
        val state = stateWith(paragraphWithLink)
        // collapsed caret inside "test" (6..10)
        state.onTextFieldChange(state.textFieldValue.copy(selection = TextRange(8)))

        val link = state.activeLink
        assertNotNull(link, "a collapsed caret on a link should auto-open the popup")
        assertEquals("https://test.com", link.url)
    }

    @Test
    fun selectionOverLink_doesNotOpenPopup() {
        val state = stateWith(paragraphWithLink)
        // select the whole "test" link (non-collapsed)
        state.onTextFieldChange(state.textFieldValue.copy(selection = TextRange(6, 10)))

        assertNull(state.activeLink, "a selection spanning a link must not auto-open the popup")
    }

    @Test
    fun updateLinkText_leavesCollapsedCaretAtEndAndClosesPopup() {
        val state = stateWith(SampleDocuments.SINGLE_PARAGRAPH)
        state.onTextFieldChange(state.textFieldValue.copy(selection = TextRange(6, 11)))
        state.applyLink()
        assertNotNull(state.activeLink)

        val applied = state.updateLinkText(newText = "world", url = "https://x.com", range = TextRange(6, 11))

        assertTrue(applied)
        assertTrue(state.textFieldValue.selection.collapsed, "caret should be collapsed after editing")
        assertEquals(11, state.textFieldValue.selection.start)
        assertNull(state.activeLink, "popup should close after committing the edit")
    }

    @Test
    fun updateLinkText_longerText_collapsesCaretAtNewEnd() {
        val state = stateWith(SampleDocuments.SINGLE_PARAGRAPH)
        state.onTextFieldChange(state.textFieldValue.copy(selection = TextRange(6, 11)))
        state.applyLink()

        val applied = state.updateLinkText(newText = "internet", url = "https://x.com", range = TextRange(6, 11))

        assertTrue(applied)
        assertTrue(state.textFieldValue.selection.collapsed)
        // "Hello " + "internet" -> caret at 6 + 8 = 14
        assertEquals(14, state.textFieldValue.selection.start)
        assertNull(state.activeLink)
    }

    @Test
    fun removeLink_leavesCollapsedCaretAndClosesPopup() {
        val state = stateWith(paragraphWithLink)
        state.onTextFieldChange(state.textFieldValue.copy(selection = TextRange(6, 10)))
        state.applyLink()
        assertNotNull(state.activeLink)

        val removed = state.removeLink(TextRange(6, 10))

        assertTrue(removed)
        assertTrue(state.textFieldValue.selection.collapsed, "caret should be collapsed after removing")
        assertEquals(10, state.textFieldValue.selection.start)
        assertNull(state.activeLink, "popup should close after removing the link")
    }
}
