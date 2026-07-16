package com.jjrodcast.textkit

import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.components.TextEditorListItem
import com.jjrodcast.textkit.editor.core.TextKitEditorManager
import com.jjrodcast.textkit.editor.core.transactions.models.TextEditorSelectedMark
import com.jjrodcast.textkit.editor.core.transactions.models.TextEditorTransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The selection range returned after list-item changes must stay anchored to the selected text
 * despite the decorator (bullet/number/checkbox) length changes shifting every offset. List
 * decorators include indentation tabs, so the shift must account for the full decorator length, and
 * the start must not drift past the beginning of the selection.
 */
class ListRangeTest {

    private val numberedList = """
        {"type":"doc","content":[
          {"type":"orderedList","attrs":{"start":1},"content":[
            {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"aaa"}]}]},
            {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"bbb"}]}]},
            {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"ccc"}]}]}
          ]}
        ]}
    """

    private val plainParagraphs = """
        {"type":"doc","content":[
          {"type":"paragraph","content":[{"type":"text","text":"aaa"}]},
          {"type":"paragraph","content":[{"type":"text","text":"bbb"}]},
          {"type":"paragraph","content":[{"type":"text","text":"ccc"}]}
        ]}
    """

    private fun TextKitEditorManager.changeListOverWholeDoc(
        from: TextEditorListItem,
        to: TextEditorListItem,
    ): TextRange = updateDocument(
        selection = TextRange(0, text.length),
        prevSelectedMark = TextEditorSelectedMark(listItemSelectedValue = from),
        currSelectedMark = TextEditorSelectedMark(listItemSelectedValue = to),
        transactionType = TextEditorTransactionType.Format,
    ).second

    @Test
    fun removing_a_list_over_the_whole_document_returns_the_new_document_range() {
        val editor = editorFrom(numberedList)
        val range = editor.changeListOverWholeDoc(TextEditorListItem.NumberedList, TextEditorListItem.None)

        // Selection stays over the whole (now shorter) document: start at 0, end on the last char.
        assertEquals(0, range.start)
        assertEquals(editor.text.length, range.end)
    }

    @Test
    fun applying_a_list_over_the_whole_document_returns_the_new_document_range() {
        val editor = editorFrom(plainParagraphs)
        val range = editor.changeListOverWholeDoc(TextEditorListItem.None, TextEditorListItem.NumberedList)

        // Selection grows with the inserted decorators: start stays at 0, end on the last char.
        assertEquals(0, range.start)
        assertEquals(editor.text.length, range.end)
    }

    @Test
    fun returned_range_is_always_valid_and_non_negative() {
        val editor = editorFrom(numberedList)
        val range = editor.changeListOverWholeDoc(TextEditorListItem.NumberedList, TextEditorListItem.None)
        assertTrue(range.start >= 0 && range.end >= range.start)
    }
}
