package com.jjrodcast.textkit

import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.core.TextKitEditorManager
import com.jjrodcast.textkit.editor.models.TextKitTrigger
import com.jjrodcast.textkit.editor.models.createTextKitConfiguration
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Mirror of [MentionSerializationTest] for the `hashtag` inline node: it must load into the flat
 * stream as `#<label>`, survive editing as an atomic unit, and serialize back to exactly
 * `{"type":"hashtag","attrs":{"id":…,"label":…}}` — with no trigger char leaking into attrs.
 */
class HashtagSerializationTest {

    private fun hashtagConfig() = createTextKitConfiguration {
        addTrigger { TextKitTrigger.TextKitMentionTrigger() }
        addTrigger { TextKitTrigger.TextKitHashtagTrigger() }
    }

    private fun editorWithTriggers(json: String): TextKitEditorManager =
        TextKitEditorManager(hashtagConfig()).apply { load(json, isViewer = false) }

    private fun docWithHashtag(
        lead: String = "Hi ",
        id: String = "1",
        label: String = "kotlin",
        trail: String = " bye",
    ) = buildString {
        append("""{"type":"doc","content":[{"type":"paragraph","content":[""")
        if (lead.isNotEmpty()) append("""{"type":"text","text":"$lead"},""")
        append("""{"type":"hashtag","attrs":{"id":"$id","label":"$label"}}""")
        if (trail.isNotEmpty()) append(""",{"type":"text","text":"$trail"}""")
        append("""]}]}""")
    }

    @Test
    fun hashtag_loads_as_hash_prefixed_label_in_flat_text() {
        val editor = editorFrom(docWithHashtag())
        assertTrue(
            editor.text.contains("Hi #kotlin bye"),
            "Expected flat text to contain '#kotlin', was: \"${editor.text}\""
        )
    }

    @Test
    fun hashtag_round_trips_with_only_id_and_label_attrs() {
        val json = editorFrom(docWithHashtag()).toJson()
        assertTrue(json.contains("\"type\":\"hashtag\""), "missing hashtag node: $json")
        assertTrue(
            json.contains("\"attrs\":{\"id\":\"1\",\"label\":\"kotlin\"}"),
            "attrs not preserved exactly: $json"
        )
        assertFalse(json.contains("\"#\""), "trigger char leaked into serialized attrs: $json")
    }

    @Test
    fun hashtag_at_start_of_paragraph_round_trips() {
        val json = editorFrom(docWithHashtag(lead = "", trail = " hello")).toJson()
        assertTrue(json.contains("\"attrs\":{\"id\":\"1\",\"label\":\"kotlin\"}"), json)
    }

    @Test
    fun hashtag_at_end_of_paragraph_round_trips() {
        val json = editorFrom(docWithHashtag(lead = "hello ", trail = "")).toJson()
        assertTrue(json.contains("\"attrs\":{\"id\":\"1\",\"label\":\"kotlin\"}"), json)
    }

    @Test
    fun hashtag_survives_typing_after_it() {
        val editor = editorFrom(docWithHashtag())
        editor.typeText(editor.text.trimEnd('\n').length, "!")
        val json = editor.toJson()
        assertTrue(
            json.contains("\"attrs\":{\"id\":\"1\",\"label\":\"kotlin\"}"),
            "hashtag corrupted after typing: $json"
        )
    }

    @Test
    fun insert_hashtag_via_insert_token_replaces_query_and_serializes_node() {
        val editor = editorWithTriggers(
            """{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"Hi #ko bye"}]}]}"""
        )
        val range = editor.rangeOf("#ko")
        editor.insertToken(nodeType = "hashtag", id = "1", label = "kotlin", replaceRange = range)

        assertTrue(editor.text.contains("Hi #kotlin bye"), editor.text)
        val json = editor.toJson()
        assertTrue(json.contains("\"type\":\"hashtag\""), json)
        assertTrue(json.contains("\"attrs\":{\"id\":\"1\",\"label\":\"kotlin\"}"), json)
    }

    @Test
    fun deleting_hashtag_range_removes_it_entirely() {
        val editor = editorFrom(docWithHashtag())
        val start = editor.offsetOf("#kotlin")
        editor.deleteRange(TextRange(start, start + "#kotlin".length))

        assertFalse(editor.toJson().contains("\"type\":\"hashtag\""), editor.toJson())
        assertFalse(editor.text.contains("#kotlin"))
    }

    @Test
    fun mixed_mention_and_hashtag_document_round_trips_both_nodes() {
        val json = """
            {"type":"doc","content":[{"type":"paragraph","content":[
              {"type":"text","text":"Hi "},
              {"type":"mention","attrs":{"id":"111","label":"Jorge"}},
              {"type":"text","text":" about "},
              {"type":"hashtag","attrs":{"id":"1","label":"kotlin"}},
              {"type":"text","text":" bye"}
            ]}]}
        """
        val editor = editorFrom(json)
        assertTrue(editor.text.contains("Hi @Jorge about #kotlin bye"), editor.text)

        val out = editor.toJson()
        assertTrue(out.contains("\"type\":\"mention\""), "mention lost: $out")
        assertTrue(out.contains("\"type\":\"hashtag\""), "hashtag lost: $out")
        assertTrue(out.contains("\"attrs\":{\"id\":\"111\",\"label\":\"Jorge\"}"), out)
        assertTrue(out.contains("\"attrs\":{\"id\":\"1\",\"label\":\"kotlin\"}"), out)
    }
}
