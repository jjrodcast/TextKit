package com.jjrodcast.textkit

import androidx.compose.ui.text.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `deleteRange` is the programmatic delete used by the token/slash-command flows
 * (`TextKitTokenState.commitCommand`, `deletePartialToken`). It used to call the piece table's raw
 * delete, bypassing the decorator-aware removal path that typed deletes and `removeEmbedAt` go
 * through — so a range that swallowed a paragraph's trailing line break merged the following list
 * item into the line and stranded its decorator mid-line (the corruption class of #67/#74/#82).
 */
class DeleteRangeAcrossParagraphsTest {

    @Test
    fun a_range_over_the_trailing_break_keeps_the_next_item_marker_at_line_start() {
        val editor = editorFrom(
            """{"type":"doc","content":[
              {"type":"paragraph","content":[{"type":"text","text":"ab"}]},
              {"type":"bulletList","content":[
                {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"x"}]}]}
              ]}
            ]}"""
        )
        // Remove "b" plus the paragraph's line break — the window a widened token deletion produces.
        editor.deleteRange(TextRange(1, 3))
        editor.getParagraphs().forEach { paragraph ->
            assertTrue(
                paragraph.children.drop(1).none { it.decorator != null },
                "decorator stranded mid-line: ${editor.text.replace("\n", "\\n").replace("\t", "\\t")}",
            )
        }
        val json = editor.toJson()
        assertEquals(json, editorFrom(json).toJson())
    }

    @Test
    fun a_collapsed_range_is_a_no_op() {
        val editor = editorFrom(
            """{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"ab"}]}]}"""
        )
        val before = editor.toJson()
        assertEquals(TextRange(1), editor.deleteRange(TextRange(1, 1)))
        assertEquals(before, editor.toJson())
    }
}
