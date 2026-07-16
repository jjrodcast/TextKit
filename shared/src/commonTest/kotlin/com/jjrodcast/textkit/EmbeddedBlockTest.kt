package com.jjrodcast.textkit

import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.utils.DocumentUtils
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Embedded blocks (tables/images/documents): a non-renderable block is shown in the editor as a
 * one-line atomic placeholder while the original JSON is kept verbatim and round-trips through
 * [com.jjrodcast.textkit.editor.core.TextKitEditorManager.toJson]. Focused mostly on tables.
 */
class EmbeddedBlockTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun content(docJson: String): JsonArray =
        json.parseToJsonElement(docJson).jsonObject["content"]!!.jsonArray

    private fun JsonElement.type(): String = jsonObject["type"]!!.jsonPrimitive.content

    // A small 2x2 table (headers + one data row), same shape as the user's example.
    private val tableNode = """
        {"type":"table","content":[
          {"type":"tableRow","content":[
            {"type":"tableHeader","attrs":{"colspan":1,"rowspan":1,"colwidth":null},"content":[{"type":"paragraph","content":[{"type":"text","text":"Nombre"}]}]},
            {"type":"tableHeader","attrs":{"colspan":1,"rowspan":1,"colwidth":null},"content":[{"type":"paragraph","content":[{"type":"text","text":"Edad"}]}]}
          ]},
          {"type":"tableRow","content":[
            {"type":"tableCell","attrs":{"colspan":1,"rowspan":1,"colwidth":null},"content":[{"type":"paragraph","content":[{"type":"text","text":"Juan"}]}]},
            {"type":"tableCell","attrs":{"colspan":1,"rowspan":1,"colwidth":null},"content":[{"type":"paragraph","content":[{"type":"text","text":"30"}]}]}
          ]}
        ]}
    """.trimIndent()

    private fun docWith(vararg nodes: String): String =
        """{"type":"doc","content":[${nodes.joinToString(",")}]}"""

    private val paragraph = { text: String -> """{"type":"paragraph","content":[{"type":"text","text":"$text"}]}""" }

    // ── Load / placeholder ────────────────────────────────────────────────────

    @Test
    fun table_loads_as_a_single_placeholder_line() {
        val editor = editorFrom(docWith(tableNode))

        // The editor text shows only the placeholder, not the table contents.
        assertEquals("📊 Tabla 1", editor.text)
        assertFalse(editor.text.contains("Nombre"))

        val items = editor.getParagraphs().flatMap { it.children }
        assertEquals(1, items.size)
        assertTrue(items.first().isEmbed)
    }

    @Test
    fun placeholder_is_its_own_paragraph_between_text() {
        val editor = editorFrom(docWith(paragraph("Before"), tableNode, paragraph("After")))

        assertEquals("Before\n📊 Tabla 1\nAfter", editor.text)
        assertEquals(3, editor.getParagraphs().size)
    }

    // ── Round-trip ─────────────────────────────────────────────────────────────

    @Test
    fun table_round_trips_verbatim_through_toJson() {
        val editor = editorFrom(docWith(tableNode))

        val output = content(editor.toJson())
        assertEquals(1, output.size)
        // The re-emitted block equals the original table node (JsonElement equality ignores key order).
        assertEquals(json.parseToJsonElement(tableNode), output.first())
    }

    @Test
    fun order_is_preserved_across_round_trip() {
        val editor = editorFrom(docWith(paragraph("Before"), tableNode, paragraph("After")))

        val types = content(editor.toJson()).map { it.type() }
        assertEquals(listOf("paragraph", "table", "paragraph"), types)
    }

    @Test
    fun image_and_document_blocks_also_round_trip() {
        val image = """{"type":"image","attrs":{"src":"x.png"}}"""
        val document = """{"type":"document","attrs":{"url":"a.pdf"}}"""
        val editor = editorFrom(docWith(image, document))

        val output = content(editor.toJson())
        assertEquals(listOf("image", "document"), output.map { it.type() })
        assertEquals(json.parseToJsonElement(image), output[0])
    }

    // ── Insert ─────────────────────────────────────────────────────────────────

    @Test
    fun insert_table_into_empty_document() {
        val editor = editorFrom(DocumentUtils.emptyDocument)

        editor.insertEmbed("table", tableNode, label = "📊 Tabla 1", at = TextRange(0))

        assertTrue(editor.text.contains("📊 Tabla 1"))
        val output = content(editor.toJson())
        assertEquals("table", output.single().type())
    }

    @Test
    fun insert_table_mid_paragraph_splits_and_keeps_both_sides() {
        val editor = editorFrom(SampleDocuments.SINGLE_PARAGRAPH) // "Hello world"
        val at = editor.offsetOf("world") // caret right before "world" → "Hello |world"

        editor.insertEmbed("table", tableNode, label = "📊 Tabla 1", at = TextRange(at))

        assertEquals("Hello \n📊 Tabla 1\nworld", editor.text)
        val types = content(editor.toJson()).map { it.type() }
        assertEquals(listOf("paragraph", "table", "paragraph"), types)
    }

    // ── Query / update / remove ─────────────────────────────────────────────────

    @Test
    fun embedAt_returns_the_block_json_and_range() {
        val editor = editorFrom(docWith(tableNode))
        val offset = editor.offsetOf("Tabla")

        val info = editor.embedAt(offset)
        assertNotNull(info)
        assertEquals("table", info.embedType)
        assertTrue(info.rawJson.contains("tableRow"))
    }

    @Test
    fun update_replaces_the_block_json() {
        val editor = editorFrom(docWith(tableNode))
        val info = editor.embedAt(editor.offsetOf("Tabla"))!!

        val newTable = """{"type":"table","content":[{"type":"tableRow","content":[{"type":"tableCell","attrs":{},"content":[{"type":"paragraph","content":[{"type":"text","text":"Solo"}]}]}]}]}"""
        editor.updateEmbedAt(info.range, newTable)

        val output = content(editor.toJson())
        assertEquals(json.parseToJsonElement(newTable), output.single())
        // Visible placeholder is unchanged.
        assertEquals("📊 Tabla 1", editor.text)
    }

    @Test
    fun remove_deletes_the_placeholder_and_the_node() {
        val editor = editorFrom(docWith(paragraph("Before"), tableNode, paragraph("After")))
        val info = editor.embedAt(editor.offsetOf("Tabla"))!!

        editor.removeEmbedAt(info.range)

        assertFalse(editor.text.contains("Tabla"))
        val types = content(editor.toJson()).map { it.type() }
        assertFalse(types.contains("table"))
    }

    @Test
    fun removing_the_only_block_leaves_an_empty_document() {
        val editor = editorFrom(docWith(tableNode))
        val info = editor.embedAt(editor.offsetOf("Tabla"))!!

        editor.removeEmbedAt(info.range)

        assertFalse(editor.text.contains("Tabla"))
    }

    // ── Numbering ────────────────────────────────────────────────────────────────

    @Test
    fun placeholders_are_numbered_per_type() {
        val editor = editorFrom(docWith(tableNode, tableNode))

        assertTrue(editor.text.contains("📊 Tabla 1"))
        assertTrue(editor.text.contains("📊 Tabla 2"))
    }
}
