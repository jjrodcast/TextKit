package com.jjrodcast.textkit

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.core.TextKitEditorManager
import com.jjrodcast.textkit.editor.core.parser.BoldMark
import com.jjrodcast.textkit.editor.core.parser.TextStyleMark
import com.jjrodcast.textkit.editor.models.createTextKitConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * How `textStyle` marks are resolved against the [createTextKitConfiguration] defaults, both when
 * loading a document and when clearing a color from the UI.
 *
 * The configuration below uses a distinctive default color and font size so the tests can tell a
 * "came from the document" value apart from a "fell back to the configuration" value.
 */
class TextStyleDefaultsTest {

    private val defaultColorHex = "#1b75d0"
    private val defaultFontSize = 22

    private fun editor(json: String): TextKitEditorManager {
        val configuration = createTextKitConfiguration {
            textColor { Color(0xFF1B75D0) }
            fontSize { defaultFontSize }
        }
        return TextKitEditorManager(configuration).apply { load(json, isViewer = false) }
    }

    private fun docWithMark(markJson: String) = """
        {"type":"doc","content":[
          {"type":"paragraph","content":[
            {"type":"text","marks":[$markJson],"text":"styled"}
          ]}
        ]}
    """

    private fun TextKitEditorManager.textStyleOf(needle: String): TextStyleMark? =
        marksAt(rangeOf(needle)).filterIsInstance<TextStyleMark>().firstOrNull()

    // Case 1 -----------------------------------------------------------------

    @Test
    fun a_mark_without_text_style_does_not_gain_one() {
        val editor = editor(docWithMark("""{"type":"bold"}"""))

        val marks = editor.marksAt(editor.rangeOf("styled"))
        assertTrue(marks.has<BoldMark>())
        assertFalse(marks.has<TextStyleMark>(), "no textStyle in the document => none in the piece table")
    }

    // Case 2 -----------------------------------------------------------------

    @Test
    fun text_style_with_only_color_gets_the_default_font_size() {
        val editor = editor(docWithMark("""{"type":"textStyle","attrs":{"color":"#ff0000"}}"""))

        val style = assertNotNull(editor.textStyleOf("styled"))
        assertEquals("#ff0000", style.attrs.color)
        assertEquals(defaultFontSize, style.attrs.fontSize)
    }

    @Test
    fun text_style_with_null_font_size_gets_the_default_font_size() {
        val editor = editor(docWithMark("""{"type":"textStyle","attrs":{"color":"#ff0000","fontSize":null}}"""))

        val style = assertNotNull(editor.textStyleOf("styled"))
        assertEquals("#ff0000", style.attrs.color)
        assertEquals(defaultFontSize, style.attrs.fontSize)
    }

    // Case 3 -----------------------------------------------------------------

    @Test
    fun text_style_with_only_font_size_gets_the_default_color() {
        val editor = editor(docWithMark("""{"type":"textStyle","attrs":{"fontSize":40}}"""))

        val style = assertNotNull(editor.textStyleOf("styled"))
        assertEquals(defaultColorHex, style.attrs.color)
        assertEquals(40, style.attrs.fontSize)
    }

    @Test
    fun text_style_with_both_values_keeps_them() {
        val editor = editor(docWithMark("""{"type":"textStyle","attrs":{"color":"#0a0b0c","fontSize":33}}"""))

        val style = assertNotNull(editor.textStyleOf("styled"))
        assertEquals("#0a0b0c", style.attrs.color)
        assertEquals(33, style.attrs.fontSize)
    }

    // Case 4 -----------------------------------------------------------------

    @Test
    fun clearing_the_color_keeps_the_font_size_and_uses_the_default_color() {
        val editor = editor(docWithMark("""{"type":"textStyle","attrs":{"color":"#ff0000","fontSize":30}}"""))
        val range = editor.rangeOf("styled")

        // Sanity: the loaded color is the document one.
        assertEquals("#ff0000", editor.textStyleOf("styled")!!.attrs.color)

        assertTrue(editor.setColor(range, null))

        val style = assertNotNull(editor.marksAt(range).filterIsInstance<TextStyleMark>().firstOrNull())
        assertEquals(defaultColorHex, style.attrs.color, "removing the color falls back to the config color")
        assertEquals(30, style.attrs.fontSize, "the current font size is preserved")
    }

    @Test
    fun setting_a_color_keeps_the_current_font_size() {
        val editor = editor(docWithMark("""{"type":"textStyle","attrs":{"color":"#ff0000","fontSize":30}}"""))
        val range = editor.rangeOf("styled")

        assertTrue(editor.setColor(range, "#00ff00"))

        val style = assertNotNull(editor.marksAt(range).filterIsInstance<TextStyleMark>().firstOrNull())
        assertEquals("#00ff00", style.attrs.color)
        assertEquals(30, style.attrs.fontSize)
    }

    @Test
    fun clearing_the_color_on_plain_text_uses_both_config_defaults() {
        // "world" has no marks at all; removing color should still yield the default text style.
        val editor = editor(
            """{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"hello world"}]}]}"""
        )
        val range = editor.rangeOf("world")

        editor.setColor(range, null)

        val style = assertNotNull(editor.marksAt(range).filterIsInstance<TextStyleMark>().firstOrNull())
        assertEquals(defaultColorHex, style.attrs.color)
        assertEquals(defaultFontSize, style.attrs.fontSize)
    }
}
