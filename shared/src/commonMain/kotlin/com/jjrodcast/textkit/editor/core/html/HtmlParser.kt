package com.jjrodcast.textkit.editor.core.html

import com.jjrodcast.textkit.editor.core.export.ExportHtml
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
import com.jjrodcast.textkit.editor.core.parser.HeadingAttrs
import com.jjrodcast.textkit.editor.core.parser.HeadingLevels
import com.jjrodcast.textkit.editor.core.parser.HighlightMark
import com.jjrodcast.textkit.editor.core.parser.ItalicMark
import com.jjrodcast.textkit.editor.core.parser.LinkAttrs
import com.jjrodcast.textkit.editor.core.parser.LinkMark
import com.jjrodcast.textkit.editor.core.parser.ListAttrs
import com.jjrodcast.textkit.editor.core.parser.ListItem
import com.jjrodcast.textkit.editor.core.parser.Mark
import com.jjrodcast.textkit.editor.core.parser.Mention
import com.jjrodcast.textkit.editor.core.parser.MentionType
import com.jjrodcast.textkit.editor.core.parser.OrderedList
import com.jjrodcast.textkit.editor.core.parser.Paragraph
import com.jjrodcast.textkit.editor.core.parser.ParagraphAttrs
import com.jjrodcast.textkit.editor.core.parser.StrikeMark
import com.jjrodcast.textkit.editor.core.parser.TEXT_EDITOR_JSON
import com.jjrodcast.textkit.editor.core.parser.TaskList
import com.jjrodcast.textkit.editor.core.parser.TaskListAttrs
import com.jjrodcast.textkit.editor.core.parser.TaskListItem
import com.jjrodcast.textkit.editor.core.parser.Text
import com.jjrodcast.textkit.editor.core.parser.TextAlign
import com.jjrodcast.textkit.editor.core.parser.TextEditorDocument
import com.jjrodcast.textkit.editor.core.parser.TextStyleAttrs
import com.jjrodcast.textkit.editor.core.parser.TextStyleMark
import com.jjrodcast.textkit.editor.core.parser.TokenAttrs
import com.jjrodcast.textkit.editor.core.parser.UnderlineMark
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Converts HTML to the editor's document JSON — the inverse of `HtmlSerializer`, targeting the
 * subset that exporter emits so its output round-trips, plus the synonyms and looseness real-world
 * HTML brings (`<b>`/`<i>`/`<del>`, unclosed `<p>`/`<li>`, tag soup, loose inline content).
 *
 * The import is **lossy but text-safe**: rendered content is never dropped, only demoted. An
 * unknown tag is unwrapped around its content; loose inline content becomes a paragraph. The
 * deliberate exceptions are the elements whose content a browser never renders — `<script>`,
 * `<style>` and inert `<template>` bodies — which are discarded whole, so the import matches what
 * the user actually saw on the page. And it is a sanitizer by
 * construction (issue #44): the output is typed nodes, so markup can never pass through verbatim —
 * `<script>`/`<style>` bodies are discarded wholesale, event handlers have nowhere to live, an
 * unsafe-scheme `href` drops its link (the text stays, `ExportHtml.safeHref`'s rule inbound), an
 * unsafe `<img src>` degrades to the alt text, and `style` values are re-parsed into validated
 * attributes rather than carried as CSS.
 */
fun htmlToJson(html: String): String =
    TEXT_EDITOR_JSON.encodeToString(TextEditorDocument.serializer(), HtmlParser().parse(html))

internal class HtmlParser {

    fun parse(html: String): TextEditorDocument {
        val root = buildTree(tokenize(html))
        return TextEditorDocument(mapBlocks(root.children))
    }

    // ── DOM-lite ─────────────────────────────────────────────────────────────

    internal sealed class Node

    internal class Element(
        val name: String,
        val attrs: Map<String, String>,
        val children: MutableList<Node> = mutableListOf(),
    ) : Node()

    internal class TextNode(val text: String) : Node()

    private sealed class Token {
        data class Open(val name: String, val attrs: Map<String, String>, val selfClosing: Boolean) : Token()
        data class Close(val name: String) : Token()
        data class Chars(val text: String) : Token()
    }

    // ── Tokenizer ────────────────────────────────────────────────────────────

    private fun tokenize(html: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var i = 0
        val text = StringBuilder()
        fun flushText() {
            if (text.isNotEmpty()) {
                tokens += Token.Chars(decodeEntities(text.toString()))
                text.clear()
            }
        }
        while (i < html.length) {
            val ch = html[i]
            if (ch != '<') {
                text.append(ch)
                i++
                continue
            }
            when {
                html.startsWith("<!--", i) -> {
                    flushText()
                    val end = html.indexOf("-->", i + 4)
                    i = if (end < 0) html.length else end + 3
                }

                html.startsWith("<!", i) || html.startsWith("<?", i) -> {
                    flushText()
                    val end = html.indexOf('>', i)
                    i = if (end < 0) html.length else end + 1
                }

                i + 1 < html.length && (html[i + 1].isLetter() || html[i + 1] == '/') -> {
                    val end = html.indexOf('>', i)
                    if (end < 0) {
                        // an unterminated tag at the end of input is not a tag; keep it as text
                        text.append(ch)
                        i++
                        continue
                    }
                    flushText()
                    val inner = html.substring(i + 1, end)
                    if (inner.startsWith("/")) {
                        tokens += Token.Close(inner.substring(1).trim().lowercase())
                    } else {
                        val selfClosing = inner.endsWith("/")
                        val content = if (selfClosing) inner.dropLast(1) else inner
                        val name = content.takeWhile { !it.isWhitespace() }.lowercase()
                        tokens += Token.Open(name, parseAttrs(content.drop(name.length)), selfClosing)
                        // A raw-text element's body is discarded wholesale, markup and all.
                        if (name in RAW_TEXT_ELEMENTS && !selfClosing) {
                            val close = html.indexOf("</$name", end + 1, ignoreCase = true)
                            i = if (close < 0) {
                                html.length
                            } else {
                                val closeEnd = html.indexOf('>', close)
                                if (closeEnd < 0) html.length else closeEnd + 1
                            }
                            tokens += Token.Close(name)
                            continue
                        }
                    }
                    i = end + 1
                }

                else -> {
                    text.append(ch)
                    i++
                }
            }
        }
        flushText()
        return tokens
    }

    private fun parseAttrs(raw: String): Map<String, String> {
        val attrs = mutableMapOf<String, String>()
        var i = 0
        while (i < raw.length) {
            while (i < raw.length && raw[i].isWhitespace()) i++
            if (i >= raw.length) break
            val nameStart = i
            while (i < raw.length && !raw[i].isWhitespace() && raw[i] != '=') i++
            val name = raw.substring(nameStart, i).lowercase()
            if (name.isEmpty()) { i++; continue }
            while (i < raw.length && raw[i].isWhitespace()) i++
            if (i >= raw.length || raw[i] != '=') {
                attrs[name] = ""
                continue
            }
            i++
            while (i < raw.length && raw[i].isWhitespace()) i++
            val value = when {
                i < raw.length && (raw[i] == '"' || raw[i] == '\'') -> {
                    val quote = raw[i]
                    val end = raw.indexOf(quote, i + 1)
                    if (end < 0) raw.substring(i + 1).also { i = raw.length }
                    else raw.substring(i + 1, end).also { i = end + 1 }
                }
                else -> {
                    val start = i
                    while (i < raw.length && !raw[i].isWhitespace()) i++
                    raw.substring(start, i)
                }
            }
            attrs[name] = decodeEntities(value)
        }
        return attrs
    }

    private fun decodeEntities(text: String): String {
        if ('&' !in text) return text
        return buildString {
            var i = 0
            while (i < text.length) {
                if (text[i] != '&') {
                    append(text[i]); i++
                    continue
                }
                val semi = text.indexOf(';', i + 1)
                if (semi < 0 || semi - i > MAX_ENTITY_LENGTH) {
                    append(text[i]); i++
                    continue
                }
                val entity = text.substring(i + 1, semi)
                val decoded = when {
                    NAMED_ENTITIES.containsKey(entity) -> NAMED_ENTITIES.getValue(entity)
                    entity.startsWith("#x") || entity.startsWith("#X") ->
                        entity.drop(2).toIntOrNull(16)?.let(::codePointToString)
                    entity.startsWith("#") ->
                        entity.drop(1).toIntOrNull()?.let(::codePointToString)
                    else -> null
                }
                if (decoded != null) {
                    append(decoded)
                    i = semi + 1
                } else {
                    append(text[i]); i++
                }
            }
        }
    }

    /**
     * The string for one Unicode code point — a surrogate pair for the supplementary planes,
     * which `Int.toChar()` would truncate. Lone-surrogate and out-of-range values yield null.
     */
    private fun codePointToString(codePoint: Int): String? = when (codePoint) {
        in 1..0xD7FF, in 0xE000..0xFFFF -> codePoint.toChar().toString()
        in 0x10000..0x10FFFF -> {
            val value = codePoint - 0x10000
            charArrayOf(
                ((value shr 10) + 0xD800).toChar(),
                ((value and 0x3FF) + 0xDC00).toChar(),
            ).concatToString()
        }
        else -> null
    }

    // ── Tree builder ─────────────────────────────────────────────────────────

    /** Builds the element tree with browser-style recovery: void elements never open, `<p>`/`<li>`
     *  and table cells auto-close their predecessors, and a stray close tag is ignored. */
    private fun buildTree(tokens: List<Token>): Element {
        val root = Element(ROOT, emptyMap())
        val stack = ArrayDeque<Element>().apply { addLast(root) }
        fun closeUpTo(name: String) {
            if (stack.none { it.name == name }) return
            while (stack.size > 1) {
                val closed = stack.removeLast()
                if (closed.name == name) break
            }
        }
        fun autoClose(opening: String) {
            when {
                opening in BLOCK_TAGS && stack.last().name == "p" -> closeUpTo("p")
                opening == "li" && stack.any { it.name == "li" } &&
                    stack.last().name !in LIST_CONTAINER_TAGS -> closeUpTo("li")
                (opening == "td" || opening == "th") && stack.last().name in CELL_TAGS ->
                    closeUpTo(stack.last().name)
                opening == "tr" && stack.last().name in CELL_TAGS + "tr" -> closeUpTo("tr")
            }
        }
        tokens.forEach { token ->
            when (token) {
                is Token.Chars -> stack.last().children += TextNode(token.text)
                is Token.Open -> {
                    autoClose(token.name)
                    val element = Element(token.name, token.attrs)
                    stack.last().children += element
                    if (!token.selfClosing && token.name !in VOID_ELEMENTS && token.name !in RAW_TEXT_ELEMENTS) {
                        stack.addLast(element)
                    }
                }
                is Token.Close -> closeUpTo(token.name)
            }
        }
        return root
    }

    // ── Block mapping ────────────────────────────────────────────────────────

    private fun mapBlocks(nodes: List<Node>, marks: Set<Mark> = emptySet()): List<BaseParagraph> {
        val blocks = mutableListOf<BaseParagraph>()
        val looseInline = mutableListOf<Node>()
        fun flushLoose() {
            val content = mapInline(looseInline, marks)
            looseInline.clear()
            // a run of nothing but breaks (Apple's trailing interchange <br>) is clipboard
            // framing, not content
            if (content.any { it !is HardBreak }) blocks += Paragraph(content = content)
        }
        nodes.forEach { node ->
            when {
                node is TextNode && node.text.isBlank() && looseInline.isEmpty() -> Unit
                node is Element && node.name in DROPPED_ELEMENTS -> Unit
                node is Element && node.name in BLOCK_TAGS -> {
                    flushLoose()
                    blocks += mapBlock(node, marks)
                }
                // an element that CONTAINS block children is a transparent container. An INLINE
                // container's cascade flows down — Google Docs wraps whole documents in a styled
                // <b> whose font-weight:normal suppresses the bold its tag implies. A block-level
                // container's styles do NOT: a browser copy inlines the page's COMPUTED style onto
                // every div and section, and honoring that would coat the paste in the site theme.
                node is Element && node.children.any { it is Element && it.name in BLOCK_TAGS } -> {
                    flushLoose()
                    val inherited = if (node.name in INLINE_TAGS) cascade(node, marks, tagMarkOf(node.name)) else marks
                    blocks += mapBlocks(node.children, inherited)
                }
                // anything else — text, marks, unknown inline tags — is loose inline content that
                // browsers wrap into an implicit paragraph
                else -> looseInline += node
            }
        }
        flushLoose()
        return blocks
    }

    /** The mark an inline tag implies by itself, before its style cascade is applied. */
    private fun tagMarkOf(name: String): Mark? = when (name) {
        "strong", "b" -> BoldMark()
        "em", "i" -> ItalicMark()
        "u", "ins" -> UnderlineMark()
        "s", "del", "strike" -> StrikeMark()
        "mark" -> HighlightMark()
        else -> null
    }

    private fun mapBlock(element: Element, marks: Set<Mark> = emptySet()): List<BaseParagraph> = when (element.name) {
        "p" -> listOf(Paragraph(attrs = ParagraphAttrs(textAlign = alignOf(element)), content = mapInline(element.children, marks)))

        "h1", "h2", "h3", "h4", "h5", "h6" -> {
            val level = element.name.drop(1).toInt().coerceIn(HeadingLevels.H1, HeadingLevels.H6)
            listOf(Heading(attrs = HeadingAttrs(level = level, textAlign = alignOf(element)), content = mapInline(element.children, marks)))
        }

        "blockquote" -> listOf(Blockquote(content = mapBlocks(element.children, marks)))

        "ul" ->
            if (element.attrs[DATA_TYPE] == TASK_LIST_TYPE) listOf(taskList(element))
            else listOf(BulletedList(content = listItems(element)))

        "ol" -> {
            val start = element.attrs["start"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
            listOf(OrderedList(attrs = ListAttrs(start = start), content = listItems(element)))
        }

        "table" -> listOf(tableEmbed(element))

        "img" -> imageBlock(element)

        "pre" -> listOf(codeEmbed(element))

        "div" -> {
            val dataType = element.attrs[DATA_TYPE]
            if (dataType != null) {
                // the export's placeholder for an embed it could not render; keep type and id so
                // the shape survives a round trip
                listOf(
                    EmbedBlock(
                        embedType = dataType,
                        id = element.attrs[DATA_ID] ?: dataType,
                        raw = buildJsonObject {
                            put("type", dataType)
                            put("attrs", buildJsonObject { put("id", element.attrs[DATA_ID] ?: dataType) })
                        },
                    )
                )
            } else {
                // a plain div is a transparent container
                mapBlocks(element.children, marks)
            }
        }

        else -> mapBlocks(element.children, marks)
    }

    private fun listItems(list: Element): List<BaseText> =
        list.children.filterIsInstance<Element>().filter { it.name == "li" }.map { li ->
            ListItem(content = mapBlocks(li.children))
        }

    private fun taskList(list: Element): TaskList {
        val items = list.children.filterIsInstance<Element>().filter { it.name == "li" }.map { li ->
            val checkbox = li.children.filterIsInstance<Element>().firstOrNull { it.name == "input" }
            val checked = li.attrs[DATA_CHECKED] == "true" || checkbox?.attrs?.containsKey("checked") == true
            val content = li.children.filterNot { it is Element && it.name == "input" }
            TaskListItem(attrs = TaskListAttrs(checked = checked), content = mapBlocks(content))
        }
        return TaskList(content = items)
    }

    // ── Embeds ───────────────────────────────────────────────────────────────

    private fun tableEmbed(table: Element): EmbedBlock {
        // tr rows can sit directly in <table> or inside thead/tbody/tfoot sections
        val rows = table.children.filterIsInstance<Element>()
            .flatMap { if (it.name in TABLE_SECTION_TAGS) it.children.filterIsInstance<Element>() else listOf(it) }
            .filter { it.name == "tr" }
        val raw = buildJsonObject {
            put("type", EmbedTypes.Table)
            put("content", buildJsonArray {
                rows.forEach { row ->
                    add(buildJsonObject {
                        put("type", "tableRow")
                        put("content", buildJsonArray {
                            row.children.filterIsInstance<Element>()
                                .filter { it.name in CELL_TAGS }
                                .forEach { cell ->
                                    add(buildJsonObject {
                                        put("type", if (cell.name == "th") "tableHeader" else "tableCell")
                                        put("attrs", buildJsonObject {
                                            put("colspan", cell.attrs["colspan"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1)
                                            put("rowspan", cell.attrs["rowspan"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1)
                                            put("colwidth", JsonNull)
                                        })
                                        put("content", buildJsonArray {
                                            cellBlocks(cell).forEach {
                                                add(TEXT_EDITOR_JSON.encodeToJsonElement(BaseParagraph.serializer(), it))
                                            }
                                        })
                                    })
                                }
                        })
                    })
                }
            })
        }
        return EmbedBlock(embedType = EmbedTypes.Table, id = EmbedTypes.Table, raw = raw)
    }

    /** A cell's blocks; a cell holding only inline content still yields one paragraph. */
    private fun cellBlocks(cell: Element): List<BaseParagraph> {
        val blocks = mapBlocks(cell.children)
        return blocks.ifEmpty { listOf(Paragraph()) }
    }

    /** An `<img>`: a safe `src` becomes the image embed; an unsafe one degrades to the alt text. */
    private fun imageBlock(img: Element): List<BaseParagraph> {
        val src = img.attrs["src"]?.let { ExportHtml.safeHref(it) }
        if (src == null) {
            val alt = img.attrs["alt"].orEmpty()
            return if (alt.isBlank()) emptyList()
            else listOf(Paragraph(content = listOf(Text(text = alt))))
        }
        val raw = buildJsonObject {
            put("type", EmbedTypes.Image)
            put("attrs", buildJsonObject {
                put("src", src)
                put("alt", img.attrs["alt"].orEmpty())
            })
        }
        return listOf(EmbedBlock(embedType = EmbedTypes.Image, id = EmbedTypes.Image, raw = raw))
    }

    /** `<pre>`(`<code>`) → the `codeBlock` embed; a `language-*` class becomes the language. */
    private fun codeEmbed(pre: Element): EmbedBlock {
        val code = pre.children.filterIsInstance<Element>().firstOrNull { it.name == "code" }
        val source = code ?: pre
        val language = (code?.attrs?.get("class") ?: pre.attrs["class"]).orEmpty()
            .split(' ').firstOrNull { it.startsWith(LANGUAGE_CLASS_PREFIX) }
            ?.removePrefix(LANGUAGE_CLASS_PREFIX).orEmpty()
        val text = rawText(source).removeSuffix("\n")
        val raw = buildJsonObject {
            put("type", EmbedTypes.CodeBlock)
            put("attrs", buildJsonObject { put("language", language) })
            put("content", buildJsonArray {
                if (text.isNotEmpty()) {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", text)
                    })
                }
            })
        }
        return EmbedBlock(embedType = EmbedTypes.CodeBlock, id = EmbedTypes.CodeBlock, raw = raw)
    }

    /** All text under [node], tags stripped, whitespace preserved (pre content is verbatim). */
    private fun rawText(node: Node): String = when (node) {
        is TextNode -> node.text
        is Element -> if (node.name == "br") "\n" else node.children.joinToString(separator = "") { rawText(it) }
    }

    // ── Inline mapping ───────────────────────────────────────────────────────

    private fun mapInline(nodes: List<Node>, marks: Set<Mark>): List<BaseText> {
        val out = mutableListOf<BaseText>()
        nodes.forEach { node ->
            when (node) {
                is TextNode -> {
                    val collapsed = collapseWhitespace(node.text)
                    if (collapsed.isNotEmpty()) out += Text(text = collapsed, marks = marks)
                }
                is Element -> when (node.name) {
                    in DROPPED_ELEMENTS -> Unit
                    "br" -> out += HardBreak(marks = marks)
                    "strong", "b" -> out += mapInline(node.children, cascade(node, marks, BoldMark()))
                    "em", "i" -> out += mapInline(node.children, cascade(node, marks, ItalicMark()))
                    "u", "ins" -> out += mapInline(node.children, cascade(node, marks, UnderlineMark()))
                    "s", "del", "strike" -> out += mapInline(node.children, cascade(node, marks, StrikeMark()))
                    "mark" -> out += mapInline(node.children, cascade(node, marks, HighlightMark()))
                    "a" -> {
                        // an unsafe scheme drops the link and keeps the text — safeHref inbound
                        val href = node.attrs["href"]?.let { ExportHtml.safeHref(it) }
                        val linked = if (href != null) marks + LinkMark(LinkAttrs(href = href)) else marks
                        out += mapInline(node.children, cascade(node, linked, null))
                    }
                    "span" -> out += span(node, marks)
                    else -> out += mapInline(node.children, cascade(node, marks, null))
                }
            }
        }
        return mergeAdjacent(out)
    }

    private fun span(node: Element, marks: Set<Mark>): List<BaseText> {
        val dataType = node.attrs[DATA_TYPE]
        // A token needs an identity: without a non-blank data-id the span degrades to its plain
        // text (the visible label, trigger char and all) instead of minting an id-less token.
        val tokenId = node.attrs[DATA_ID]?.takeIf { it.isNotBlank() }
        if ((dataType == MentionType.Mention || dataType == HashtagType.Hashtag) && tokenId != null) {
            val trigger = if (dataType == MentionType.Mention) MentionType.DEFAULT_MENTION_CHAR else HashtagType.DEFAULT_HASHTAG_CHAR
            val label = rawText(node).trim().removePrefix(trigger.toString())
            val attrs = TokenAttrs(id = tokenId, label = label)
            return listOf(
                if (dataType == MentionType.Mention) Mention(attrs = attrs, marks = marks)
                else Hashtag(attrs = attrs, marks = marks)
            )
        }
        return mapInline(node.children, cascade(node, marks, null))
    }

    /**
     * The marks in effect inside [element]: the inherited set, the element's own tag semantics
     * ([tagMark]), and its `style` declarations applied CSS-cascade style — a declaration both
     * adds and *overrides*, so Google Docs' `<b style="font-weight:normal">` document wrapper
     * bolds nothing, and a `font-weight:400` span un-bolds inherited bold (#152).
     */
    private fun cascade(element: Element, inherited: Set<Mark>, tagMark: Mark?): Set<Mark> {
        val style = element.attrs["style"]
        var marks = if (tagMark != null) inherited + tagMark else inherited
        if (style == null) return marks
        var color: String? = null
        var fontSize = TextStyleAttrs.UNSET_FONT_SIZE
        style.split(';').forEach { declaration ->
            val key = declaration.substringBefore(':').trim().lowercase()
            val value = declaration.substringAfter(':', "").trim().lowercase()
            when (key) {
                "font-weight" -> marks = when {
                    value == "bold" || value == "bolder" || (value.toIntOrNull() ?: 0) >= BOLD_WEIGHT ->
                        marks + BoldMark()
                    value == "normal" || value == "lighter" || value.toIntOrNull() != null ->
                        marks - BoldMark()
                    else -> marks
                }
                "font-style" -> marks = when (value) {
                    "italic", "oblique" -> marks + ItalicMark()
                    "normal" -> marks - ItalicMark()
                    else -> marks
                }
                "text-decoration", "text-decoration-line" -> {
                    if ("underline" in value) marks = marks + UnderlineMark()
                    if ("line-through" in value) marks = marks + StrikeMark()
                    if (value == "none") marks = marks - UnderlineMark() - StrikeMark()
                }
                // any explicit non-transparent background reads as a highlight
                "background-color" -> if (value != "transparent" && ExportHtml.safeColor(hexOf(value)) != null) {
                    marks = marks + HighlightMark()
                }
                ExportHtml.COLOR -> color = ExportHtml.safeColor(hexOf(value))?.takeIf { it !in DEFAULT_TEXT_COLORS }
                ExportHtml.FONT_SIZE -> fontSize = parseFontSize(value) ?: fontSize
            }
        }
        if (color != null || fontSize != TextStyleAttrs.UNSET_FONT_SIZE) {
            marks = marks.filterNotTo(mutableSetOf()) { it is TextStyleMark } +
                TextStyleMark(TextStyleAttrs(color = color ?: "", fontSize = fontSize))
        }
        return marks
    }

    /** [value] with an `rgb()`/`rgba()` form converted to hex — browser copies inline computed
     *  styles, which always come back as `rgb(36, 36, 36)` — passed through otherwise. */
    private fun hexOf(value: String): String {
        val match = RGB_COLOR.find(value) ?: return value
        val channels = match.groupValues[1].split(',').map { it.trim().toIntOrNull() ?: return value }
        if (channels.size < 3 || channels.any { it !in 0..255 }) return value
        return buildString {
            append('#')
            channels.take(3).forEach { channel ->
                append(channel.toString(16).padStart(2, '0'))
            }
        }
    }

    /** A `font-size` in px or pt (Google Docs emits pt, with float noise) as whole pixels. */
    private fun parseFontSize(value: String): Int? = when {
        value.endsWith("px") -> value.removeSuffix("px").trim().toDoubleOrNull()
        value.endsWith("pt") -> value.removeSuffix("pt").trim().toDoubleOrNull()?.times(PX_PER_PT)
        else -> null
    }?.let { kotlin.math.round(it).toInt() }?.takeIf { it in 1..MAX_FONT_SIZE }

    private fun alignOf(element: Element): TextAlign {
        val style = element.attrs["style"] ?: return TextAlign.Left
        val value = style.split(';')
            .firstOrNull { it.substringBefore(':').trim().lowercase() == ExportHtml.TEXT_ALIGN }
            ?.substringAfter(':')?.trim()?.lowercase()
        return when (value) {
            "center" -> TextAlign.Center
            "right" -> TextAlign.Right
            "justify" -> TextAlign.Justify
            else -> TextAlign.Left
        }
    }

    /** HTML collapses whitespace runs to one space outside `pre`. */
    private fun collapseWhitespace(text: String): String =
        text.replace(WHITESPACE_RUN, " ")

    /** Adjacent text nodes with identical marks merge, so tag boundaries leave no seams. */
    private fun mergeAdjacent(nodes: List<BaseText>): List<BaseText> {
        val merged = mutableListOf<BaseText>()
        nodes.forEach { node ->
            val last = merged.lastOrNull()
            if (node is Text && last is Text && last.marks == node.marks) {
                merged[merged.size - 1] = last.copy(text = last.text + node.text)
            } else {
                merged += node
            }
        }
        return merged
    }

    private companion object {
        const val ROOT = "#root"
        const val DATA_TYPE = "data-type"
        const val DATA_ID = "data-id"
        const val DATA_CHECKED = "data-checked"
        const val TASK_LIST_TYPE = "taskList"
        const val LANGUAGE_CLASS_PREFIX = "language-"
        const val MAX_ENTITY_LENGTH = 12
        const val MAX_FONT_SIZE = 512
        const val BOLD_WEIGHT = 600
        const val PX_PER_PT = 4.0 / 3.0

        /**
         * Colors treated as "no color set": Google Docs stamps `color:#000000` on every span,
         * styled or not, so importing it verbatim would coat whole pastes in explicit black —
         * wrong in dark themes and pure noise in the document.
         */
        val DEFAULT_TEXT_COLORS = setOf("#000000", "#000")

        /** The named entities that show up in real clipboard HTML (#152) — punctuation and
         *  symbols producers substitute for typed characters — plus the XML five. */
        val NAMED_ENTITIES = mapOf(
            "amp" to "&",
            "lt" to "<",
            "gt" to ">",
            "quot" to "\"",
            "apos" to "'",
            "nbsp" to " ",
            "rsquo" to "\u2019",
            "lsquo" to "\u2018",
            "rdquo" to "\u201D",
            "ldquo" to "\u201C",
            "ndash" to "\u2013",
            "mdash" to "\u2014",
            "hellip" to "\u2026",
            "middot" to "\u00B7",
            "bull" to "\u2022",
            "deg" to "\u00B0",
            "copy" to "\u00A9",
            "reg" to "\u00AE",
            "trade" to "\u2122",
            "times" to "\u00D7",
            "laquo" to "\u00AB",
            "raquo" to "\u00BB",
            "shy" to "",
        )

        val RAW_TEXT_ELEMENTS = setOf("script", "style")
        val DROPPED_ELEMENTS = setOf("script", "style", "head", "title", "template", "svg")
        val VOID_ELEMENTS = setOf("br", "img", "input", "hr", "meta", "link", "col", "area", "base", "embed", "source", "track", "wbr")
        val BLOCK_TAGS = setOf(
            "p", "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "blockquote",
            "table", "img", "pre", "div",
        )
        val LIST_CONTAINER_TAGS = setOf("ul", "ol")
        val INLINE_TAGS = setOf("span", "a", "b", "strong", "i", "em", "u", "ins", "s", "del", "strike", "mark", "font", "sub", "sup", "code", "small")
        val RGB_COLOR = Regex("""^rgba?\(([^)]+)\)$""")
        val CELL_TAGS = setOf("td", "th")
        val TABLE_SECTION_TAGS = setOf("thead", "tbody", "tfoot")
        val WHITESPACE_RUN = Regex("""\s+""")
    }
}
