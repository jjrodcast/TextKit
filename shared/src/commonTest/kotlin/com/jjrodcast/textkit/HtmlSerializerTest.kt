package com.jjrodcast.textkit

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
    fun exports_tokens_as_their_visible_label() {
        assertEquals(
            "<p>@ada #kotlin</p>",
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
}
