package com.jjrodcast.textkit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Loading a document for editing must not lose blockquote content (#115). Top-level quotes now
 * round-trip whole (#126, see `BlockquoteRoundTripTest`); a quote nested inside a list item still
 * degrades to its item's paragraphs — the list machinery has no representation for it — but the
 * text always survives.
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
    fun a_blockquote_inside_a_list_item_keeps_its_text() {
        // The nested case is the treacherous one: an un-unwrapped blockquote here still reaches
        // the piece table and RENDERS, but the export drops it — content the user can see that
        // never saves.
        val editor = editorFrom(
            """{"type":"doc","content":[
              {"type":"bulletList","content":[
                {"type":"listItem","content":[
                  {"type":"paragraph","content":[{"type":"text","text":"item"}]},
                  {"type":"blockquote","content":[
                    {"type":"paragraph","content":[{"type":"text","text":"nested quote"}]}
                  ]}
                ]}
              ]}
            ]}"""
        )
        assertTrue(editor.text.contains("nested quote"))
        val saved = editor.toJson()
        assertTrue(saved.contains("nested quote"), "quoted text rendered but lost on save: $saved")
        assertEquals(saved, editorFrom(saved).toJson())
    }

    @Test
    fun a_blockquote_inside_a_task_item_keeps_its_text() {
        val editor = editorFrom(
            """{"type":"doc","content":[
              {"type":"taskList","content":[
                {"type":"taskItem","attrs":{"checked":false},"content":[
                  {"type":"paragraph","content":[{"type":"text","text":"task"}]},
                  {"type":"blockquote","content":[
                    {"type":"paragraph","content":[{"type":"text","text":"quoted note"}]}
                  ]}
                ]}
              ]}
            ]}"""
        )
        val saved = editor.toJson()
        assertTrue(saved.contains("quoted note"), "quoted text rendered but lost on save: $saved")
        assertEquals(saved, editorFrom(saved).toJson())
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
