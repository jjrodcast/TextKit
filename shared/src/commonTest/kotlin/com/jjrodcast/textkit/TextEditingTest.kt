package com.jjrodcast.textkit

import androidx.compose.ui.text.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Adding, deleting and replacing text, plus splitting one paragraph into many and merging them
 * back — driven through the same [com.jjrodcast.textkit.editor.core.transactions.text.TextTransaction]
 * path the Compose text field uses.
 */
class TextEditingTest {

    @Test
    fun inserts_text_at_the_beginning() {
        val editor = editorFrom(SampleDocuments.SINGLE_PARAGRAPH)

        editor.typeText(offset = 0, textToAdd = "Say: ")

        assertEquals("Say: Hello world", editor.text)
        assertEquals(1, editor.getParagraphs().size)
    }

    @Test
    fun inserts_text_in_the_middle() {
        val editor = editorFrom(SampleDocuments.SINGLE_PARAGRAPH)

        editor.typeText(offset = editor.offsetOf("world"), textToAdd = "brave ")

        assertEquals("Hello brave world", editor.text)
    }

    @Test
    fun appends_text_at_the_end() {
        val editor = editorFrom(SampleDocuments.SINGLE_PARAGRAPH)

        editor.typeText(offset = editor.text.length, textToAdd = "!")

        assertEquals("Hello world!", editor.text)
    }

    @Test
    fun deletes_a_leading_word() {
        val editor = editorFrom(SampleDocuments.SINGLE_PARAGRAPH)

        editor.deleteText(offset = 0, length = "Hello ".length)

        assertEquals("world", editor.text)
        assertEquals(1, editor.getParagraphs().size)
    }

    @Test
    fun deletes_a_trailing_word() {
        val editor = editorFrom(SampleDocuments.SINGLE_PARAGRAPH)

        editor.deleteText(offset = editor.offsetOf(" world"), length = " world".length)

        assertEquals("Hello", editor.text)
    }

    @Test
    fun replaces_a_word() {
        val editor = editorFrom(SampleDocuments.SINGLE_PARAGRAPH)
        val range = editor.rangeOf("world")

        editor.replaceText(offset = range.start, removeLength = range.length, textToAdd = "there")

        assertEquals("Hello there", editor.text)
    }

    @Test
    fun typing_a_line_break_splits_one_paragraph_into_two() {
        val editor = editorFrom(SampleDocuments.SINGLE_PARAGRAPH)

        editor.typeText(offset = editor.offsetOf("world"), textToAdd = "\n")

        assertEquals("Hello \nworld", editor.text)
        assertEquals(2, editor.getParagraphs().size)
    }

    @Test
    fun deleting_the_line_break_merges_two_paragraphs_into_one() {
        val editor = editorFrom(SampleDocuments.TWO_PARAGRAPHS)
        val lineBreak = editor.offsetOf("\n")
        assertTrue(lineBreak > 0)

        editor.deleteText(offset = lineBreak, length = 1)

        assertEquals("First paragraphSecond paragraph", editor.text)
        assertEquals(1, editor.getParagraphs().size)
    }

    @Test
    fun splitting_then_merging_restores_the_original_text() {
        val editor = editorFrom(SampleDocuments.SINGLE_PARAGRAPH)
        val at = editor.offsetOf("world")

        editor.typeText(offset = at, textToAdd = "\n")
        assertEquals(2, editor.getParagraphs().size)

        editor.deleteText(offset = at, length = 1)
        assertEquals("Hello world", editor.text)
        assertEquals(1, editor.getParagraphs().size)
    }

    @Test
    fun many_sequential_insertions_keep_the_text_consistent() {
        val editor = editorFrom(DocumentUtils_emptyText())

        val expected = StringBuilder()
        repeat(50) { i ->
            val chunk = "w$i "
            editor.typeText(offset = editor.text.length, textToAdd = chunk)
            expected.append(chunk)
        }

        assertEquals(expected.toString(), editor.text)
    }

    @Test
    fun insert_then_delete_the_same_span_is_a_no_op() {
        val editor = editorFrom(SampleDocuments.THREE_PARAGRAPHS)
        val original = editor.text

        editor.typeText(offset = 5, textToAdd = "XXXX")
        assertEquals(original.length + 4, editor.text.length)

        editor.deleteText(offset = 5, length = 4)
        assertEquals(original, editor.text)
        assertEquals(3, editor.getParagraphs().size)
    }

    @Test
    fun editing_inside_a_bold_run_keeps_the_surrounding_bold_mark() {
        val editor = editorFrom(SampleDocuments.PARAGRAPH_WITH_BOLD)
        val bold = editor.rangeOf("bolded")

        // Insert plain text in the middle of the bold word.
        editor.typeText(offset = bold.start + 3, textToAdd = "XY")

        assertTrue(editor.text.contains("bolXYded"))
        // The characters that were bold before the edit are still bold.
        val stillBold = editor.marksAt(TextRange(bold.start, bold.start + 3))
        assertTrue(stillBold.has<com.jjrodcast.textkit.editor.core.parser.BoldMark>())
    }

    // The empty document is the simplest way to start from a blank editor.
    private fun DocumentUtils_emptyText() = com.jjrodcast.textkit.editor.utils.DocumentUtils.emptyDocument
}
