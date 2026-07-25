package com.jjrodcast.textkit.editor.core.export

import com.jjrodcast.textkit.editor.core.parser.BaseParagraph
import com.jjrodcast.textkit.editor.core.parser.BaseText
import com.jjrodcast.textkit.editor.core.parser.Blockquote
import com.jjrodcast.textkit.editor.core.parser.BoldMark
import com.jjrodcast.textkit.editor.core.parser.BulletedList
import com.jjrodcast.textkit.editor.core.parser.EmbedBlock
import com.jjrodcast.textkit.editor.core.parser.EmbedTypes
import com.jjrodcast.textkit.editor.core.parser.HardBreak
import com.jjrodcast.textkit.editor.core.parser.Hashtag
import com.jjrodcast.textkit.editor.core.parser.HashtagType
import com.jjrodcast.textkit.editor.core.parser.Heading
import com.jjrodcast.textkit.editor.core.parser.HeadingLevels
import com.jjrodcast.textkit.editor.core.parser.HighlightMark
import com.jjrodcast.textkit.editor.core.parser.InlineToken
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
import com.jjrodcast.textkit.editor.core.parser.TEXT_EDITOR_JSON
import com.jjrodcast.textkit.editor.core.parser.TaskList
import com.jjrodcast.textkit.editor.core.parser.TaskListItem
import com.jjrodcast.textkit.editor.core.parser.Text
import com.jjrodcast.textkit.editor.core.parser.TextEditorDocument
import com.jjrodcast.textkit.editor.core.parser.TextStyleMark
import com.jjrodcast.textkit.editor.core.parser.UnderlineMark
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Exports a [TextEditorDocument] as GitHub Flavored Markdown.
 *
 * GFM is the target because it covers the structure the editor has natively — task lists, tables and
 * strikethrough. For marks GFM has no syntax for (underline, highlight and a colour/size `textStyle`)
 * the exporter falls back to inline HTML, which GFM passes through; the same HTML sanitisation the
 * HTML export relies on ([ExportHtml]) is applied so nothing user-supplied can inject script or CSS.
 *
 * This is a **lossy** export by nature: a mark or block Markdown cannot represent loses its formatting
 * (never its text), inline tokens become plain `@label` / `#label` text (their `data-id` identity is
 * dropped), and a blank paragraph has no Markdown representation. [DocumentSerializer]/`toJson` remain
 * the lossless round-trip format; Markdown is for sharing.
 */
internal class MarkdownSerializer : DocumentSerializer {

    override fun serialize(document: TextEditorDocument): String =
        document.content
            .map { block(it) }
            .filter { it.isNotEmpty() }
            .joinToString(separator = BLOCK_SEPARATOR)

    // ── Blocks ───────────────────────────────────────────────────────────────

    private fun block(paragraph: BaseParagraph): String = when (paragraph) {
        is Paragraph -> inline(paragraph.content)

        is Heading -> {
            val level = paragraph.attrs.level.coerceIn(HeadingLevels.H1, HeadingLevels.H6)
            "${"#".repeat(level)} ${inline(paragraph.content)}"
        }

        is BulletedList -> paragraph.content.joinToString(separator = "\n") { listRow(BULLET_MARKER, it) }

        is OrderedList -> {
            val start = paragraph.attrs.start.coerceAtLeast(MIN_LIST_START)
            paragraph.content.mapIndexed { index, item -> listRow("${start + index}.", item) }
                .joinToString(separator = "\n")
        }

        is TaskList -> paragraph.content.joinToString(separator = "\n") { taskRow(it) }

        is Blockquote -> blockquote(paragraph.content)

        is EmbedBlock -> embed(paragraph)

        is ParagraphNone -> ""
    }

    /** Renders [blocks] and prefixes every line with `> `, so nested block content stays quoted. */
    private fun blockquote(blocks: List<BaseParagraph>): String {
        val inner = blocks.map { block(it) }.filter { it.isNotEmpty() }.joinToString(separator = BLOCK_SEPARATOR)
        return inner.split("\n").joinToString(separator = "\n") { if (it.isEmpty()) ">" else "> $it" }
    }

    // ── Lists ────────────────────────────────────────────────────────────────

