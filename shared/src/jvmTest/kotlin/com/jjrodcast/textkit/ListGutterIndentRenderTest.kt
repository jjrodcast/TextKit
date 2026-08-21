package com.jjrodcast.textkit

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.use
import com.jjrodcast.textkit.editor.models.createTextKitConfiguration
import com.jjrodcast.textkit.theme.TextKitTheme
import com.jjrodcast.textkit.ui.TextKitEditor
import com.jjrodcast.textkit.ui.state.TextKitState
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Renders the real editor offscreen and asserts the laid-out geometry of list items (#135).
 *
 * Two regressions pinned here, both Skia-target text behaviors the gutter reservation must not
 * rely on: a paragraph whose display text contains no whitespace never gets its TextIndent
 * applied (a lone word as a list item rendered at the container edge, under the marker), and a
 * first-line TextIndent is invisible to the text's intrinsic width (a content-sized field
 * soft-wrapped its own text mid-word). The display now keeps the marker's trailing space and
 * that character's advance IS the first-line gutter, so the reserved and rendered widths agree.
 * These tests measure the actual TextLayoutResult, so they fail on either raw behavior.
 */
@OptIn(ExperimentalComposeUiApi::class)
class ListGutterIndentRenderTest {

    private fun rendered(state: TextKitState, frames: Int = 3): androidx.compose.ui.text.TextLayoutResult {
        var time = 0L
        ImageComposeScene(width = 1600, height = 200) {
            TextKitTheme { TextKitEditor(state = state) }
        }.use { scene ->
            repeat(frames) { time += 16_000_000; scene.render(time) }
        }
        return state.textLayoutResult ?: error("no layout")
    }

    private fun stateOf(word: String) = TextKitState(
        """{"type":"doc","content":[
          {"type":"orderedList","attrs":{"start":1},"content":[
            {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"$word"}]}]}
          ]}
        ]}""",
        createTextKitConfiguration()
    ).apply { setup() }

    /**
     * The gutter is 2.75em wide and a line box 1.5em tall, while a bare space advance is ~0.25em —
     * so "content starts beyond one line height" separates a working gutter from a regressed one
     * at any font size or density, without a hard-coded pixel threshold.
     */
    private fun assertContentClearsTheGutter(layout: androidx.compose.ui.text.TextLayoutResult, message: String) {
        val lineHeight = layout.getLineBottom(0) - layout.getLineTop(0)
        assertTrue(layout.getHorizontalPosition(1, true) > lineHeight, message)
    }

    @Test
    fun a_single_word_list_item_is_indented_past_its_marker() {
        val layout = rendered(stateOf("loneword"))
        // display position 1 is the item's first content character — past the gutter, not at the edge
        assertContentClearsTheGutter(layout, "the item's text must not sit at the container edge")
    }

    @Test
    fun a_list_item_never_wraps_short_of_its_available_width() {
        // A first-line TextIndent is excluded from the intrinsic width, so a content-sized field
        // used to soft-wrap its own single word mid-word; the kept space's advance is intrinsic.
        assertTrue(rendered(stateOf("loneword")).lineCount == 1, "lone word must stay on one line")
        assertTrue(rendered(stateOf("two words")).lineCount == 1, "spaced text must stay on one line")
    }

    @Test
    fun converting_a_single_word_line_keeps_the_item_indented() {
        val state = TextKitState("{}", createTextKitConfiguration()).apply { setup() }
        fun type(offset: Int, text: String): Int {
            val before = state.textFieldValue
            val inserted = before.text.substring(0, offset) + text + before.text.substring(offset)
            state.onTextFieldChange(TextFieldValue(inserted, TextRange(offset + text.length)))
            return state.textFieldValue.selection.start
        }
        var time = 0L
        ImageComposeScene(width = 700, height = 200) {
            TextKitTheme { TextKitEditor(state = state) }
        }.use { scene ->
            fun frame() { time += 16_000_000; scene.render(time) }
            frame()
            type(0, "loneword")
            frame()
            // the user gesture from the issue: caret to line start, type the list pattern
            var c = type(0, "1")
            c = type(c, ".")
            type(c, " ")
            frame(); frame()
        }
        val layout = state.textLayoutResult ?: error("no layout")
        assertContentClearsTheGutter(layout, "the converted item's text must not sit under the marker")
        assertTrue(layout.lineCount == 1, "the converted item must not wrap")
    }
}
