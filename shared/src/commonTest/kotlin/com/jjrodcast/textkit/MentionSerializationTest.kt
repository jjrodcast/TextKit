package com.jjrodcast.textkit

import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.components.TextEditorStyleItem
import com.jjrodcast.textkit.editor.core.parser.BoldMark
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers the `mention` inline node: it must load into the flat stream as `@<label>`, survive editing
 * as an atomic unit, and serialize back to exactly `{"type":"mention","attrs":{"id":…,"label":…}}`
 * — with no extra attrs (notably no trigger char) leaking in.
 */
class MentionSerializationTest {

    private fun docWithMention(
        lead: String = "Hi ",
        id: String = "111",
        label: String = "Jorge Rodriguez",
        trail: String = " bye",
    ) = buildString {
        append("""{"type":"doc","content":[{"type":"paragraph","content":[""")
        if (lead.isNotEmpty()) append("""{"type":"text","text":"$lead"},""")
        append("""{"type":"mention","attrs":{"id":"$id","label":"$label"}}""")
        if (trail.isNotEmpty()) append(""",{"type":"text","text":"$trail"}""")
        append("""]}]}""")
    }

    @Test
    fun mention_loads_as_at_prefixed_label_in_flat_text() {
        val editor = editorFrom(docWithMention())
        assertTrue(
            editor.text.contains("Hi @Jorge Rodriguez bye"),
            "Expected flat text to contain '@Jorge Rodriguez', was: \"${editor.text}\""
        )
    }

    @Test
    fun mention_round_trips_with_only_id_and_label_attrs() {
        val json = editorFrom(docWithMention()).toJson()
        assertTrue(json.contains("\"type\":\"mention\""), "missing mention node: $json")
        assertTrue(
            json.contains("\"attrs\":{\"id\":\"111\",\"label\":\"Jorge Rodriguez\"}"),
            "attrs not preserved exactly: $json"
        )
        assertFalse(
            json.contains("mentionSuggestionChar"),
            "trigger char leaked into serialized attrs: $json"
        )
    }

    @Test
    fun mention_at_start_of_paragraph_round_trips() {
        val json = editorFrom(docWithMention(lead = "", trail = " hello")).toJson()
        assertTrue(json.contains("\"attrs\":{\"id\":\"111\",\"label\":\"Jorge Rodriguez\"}"), json)
    }

    @Test
    fun mention_at_end_of_paragraph_round_trips() {
        val json = editorFrom(docWithMention(lead = "hello ", trail = "")).toJson()
        assertTrue(json.contains("\"attrs\":{\"id\":\"111\",\"label\":\"Jorge Rodriguez\"}"), json)
    }

    @Test
    fun mention_with_empty_label_round_trips() {
        val json = editorFrom(docWithMention(label = "")).toJson()
        assertTrue(json.contains("\"type\":\"mention\""), json)
        assertTrue(json.contains("\"id\":\"111\""), json)
    }

    @Test
    fun mention_survives_typing_after_it() {
        val editor = editorFrom(docWithMention())
        // Type at the very end of the paragraph text.
        editor.typeText(editor.text.trimEnd('\n').length, "!")
        val json = editor.toJson()
        assertTrue(
            json.contains("\"attrs\":{\"id\":\"111\",\"label\":\"Jorge Rodriguez\"}"),
            "mention corrupted after typing: $json"
        )
    }

    @Test
    fun insert_mention_replaces_query_and_serializes_node() {
        val editor = editorFrom(
            """{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"Hi @jo bye"}]}]}"""
        )
        val range = editor.rangeOf("@jo")
        editor.insertMention(id = "111", label = "Jorge Rodriguez", replaceRange = range)

        assertTrue(editor.text.contains("Hi @Jorge Rodriguez bye"), editor.text)
        val json = editor.toJson()
        assertTrue(json.contains("\"type\":\"mention\""), json)
        assertTrue(json.contains("\"attrs\":{\"id\":\"111\",\"label\":\"Jorge Rodriguez\"}"), json)
    }

    @Test
    fun mention_marks_round_trip() {
        val json = """
            {"type":"doc","content":[{"type":"paragraph","content":[
              {"type":"text","text":"Hi "},
              {"type":"mention","attrs":{"id":"111","label":"Jorge Rodriguez"},"marks":[{"type":"bold"}]},
              {"type":"text","text":" bye"}
            ]}]}
        """
        val out = editorFrom(json).toJson()
        assertTrue(out.contains("\"type\":\"mention\""), out)
        assertTrue(out.contains("\"attrs\":{\"id\":\"111\",\"label\":\"Jorge Rodriguez\"}"), out)
        // The bold mark on the mention survives the round-trip.
        val mentionNode = out.substringAfter("\"type\":\"mention\"")
        assertTrue(mentionNode.contains("\"type\":\"bold\""), "bold mark lost on mention: $out")
    }

