package com.jjrodcast.textkit

import com.jjrodcast.textkit.editor.utils.TABS
import com.jjrodcast.textkit.editor.utils.TASK_DECORATOR_UNCHECKED_INTERACTIVE
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Converting a line by typing a list pattern must consume exactly the pattern characters the
 * document already holds (#138): the update's delete length was derived from the new marker's
 * tab prefix, which matches the typed characters only for a one-character trigger at level one —
 * an insert carrying the whole pattern (a paste) found nothing to consume and instead swallowed
 * the line's terminating break and sheared the first character off the next item's marker. The
 * renumber pass also lost the items after the caret when the converted line was empty, because
 * the collapsed-caret window resolves backward there; it now derives from the converted
 * paragraph's own span.
 */
class PatternInsertOnEmptyLineTest {

    private val doc = """{"type":"doc","content":[
      {"type":"orderedList","attrs":{"start":1},"content":[
        {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"First"}]}]}
      ]},
      {"type":"paragraph","content":[]},
      {"type":"orderedList","attrs":{"start":1},"content":[
        {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"Second"}]}]}
      ]}
    ]}"""

    // Marker strings are platform-relative: the tab unit and the task glyph differ per target.
    private val healed = "${TABS}1. First\n${TABS}2. \n${TABS}3. Second"

    @Test
    fun a_single_insert_of_the_whole_pattern_converts_without_shearing() {
        val e = editorFrom(doc)
        val emptyLine = e.text.indexOf('\n') + 1
        e.typeText(emptyLine, "3. ")
        assertEquals(healed, e.text)
        assertEquals(e.toJson(), editorFrom(e.toJson()).toJson())
    }

    @Test
    fun typing_the_pattern_char_by_char_matches_the_single_insert() {
        val e = editorFrom(doc)
        var c = e.typeText(e.text.indexOf('\n') + 1, "3").end
        c = e.typeText(c, ".").end
        e.typeText(c, " ")
        assertEquals(healed, e.text)
        assertEquals(e.toJson(), editorFrom(e.toJson()).toJson())
    }

    @Test
    fun a_task_pattern_insert_above_an_item_converts_without_shearing() {
        val e = editorFrom(doc)
        val emptyLine = e.text.indexOf('\n') + 1
        e.typeText(emptyLine, "-[] ")
        val saved = e.toJson()
        assertEquals("${TABS}1. First\n$TABS$TASK_DECORATOR_UNCHECKED_INTERACTIVE\n${TABS}1. Second", e.text)
        assertEquals(saved, editorFrom(saved).toJson())
    }

    @Test
    fun typing_the_pattern_at_the_document_end_still_converts() {
        // Typed character by character: the space triggers with "3." already in the document, the
        // case the old delete-length arithmetic was tuned to — it must keep working.
        val e = editorFrom("""{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"plain"}]},{"type":"paragraph","content":[]}]}""")
        var c = e.typeText(e.text.length, "3").end
        c = e.typeText(c, ".").end
        e.typeText(c, " ")
        assertEquals("plain\n${TABS}3. ", e.text)
        assertEquals(e.toJson(), editorFrom(e.toJson()).toJson())
    }
}
