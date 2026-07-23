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
import com.jjrodcast.textkit.editor.core.parser.TextStyleAttrs.Companion.UNSET_FONT_SIZE
import com.jjrodcast.textkit.editor.core.parser.TextStyleMark
import com.jjrodcast.textkit.editor.core.parser.UnderlineMark
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Exports a [TextEditorDocument] as semantic HTML.
 *
 * Semantic tags are used over inline styles (`<strong>` rather than `<span style="font-weight:bold">`);
 * the only inline style emitted is for `textStyle`, which carries a colour and font size that have no
 * semantic equivalent.
 *
 * Opaque embedded blocks are rendered from the verbatim JSON the editor keeps in [EmbedBlock.raw]:
 * tables become real `<table>` markup and images `<img>`. Inline tokens (mentions, hashtags) keep
 * their identity in `data-*` attributes alongside the visible label.
 *
 * The document is user-supplied (loaded from JSON), so values that end up in an attribute the browser
 * *acts on* — a link `href`, a `textStyle` colour, an `<ol start>` — are validated, not just escaped:
 * only safe URL schemes ([SAFE_SCHEMES]) keep their link, only a valid hex colour is emitted, and
 * `start` is clamped. This keeps the exported markup safe to inject into a DOM/WebView.
 */
internal class HtmlSerializer : DocumentSerializer {

    override fun serialize(document: TextEditorDocument): String =
        document.content.joinToString(separator = "") { block(it) }

    // ── Blocks ───────────────────────────────────────────────────────────────

    private fun block(paragraph: BaseParagraph): String = when (paragraph) {
        is Paragraph -> tag(Tag.Paragraph, inline(paragraph.content))

        is Heading -> {
            val level = paragraph.attrs.level.coerceIn(HeadingLevels.H1, HeadingLevels.H6)
            tag("${Tag.Heading}$level", inline(paragraph.content))
        }

        is BulletedList -> tag(Tag.UnorderedList, paragraph.content.joinToString(separator = "") { listItem(it) })

        is OrderedList -> {
            // A list start must be a positive integer; clamp bad input. `start` is only worth
            // emitting when the list does not begin at 1.
            val start = paragraph.attrs.start.coerceAtLeast(MIN_LIST_START)
            val attributes = if (start != DEFAULT_LIST_START) attr(Attr.Start, start.toString()) else ""
            tag(Tag.OrderedList, paragraph.content.joinToString(separator = "") { listItem(it) }, attributes)
        }

        is TaskList -> tag(
            name = Tag.UnorderedList,
            body = paragraph.content.joinToString(separator = "") { taskItem(it) },
            attributes = attr(Attr.DataType, TASK_LIST_TYPE),
        )

        is Blockquote -> tag(Tag.Blockquote, paragraph.content.joinToString(separator = "") { block(it) })

        is EmbedBlock -> embed(paragraph)

        is ParagraphNone -> ""
    }

    // ── Embedded blocks ──────────────────────────────────────────────────────

    /**
     * An opaque embedded block. The editor keeps the original JSON subtree verbatim in
     * [EmbedBlock.raw], so the known types are rendered from it. An unrecognised type is emitted as
     * an empty element carrying its type and id rather than being dropped silently.
     */
    private fun embed(block: EmbedBlock): String = when (block.embedType) {
        EmbedTypes.Table -> table(block.raw)
        EmbedTypes.Image -> image(block.raw)
        else -> tag(
            name = Tag.Div,
            body = "",
            attributes = attr(Attr.DataType, escapeAttribute(block.embedType)) +
                attr(Attr.DataId, escapeAttribute(block.id)),
        )
    }

    private fun table(raw: JsonElement): String {
        val rows = raw.childNodes().joinToString(separator = "") { row ->
            tag(Tag.TableRow, row.childNodes().joinToString(separator = "") { cell(it) })
        }
        return tag(Tag.Table, rows)
    }

    private fun cell(cell: JsonElement): String {
        val name = if (cell.nodeType() == TABLE_HEADER_TYPE) Tag.TableHeaderCell else Tag.TableCell
        val attrs = cell.jsonObject["attrs"]?.jsonObject
        // colspan / rowspan default to 1; only emit them when they actually span.
        val attributes = listOf(Attr.ColSpan, Attr.RowSpan)
            .mapNotNull { key ->
                val span = attrs?.get(key)?.jsonPrimitive?.intOrNull ?: DEFAULT_SPAN
                if (span != DEFAULT_SPAN) attr(key, span.toString()) else null
            }
            .joinToString(separator = "")
        return tag(name, cell.blockContent().joinToString(separator = "") { block(it) }, attributes)
    }

    private fun image(raw: JsonElement): String {
        val attrs = raw.jsonObject["attrs"]?.jsonObject
        val src = attrs?.get(Attr.Src)?.jsonPrimitive?.contentOrNull.orEmpty()
        val alt = attrs?.get(Attr.Alt)?.jsonPrimitive?.contentOrNull.orEmpty()
        return "<${Tag.Image}${attr(Attr.Src, escapeAttribute(src))}${attr(Attr.Alt, escapeAttribute(alt))}>"
    }

