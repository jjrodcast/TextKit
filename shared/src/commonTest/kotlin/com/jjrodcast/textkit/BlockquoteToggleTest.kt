package com.jjrodcast.textkit

import androidx.compose.ui.text.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `toggleBlockquote` (#126): paragraph-level like alignment — all-quoted removes, anything else
 * applies; list items and embed placeholders are never quoted in this phase.
 */
class BlockquoteToggleTest {

    private fun editor() = editorFrom(
        """{"type":"doc","content":[
          {"type":"paragraph","content":[{"type":"text","text":"one"}]},
          {"type":"paragraph","content":[{"type":"text","text":"two"}]},
          {"type":"bulletList","content":[
            {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"item"}]}]}
          ]}
        ]}"""
    )

    @Test
    fun applying_over_two_paragraphs_exports_one_quote_node() {
        val e = editor()
        assertTrue(e.toggleBlockquote(TextRange(0, e.text.indexOf("two") + 3)))
        val saved = e.toJson()
        assertEquals(1, Regex("\"type\":\"blockquote\"").findAll(saved).count(), saved)
        assertTrue(saved.contains("one") && saved.contains("two"))
        assertEquals(saved, editorFrom(saved).toJson())
    }

    @Test
    fun toggling_twice_restores_the_original_export() {
        val e = editor()
        val before = e.toJson()
        val range = TextRange(0, e.text.indexOf("two") + 3)
        assertTrue(e.toggleBlockquote(range))
        assertTrue(e.toggleBlockquote(range))
        assertEquals(before, e.toJson())
    }

    @Test
    fun a_collapsed_caret_quotes_its_paragraph_only() {
        val e = editor()
        assertTrue(e.toggleBlockquote(TextRange(1)))
        val saved = e.toJson()
        assertTrue(e.isBlockquote(TextRange(1)))
        // Mid-paragraph probe: a collapsed offset AT a paragraph start resolves to the paragraph
        // ending there, the same boundary convention marks and alignment follow.
        assertFalse(e.isBlockquote(TextRange(e.text.indexOf("two") + 1)))
        assertEquals(saved, editorFrom(saved).toJson())
    }

    @Test
    fun list_items_are_never_quoted() {
        val e = editor()
        val itemAt = e.text.indexOf("item")
        assertFalse(e.toggleBlockquote(TextRange(itemAt, itemAt + 4)))
        assertFalse(e.isBlockquote(TextRange(itemAt)))
        assertTrue(!e.toJson().contains("blockquote"))
    }

    @Test
    fun undo_restores_the_unquoted_document() {
        val e = editor()
        val before = e.toJson()
        val point = e.captureHistoryPoint(TextRange(0))
        assertTrue(e.toggleBlockquote(TextRange(0, 3)))
        e.pushHistory(point)
        e.undo(TextRange(0))
        assertEquals(before, e.toJson())
    }
}