    @Test
    fun applying_a_mark_over_a_mention_keeps_it_a_mention() {
        val editor = editorFrom(docWithMention())
        val start = editor.offsetOf("@Jorge Rodriguez")
        val range = TextRange(start, start + "@Jorge Rodriguez".length)
        assertTrue(editor.applyStyle(range, TextEditorStyleItem.Bold))

        val json = editor.toJson()
        assertTrue(json.contains("\"type\":\"mention\""), "mention corrupted by formatting: $json")
        assertTrue(json.contains("\"attrs\":{\"id\":\"111\",\"label\":\"Jorge Rodriguez\"}"), json)
        val mentionNode = json.substringAfter("\"type\":\"mention\"")
        assertTrue(mentionNode.contains("\"type\":\"bold\""), "bold not applied to mention: $json")
    }

    @Test
    fun formatting_a_multi_piece_selection_spanning_a_mention_keeps_it_intact() {
        // A non-collapsed selection that spans surrounding text AND a mention must apply the mark to
        // the whole range without folding the atomic mention into a neighbor (regression: the multi-
        // piece formatter merged the mention with its neighbor and dropped its text).
        val editor = editorFrom(
            """{"type":"doc","content":[{"type":"paragraph","content":[
              {"type":"text","text":"Hey "},
              {"type":"mention","attrs":{"id":"1","label":"Jorge"}},
              {"type":"text","text":" welcome friend"}
            ]}]}"""
        )
        val text = editor.text.trimEnd('\n')
        assertTrue(editor.applyStyle(TextRange(0, text.length), TextEditorStyleItem.Bold))

        // Mention survives — its visible text and node are intact.
        assertTrue(editor.text.contains("@Jorge"), "mention text lost: \"${editor.text}\"")
        val json = editor.toJson()
        assertTrue(json.contains("\"type\":\"mention\""), "mention corrupted: $json")
        assertTrue(json.contains("\"attrs\":{\"id\":\"1\",\"label\":\"Jorge\"}"), json)

        // The mark reaches the plain-text pieces around the mention.
        val items = editor.getParagraphs().flatMap { it.children }
        val surrounding = items.filter { !it.isMention && it.text.isNotBlank() }
        assertTrue(
            surrounding.isNotEmpty() && surrounding.all { c -> c.marks.any { it is BoldMark } },
            "bold not applied around mention: ${items.map { it.text to it.marks }}"
        )
    }

    @Test
    fun toggling_a_mark_on_a_mention_between_plain_neighbors_does_not_crash() {
        // Regression: removing a mark from a mention whose neighbors already match the target marks
        // routed through the piece merge and fed a left-spanning offset into the central-piece
        // transaction, producing a negative buffer offset (crash) and swallowing the neighbor text.
        val editor = editorFrom(docWithMention(lead = "Hey ", label = "Jorge", trail = " welcome"))

        repeat(3) {
            editor.applyStyle(editor.rangeOf("@Jorge"), TextEditorStyleItem.Bold)
            val range = editor.rangeOf("@Jorge")
            editor.removeStyle(range, editor.marksAt(range), TextEditorStyleItem.Bold)
        }

        // Text and node survive every cycle, and no bold lingers on the mention.
        assertTrue(editor.text.contains("Hey @Jorge welcome"), "text corrupted: \"${editor.text}\"")
        val json = editor.toJson()
        assertTrue(json.contains("\"type\":\"mention\""), json)
        assertTrue(json.contains("\"attrs\":{\"id\":\"111\",\"label\":\"Jorge\"}"), json)
        assertFalse(editor.marksAt(editor.rangeOf("@Jorge")).any { it is BoldMark }, "bold not removed: $json")
    }

    @Test
    fun deleting_mention_range_removes_it_entirely() {
        val editor = editorFrom(docWithMention())
        val start = editor.offsetOf("@Jorge Rodriguez")
        editor.deleteRange(TextRange(start, start + "@Jorge Rodriguez".length))

        assertFalse(editor.toJson().contains("\"type\":\"mention\""), editor.toJson())
        assertEquals(false, editor.text.contains("@Jorge Rodriguez"))
    }
}
