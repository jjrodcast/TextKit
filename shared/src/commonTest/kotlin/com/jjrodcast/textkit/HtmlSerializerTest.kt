package com.jjrodcast.textkit

import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.components.TextEditorListItem
import com.jjrodcast.textkit.editor.components.TextEditorStyleItem
import com.jjrodcast.textkit.editor.core.export.HtmlSerializer
import com.jjrodcast.textkit.editor.core.parser.BaseParagraph
import com.jjrodcast.textkit.editor.core.parser.Blockquote
import com.jjrodcast.textkit.editor.core.parser.BoldMark
import com.jjrodcast.textkit.editor.core.parser.BulletedList
import com.jjrodcast.textkit.editor.core.parser.HardBreak
import com.jjrodcast.textkit.editor.core.parser.Hashtag
import com.jjrodcast.textkit.editor.core.parser.Heading
import com.jjrodcast.textkit.editor.core.parser.HeadingAttrs
import com.jjrodcast.textkit.editor.core.parser.HighlightMark
import com.jjrodcast.textkit.editor.core.parser.ItalicMark
import com.jjrodcast.textkit.editor.core.parser.LinkAttrs
import com.jjrodcast.textkit.editor.core.parser.LinkMark
import com.jjrodcast.textkit.editor.core.parser.ListAttrs
import com.jjrodcast.textkit.editor.core.parser.ListItem
import com.jjrodcast.textkit.editor.core.parser.Mark
import com.jjrodcast.textkit.editor.core.parser.Mention
import com.jjrodcast.textkit.editor.core.parser.OrderedList
import com.jjrodcast.textkit.editor.core.parser.Paragraph
import com.jjrodcast.textkit.editor.core.parser.StrikeMark
import com.jjrodcast.textkit.editor.core.parser.TaskList
import com.jjrodcast.textkit.editor.core.parser.TaskListAttrs
import com.jjrodcast.textkit.editor.core.parser.TaskListItem
import com.jjrodcast.textkit.editor.core.parser.Text
import com.jjrodcast.textkit.editor.core.parser.TextEditorDocument
import com.jjrodcast.textkit.editor.core.parser.TextStyleAttrs
import com.jjrodcast.textkit.editor.core.parser.TextStyleMark
import com.jjrodcast.textkit.editor.core.parser.TokenAttrs
import com.jjrodcast.textkit.editor.core.parser.UnderlineMark
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * HTML export.
 *
 * `HtmlSerializer` is a pure function over the parsed document AST, so most cases build a
 * [TextEditorDocument] directly and assert the exact HTML — that covers every node type, including
 * the ones a piece-table round-trip does not reproduce (headings and blockquotes are flattened into
 * styled paragraphs on load; see `PieceTableConverter`). A final group goes through
 * `TextKitEditorManager.toHtml()` to cover the end-to-end path.
 *
 * Mark nesting follows a fixed priority, so exact-string assertions are stable regardless of the
 * iteration order of the underlying mark `Set`.
 */
class HtmlSerializerTest {

    private fun html(vararg blocks: BaseParagraph) =
        HtmlSerializer().serialize(TextEditorDocument(blocks.toList()))

    private fun paragraphOf(text: String, marks: Set<Mark> = emptySet()) =
        Paragraph(listOf(Text(text, marks)))

    // ── Blocks ───────────────────────────────────────────────────────────────

    @Test
    fun exports_a_paragraph() {
        assertEquals("<p>Hello world</p>", html(paragraphOf("Hello world")))
    }

    @Test
    fun exports_each_block_in_document_order() {
        assertEquals(
            "<p>first</p><p>second</p>",
            html(paragraphOf("first"), paragraphOf("second")),
        )
    }

    @Test
    fun exports_an_empty_document_as_an_empty_string() {
        assertEquals("", HtmlSerializer().serialize(TextEditorDocument()))
    }

    @Test
    fun exports_an_empty_paragraph_so_blank_lines_survive() {
        assertEquals("<p></p>", html(Paragraph()))
    }

