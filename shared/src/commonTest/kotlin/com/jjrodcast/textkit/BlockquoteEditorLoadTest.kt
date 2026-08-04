package com.jjrodcast.textkit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Loading a document for editing must not lose blockquote content.
 *
 * `loadWith` used to drop blockquote nodes wholesale in editor mode — the node *and* every
 * paragraph inside it — so opening a document with a quote and saving it deleted the quoted text.
 * The editor still cannot edit a blockquote, so the node degrades to its inner paragraphs: the
 * quote structure is lost (as it always was), but the text now survives.
 */
class BlockquoteEditorLoadTest {

    @Test
    fun blockquote_paragraphs_survive_an_editor_load() {
        val editor = editorFrom(
            """{"type":"doc","content":[
              {"type":"paragraph","content":[{"type":"text","text":"before"}]},
              {"type":"blockquote","content":[
                {"type":"paragraph","content":[{"type":"text","text":"quoted"}]},
                {"type":"paragraph","content":[{"type":"text","text":"still quoted"}]}
              ]},
              {"type":"paragraph","content":[{"type":"text","text":"after"}]}
            ]}"""
        )
        assertEquals("before\nquoted\nstill quoted\nafter", editor.text)
    }

    @Test
    fun blockquote_content_survives_an_open_and_save() {
        val editor = editorFrom(
            """{"type":"doc","content":[
              {"type":"blockquote","content":[
                {"type":"paragraph","content":[{"type":"text","text":"quoted"}]}
              ]}
            ]}"""
        )
        val saved = editor.toJson()
        assertTrue(saved.contains("quoted"), "quoted text lost across open-and-save: $saved")
        assertEquals(saved, editorFrom(saved).toJson())
    }

    @Test
    fun nested_blockquotes_unwrap_to_every_inner_paragraph() {
        val editor = editorFrom(
            """{"type":"doc","content":[
              {"type":"blockquote","content":[
                {"type":"paragraph","content":[{"type":"text","text":"outer"}]},
                {"type":"blockquote","content":[
                  {"type":"paragraph","content":[{"type":"text","text":"inner"}]}
                ]}
              ]}
            ]}"""
        )
        assertEquals("outer\ninner", editor.text)
    }

    @Test
    fun a_list_inside_a_blockquote_stays_a_list() {
        val editor = editorFrom(
            """{"type":"doc","content":[
              {"type":"blockquote","content":[
                {"type":"bulletList","content":[
                  {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"item"}]}]}
                ]}
              ]}
            ]}"""
        )
        val json = editor.toJson()
        assertTrue(json.contains("\"type\":\"bulletList\""), "list flattened away: $json")
        assertTrue(json.contains("item"))
    }
}
