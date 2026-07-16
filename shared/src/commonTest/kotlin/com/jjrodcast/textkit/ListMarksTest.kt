package com.jjrodcast.textkit

import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.core.parser.BoldMark
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Marks reported for a selection spanning several list-item paragraphs. The list decorator pieces
 * (bullets/numbers/checkboxes) carry no marks and must not dilute the "common marks" computation, so
 * the format toolbar reflects marks shared by the whole selected range.
 */
class ListMarksTest {

    private fun boldList(type: String, allBold: Boolean = true): String {
        val secondMarks = if (allBold) """"marks":[{"type":"bold"}],""" else ""
        return """
            {"type":"doc","content":[
              {"type":"$type","content":[
                {"type":"listItem","content":[{"type":"paragraph","content":[
                  {"type":"text","marks":[{"type":"bold"}],"text":"one"}]}]},
                {"type":"listItem","content":[{"type":"paragraph","content":[
                  {"type":"text",$secondMarks"text":"two"}]}]}
              ]}
            ]}
        """
    }

    private fun assertCommonBoldAcrossWholeDoc(json: String): Set<com.jjrodcast.textkit.editor.core.parser.Mark> {
        val editor = editorFrom(json)
        return editor.marksAt(TextRange(0, editor.text.length))
    }

    @Test
    fun bold_common_across_bulleted_list_selection() {
        val marks = assertCommonBoldAcrossWholeDoc(boldList("bulletList"))
        assertTrue(marks.has<BoldMark>(), "bold should be common across the bulleted list; marks=$marks")
    }

    @Test
    fun bold_common_across_ordered_list_selection() {
        val marks = assertCommonBoldAcrossWholeDoc(boldList("orderedList"))
        assertTrue(marks.has<BoldMark>(), "bold should be common across the ordered list; marks=$marks")
    }

    @Test
    fun bold_common_across_task_list_selection() {
        val json = """
            {"type":"doc","content":[
              {"type":"taskList","content":[
                {"type":"taskItem","attrs":{"checked":false},"content":[{"type":"paragraph","content":[
                  {"type":"text","marks":[{"type":"bold"}],"text":"one"}]}]},
                {"type":"taskItem","attrs":{"checked":true},"content":[{"type":"paragraph","content":[
                  {"type":"text","marks":[{"type":"bold"}],"text":"two"}]}]}
              ]}
            ]}
        """
        val marks = assertCommonBoldAcrossWholeDoc(json)
        assertTrue(marks.has<BoldMark>(), "bold should be common across the task list; marks=$marks")
    }

    @Test
    fun bold_not_common_when_one_list_item_is_not_bold() {
        val marks = assertCommonBoldAcrossWholeDoc(boldList("bulletList", allBold = false))
        assertFalse(marks.has<BoldMark>(), "bold must not be reported when an item lacks it; marks=$marks")
    }
}