    @Test
    fun exports_headings_at_their_level() {
        assertEquals(
            "<h1>Title</h1><h3>Section</h3>",
            html(
                Heading(HeadingAttrs(level = 1), listOf(Text("Title"))),
                Heading(HeadingAttrs(level = 3), listOf(Text("Section"))),
            ),
        )
    }

    @Test
    fun clamps_heading_levels_outside_h1_to_h6() {
        assertEquals(
            "<h6>too deep</h6><h1>too shallow</h1>",
            html(
                Heading(HeadingAttrs(level = 9), listOf(Text("too deep"))),
                Heading(HeadingAttrs(level = 0), listOf(Text("too shallow"))),
            ),
        )
    }

    @Test
    fun exports_a_bulleted_list() {
        assertEquals(
            "<ul><li><p>one</p></li><li><p>two</p></li></ul>",
            html(
                BulletedList(
                    listOf(
                        ListItem(listOf(paragraphOf("one"))),
                        ListItem(listOf(paragraphOf("two"))),
                    ),
                ),
            ),
        )
    }

    @Test
    fun omits_the_ordered_list_start_attribute_when_it_begins_at_one() {
        assertEquals(
            "<ol><li><p>one</p></li></ol>",
            html(OrderedList(ListAttrs(start = 1), listOf(ListItem(listOf(paragraphOf("one")))))),
        )
    }

    @Test
    fun emits_the_ordered_list_start_attribute_when_it_does_not_begin_at_one() {
        assertEquals(
            "<ol start=\"3\"><li><p>three</p></li></ol>",
            html(OrderedList(ListAttrs(start = 3), listOf(ListItem(listOf(paragraphOf("three")))))),
        )
    }

    @Test
    fun exports_a_task_list_with_interactive_checkboxes() {
        val output = html(
            TaskList(
                listOf(
                    TaskListItem(TaskListAttrs(checked = false), listOf(paragraphOf("buy milk"))),
                    TaskListItem(TaskListAttrs(checked = true), listOf(paragraphOf("walk dog"))),
                ),
            ),
        )

        assertEquals(
            "<ul data-type=\"taskList\">" +
                "<li data-type=\"taskItem\" data-checked=\"false\">" +
                "<input type=\"checkbox\"><p>buy milk</p></li>" +
                "<li data-type=\"taskItem\" data-checked=\"true\">" +
                "<input type=\"checkbox\" checked><p>walk dog</p></li>" +
                "</ul>",
            output,
        )
        // The checkbox must stay interactive — never rendered disabled.
        assertFalse(output.contains("disabled"))
    }

    @Test
    fun exports_a_blockquote_wrapping_its_blocks() {
        assertEquals(
            "<blockquote><p>quoted</p></blockquote>",
            html(Blockquote(listOf(paragraphOf("quoted")))),
        )
    }

    @Test
    fun exports_a_hard_break() {
        assertEquals(
            "<p>a<br>b</p>",
            html(Paragraph(listOf(Text("a"), HardBreak(), Text("b")))),
        )
    }

    // ── Marks ────────────────────────────────────────────────────────────────

    @Test
    fun exports_each_simple_mark_with_its_semantic_tag() {
        val cases = listOf(
            BoldMark() to "strong",
            ItalicMark() to "em",
            UnderlineMark() to "u",
            StrikeMark() to "s",
            HighlightMark() to "mark",
        )

        cases.forEach { (mark, tag) ->
            assertEquals(
                "<p><$tag>styled</$tag></p>",
                html(paragraphOf("styled", setOf(mark))),
                "mark: ${mark.type}",
            )
        }
    }

    @Test
    fun exports_a_link_with_its_href() {
        assertEquals(
            "<p><a href=\"https://test.com\">visit</a></p>",
            html(paragraphOf("visit", setOf(LinkMark(LinkAttrs("https://test.com"))))),
        )
    }

    @Test
    fun nests_multiple_marks_in_a_deterministic_order() {
        assertEquals(
            "<p><strong><em>both</em></strong></p>",
            html(paragraphOf("both", setOf(ItalicMark(), BoldMark()))),
        )
    }

