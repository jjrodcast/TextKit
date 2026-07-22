package com.jjrodcast.textkit.editor.core.export

import com.jjrodcast.textkit.editor.core.parser.BaseParagraph
import com.jjrodcast.textkit.editor.core.parser.BaseText
import com.jjrodcast.textkit.editor.core.parser.Blockquote
import com.jjrodcast.textkit.editor.core.parser.BoldMark
import com.jjrodcast.textkit.editor.core.parser.BulletedList
import com.jjrodcast.textkit.editor.core.parser.EmbedBlock
import com.jjrodcast.textkit.editor.core.parser.HardBreak
import com.jjrodcast.textkit.editor.core.parser.Hashtag
import com.jjrodcast.textkit.editor.core.parser.HashtagType
import com.jjrodcast.textkit.editor.core.parser.Heading
import com.jjrodcast.textkit.editor.core.parser.HeadingLevels
import com.jjrodcast.textkit.editor.core.parser.HighlightMark
import com.jjrodcast.textkit.editor.core.parser.ItalicMark
import com.jjrodcast.textkit.editor.core.parser.LinkMark
import com.jjrodcast.textkit.editor.core.parser.ListItem
import com.jjrodcast.textkit.editor.core.parser.Mark
import com.jjrodcast.textkit.editor.core.parser.Mention
import com.jjrodcast.textkit.editor.core.parser.MentionType
import com.jjrodcast.textkit.editor.core.parser.None
import com.jjrodcast.textkit.editor.core.parser.OrderedList
import com.jjrodcast.textkit.editor.core.parser.Paragraph
import com.jjrodcast.textkit.editor.core.parser.ParagraphNone
import com.jjrodcast.textkit.editor.core.parser.StrikeMark
import com.jjrodcast.textkit.editor.core.parser.TaskList
import com.jjrodcast.textkit.editor.core.parser.TaskListItem
import com.jjrodcast.textkit.editor.core.parser.Text
import com.jjrodcast.textkit.editor.core.parser.TextEditorDocument
import com.jjrodcast.textkit.editor.core.parser.TextStyleAttrs.Companion.UNSET_FONT_SIZE
import com.jjrodcast.textkit.editor.core.parser.TextStyleMark
import com.jjrodcast.textkit.editor.core.parser.UnderlineMark

/**
 * Exports a [TextEditorDocument] as semantic HTML.
 *
 * Semantic tags are used over inline styles (`<strong>` rather than `<span style="font-weight:bold">`);
 * the only inline style emitted is for `textStyle`, which carries a colour and font size that have no
 * semantic equivalent.
 *
 * Block nodes the editor keeps as opaque embeds (tables, images, …) are not emitted yet — see
 * [EmbedBlock]. Inline tokens (mentions, hashtags) are emitted as their visible label text; carrying
 * their identity in `data-*` attributes is deferred to the same follow-up.
 */
internal class HtmlSerializer : DocumentSerializer {

    override fun serialize(document: TextEditorDocument): String =
        document.content.joinToString(separator = "") { block(it) }

    // ── Blocks ───────────────────────────────────────────────────────────────

    private fun block(paragraph: BaseParagraph): String = when (paragraph) {
        is Paragraph -> tag("p", inline(paragraph.content))

        is Heading -> {
            val level = paragraph.attrs.level.coerceIn(HeadingLevels.H1, HeadingLevels.H6)
            tag("h$level", inline(paragraph.content))
        }

        is BulletedList -> tag("ul", paragraph.content.joinToString(separator = "") { listItem(it) })

        is OrderedList -> {
            // `start` is only meaningful when the list does not begin at 1.
            val start = paragraph.attrs.start
            val attributes = if (start != DEFAULT_LIST_START) " start=\"$start\"" else ""
            tag("ol", paragraph.content.joinToString(separator = "") { listItem(it) }, attributes)
        }

        is TaskList -> tag(
            name = "ul",
            body = paragraph.content.joinToString(separator = "") { taskItem(it) },
            attributes = " data-type=\"$TASK_LIST_TYPE\"",
        )

        is Blockquote -> tag("blockquote", paragraph.content.joinToString(separator = "") { block(it) })

        // Opaque embedded blocks (table, image, …) are handled in a follow-up.
        is EmbedBlock -> ""

        is ParagraphNone -> ""
    }

