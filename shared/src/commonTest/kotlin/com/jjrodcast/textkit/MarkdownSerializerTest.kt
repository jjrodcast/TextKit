package com.jjrodcast.textkit

import com.jjrodcast.textkit.editor.components.TextEditorStyleItem
import com.jjrodcast.textkit.editor.core.export.MarkdownSerializer
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

/**
 * Markdown export (GitHub Flavored Markdown).
 *
 * `MarkdownSerializer` is a pure function over the parsed document AST, so most cases build a
 * [TextEditorDocument] directly and assert the exact Markdown — that covers every node type, including
 * the ones a piece-table round-trip does not reproduce (headings and blockquotes are flattened into
 * styled paragraphs on load; see `PieceTableConverter`). A final group goes through
 * `TextKitEditorManager.toMarkdown()` to cover the end-to-end path.
 *
 * Mark nesting follows a fixed priority, so exact-string assertions are stable regardless of the
 * iteration order of the underlying mark `Set`.
 */
class MarkdownSerializerTest {

    private fun md(vararg blocks: BaseParagraph) =
        MarkdownSerializer().serialize(TextEditorDocument(blocks.toList()))

    private fun paragraphOf(text: String, marks: Set<Mark> = emptySet()) =
        Paragraph(content = listOf(Text(text, marks)))

    private fun listItemOf(text: String) = ListItem(listOf(paragraphOf(text)))

    private fun taskItemOf(text: String, checked: Boolean) =
        TaskListItem(TaskListAttrs(checked), listOf(paragraphOf(text)))

    // ── Blocks ───────────────────────────────────────────────────────────────

    @Test
    fun exports_a_paragraph() {
        assertEquals("Hello world", md(paragraphOf("Hello world")))
    }

    @Test
    fun separates_blocks_with_a_blank_line() {
        assertEquals("first\n\nsecond", md(paragraphOf("first"), paragraphOf("second")))
    }

    @Test
    fun exports_an_empty_document_as_an_empty_string() {
        assertEquals("", MarkdownSerializer().serialize(TextEditorDocument()))
    }

    @Test
    fun drops_blank_paragraphs_that_markdown_cannot_represent() {
        assertEquals("a\n\nb", md(paragraphOf("a"), Paragraph(), paragraphOf("b")))
    }

    @Test
    fun exports_headings_at_their_level() {
        assertEquals(
            "# Title\n\n### Section",
            md(
                Heading(HeadingAttrs(level = 1), listOf(Text("Title"))),
                Heading(HeadingAttrs(level = 3), listOf(Text("Section"))),
            ),
        )
    }

    @Test
    fun clamps_an_out_of_range_heading_level() {
        assertEquals("###### Deep", md(Heading(HeadingAttrs(level = 9), listOf(Text("Deep")))))
        assertEquals("# Shallow", md(Heading(HeadingAttrs(level = 0), listOf(Text("Shallow")))))
    }

    @Test
    fun exports_a_bulleted_list() {
        assertEquals(
            "- one\n- two",
            md(BulletedList(listOf(listItemOf("one"), listItemOf("two")))),
        )
    }

    @Test
    fun exports_an_ordered_list_from_its_start() {
        assertEquals(
            "3. one\n4. two",
            md(OrderedList(ListAttrs(start = 3), listOf(listItemOf("one"), listItemOf("two")))),
        )
    }

    @Test
    fun clamps_a_non_positive_ordered_list_start() {
        assertEquals(
            "1. one\n2. two",
            md(OrderedList(ListAttrs(start = 0), listOf(listItemOf("one"), listItemOf("two")))),
        )
    }

    @Test
    fun exports_a_task_list_with_checkbox_state() {
        assertEquals(
            "- [ ] buy milk\n- [x] walk dog",
            md(TaskList(listOf(taskItemOf("buy milk", false), taskItemOf("walk dog", true)))),
        )
    }

    @Test
    fun exports_a_blockquote() {
        assertEquals("> quoted text", md(Blockquote(listOf(paragraphOf("quoted text")))))
    }

    @Test
    fun keeps_every_line_of_a_multi_paragraph_blockquote_quoted() {
        assertEquals(
            "> a\n>\n> b",
            md(Blockquote(listOf(paragraphOf("a"), paragraphOf("b")))),
        )
    }

    @Test
    fun nests_a_sublist_tightly_under_its_item() {
        assertEquals(
            "- A\n    - B",
            md(
                BulletedList(
                    listOf(
                        ListItem(listOf(paragraphOf("A"), BulletedList(listOf(listItemOf("B"))))),
                    ),
                ),
            ),
        )
    }

    @Test
    fun separates_a_second_paragraph_in_an_item_with_a_blank_line() {
        // Without the blank line a Markdown renderer merges the two paragraphs into one.
        assertEquals(
            "- A\n\n    B",
            md(BulletedList(listOf(ListItem(listOf(paragraphOf("A"), paragraphOf("B")))))),
        )
    }

    // ── Marks ────────────────────────────────────────────────────────────────

    @Test
    fun exports_bold_italic_and_strikethrough() {
        assertEquals("**x**", md(paragraphOf("x", setOf(BoldMark()))))
        assertEquals("_x_", md(paragraphOf("x", setOf(ItalicMark()))))
        assertEquals("~~x~~", md(paragraphOf("x", setOf(StrikeMark()))))
    }

    @Test
    fun nests_marks_in_a_fixed_order() {
        assertEquals("**_x_**", md(paragraphOf("x", setOf(BoldMark(), ItalicMark()))))
    }

    @Test
    fun falls_back_to_html_for_underline_and_highlight() {
        assertEquals("<u>x</u>", md(paragraphOf("x", setOf(UnderlineMark()))))
        assertEquals("<mark>x</mark>", md(paragraphOf("x", setOf(HighlightMark()))))
    }

