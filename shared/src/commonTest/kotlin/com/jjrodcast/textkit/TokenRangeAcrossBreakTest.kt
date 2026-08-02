package com.jjrodcast.textkit

import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.components.TextEditorListItem
import com.jjrodcast.textkit.editor.core.TextKitEditorManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A token whose replace range reaches past the end of its paragraph removes that paragraph's line
 * break, so the item below moves up into the freed line. The token must then still land inside that
 * item's content: the caret the removal reports sits exactly on the item's marker, and an offset at
 * a paragraph boundary has to resolve to the paragraph that *starts* there, not the one that ends.
 */
class TokenRangeAcrossBreakTest {

    private fun TextKitEditorManager.assertNoMidlineDecorator() {
        getParagraphs().forEachIndexed { i, p ->
            assertTrue(
                p.children.drop(1).none { it.decorator != null },
                "paragraph $i carries a mid-line decorator: ${text.replace("\n", "\\n").replace("\t", "\\t")}",
            )
        }
    }

    /** "a", an empty line, then a numbered item holding "b". */
    private fun editorWithEmptyLineAboveAnItem(): TextKitEditorManager {
        val editor = editorFrom("{}")
        editor.typeText(0, "ab")
        editor.typeText(1, "\n")
        editor.toListItem(TextRange(2, 3), TextEditorListItem.None, TextEditorListItem.NumberedList)
        editor.typeText(1, "\n")
        return editor
    }

    @Test
    fun a_token_replacing_the_line_above_an_item_lands_inside_that_item() {
        val editor = editorWithEmptyLineAboveAnItem()

        // The range covers the empty line's break, so the item below moves up into it.
        editor.insertMention(id = "u1", label = "J", replaceRange = TextRange(2, 3))

        editor.assertNoMidlineDecorator()
        val item = editor.getParagraphs().last()
        assertEquals(1, item.children.count { it.decorator != null }, editor.text.replace("\t", "\\t"))
        assertTrue(editor.text.endsWith("@Jb"), editor.text.replace("\t", "\\t").replace("\n", "\\n"))
        val once = editor.toJson()
        assertEquals(once, editorFrom(once).toJson(), "export is not a fixed point")
    }

    @Test
    fun a_hashtag_replacing_the_line_above_an_item_lands_inside_that_item() {
        val editor = editorWithEmptyLineAboveAnItem()

        editor.insertToken(nodeType = "hashtag", id = "h1", label = "kt", replaceRange = TextRange(2, 3))

        editor.assertNoMidlineDecorator()
        assertTrue(editor.text.endsWith("@ktb"), editor.text.replace("\t", "\\t").replace("\n", "\\n"))
        val once = editor.toJson()
        assertEquals(once, editorFrom(once).toJson(), "export is not a fixed point")
    }
}