    /** One `-`/`N.` row. Its item's lead paragraph rides the marker line; any further block nests. */
    private fun listRow(marker: String, item: BaseText): String {
        val blocks = when (item) {
            is ListItem -> item.content
            // A list whose child is not a list item is malformed; keep its text rather than drop it.
            else -> return "$marker ${inline(listOf(item))}"
        }
        return "$marker ${itemBody(blocks)}"
    }

    /** One `- [ ]`/`- [x]` task row. */
    private fun taskRow(item: TaskListItem): String {
        val marker = if (item.attrs.checked) CHECKED_MARKER else UNCHECKED_MARKER
        return "$marker ${itemBody(item.content)}"
    }

    /**
     * A list item's content. The first paragraph is inlined onto the marker line; any remaining block
     * (a nested list, a second paragraph) is rendered and indented under the item so it stays part of
     * it. Indentation compounds with depth because each level indents its own nested output.
     */
    private fun itemBody(blocks: List<BaseParagraph>): String {
        val lead = (blocks.firstOrNull() as? Paragraph)?.let { inline(it.content) } ?: ""
        val rest = if (blocks.firstOrNull() is Paragraph) blocks.drop(1) else blocks
        val nested = rest.map { block(it) }.filter { it.isNotEmpty() }.joinToString(separator = "\n")
        return if (nested.isEmpty()) lead else "$lead\n${indent(nested)}"
    }

    private fun indent(block: String): String =
        block.split("\n").joinToString(separator = "\n") { if (it.isEmpty()) it else "$NESTED_INDENT$it" }

    // ── Embedded blocks ──────────────────────────────────────────────────────

    /**
     * A known embed becomes its Markdown form (a GFM table, an `![alt](src)` image); an unrecognised
     * type falls back to the same opaque `<div>` the HTML export emits, so it is never dropped.
     */
    private fun embed(block: EmbedBlock): String = when (block.embedType) {
        EmbedTypes.Table -> table(block.raw)
        EmbedTypes.Image -> image(block.raw)
        else -> "<div${htmlAttr("data-type", block.embedType)}${htmlAttr("data-id", block.id)}></div>"
    }

    /**
     * A GFM pipe table built from the verbatim embed JSON. The first row is the header (GFM has no
     * headerless table); a `--- | ---` delimiter row follows. Cell content is reduced to a single
     * inline line — a table cell cannot hold block content or newlines.
     */
    private fun table(raw: JsonElement): String {
        val rows = raw.childNodes().map { row -> row.childNodes().map { cell -> cellText(cell) } }
        if (rows.isEmpty()) return ""
        val columns = rows.maxOf { it.size }
        fun line(cells: List<String>) =
            (0 until columns).joinToString(separator = " | ", prefix = "| ", postfix = " |") { cells.getOrElse(it) { "" } }

        val header = line(rows.first())
        val delimiter = (0 until columns).joinToString(separator = " | ", prefix = "| ", postfix = " |") { "---" }
        val body = rows.drop(1).joinToString(separator = "\n") { line(it) }
        return listOf(header, delimiter, body).filter { it.isNotEmpty() }.joinToString(separator = "\n")
    }

    /** One table cell as a single inline line: block content is flattened and newlines collapsed. */
    private fun cellText(cell: JsonElement): String =
        cell.blockContent()
            .filterIsInstance<Paragraph>()
            .joinToString(separator = " ") { inline(it.content) }
            .replace("\n", " ")

    private fun image(raw: JsonElement): String {
        val attrs = raw.jsonObject["attrs"]?.jsonObject
        val src = attrs?.get("src")?.jsonPrimitive?.contentOrNull.orEmpty()
        val alt = attrs?.get("alt")?.jsonPrimitive?.contentOrNull.orEmpty()
        return "![${escapeText(alt)}](${escapeDestination(src)})"
    }

    /** The `"content"` of a raw embed node as a list of raw children. */
    private fun JsonElement.childNodes(): List<JsonElement> =
        (jsonObject["content"] as? JsonArray).orEmpty()

    /** The `"content"` of a raw node decoded as document blocks; malformed content yields none. */
    private fun JsonElement.blockContent(): List<BaseParagraph> {
        val content = jsonObject["content"] ?: return emptyList()
        return runCatching {
            TEXT_EDITOR_JSON.decodeFromJsonElement(ListSerializer(BaseParagraph.serializer()), content)
        }.getOrDefault(emptyList())
    }

