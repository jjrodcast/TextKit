package com.jjrodcast.textkit

import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.components.TextEditorStyleItem
import com.jjrodcast.textkit.editor.core.parser.BoldMark
import com.jjrodcast.textkit.editor.core.parser.HighlightMark
import com.jjrodcast.textkit.editor.core.parser.ItalicMark
import com.jjrodcast.textkit.editor.core.parser.StrikeMark
import com.jjrodcast.textkit.editor.core.parser.TextStyleMark
import com.jjrodcast.textkit.editor.core.parser.UnderlineMark
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Marks (bold/italic/underline/strike/highlight), colors and links applied over a selection. */
class FormattingTest {

    @Test
    fun applies_and_removes_bold() {
        val editor = editorFrom(SampleDocuments.SINGLE_PARAGRAPH)
        val range = TextRange(0, 5) // "Hello"

        assertTrue(editor.applyStyle(range, TextEditorStyleItem.Bold))
        assertTrue(editor.marksAt(range).has<BoldMark>())

        val existing = editor.marksAt(range)
        assertTrue(editor.removeStyle(range, existing, TextEditorStyleItem.Bold))
        assertFalse(editor.marksAt(range).has<BoldMark>())
    }

    @Test
    fun applies_each_inline_style() {
        val cases = listOf(
            TextEditorStyleItem.Italic to { m: Set<com.jjrodcast.textkit.editor.core.parser.Mark> -> m.has<ItalicMark>() },
            TextEditorStyleItem.Underline to { m: Set<com.jjrodcast.textkit.editor.core.parser.Mark> -> m.has<UnderlineMark>() },
            TextEditorStyleItem.Strikethrough to { m: Set<com.jjrodcast.textkit.editor.core.parser.Mark> -> m.has<StrikeMark>() },
            TextEditorStyleItem.Highlight to { m: Set<com.jjrodcast.textkit.editor.core.parser.Mark> -> m.has<HighlightMark>() },
        )
        cases.forEach { (style, check) ->
            val editor = editorFrom(SampleDocuments.SINGLE_PARAGRAPH)
            val range = TextRange(0, 5)
            assertTrue(editor.applyStyle(range, style), "applying $style should report a change")
            assertTrue(check(editor.marksAt(range)), "$style should be present after applying it")
        }
    }

    @Test
    fun applies_multiple_styles_at_once() {
        val editor = editorFrom(SampleDocuments.SINGLE_PARAGRAPH)
        val range = TextRange(0, 5)

        editor.applyStyle(range, TextEditorStyleItem.Bold, TextEditorStyleItem.Italic)

        val marks = editor.marksAt(range)
        assertTrue(marks.has<BoldMark>())
        assertTrue(marks.has<ItalicMark>())
    }

    @Test
    fun format_change_is_ignored_for_a_collapsed_selection() {
        val editor = editorFrom(SampleDocuments.SINGLE_PARAGRAPH)

        // A collapsed (empty) selection cannot receive a format mark.
        assertFalse(editor.applyStyle(TextRange(3, 3), TextEditorStyleItem.Bold))
    }

    @Test
    fun sets_a_color_as_a_text_style_mark() {
        val editor = editorFrom(SampleDocuments.SINGLE_PARAGRAPH)
        val range = TextRange(0, 5)

        assertTrue(editor.setColor(range, "#FF0000"))
        assertTrue(editor.marksAt(range).has<TextStyleMark>())
        assertTrue(editor.toJson().contains("FF0000", ignoreCase = true))
        // Applying a color must not change the text content.
        assertEquals("Hello world", editor.text)
    }

    @Test
    fun reads_back_a_link_and_its_range() {
        val editor = editorFrom(SampleDocuments.PARAGRAPH_WITH_LINK)
        val linkRange = editor.rangeOf("autodesk")

        val (href, range) = editor.getLink(linkRange.start, linkRange.end)

        assertEquals("https://autodesk.com", href)
        assertEquals(linkRange, range)
    }

    @Test
    fun no_link_reported_over_plain_text() {
        val editor = editorFrom(SampleDocuments.PARAGRAPH_WITH_LINK)

        val (href, _) = editor.getLink(0, 4) // "visi"
        assertNull(href)
    }

    @Test
    fun adds_a_link_over_plain_text() {
        val editor = editorFrom(SampleDocuments.SINGLE_PARAGRAPH)
        val range = editor.rangeOf("world")

        assertTrue(editor.setLink(range, "https://example.com"))

        val (href, _) = editor.getLink(range.start, range.end)
        assertEquals("https://example.com", href)
    }

    @Test
    fun getSearchMarkType_reports_the_selected_text() {
        val editor = editorFrom(SampleDocuments.SINGLE_PARAGRAPH)
        val range = editor.rangeOf("Hello")

        val result = editor.getSearchMarkType(range)
        assertEquals("Hello", result.text)
        assertFalse(result.isEmpty)
    }
}