    @Test
    fun falls_back_to_a_styled_span_for_a_text_style() {
        assertEquals(
            "<span style=\"color:#ff0000\">x</span>",
            md(paragraphOf("x", setOf(TextStyleMark(TextStyleAttrs(color = "#ff0000"))))),
        )
        assertEquals(
            "<span style=\"font-size:20px\">x</span>",
            md(paragraphOf("x", setOf(TextStyleMark(TextStyleAttrs(fontSize = 20))))),
        )
    }

    @Test
    fun drops_an_invalid_text_style_but_keeps_the_text() {
        assertEquals("x", md(paragraphOf("x", setOf(TextStyleMark(TextStyleAttrs(color = "red"))))))
    }

    @Test
    fun exports_a_safe_link() {
        assertEquals(
            "[x](https://a.com)",
            md(paragraphOf("x", setOf(LinkMark(LinkAttrs("https://a.com"))))),
        )
    }

    @Test
    fun keeps_a_link_outermost_around_other_marks() {
        assertEquals(
            "[**x**](https://a.com)",
            md(paragraphOf("x", setOf(LinkMark(LinkAttrs("https://a.com")), BoldMark()))),
        )
    }

    @Test
    fun drops_an_unsafe_link_scheme_but_keeps_the_text() {
        assertEquals("x", md(paragraphOf("x", setOf(LinkMark(LinkAttrs("javascript:alert(1)"))))))
    }

    // ── Escaping ─────────────────────────────────────────────────────────────

    @Test
    fun escapes_inline_markdown_metacharacters() {
        assertEquals("a\\*b\\_c", md(paragraphOf("a*b_c")))
        assertEquals("a\\|b", md(paragraphOf("a|b")))
    }

    @Test
    fun escapes_html_special_characters_as_entities() {
        assertEquals("a&lt;b&gt;&amp;c", md(paragraphOf("a<b>&c")))
    }

    @Test
    fun escapes_backslashes_before_adding_its_own() {
        assertEquals("a\\\\b", md(paragraphOf("a\\b")))
    }

    @Test
    fun exports_a_hard_break_as_a_passthrough_br() {
        assertEquals("a<br>b", md(Paragraph(content = listOf(Text("a"), HardBreak(), Text("b")))))
    }

    // ── Tokens ───────────────────────────────────────────────────────────────

    @Test
    fun exports_mentions_and_hashtags_as_plain_text() {
        assertEquals("@bob", md(Paragraph(content = listOf(Mention(TokenAttrs("1", "bob"))))))
        assertEquals("#topic", md(Paragraph(content = listOf(Hashtag(TokenAttrs("2", "topic"))))))
    }

    @Test
    fun keeps_marks_around_a_token() {
        assertEquals(
            "**@bob**",
            md(Paragraph(content = listOf(Mention(TokenAttrs("1", "bob"), setOf(BoldMark()))))),
        )
    }

    // ── Embedded blocks (end-to-end) ───────────────────────────────────────────

    @Test
    fun exports_a_table_as_a_gfm_pipe_table() {
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
            "| Name |\n| --- |\n| Juan |",
            editorFrom(json).toMarkdown(),
        )
    }

    @Test
    fun exports_an_image() {
        val json = """
            {"type":"doc","content":[
              {"type":"image","attrs":{"src":"photo.png","alt":"A photo"}}
            ]}
        """

        assertEquals("![A photo](photo.png)", editorFrom(json).toMarkdown())
    }

    @Test
    fun keeps_an_unrecognised_embed_as_html_rather_than_dropping_it() {
        val json = """
            {"type":"doc","content":[
              {"type":"document","attrs":{"id":"doc-7"}}
            ]}
        """

        assertEquals(
            "<div data-type=\"document\" data-id=\"doc-7\"></div>",
            editorFrom(json).toMarkdown(),
        )
    }

    // ── End-to-end (through the manager) ───────────────────────────────────────

    @Test
    fun exports_a_loaded_paragraph_through_the_manager() {
        assertEquals("Hello world", editorFrom(SampleDocuments.SINGLE_PARAGRAPH).toMarkdown())
    }

    @Test
    fun exports_a_loaded_ordered_list_through_the_manager() {
        assertEquals("1. one\n2. two", editorFrom(SampleDocuments.ORDERED_LIST).toMarkdown())
    }

    @Test
    fun exports_a_loaded_task_list_through_the_manager() {
        assertEquals(
            "- [ ] buy milk\n- [x] walk dog",
            editorFrom(SampleDocuments.TASK_LIST).toMarkdown(),
        )
    }

    @Test
    fun exports_a_loaded_link_through_the_manager() {
        assertEquals(
            "visit [test](https://test.com) now",
            editorFrom(SampleDocuments.PARAGRAPH_WITH_LINK).toMarkdown(),
        )
    }

    @Test
    fun exports_an_empty_editor_as_an_empty_string() {
        assertEquals("", editorFrom("{}").toMarkdown())
    }

    @Test
    fun exports_after_typing_into_the_editor() {
        val editor = editorFrom(SampleDocuments.SINGLE_PARAGRAPH)
        editor.typeText(0, "Say: ")
        assertEquals("Say: Hello world", editor.toMarkdown())
    }

    @Test
    fun exports_after_applying_a_style_in_the_editor() {
        val editor = editorFrom(SampleDocuments.SINGLE_PARAGRAPH)
        editor.applyStyle(editor.rangeOf("Hello"), TextEditorStyleItem.Bold)
        assertEquals("**Hello** world", editor.toMarkdown())
    }
}