    /** The `"type"` of a raw embed node, or an empty string when absent. */
    private fun JsonElement.nodeType(): String =
        jsonObject["type"]?.jsonPrimitive?.contentOrNull.orEmpty()

    /** The `"content"` of a raw embed node as a list of raw children. */
    private fun JsonElement.childNodes(): List<JsonElement> =
        (jsonObject["content"] as? JsonArray).orEmpty()

    /**
     * The `"content"` of a raw node decoded as document blocks, so a table cell's paragraphs go
     * through the same [block] rendering as the rest of the document. Malformed content yields no
     * blocks rather than failing the whole export.
     */
    private fun JsonElement.blockContent(): List<BaseParagraph> {
        val content = jsonObject["content"] ?: return emptyList()
        return runCatching {
            TEXT_EDITOR_JSON.decodeFromJsonElement(ListSerializer(BaseParagraph.serializer()), content)
        }.getOrDefault(emptyList())
    }

    /** A `<li>` of an ordered or bulleted list. Only [ListItem] carries block content. */
    private fun listItem(item: BaseText): String = when (item) {
        is ListItem -> tag(Tag.ListItem, item.content.joinToString(separator = "") { block(it) })
        // A list whose children are not list items is malformed; render the inline content directly
        // rather than dropping it.
        else -> tag(Tag.ListItem, inline(listOf(item)))
    }

    /**
     * A task `<li>`. The checkbox is a real, enabled `<input>` so the exported HTML stays
     * interactive; `data-checked` mirrors the state for consumers that re-import the markup.
     */
    private fun taskItem(item: TaskListItem): String {
        val checked = item.attrs.checked
        val input = "<${Tag.Checkbox}${attr(Attr.Type, CHECKBOX_INPUT_TYPE)}${if (checked) " ${Attr.Checked}" else ""}>"
        return tag(
            name = Tag.ListItem,
            body = input + item.content.joinToString(separator = "") { block(it) },
            attributes = attr(Attr.DataType, TASK_ITEM_TYPE) + attr(Attr.DataChecked, checked.toString()),
        )
    }

    // ── Inline ───────────────────────────────────────────────────────────────

    private fun inline(content: List<BaseText>): String =
        content.joinToString(separator = "") { text(it) }

    private fun text(node: BaseText): String = when (node) {
        is Text -> wrapInMarks(escapeText(node.text), node.marks)
        is HardBreak -> "<${Tag.LineBreak}>"
        is Mention -> token(node, MentionType.DEFAULT_MENTION_CHAR)
        is Hashtag -> token(node, HashtagType.DEFAULT_HASHTAG_CHAR)
        // List items are emitted by the list branches above, never as free inline content.
        is ListItem, is TaskListItem -> ""
    }

    /**
     * An atomic inline token (mention, hashtag). The visible text is `<triggerChar><label>`; the
     * node type and id ride on `data-*` attributes so the token's identity survives the export.
     */
    private fun token(node: InlineToken, triggerChar: Char): String {
        val label = escapeText(triggerChar + node.attrs.label.orEmpty())
        val body = tag(
            name = Tag.Span,
            body = label,
            attributes = attr(Attr.DataType, escapeAttribute(node.type)) +
                attr(Attr.DataId, escapeAttribute(node.attrs.id)),
        )
        return wrapInMarks(body, node.marks)
    }

    // ── Marks ────────────────────────────────────────────────────────────────

    /**
     * Wraps [body] in one element per mark. Marks are applied in a fixed order — [markPriority] —
     * so the nesting is deterministic regardless of the iteration order of the underlying `Set`.
     */
    private fun wrapInMarks(body: String, marks: Set<Mark>): String =
        marks.sortedBy(::markPriority).foldRight(body) { mark, acc -> wrapInMark(acc, mark) }