    @Test
    fun nests_a_link_outside_the_formatting_marks_it_carries() {
        assertEquals(
            "<p><a href=\"https://test.com\"><strong>bold link</strong></a></p>",
            html(
                paragraphOf(
                    "bold link",
                    setOf(BoldMark(), LinkMark(LinkAttrs("https://test.com"))),
                ),
            ),
        )
    }

    @Test
    fun exports_a_text_style_mark_as_an_inline_style() {
        assertEquals(
            "<p><span style=\"color:#FF0000;font-size:18px\">red</span></p>",
            html(
                paragraphOf(
                    "red",
                    setOf(TextStyleMark(TextStyleAttrs(color = "#FF0000", fontSize = 18))),
                ),
            ),
        )
    }

    @Test
    fun omits_a_text_style_mark_that_carries_neither_colour_nor_size() {
        assertEquals(
            "<p>plain</p>",
            html(paragraphOf("plain", setOf(TextStyleMark(TextStyleAttrs(color = "", fontSize = 0))))),
        )
    }

    @Test
    fun exports_only_the_half_of_a_text_style_mark_that_is_set() {
        assertEquals(
            "<p><span style=\"color:#00FF00\">green</span></p>",
            html(paragraphOf("green", setOf(TextStyleMark(TextStyleAttrs(color = "#00FF00", fontSize = 0))))),
        )
    }

    // ── Inline tokens ────────────────────────────────────────────────────────

    @Test
    fun exports_tokens_with_their_label_and_identity() {
        assertEquals(
            "<p>" +
                "<span data-type=\"mention\" data-id=\"1\">@ada</span>" +
                " " +
                "<span data-type=\"hashtag\" data-id=\"2\">#kotlin</span>" +
                "</p>",
            html(
                Paragraph(
                    listOf(
                        Mention(TokenAttrs(id = "1", label = "ada")),
                        Text(" "),
                        Hashtag(TokenAttrs(id = "2", label = "kotlin")),
                    ),
                ),
            ),
        )
    }

    @Test
    fun applies_marks_around_a_token() {
        assertEquals(
            "<p><strong><span data-type=\"mention\" data-id=\"7\">@ada</span></strong></p>",
            html(
                Paragraph(
                    listOf(Mention(TokenAttrs(id = "7", label = "ada"), marks = setOf(BoldMark()))),
                ),
            ),
        )
    }

    @Test
    fun escapes_a_token_label_and_id() {
        assertEquals(
            "<p><span data-type=\"mention\" data-id=\"a&quot;b\">@a&lt;b</span></p>",
            html(Paragraph(listOf(Mention(TokenAttrs(id = "a\"b", label = "a<b"))))),
        )
    }

    // ── Embedded blocks ──────────────────────────────────────────────────────

    @Test
    fun exports_a_table_with_header_and_body_cells() {
        val json = """
            {"type":"doc","content":[
              {"type":"table","content":[
                {"type":"tableRow","content":[
                  {"type":"tableHeader","attrs":{"colspan":1,"rowspan":1,"colwidth":null},
                   "content":[{"type":"paragraph","content":[{"type":"text","text":"Name"}]}]}
                ]},
                {"type":"tableRow","content":[
                  {"type":"tableCell","attrs":{"colspan":1,"rowspan":1,"colwidth":null},
                   "content":[{"type":"paragraph","content":[{"type":"text","text":"Juan"}]}]}
                ]}
              ]}
            ]}
        """

        assertEquals(
            "<table>" +
                "<tr><th><p>Name</p></th></tr>" +
                "<tr><td><p>Juan</p></td></tr>" +
                "</table>",
            editorFrom(json).toHtml(),
        )
    }

    @Test
    fun emits_table_cell_spans_only_when_they_span() {
        val json = """
            {"type":"doc","content":[
              {"type":"table","content":[
                {"type":"tableRow","content":[
                  {"type":"tableCell","attrs":{"colspan":2,"rowspan":1,"colwidth":null},
                   "content":[{"type":"paragraph","content":[{"type":"text","text":"wide"}]}]},
                  {"type":"tableCell","attrs":{"colspan":1,"rowspan":1,"colwidth":null},
                   "content":[{"type":"paragraph","content":[{"type":"text","text":"narrow"}]}]}
                ]}
              ]}
            ]}
        """

        assertEquals(
            "<table><tr>" +
                "<td colspan=\"2\"><p>wide</p></td>" +
                "<td><p>narrow</p></td>" +
                "</tr></table>",
            editorFrom(json).toHtml(),
        )
    }