    /** A `<li>` of an ordered or bulleted list. Only [ListItem] carries block content. */
    private fun listItem(item: BaseText): String = when (item) {
        is ListItem -> tag("li", item.content.joinToString(separator = "") { block(it) })
        // A list whose children are not list items is malformed; render the inline content directly
        // rather than dropping it.
        else -> tag("li", inline(listOf(item)))
    }

    /**
     * A task `<li>`. The checkbox is a real, enabled `<input>` so the exported HTML stays
     * interactive; `data-checked` mirrors the state for consumers that re-import the markup.
     */
    private fun taskItem(item: TaskListItem): String {
        val checked = item.attrs.checked
        val input = "<input type=\"checkbox\"${if (checked) " checked" else ""}>"
        return tag(
            name = "li",
            body = input + item.content.joinToString(separator = "") { block(it) },
            attributes = " data-type=\"$TASK_ITEM_TYPE\" data-checked=\"$checked\"",
        )
    }

    // ── Inline ───────────────────────────────────────────────────────────────

    private fun inline(content: List<BaseText>): String =
        content.joinToString(separator = "") { text(it) }

    private fun text(node: BaseText): String = when (node) {
        is Text -> wrapInMarks(escapeText(node.text), node.marks)
        is HardBreak -> "<br>"
        // Tokens render as the label the user sees; identity attributes come in the follow-up.
        is Mention -> wrapInMarks(
            escapeText(MentionType.DEFAULT_MENTION_CHAR + node.attrs.label.orEmpty()),
            node.marks,
        )

        is Hashtag -> wrapInMarks(
            escapeText(HashtagType.DEFAULT_HASHTAG_CHAR + node.attrs.label.orEmpty()),
            node.marks,
        )
        // List items are emitted by the list branches above, never as free inline content.
        is ListItem, is TaskListItem -> ""
    }

    // ── Marks ────────────────────────────────────────────────────────────────

    /**
     * Wraps [body] in one element per mark. Marks are applied in a fixed order — [markPriority] —
     * so the nesting is deterministic regardless of the iteration order of the underlying `Set`.
     */
    private fun wrapInMarks(body: String, marks: Set<Mark>): String =
        marks.sortedBy(::markPriority).foldRight(body) { mark, acc -> wrapInMark(acc, mark) }

    private fun wrapInMark(body: String, mark: Mark): String = when (mark) {
        is LinkMark -> tag("a", body, " href=\"${escapeAttribute(mark.attrs.href)}\"")
        is TextStyleMark -> styleOf(mark)?.let { tag("span", body, " style=\"$it\"") } ?: body
        is BoldMark -> tag("strong", body)
        is ItalicMark -> tag("em", body)
        is UnderlineMark -> tag("u", body)
        is StrikeMark -> tag("s", body)
        is HighlightMark -> tag("mark", body)
        is None -> body
    }

    /** Outermost first. Keeps output stable across runs and platforms. */
    private fun markPriority(mark: Mark): Int = when (mark) {
        is LinkMark -> 0
        is TextStyleMark -> 1
        is BoldMark -> 2
        is ItalicMark -> 3
        is UnderlineMark -> 4
        is StrikeMark -> 5
        is HighlightMark -> 6
        is None -> 7
    }

    /** The CSS for a `textStyle` mark, or `null` when it carries neither a colour nor a size. */
    private fun styleOf(mark: TextStyleMark): String? {
        val declarations = buildList {
            mark.attrs.color?.takeIf { it.isNotBlank() }?.let { add("color:${escapeAttribute(it)}") }
            mark.attrs.fontSize.takeIf { it != UNSET_FONT_SIZE }?.let { add("font-size:${it}px") }
        }
        return declarations.takeIf { it.isNotEmpty() }?.joinToString(separator = ";")
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun tag(name: String, body: String, attributes: String = "") =
        "<$name$attributes>$body</$name>"

    /** Escapes text content. `&` must be replaced first so the other entities are not double-escaped. */
    private fun escapeText(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    private fun escapeAttribute(value: String): String = escapeText(value).replace("\"", "&quot;")

    private companion object {
        const val DEFAULT_LIST_START = 1
        const val TASK_LIST_TYPE = "taskList"
        const val TASK_ITEM_TYPE = "taskItem"
    }
}