    private fun wrapInMark(body: String, mark: Mark): String = when (mark) {
        // An unsafe or non-http(s)/mailto/tel scheme drops the link but keeps the text, so a
        // `javascript:`/`data:` href can never reach a consumer's DOM.
        is LinkMark -> safeHref(mark.attrs.href)
            ?.let { tag(Tag.Anchor, body, attr(Attr.Href, escapeAttribute(it))) }
            ?: body

        is TextStyleMark -> styleOf(mark)?.let { tag(Tag.Span, body, attr(Attr.Style, it)) } ?: body
        is BoldMark -> tag(Tag.Bold, body)
        is ItalicMark -> tag(Tag.Italic, body)
        is UnderlineMark -> tag(Tag.Underline, body)
        is StrikeMark -> tag(Tag.Strike, body)
        is HighlightMark -> tag(Tag.Highlight, body)
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

    /**
     * The CSS for a `textStyle` mark, or `null` when nothing valid is left to emit. The colour is
     * only emitted when it is a valid hex value ([HEX_COLOR]) and the font size only when it is a
     * sane positive number — otherwise that declaration is dropped, so nothing user-supplied can
     * inject extra CSS through the `style` attribute.
     */
    private fun styleOf(mark: TextStyleMark): String? {
        val declarations = buildList {
            mark.attrs.color?.let(::safeColor)?.let { add("${Css.Color}:$it") }
            mark.attrs.fontSize
                .takeIf { it != UNSET_FONT_SIZE && it in MIN_FONT_SIZE..MAX_FONT_SIZE }
                ?.let { add("${Css.FontSize}:${it}px") }
        }
        return declarations.takeIf { it.isNotEmpty() }?.joinToString(separator = ";")
    }

    // ── Sanitisation ─────────────────────────────────────────────────────────

    /** Returns [href] when its scheme is safe (or it is a relative URL), otherwise `null`. */
    private fun safeHref(href: String): String? {
        val trimmed = href.trim()
        if (trimmed.isEmpty()) return null
        val scheme = schemeOf(trimmed) ?: return trimmed // no scheme → relative URL, safe
        return trimmed.takeIf { scheme in SAFE_SCHEMES }
    }

    /**
     * The lower-cased URL scheme of [url] (e.g. `"https"`), or `null` when it has none. A scheme is
     * the run of letters/digits/`+`/`.`/`-` before the first `:`, and only when no `/`, `?` or `#`
     * appears first (those mean the `:` belongs to a path/query/fragment, i.e. a relative URL).
     */
    private fun schemeOf(url: String): String? {
        val colon = url.indexOf(':')
        if (colon <= 0) return null
        val prefix = url.substring(0, colon)
        if (prefix.any { it == '/' || it == '?' || it == '#' }) return null
        if (!prefix.first().isLetter()) return null
        if (!prefix.all { it.isLetterOrDigit() || it == '+' || it == '.' || it == '-' }) return null
        return prefix.lowercase()
    }

    /** Returns [color] when it is a valid `#rgb` / `#rgba` / `#rrggbb` / `#rrggbbaa` hex value. */
    private fun safeColor(color: String): String? = color.trim().takeIf { HEX_COLOR.matches(it) }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun tag(name: String, body: String, attributes: String = "") =
        "<$name$attributes>$body</$name>"

    /** Renders one attribute, e.g. `attr("href", "x")` → ` href="x"`. Escape user values first. */
    private fun attr(name: String, value: String) = " $name=\"$value\""

    /** Escapes text content. `&` must be replaced first so the other entities are not double-escaped. */
    private fun escapeText(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    private fun escapeAttribute(value: String): String = escapeText(value).replace("\"", "&quot;")

    /** HTML element names. */
    private object Tag {
        const val Paragraph = "p"
        const val Heading = "h" // suffixed with the level, e.g. "h1"
        const val UnorderedList = "ul"
        const val OrderedList = "ol"
        const val ListItem = "li"
        const val Blockquote = "blockquote"
        const val LineBreak = "br"
        const val Table = "table"
        const val TableRow = "tr"
        const val TableHeaderCell = "th"
        const val TableCell = "td"
        const val Image = "img"
        const val Div = "div"
        const val Span = "span"
        const val Anchor = "a"
        const val Bold = "strong"
        const val Italic = "em"
        const val Underline = "u"
        const val Strike = "s"
        const val Highlight = "mark"
        const val Checkbox = "input"
    }

    /** HTML attribute names. */
    private object Attr {
        const val Start = "start"
        const val DataType = "data-type"
        const val DataId = "data-id"
        const val DataChecked = "data-checked"
        const val ColSpan = "colspan"
        const val RowSpan = "rowspan"
        const val Href = "href"
        const val Style = "style"
        const val Src = "src"
        const val Alt = "alt"
        const val Type = "type"
        const val Checked = "checked"
    }

    /** CSS property names used in the `style` attribute. */
    private object Css {
        const val Color = "color"
        const val FontSize = "font-size"
    }

    private companion object {
        const val DEFAULT_LIST_START = 1
        const val MIN_LIST_START = 1
        const val TASK_LIST_TYPE = "taskList"
        const val TASK_ITEM_TYPE = "taskItem"
        const val TABLE_HEADER_TYPE = "tableHeader"
        const val CHECKBOX_INPUT_TYPE = "checkbox"
        const val DEFAULT_SPAN = 1
        const val MIN_FONT_SIZE = 1
        const val MAX_FONT_SIZE = 512

        /** URL schemes safe to keep on an exported link; anything else drops the link. */
        val SAFE_SCHEMES = setOf("http", "https", "mailto", "tel")

        /** `#rgb`, `#rgba`, `#rrggbb` or `#rrggbbaa`. */
        val HEX_COLOR = Regex("^#([0-9a-fA-F]{3,4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")
    }
}