    @Test
    fun exports_an_image_with_its_source() {
        val json = """
            {"type":"doc","content":[
              {"type":"image","attrs":{"src":"photo.png","alt":"A photo"}}
            ]}
        """

        assertEquals("<img src=\"photo.png\" alt=\"A photo\">", editorFrom(json).toHtml())
    }

    @Test
    fun exports_an_image_without_an_alt_as_an_empty_alt() {
        val json = """{"type":"doc","content":[{"type":"image","attrs":{"src":"x.png"}}]}"""

        assertEquals("<img src=\"x.png\" alt=\"\">", editorFrom(json).toHtml())
    }

    @Test
    fun keeps_an_unrecognised_embed_as_an_element_rather_than_dropping_it() {
        val json = """
            {"type":"doc","content":[
              {"type":"document","attrs":{"id":"doc-7"}}
            ]}
        """

        assertEquals(
            "<div data-type=\"document\" data-id=\"doc-7\"></div>",
            editorFrom(json).toHtml(),
        )
    }

    // ── Escaping ─────────────────────────────────────────────────────────────

    @Test
    fun escapes_html_special_characters_in_text() {
        assertEquals("<p>a &lt; b &amp; c &gt; d</p>", html(paragraphOf("a < b & c > d")))
    }

    @Test
    fun escapes_ampersands_once_only() {
        // The literal text "&lt;" must become "&amp;lt;", not "&amp;amp;lt;".
        assertEquals("<p>&amp;lt;</p>", html(paragraphOf("&lt;")))
    }

    @Test
    fun escapes_quotes_and_ampersands_in_a_link_href() {
        assertEquals(
            "<p><a href=\"https://test.com/?q=&quot;x&quot;&amp;y=1\">tricky</a></p>",
            html(paragraphOf("tricky", setOf(LinkMark(LinkAttrs("https://test.com/?q=\"x\"&y=1"))))),
        )
    }

    // ── Sanitisation ─────────────────────────────────────────────────────────

    @Test
    fun keeps_safe_link_schemes() {
        listOf(
            "https://test.com",
            "http://test.com",
            "mailto:a@b.com",
            "tel:+123",
            "/relative/path",
            "#anchor",
        ).forEach { href ->
            assertEquals(
                "<p><a href=\"$href\">x</a></p>",
                html(paragraphOf("x", setOf(LinkMark(LinkAttrs(href))))),
                "href: $href",
            )
        }
    }

    @Test
    fun drops_a_link_with_an_unsafe_scheme_but_keeps_the_text() {
        listOf(
            "javascript:alert(1)",
            "JavaScript:alert(1)",
            "data:text/html,<script>",
            "vbscript:msgbox",
        ).forEach { href ->
            assertEquals(
                "<p>click</p>",
                html(paragraphOf("click", setOf(LinkMark(LinkAttrs(href))))),
                "href: $href",
            )
        }
    }

    @Test
    fun emits_a_valid_hex_colour_but_drops_anything_else() {
        assertEquals(
            "<p><span style=\"color:#ABC\">x</span></p>",
            html(paragraphOf("x", setOf(TextStyleMark(TextStyleAttrs(color = "#ABC", fontSize = 0))))),
        )
        // An injection attempt through the colour is not a valid hex value, so it is dropped entirely.
        assertEquals(
            "<p>x</p>",
            html(paragraphOf("x", setOf(TextStyleMark(TextStyleAttrs(color = "#fff;background:url(x)", fontSize = 0))))),
        )
        assertEquals(
            "<p>x</p>",
            html(paragraphOf("x", setOf(TextStyleMark(TextStyleAttrs(color = "red", fontSize = 0))))),
        )
    }

