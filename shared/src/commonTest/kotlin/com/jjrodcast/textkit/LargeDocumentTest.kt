package com.jjrodcast.textkit

import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.components.TextEditorStyleItem
import com.jjrodcast.textkit.editor.core.parser.BoldMark
import com.jjrodcast.textkit.editor.utils.DocumentUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Exercises the bundled real-world sample documents from [DocumentUtils]. These are large,
 * deeply-nested documents (nested numbered/bulleted lists, task lists, mixed inline marks,
 * blockquotes) and are the best fixtures for catching regressions on non-trivial content.
 */
class LargeDocumentTest {

    // All bundled samples load — including V2 (`"fontSize": null`) and V6 (missing `fontSize`),
    // which are handled by the config-default resolution (see TextStyleDefaultsTest).
    private val samples: List<Pair<String, String>> = listOf(
        "V1" to DocumentUtils.complexJsonV1,
        "V2" to DocumentUtils.complexJsonV2,
        "V3" to DocumentUtils.complexJsonV3,
        "V4" to DocumentUtils.complexJsonV4,
        "V5" to DocumentUtils.complexJsonV5,
        "V6" to DocumentUtils.complexJsonV6,
    )

    // Subset whose serialization is stable. V3 and V6 are excluded — see
    // [PotentialBugsTest.sample_v3_reserialization_is_not_idempotent] and
    // [PotentialBugsTest.sample_v6_reserialization_is_not_idempotent].
    private val idempotentSamples: List<Pair<String, String>> = listOf(
        "V1" to DocumentUtils.complexJsonV1,
        "V2" to DocumentUtils.complexJsonV2,
        "V4" to DocumentUtils.complexJsonV4,
        "V5" to DocumentUtils.complexJsonV5,
    )

    @Test
    fun every_sample_loads_into_non_empty_text_and_paragraphs() {
        samples.forEach { (name, json) ->
            val editor = editorFrom(json)
            assertTrue(editor.text.isNotEmpty(), "$name should produce non-empty text")
            assertTrue(editor.getParagraphs().isNotEmpty(), "$name should produce paragraphs")
        }
    }

    @Test
    fun paragraph_offsets_tile_the_text_stream_without_gaps() {
        samples.forEach { (name, json) ->
            val editor = editorFrom(json)
            var cursor = 0
            editor.getParagraphs().flatMap { it.children }.forEach { item ->
                assertEquals(cursor, item.start, "$name: piece '${item.text.take(10)}' offset mismatch")
                assertEquals(item.start + item.text.length, item.end, "$name: piece end mismatch")
                cursor = item.end
            }
            assertEquals(editor.text.length, cursor, "$name: offsets must cover the whole stream")
        }
    }

    @Test
    fun toJson_is_idempotent_across_a_second_round_trip() {
        idempotentSamples.forEach { (name, json) ->
            val once = editorFrom(json).toJson()
            val twice = editorFrom(once).toJson()
            assertEquals(once, twice, "$name: serialization should be stable")
        }
    }

    @Test
    fun stress_append_many_words_keeps_length_invariant() {
        samples.forEach { (name, json) ->
            val editor = editorFrom(json)
            var expectedLength = editor.text.length

            repeat(100) { i ->
                val chunk = "z$i "
                editor.typeText(offset = editor.text.length, textToAdd = chunk)
                expectedLength += chunk.length
                assertEquals(expectedLength, editor.text.length, "$name: length drifted at append #$i")
            }
            assertTrue(editor.text.endsWith("z99 "))
        }
    }

    @Test
    fun stress_append_then_bulk_delete_is_lossless() {
        samples.forEach { (name, json) ->
            val editor = editorFrom(json)
            val original = editor.text

            // Append at the end (always plain text — never inside a decorator).
            repeat(20) {
                editor.typeText(offset = editor.text.length, textToAdd = "###")
            }
            assertEquals(original.length + 60, editor.text.length, "$name: insert accounting is off")

            // Delete the whole appended block in one go.
            editor.deleteText(offset = original.length, length = 60)
            assertEquals(original, editor.text, "$name: insert/delete cycle should be lossless")
        }
    }

    @Test
    fun applying_bold_across_a_range_in_a_large_document_does_not_corrupt_text() {
        val editor = editorFrom(DocumentUtils.complexJsonV1)
        val original = editor.text

        // Bold a window fully inside the first plain-text paragraph.
        val range = TextRange(0, 10)
        editor.applyStyle(range, TextEditorStyleItem.Bold)

        // Formatting must never change the text content, only the marks.
        assertEquals(original, editor.text)
        assertTrue(editor.marksAt(range).has<BoldMark>())
    }

    @Test
    fun repeated_full_document_reserialization_never_throws() {
        samples.forEach { (name, json) ->
            var current = json
            repeat(5) {
                current = editorFrom(current).toJson()
                assertTrue(current.isNotEmpty(), "$name: reserialization produced empty output")
            }
        }
    }
}