    // ── Inline ───────────────────────────────────────────────────────────────

    private fun inline(content: List<BaseText>): String =
        content.joinToString(separator = "") { text(it) }

    private fun text(node: BaseText): String = when (node) {
        is Text -> wrapInMarks(escapeText(node.text), node.marks)
        // No Markdown inline hard break survives a table cell; the passthrough <br> does.
        is HardBreak -> HARD_BREAK
        is Mention -> token(node, MentionType.DEFAULT_MENTION_CHAR)
        is Hashtag -> token(node, HashtagType.DEFAULT_HASHTAG_CHAR)
        // List items are emitted by the list branches above, never as free inline content.
        is ListItem, is TaskListItem -> ""
    }

    /**
     * An inline token (mention, hashtag) as plain `<triggerChar><label>` text — Markdown has no node
     * to carry the token's id, so identity is intentionally dropped (see the class doc).
     */
    private fun token(node: InlineToken, triggerChar: Char): String =
        wrapInMarks(escapeText(triggerChar + node.attrs.label.orEmpty()), node.marks)

    // ── Marks ────────────────────────────────────────────────────────────────

    /**
     * Wraps [body] in one span per mark, in a fixed order ([markPriority]) so the nesting is
     * deterministic regardless of the iteration order of the underlying `Set`.
     */
    private fun wrapInMarks(body: String, marks: Set<Mark>): String =
        marks.sortedBy(::markPriority).foldRight(body) { mark, acc -> wrapInMark(acc, mark) }

    private fun wrapInMark(body: String, mark: Mark): String = when (mark) {
        // An unsafe scheme drops the link but keeps the text, so a `javascript:` href can never ride
        // out in the exported Markdown.
        is LinkMark -> ExportHtml.safeHref(mark.attrs.href)
            ?.let { "[$body](${escapeDestination(it)})" }
            ?: body

        is BoldMark -> "**$body**"
        is ItalicMark -> "_${body}_"
        is StrikeMark -> "~~$body~~"
        // No GFM syntax → inline HTML passthrough, sanitised the same way the HTML export is.
        is UnderlineMark -> "<u>$body</u>"
        is HighlightMark -> "<mark>$body</mark>"
        is TextStyleMark -> ExportHtml.textStyleCss(mark)?.let { "<span${htmlAttr("style", it)}>$body</span>" } ?: body
        is None -> body
    }

    /** Outermost first. Keeps output stable across runs and platforms; mirrors the HTML export. */
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

    // ── Escaping ─────────────────────────────────────────────────────────────

    /**
     * Escapes text so it renders literally: HTML-dangerous characters (`&`, `<`, `>`) become entities
     * — which keeps the text safe when it sits inside an inline-HTML fallback element too — and the
     * inline Markdown metacharacters are backslash-escaped. `\` is escaped first so the backslashes
     * added afterwards are not themselves re-escaped.
     */
    private fun escapeText(value: String): String {
        val html = value
            .replace("\\", "\\\\")
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
        return buildString {
            html.forEach { ch -> if (ch in MARKDOWN_METACHARACTERS) append('\\'); append(ch) }
        }
    }

    /** Escapes a URL for a `(...)` link/image destination: the delimiters that would end it early. */
    private fun escapeDestination(url: String): String = url
        .replace("\\", "\\\\")
        .replace("(", "\\(")
        .replace(")", "\\)")
        .replace(" ", "%20")

    /** One inline-HTML attribute, e.g. ` style="color:#fff"`, with the value attribute-escaped. */
    private fun htmlAttr(name: String, value: String): String = " $name=\"${ExportHtml.escapeAttribute(value)}\""

    private companion object {
        const val BLOCK_SEPARATOR = "\n\n"
        const val NESTED_INDENT = "    "
        const val BULLET_MARKER = "-"
        const val UNCHECKED_MARKER = "- [ ]"
        const val CHECKED_MARKER = "- [x]"
        const val MIN_LIST_START = 1
        const val HARD_BREAK = "<br>"

        /** Inline Markdown metacharacters backslash-escaped in text so they render literally. */
        val MARKDOWN_METACHARACTERS = setOf('`', '*', '_', '[', ']', '~', '|')
    }
}