    @Test
    fun drops_a_non_positive_or_absurd_font_size() {
        assertEquals(
            "<p>x</p>",
            html(paragraphOf("x", setOf(TextStyleMark(TextStyleAttrs(color = "", fontSize = -4))))),
        )
        assertEquals(
            "<p>x</p>",
            html(paragraphOf("x", setOf(TextStyleMark(TextStyleAttrs(color = "", fontSize = 100_000))))),
        )
    }

    @Test
    fun clamps_a_non_positive_ordered_list_start() {
        val zeroStart = OrderedList(ListAttrs(start = 0), listOf(ListItem(listOf(paragraphOf("a")))))
        val negativeStart = OrderedList(ListAttrs(start = -3), listOf(ListItem(listOf(paragraphOf("a")))))

        // Clamped to 1, which is the default, so no start attribute is emitted (never `start="0"`).
        assertEquals("<ol><li><p>a</p></li></ol>", html(zeroStart))
        assertEquals("<ol><li><p>a</p></li></ol>", html(negativeStart))
    }

    // ── End to end, through the editor ───────────────────────────────────────

    @Test
    fun exports_a_loaded_document_through_the_manager() {
        assertEquals("<p>Hello world</p>", editorFrom(SampleDocuments.SINGLE_PARAGRAPH).toHtml())
    }

    @Test
    fun exports_a_loaded_ordered_list_through_the_manager() {
        assertEquals(
            "<ol><li><p>one</p></li><li><p>two</p></li></ol>",
            editorFrom(SampleDocuments.ORDERED_LIST).toHtml(),
        )
    }

    @Test
    fun exports_a_loaded_link_through_the_manager() {
        assertEquals(
            "<p>visit <a href=\"https://test.com\">test</a> now</p>",
            editorFrom(SampleDocuments.PARAGRAPH_WITH_LINK).toHtml(),
        )
    }

    @Test
    fun exports_an_empty_editor_as_an_empty_string() {
        assertEquals("", editorFrom("{}").toHtml())
    }

    // ── After editing in the editor ──────────────────────────────────────────
    // toHtml() reads the live document, so exporting after edits must reflect them.

    @Test
    fun exports_text_typed_into_the_editor() {
        val editor = editorFrom(SampleDocuments.SINGLE_PARAGRAPH)

        editor.typeText(offset = 0, textToAdd = "Say: ")

        assertEquals("<p>Say: Hello world</p>", editor.toHtml())
    }

    @Test
    fun exports_a_mark_applied_in_the_editor() {
        val editor = editorFrom(SampleDocuments.SINGLE_PARAGRAPH)

        // Bold the word "Hello".
        assertTrue(editor.applyStyle(TextRange(0, 5), TextEditorStyleItem.Bold))

        assertEquals("<p><strong>Hello</strong> world</p>", editor.toHtml())
    }

    @Test
    fun exports_a_link_added_in_the_editor() {
        val editor = editorFrom(SampleDocuments.SINGLE_PARAGRAPH)

        assertTrue(editor.setLink(editor.rangeOf("world"), "https://test.com"))

        assertEquals(
            "<p>Hello <a href=\"https://test.com\">world</a></p>",
            editor.toHtml(),
        )
    }

    @Test
    fun exports_a_list_created_in_the_editor() {
        val editor = editorFrom(SampleDocuments.SINGLE_PARAGRAPH)

        assertTrue(
            editor.toListItem(
                TextRange(0, editor.offsetOf("world")),
                from = TextEditorListItem.None,
                to = TextEditorListItem.BulletedList,
            ),
        )

        assertEquals("<ul><li><p>Hello world</p></li></ul>", editor.toHtml())
    }

    @Test
    fun reflects_a_sequence_of_edits_in_the_export() {
        val editor = editorFrom(SampleDocuments.SINGLE_PARAGRAPH)

        editor.typeText(offset = editor.text.length, textToAdd = "!")
        editor.applyStyle(TextRange(0, 5), TextEditorStyleItem.Italic)

        assertEquals("<p><em>Hello</em> world!</p>", editor.toHtml())
    }
}
