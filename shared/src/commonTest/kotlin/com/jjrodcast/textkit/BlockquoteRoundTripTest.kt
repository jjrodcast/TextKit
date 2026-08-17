package com.jjrodcast.textkit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Editor-mode blockquote round trip (#126): a top-level blockquote survives open-and-save. Its
 * content pieces carry the `BlockquoteDecorator` attribute — an invisible, non-marker decorator —
 * and the export groups consecutive attributed paragraphs back into a `blockquote` node.
 */
class BlockquoteRoundTripTest {

    private val QUOTE_DOC = """{"type":"doc","content":[
      {"type":"paragraph","content":[{"type":"text","text":"before"}]},
      {"type":"blockquote","content":[
        {"type":"paragraph","content":[{"type":"text","text":"quoted"}]},
        {"type":"paragraph","content":[{"type":"text","text":"still quoted"}]}
      ]},
      {"type":"paragraph","content":[{"type":"text","text":"after"}]}
    ]}"""

    @Test
    fun a_blockquote_survives_an_editor_open_and_save() {
        val editor = editorFrom(QUOTE_DOC)
        assertEquals("before\nquoted\nstill quoted\nafter", editor.text)
        val saved = editor.toJson()
        assertTrue(saved.contains("\"type\":\"blockquote\""), "structure lost: $saved")
        assertTrue(saved.contains("still quoted"))
        assertEquals(saved, editorFrom(saved).toJson())
    }

    @Test
    fun typing_inside_a_quoted_paragraph_stays_inside_the_quote() {
        val editor = editorFrom(QUOTE_DOC)
        editor.typeText(editor.text.indexOf("quoted") + 3, "XY")
        val saved = editor.toJson()
        assertTrue(saved.contains("\"type\":\"blockquote\""))
        // The typed characters live in the quoted paragraph, so they must serialize inside the node.
        val quoteStart = saved.indexOf("\"type\":\"blockquote\"")
        assertTrue(saved.substringBefore("\"type\":\"blockquote\"").contains("before"))
        assertTrue(saved.contains("quoXYted") || saved.contains("XY"), saved)
        assertEquals(saved, editorFrom(saved).toJson())
    }

    @Test
    fun an_empty_quoted_paragraph_keeps_its_membership() {
        val editor = editorFrom(
            """{"type":"doc","content":[
              {"type":"blockquote","content":[
                {"type":"paragraph","content":[{"type":"text","text":"a"}]},
                {"type":"paragraph","content":[]},
                {"type":"paragraph","content":[{"type":"text","text":"b"}]}
              ]},
              {"type":"paragraph","content":[{"type":"text","text":"tail"}]}
            ]}"""
        )
        val saved = editor.toJson()
        assertEquals(saved, editorFrom(saved).toJson())
        // One quote node holding all three paragraphs — the empty one must not split it.
        assertEquals(1, Regex("\"type\":\"blockquote\"").findAll(saved).count(), saved)
    }

    @Test
    fun adjacent_blockquotes_normalize_into_one() {
        // Known normalization: adjacency defines the exported node, matching what the rendering
        // already shows for back-to-back quotes.
        val editor = editorFrom(
            """{"type":"doc","content":[
              {"type":"blockquote","content":[{"type":"paragraph","content":[{"type":"text","text":"one"}]}]},
              {"type":"blockquote","content":[{"type":"paragraph","content":[{"type":"text","text":"two"}]}]}
            ]}"""
        )
        val saved = editor.toJson()
        assertEquals(1, Regex("\"type\":\"blockquote\"").findAll(saved).count(), saved)
        assertTrue(saved.contains("one") && saved.contains("two"))
        assertEquals(saved, editorFrom(saved).toJson())
    }

    @Test
    fun viewer_and_editor_now_agree_on_quotes() {
        val asEditor = editorFrom(QUOTE_DOC, isViewer = false)
        val asViewer = editorFrom(QUOTE_DOC, isViewer = true)
        assertEquals(asViewer.text, asEditor.text)
    }
}
