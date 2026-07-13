package com.jjrodcast.textkit

import com.jjrodcast.textkit.editor.core.piecetable.models.TextDecoratorModel
import com.jjrodcast.textkit.editor.utils.DocumentUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Loading a ProseMirror document into the piece table, reading it back as plain text / paragraphs,
 * and serializing it again.
 */
class DocumentLoadingTest {

    @Test
    fun loads_single_paragraph_as_plain_text() {
        val editor = editorFrom(SampleDocuments.SINGLE_PARAGRAPH)

        assertEquals("Hello world", editor.text)
        assertEquals(1, editor.getParagraphs().size)
        assertEquals(1, editor.getParagraphs().first().children.size)
    }

    @Test
    fun paragraphs_are_delimited_by_trailing_line_breaks() {
        val editor = editorFrom(SampleDocuments.TWO_PARAGRAPHS)

        assertEquals("First paragraph\nSecond paragraph", editor.text)
        val paragraphs = editor.getParagraphs()
        assertEquals(2, paragraphs.size)
        // Every paragraph except the last one ends with a line break in the flat stream.
        assertTrue(paragraphs[0].children.last().text.endsWith("\n"))
        assertFalse(paragraphs[1].children.last().text.endsWith("\n"))
    }

    @Test
    fun three_paragraphs_keep_order_and_offsets_are_contiguous() {
        val editor = editorFrom(SampleDocuments.THREE_PARAGRAPHS)

        assertEquals("Alpha\nBeta\nGamma", editor.text)
        val flat = editor.getParagraphs().flatMap { it.children }
        // start/end offsets must tile the text stream with no gaps or overlaps.
        var cursor = 0
        flat.forEach { item ->
            assertEquals(cursor, item.start, "item '${item.text}' should start where the previous ended")
            assertEquals(item.start + item.text.length, item.end)
            cursor = item.end
        }
        assertEquals(editor.text.length, cursor)
    }

    @Test
    fun preserves_inline_marks_from_the_document() {
        val editor = editorFrom(SampleDocuments.PARAGRAPH_WITH_BOLD)

        assertEquals("normal bolded tail", editor.text)
        val marks = editor.marksAt(editor.rangeOf("bolded"))
        assertTrue(marks.has<com.jjrodcast.textkit.editor.core.parser.BoldMark>())
    }

    @Test
    fun empty_document_produces_empty_text() {
        val editor = editorFrom(DocumentUtils.emptyDocument)

        assertEquals("", editor.text)
        // An empty document still exposes a single (empty) paragraph so a cursor has somewhere to live.
        assertEquals(1, editor.getParagraphs().size)
    }

    @Test
    fun editor_mode_strips_blockquotes_but_viewer_mode_keeps_them() {
        val asEditor = editorFrom(SampleDocuments.BLOCKQUOTE, isViewer = false)
        assertEquals("before\nafter", asEditor.text)
        assertFalse(asEditor.isViewer)

        val asViewer = editorFrom(SampleDocuments.BLOCKQUOTE, isViewer = true)
        assertEquals("before\nquoted text\nafter", asViewer.text)
        assertTrue(asViewer.isViewer)
        // The blockquote paragraph carries a Blockquote decorator when kept.
        val quoted = asViewer.getParagraphs().first { p -> p.children.any { it.text.contains("quoted") } }
        assertTrue(quoted.children.any { it.decorator is TextDecoratorModel.BlockquoteDecorator })
    }

    @Test
    fun toJson_roundtrip_is_idempotent_for_small_documents() {
        listOf(
            SampleDocuments.SINGLE_PARAGRAPH,
            SampleDocuments.TWO_PARAGRAPHS,
            SampleDocuments.PARAGRAPH_WITH_BOLD,
            SampleDocuments.PARAGRAPH_WITH_LINK,
            SampleDocuments.ORDERED_LIST,
            SampleDocuments.TASK_LIST,
        ).forEach { doc ->
            val once = editorFrom(doc).toJson()
            val twice = editorFrom(once).toJson()
            assertEquals(once, twice, "toJson should be stable across a load/serialize round-trip")
        }
    }

    @Test
    fun empty_document_serializes_back_to_empty_json() {
        assertEquals("{}", editorFrom(DocumentUtils.emptyDocument).toJson())
    }
}
