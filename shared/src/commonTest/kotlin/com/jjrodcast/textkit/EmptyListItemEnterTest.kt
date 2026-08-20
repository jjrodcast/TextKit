package com.jjrodcast.textkit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Enter on an EMPTY list item walks the item back out — one level down when nested, out of the
 * list at the top level — and leaves the caret at the item's content start (issue #133: a nested
 * task item's demotion was silently dropped, leaving the caret stranded inside the marker; the
 * caret is now derived from the transactions that were actually emitted).
 */
class EmptyListItemEnterTest {

    private fun show(s: String) = s.replace("\n", "\\n").replace("\t", "\\t")

    /** Caret must sit at the content start of the line it was on — same line, after its marker. */
    private fun assertCaretAtContentStart(e: com.jjrodcast.textkit.editor.core.TextKitEditorManager, caret: Int, lineIndex: Int) {
        val lineStart = e.text.split("\n").take(lineIndex).sumOf { it.length + 1 }
        val paragraph = e.getParagraphs().getOrNull(lineIndex)
        val expected = paragraph?.children?.firstOrNull()
            ?.let { if (it.decorator?.isMarker == true) it.end else lineStart }
            ?: lineStart
        assertEquals(expected, caret, "caret in '${show(e.text)}'")
    }

    @Test
    fun enter_demotes_an_empty_task_item_nested_under_a_bullet() {
        val e = editorFrom(
            """{"type":"doc","content":[
              {"type":"bulletList","content":[
                {"type":"listItem","content":[
                  {"type":"paragraph","content":[{"type":"text","text":"a"}]},
                  {"type":"taskList","content":[
                    {"type":"taskItem","attrs":{"checked":false},"content":[{"type":"paragraph","content":[]}]}
                  ]}
                ]}
              ]}
            ]}"""
        )
        val before = e.text
        val r = e.typeText(e.text.length, "\n")
        assertTrue(e.text != before, "Enter must not be a no-op")
        assertTrue(e.text.length < before.length, "the item demotes — the marker loses a level")
        assertCaretAtContentStart(e, r.start, 1)
        assertEquals(e.toJson(), editorFrom(e.toJson()).toJson())
    }

    @Test
    fun enter_demotes_an_empty_task_item_nested_under_an_ordered_item() {
        val e = editorFrom(
            """{"type":"doc","content":[
              {"type":"orderedList","attrs":{"start":1},"content":[
                {"type":"listItem","content":[
                  {"type":"paragraph","content":[{"type":"text","text":"a"}]},
                  {"type":"taskList","content":[
                    {"type":"taskItem","attrs":{"checked":false},"content":[{"type":"paragraph","content":[]}]}
                  ]}
                ]}
              ]}
            ]}"""
        )
        val before = e.text
        val r = e.typeText(e.text.length, "\n")
        assertTrue(e.text != before, "Enter must not be a no-op")
        assertCaretAtContentStart(e, r.start, 1)
        assertEquals(e.toJson(), editorFrom(e.toJson()).toJson())
    }

    @Test
    fun enter_unwraps_an_empty_bullet_nested_under_a_task_item() {
        val e = editorFrom(
            """{"type":"doc","content":[
              {"type":"taskList","content":[
                {"type":"taskItem","attrs":{"checked":false},"content":[
                  {"type":"paragraph","content":[{"type":"text","text":"a"}]},
                  {"type":"bulletList","content":[
                    {"type":"listItem","content":[{"type":"paragraph","content":[]}]}
                  ]}
                ]}
              ]}
            ]}"""
        )
        val r = e.typeText(e.text.length, "\n")
        assertCaretAtContentStart(e, r.start, 1)
        assertEquals(e.toJson(), editorFrom(e.toJson()).toJson())
    }

    @Test
    fun enter_still_demotes_an_empty_bullet_nested_under_a_bullet() {
        val e = editorFrom(
            """{"type":"doc","content":[
              {"type":"bulletList","content":[
                {"type":"listItem","content":[
                  {"type":"paragraph","content":[{"type":"text","text":"a"}]},
                  {"type":"bulletList","content":[
                    {"type":"listItem","content":[{"type":"paragraph","content":[]}]}
                  ]}
                ]}
              ]}
            ]}"""
        )
        val before = e.text
        val r = e.typeText(e.text.length, "\n")
        assertTrue(e.text.length < before.length)
        assertCaretAtContentStart(e, r.start, 1)
        assertEquals(e.toJson(), editorFrom(e.toJson()).toJson())
    }

    @Test
    fun enter_unwraps_an_empty_top_level_task_item() {
        val e = editorFrom(
            """{"type":"doc","content":[
              {"type":"paragraph","content":[{"type":"text","text":"p"}]},
              {"type":"taskList","content":[
                {"type":"taskItem","attrs":{"checked":false},"content":[{"type":"paragraph","content":[]}]}
              ]}
            ]}"""
        )
        val r = e.typeText(e.text.length, "\n")
        assertTrue(!e.toJson().contains("taskList"), e.toJson())
        assertCaretAtContentStart(e, r.start, 1)
        assertEquals(e.toJson(), editorFrom(e.toJson()).toJson())
    }
}
