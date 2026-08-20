package com.jjrodcast.textkit

import androidx.compose.ui.text.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The two quote-boundary gestures (#126): Enter on an empty quoted line exits the quote, and
 * Backspace at the start of a quote unwraps its first line instead of pulling the previous line
 * into the quote. Both are modeled at the transaction entry point, so they hold for any caller
 * that types the gesture — and deliberately NOT for the multiline paste replay.
 */
class BlockquoteGesturesTest {

    private val doc = """{"type":"doc","content":[
      {"type":"paragraph","content":[{"type":"text","text":"one"}]},
      {"type":"blockquote","content":[{"type":"paragraph","content":[{"type":"text","text":"quoted"}]}]},
      {"type":"paragraph","content":[{"type":"text","text":"after"}]}
    ]}"""

    @Test
    fun enter_on_an_empty_quoted_line_exits_the_quote() {
        val e = editorFrom(doc)
        val endOfQuote = e.text.indexOf("quoted") + 6
        val first = e.typeText(endOfQuote, "\n")
        assertTrue(e.isBlockquote(TextRange(first.start, first.start + 1)))

        val textBefore = e.text
        val second = e.typeText(first.start, "\n")
        // The break is swallowed: the empty line turns plain instead of extending the quote.
        assertEquals(textBefore, e.text)
        assertEquals(first.start, second.start)
        assertFalse(e.isBlockquote(TextRange(second.start, second.start + 1)))

        val saved = e.toJson()
        assertEquals(1, Regex("\"type\":\"blockquote\"").findAll(saved).count(), saved)
        assertTrue(saved.contains("quoted"))
        assertEquals(saved, editorFrom(saved).toJson())
    }

    @Test
    fun backspace_at_quote_start_unwraps_before_merging() {
        val e = editorFrom(doc)
        val quoteStart = e.text.indexOf("quoted")
        val textBefore = e.text

        val caret = e.deleteText(quoteStart - 1, 1)
        // First backspace: nothing is deleted, the quote's first line steps out.
        assertEquals(textBefore, e.text)
        assertEquals(quoteStart, caret.start)
        assertFalse(e.isBlockquote(TextRange(quoteStart, quoteStart + 1)))
        assertFalse(e.toJson().contains("blockquote"))

        // Second backspace: an ordinary plain-with-plain merge.
        e.deleteText(quoteStart - 1, 1)
        assertEquals("onequoted\nafter", e.text)
        assertFalse(e.toJson().contains("blockquote"))
        assertEquals(e.toJson(), editorFrom(e.toJson()).toJson())
    }

    @Test
    fun backspace_between_two_quoted_lines_still_merges() {
        val e = editorFrom(
            """{"type":"doc","content":[
              {"type":"blockquote","content":[
                {"type":"paragraph","content":[{"type":"text","text":"first"}]},
                {"type":"paragraph","content":[{"type":"text","text":"second"}]}
              ]}
            ]}"""
        )
        val secondStart = e.text.indexOf("second")
        e.deleteText(secondStart - 1, 1)
        assertEquals("firstsecond", e.text)
        assertTrue(e.isBlockquote(TextRange(0, e.text.length)))
        assertEquals(e.toJson(), editorFrom(e.toJson()).toJson())
    }

    @Test
    fun pasted_blank_lines_inside_a_quote_survive() {
        val e = editorFrom(doc)
        val endOfQuote = e.text.indexOf("quoted") + 6
        val emptyLine = e.typeText(endOfQuote, "\n")

        // A paste replays its breaks with the Enter-exit disabled: the blank line it carries must
        // land as a blank quoted line, not get swallowed as an "exit".
        e.typeText(emptyLine.start, "alpha\n\nbeta")
        assertTrue(e.text.contains("alpha\n\nbeta"))
        val blankAt = e.text.indexOf("alpha") + 6
        assertTrue(e.isBlockquote(TextRange(blankAt, blankAt + 1)))
        assertEquals(e.toJson(), editorFrom(e.toJson()).toJson())
    }

    @Test
    fun undo_restores_the_quote_after_the_enter_exit() {
        val e = editorFrom(doc)
        val endOfQuote = e.text.indexOf("quoted") + 6
        val first = e.typeText(endOfQuote, "\n")
        val before = e.toJson()

        val point = e.captureHistoryPoint(TextRange(first.start))
        e.typeText(first.start, "\n")
        e.pushHistory(point)
        e.undo(TextRange(first.start))
        assertEquals(before, e.toJson())
    }
}
