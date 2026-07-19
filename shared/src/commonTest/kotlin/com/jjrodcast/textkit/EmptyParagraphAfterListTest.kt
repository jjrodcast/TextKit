package com.jjrodcast.textkit

import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.components.TextEditorListItem
import com.jjrodcast.textkit.editor.utils.TABS
import kotlin.test.Test
import kotlin.test.assertEquals

class EmptyParagraphAfterListTest {

    // An ordered list, then a genuinely empty paragraph (it survives because content follows it),
    // then a plain text paragraph. Mirrors complexJsonV1's "empty paragraph after the list" case.
    private val listThenEmptyThenText = """
        {"type":"doc","content":[
          {"type":"orderedList","attrs":{"start":1},"content":[
            {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"one"}]}]},
            {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"two"}]}]}
          ]},
          {"type":"paragraph"},
          {"type":"paragraph","content":[{"type":"text","text":"after"}]}
        ]}
    """

    @Test
    fun caretOnEmptyParagraphBetweenListAndText_isNotMarkedAsList() {
        val editor = editorFrom(listThenEmptyThenText)
        // The empty paragraph sits between the "two\n" list item and "after"; its offset is right
        // after the list's last line break.
        val emptyOffset = editor.offsetOf("after") - 1
        assertEquals(
            TextEditorListItem.None,
            editor.getSearchMarkType(TextRange(emptyOffset)).listItem,
            "Empty paragraph after the list must not be marked as a list"
        )
    }

    @Test
    fun caretInsideOrderedListItem_staysMarkedAsList() {
        val editor = editorFrom(listThenEmptyThenText)
        val inside = editor.offsetOf("two")
        assertEquals(
            TextEditorListItem.NumberedList,
            editor.getSearchMarkType(TextRange(inside)).listItem,
            "Caret inside a list item must still report the list type"
        )
    }

    @Test
    fun caretAtStartOfSecondListItem_staysMarkedAsList() {
        val editor = editorFrom(listThenEmptyThenText)
        // Boundary right after the first item's line break and before the second item's marker.
        val startOfSecond = editor.offsetOf("${TABS}2.")
        assertEquals(
            TextEditorListItem.NumberedList,
            editor.getSearchMarkType(TextRange(startOfSecond)).listItem,
            "Caret at the start of a following list item must still report the list type"
        )
    }
}
