package com.jjrodcast.textkit.editor.core.markdown

import com.jjrodcast.textkit.editor.core.export.ExportHtml
import com.jjrodcast.textkit.editor.core.parser.BaseParagraph
import com.jjrodcast.textkit.editor.core.parser.BaseText
import com.jjrodcast.textkit.editor.core.parser.Blockquote
import com.jjrodcast.textkit.editor.core.parser.BoldMark
import com.jjrodcast.textkit.editor.core.parser.BulletedList
import com.jjrodcast.textkit.editor.core.parser.EmbedBlock
import com.jjrodcast.textkit.editor.core.parser.EmbedTypes
import com.jjrodcast.textkit.editor.core.parser.HardBreak
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
import com.jjrodcast.textkit.editor.core.parser.TextDirection
import com.jjrodcast.textkit.editor.core.parser.TextEditorDocument
import com.jjrodcast.textkit.editor.core.parser.UnderlineMark
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Converts GitHub Flavored Markdown to the editor's document JSON — the inverse of
 * `MarkdownSerializer`, targeting exactly the subset that exporter emits so that
 * `markdownToJson(toMarkdown())` preserves every structure the two sides share.
 *
 * Like the export, the import is **lossy but text-safe**: anything the subset does not cover
 * degrades to its plain text, never to dropped content. Concretely — unknown inline HTML tags are
 * stripped around their content (`<u>`/`<mark>`/`<br>` are recognized, restoring underline,
 * highlight and hard breaks), a code span keeps its inner text, an inline image degrades to its
 * alt text, and any line no block rule claims is a paragraph. Soft line breaks inside a paragraph
 * join with a space, the CommonMark rendering.
 *
 * The exporter's block-level HTML fallbacks are recognized too: a one-line `<p>`/`<hN>` restores its
 * `dir` and `text-align`, and a `<div dir="rtl">` HTML block makes the lists, quotes and paragraphs
 * inside it right-to-left.
 */
fun markdownToJson(markdown: String): String =
    TEXT_EDITOR_JSON.encodeToString(TextEditorDocument.serializer(), MarkdownParser().parse(markdown))

internal class MarkdownParser {

    fun parse(markdown: String): TextEditorDocument =
        TextEditorDocument(parseBlocks(markdown.replace("\r\n", "\n").replace('\r', '\n').split("\n")))

    // ── Blocks ───────────────────────────────────────────────────────────────

    private fun parseBlocks(lines: List<String>): List<BaseParagraph> {
        val blocks = mutableListOf<BaseParagraph>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            when {
                line.isBlank() -> i++

                isTable(lines, i) -> i = parseTable(lines, i, blocks)

                CODE_FENCE.matches(line) -> i = parseCodeFence(lines, i, blocks)

                DIR_DIV_OPEN.matches(line) -> i = parseDirectionBlock(lines, i, blocks)

                HTML_BLOCK_LINE.matches(line) -> {
                    blocks += htmlBlock(line)
                    i++
                }

                HEADING.matches(line) -> {
                    val (hashes, rest) = HEADING.find(line)!!.destructured
                    val level = hashes.length.coerceIn(HeadingLevels.H1, HeadingLevels.H6)
                    blocks += Heading(attrs = HeadingAttrs(level = level), content = inline(rest))
                    i++
                }

                isQuote(line) -> i = parseQuote(lines, i, blocks)

                listMarker(line, indent = 0) != null -> i = parseList(lines, i, indent = 0, blocks)

                IMAGE_LINE.matches(line) -> {
                    val (alt, dest) = IMAGE_LINE.find(line)!!.destructured
                    blocks += imageEmbed(unescape(alt), unescapeDestination(dest))
                    i++
                }

                else -> i = parseParagraph(lines, i, blocks)
            }
        }
        return blocks
    }

    private fun parseParagraph(lines: List<String>, start: Int, blocks: MutableList<BaseParagraph>): Int {
        var i = start
        val run = mutableListOf<String>()
        while (i < lines.size) {
            val line = lines[i]
            if (line.isBlank() || HEADING.matches(line) || isQuote(line) ||
                listMarker(line, indent = 0) != null || isTable(lines, i) || CODE_FENCE.matches(line) ||
                DIR_DIV_OPEN.matches(line) || HTML_BLOCK_LINE.matches(line)
            ) break
            run += line.trim()
            i++
        }
        // Soft breaks join with a space (CommonMark); an explicit <br> in the text becomes a
        // HardBreak in the inline pass.
        val content = inline(run.joinToString(separator = " "))
        if (content.isNotEmpty()) blocks += Paragraph(content = content)
        return i
    }

    // ── Blockquote ───────────────────────────────────────────────────────────

    private fun isQuote(line: String) = line.startsWith(">")

    /** Consecutive `> `-prefixed lines (a bare `>` is a blank line inside the quote) unwrap one
     *  marker level and re-enter the block parser, so nested structure stays quoted. */
    private fun parseQuote(lines: List<String>, start: Int, blocks: MutableList<BaseParagraph>): Int {
        var i = start
        val inner = mutableListOf<String>()
        while (i < lines.size && isQuote(lines[i])) {
            inner += lines[i].removePrefix(">").removePrefix(" ")
            i++
        }
        val content = parseBlocks(inner)
        if (content.isNotEmpty()) blocks += Blockquote(content = content)
        return i
    }

    // ── Lists ────────────────────────────────────────────────────────────────

    private enum class ListKind { Bullet, Ordered, Task }

    private data class ListRow(val kind: ListKind, val checked: Boolean, val number: Int, val lead: String)

    /** The marker at exactly [indent] leading spaces, or null when the line is not a list row. */
    private fun listMarker(line: String, indent: Int): ListRow? {
        if (line.length <= indent || !line.startsWith(" ".repeat(indent))) return null
        val body = line.substring(indent)
        if (body.startsWith(" ")) return null
        TASK.find(body)?.let { return ListRow(ListKind.Task, it.groupValues[1].lowercase() == "x", 0, it.groupValues[2]) }
        BULLET.find(body)?.let { return ListRow(ListKind.Bullet, false, 0, it.groupValues[1]) }
        ORDERED.find(body)?.let { return ListRow(ListKind.Ordered, false, it.groupValues[1].toIntOrNull() ?: 1, it.groupValues[2]) }
        return null
    }

    /**
     * A run of list rows at [indent]. Every row's continuation — the lines indented one level
     * deeper, whether they follow directly (a tight sublist) or after a blank line (any other
     * nested block) — is dedented and re-enters the block parser as the item's further content.
     * Consecutive rows of the same kind group into one list node; a kind switch starts a new list.
     */
    private fun parseList(lines: List<String>, start: Int, indent: Int, blocks: MutableList<BaseParagraph>): Int {
        var i = start
        var kind: ListKind? = null
        var startNumber = 1
        val items = mutableListOf<BaseText>()

        fun flush() {
            when (kind) {
                ListKind.Bullet -> blocks += BulletedList(content = items.toList())
                ListKind.Ordered -> blocks += OrderedList(attrs = ListAttrs(start = startNumber), content = items.toList())
                ListKind.Task -> blocks += TaskList(content = items.toList())
                null -> Unit
            }
            items.clear()
        }

        while (i < lines.size) {
            val row = listMarker(lines[i], indent) ?: break
            i++
            val continuation = mutableListOf<String>()
            val deeper = indent + NESTED_INDENT
            while (i < lines.size) {
                val next = lines[i]
                when {
                    next.length > deeper && next.startsWith(" ".repeat(deeper)) -> {
                        continuation += next.substring(deeper)
                        i++
                    }
                    // A blank line continues the item only when deeper-indented content follows.
                    next.isBlank() && i + 1 < lines.size &&
                        lines[i + 1].length > deeper && lines[i + 1].startsWith(" ".repeat(deeper)) -> {
                        continuation += ""
                        i++
                    }
                    else -> break
                }
            }
            // An item whose lead paragraph carries dir/alignment is exported as `- <p …>…</p>`.
            val lead = if (HTML_BLOCK_LINE.matches(row.lead)) htmlBlock(row.lead) else Paragraph(content = inline(row.lead))
            val children = mutableListOf<BaseParagraph>(lead)
            children += parseBlocks(continuation)

            if (kind != null && kind != row.kind) flush()
            if (items.isEmpty()) startNumber = row.number.coerceAtLeast(1)
            kind = row.kind
            items += when (row.kind) {
                ListKind.Task -> TaskListItem(attrs = TaskListAttrs(checked = row.checked), content = children)
                else -> ListItem(content = children)
            }
        }
        flush()
        return i
    }

    // ── Block-level HTML (direction / alignment) ─────────────────────────────

    /**
     * A one-line `<p …>…</p>` or `<hN …>…</hN>` — the exporter's fallback for a paragraph/heading with
     * a `dir` or `text-align` Markdown cannot express — back to the node with those attrs.
     */
    private fun htmlBlock(line: String): BaseParagraph {
        val (tag, rawAttributes, body) = HTML_BLOCK_LINE.find(line.trim())!!.destructured
        val attributes = HTML_ATTRIBUTE.findAll(rawAttributes)
            .associate { it.groupValues[1].lowercase() to it.groupValues[2] }
        val dir = directionOf(attributes["dir"])
        val align = alignOf(attributes["style"])
        val content = inline(body)
        val name = tag.lowercase()
        return if (name == "p") {
            Paragraph(attrs = ParagraphAttrs(textAlign = align, dir = dir), content = content)
        } else {
            val level = name.drop(1).toInt().coerceIn(HeadingLevels.H1, HeadingLevels.H6)
            Heading(attrs = HeadingAttrs(level = level, textAlign = align, dir = dir), content = content)
        }
    }

    /**
     * A `<div dir="…">` HTML block (the exporter wraps an RTL list/blockquote in one, since their
     * Markdown syntax takes no attributes) up to its matching `</div>` line. The content re-enters the
     * block parser and every resulting block takes the div's direction. Unterminated, it runs to the
     * end of the input.
     */
    private fun parseDirectionBlock(lines: List<String>, start: Int, blocks: MutableList<BaseParagraph>): Int {
        val dir = directionOf(DIR_DIV_OPEN.find(lines[start])!!.groupValues[1])
        var i = start + 1
        var depth = 1
        val inner = mutableListOf<String>()
        while (i < lines.size) {
            val line = lines[i]
            if (DIV_CLOSE.matches(line)) depth--
            else if (DIV_OPEN.matches(line)) depth++
            i++
            if (depth == 0) break
            inner += line
        }
        blocks += parseBlocks(inner).map { it.withDirection(dir) }
        return i
    }

    /**
     * This block — a top-level block of a `<div dir="…">` wrapper — with writing direction [dir]. A
     * task list (no `dir` attr) passes it on to its items' content via [inheritDirection].
     */
    private fun BaseParagraph.withDirection(dir: TextDirection): BaseParagraph {
        if (dir == TextDirection.Ltr) return this
        return when (this) {
            is Paragraph -> copy(attrs = attrs.copy(dir = dir))
            is Heading -> copy(attrs = attrs.copy(dir = dir))
            is BulletedList -> copy(attrs = attrs.copy(dir = dir))
            is OrderedList -> copy(attrs = attrs.copy(dir = dir))
            is Blockquote -> copy(attrs = attrs.copy(dir = dir))
            is TaskList -> copy(content = content.map { item ->
                if (item is TaskListItem) item.copy(content = item.content.map { it.inheritDirection(dir) }) else item
            })
            else -> this
        }
    }

    /**
     * [dir] inherited by a block nested in a task item — mirroring what the load does: paragraphs,
     * headings, quotes and nested task lists take the container's direction, but a nested
     * `bulletList`/`orderedList` is an independent `dir` node and keeps its own (e.g. the LTR a
     * `<div dir="ltr">` inside the item stated).
     */
    private fun BaseParagraph.inheritDirection(dir: TextDirection): BaseParagraph = when (this) {
        is BulletedList, is OrderedList -> this
        else -> withDirection(dir)
    }

    private fun directionOf(value: String?): TextDirection =
        if (value?.trim()?.lowercase() == "rtl") TextDirection.Rtl else TextDirection.Ltr

    /** The `text-align` declaration of an inline `style`, or [TextAlign.Left] when absent/unknown. */
    private fun alignOf(style: String?): TextAlign {
        val value = style.orEmpty().split(';')
            .firstOrNull { it.substringBefore(':').trim().lowercase() == ExportHtml.TEXT_ALIGN }
            ?.substringAfter(':')?.trim()?.lowercase()
        return when (value) {
            "center" -> TextAlign.Center
            "right" -> TextAlign.Right
            "justify" -> TextAlign.Justify
            else -> TextAlign.Left
        }
    }

    // ── Tables ───────────────────────────────────────────────────────────────

    /** A `|` row directly followed by a `--- | ---` delimiter row opens a GFM table. */
    private fun isTable(lines: List<String>, i: Int): Boolean =
        i + 1 < lines.size && lines[i].trimStart().startsWith("|") && DELIMITER_ROW.matches(lines[i + 1])

    private fun parseTable(lines: List<String>, start: Int, blocks: MutableList<BaseParagraph>): Int {
        var i = start
        val rows = mutableListOf<List<String>>()
        while (i < lines.size && lines[i].trimStart().startsWith("|")) {
            if (i != start + 1) rows += splitCells(lines[i])
            i++
        }
        val columns = rows.maxOfOrNull { it.size } ?: return i
        val raw = buildJsonObject {
            put("type", EmbedTypes.Table)
            put("content", buildJsonArray {
                rows.forEachIndexed { rowIndex, cells ->
                    add(tableRow(cells, columns, header = rowIndex == 0))
                }
            })
        }
        blocks += EmbedBlock(embedType = EmbedTypes.Table, id = EmbedTypes.Table, raw = raw)
        return i
    }

    private fun tableRow(cells: List<String>, columns: Int, header: Boolean): JsonElement = buildJsonObject {
        put("type", "tableRow")
        put("content", buildJsonArray {
            repeat(columns) { index ->
                add(buildJsonObject {
                    put("type", if (header) "tableHeader" else "tableCell")
                    put("attrs", buildJsonObject {
                        put("colspan", 1)
                        put("rowspan", 1)
                        put("colwidth", JsonNull)
                    })
                    put("content", buildJsonArray {
                        add(
                            TEXT_EDITOR_JSON.encodeToJsonElement(
                                Paragraph.serializer(),
                                Paragraph(content = inline(cells.getOrElse(index) { "" })),
                            )
                        )
                    })
                })
            }
        })
    }

    /** The cells of one `| a | b |` row; edge pipes are dropped, escaped `\|` stays literal. */
    private fun splitCells(line: String): List<String> {
        val cells = mutableListOf<String>()
        val sb = StringBuilder()
        var i = 0
        val row = line.trim().removePrefix("|").removeSuffix("|")
        while (i < row.length) {
            val ch = row[i]
            when {
                ch == '\\' && i + 1 < row.length -> {
                    sb.append(ch).append(row[i + 1])
                    i += 2
                }
                ch == '|' -> {
                    cells += sb.toString().trim()
                    sb.clear()
                    i++
                }
                else -> {
                    sb.append(ch)
                    i++
                }
            }
        }
        cells += sb.toString().trim()
        return cells
    }

    /**
     * A fenced code block: everything between the opening fence and a closing run of at least as
     * many backticks is the code, verbatim — no inline parsing, no escapes, blank lines included.
     * The info string on the opening fence becomes `attrs.language`. An unterminated fence runs to
     * the end of the input (the CommonMark behavior).
     */
    private fun parseCodeFence(lines: List<String>, start: Int, blocks: MutableList<BaseParagraph>): Int {
        val (fence, language) = CODE_FENCE.find(lines[start])!!.destructured
        var i = start + 1
        val code = mutableListOf<String>()
        while (i < lines.size) {
            val line = lines[i]
            val close = CODE_FENCE.find(line)
            if (close != null && close.groupValues[1].length >= fence.length && close.groupValues[2].isBlank()) {
                i++
                break
            }
            code += line
            i++
        }
        val raw = buildJsonObject {
            put("type", EmbedTypes.CodeBlock)
            put("attrs", buildJsonObject { put("language", language.trim()) })
            put("content", buildJsonArray {
                if (code.isNotEmpty()) {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", code.joinToString(separator = "\n"))
                    })
                }
            })
        }
        blocks += EmbedBlock(embedType = EmbedTypes.CodeBlock, id = EmbedTypes.CodeBlock, raw = raw)
        return i
    }

    // ── Embeds ───────────────────────────────────────────────────────────────

    private fun imageEmbed(alt: String, src: String): EmbedBlock {
        val raw = buildJsonObject {
            put("type", EmbedTypes.Image)
            put("attrs", buildJsonObject {
                put("src", src)
                put("alt", alt)
            })
        }
        return EmbedBlock(embedType = EmbedTypes.Image, id = EmbedTypes.Image, raw = raw)
    }

    // ── Inline ───────────────────────────────────────────────────────────────

    private fun inline(text: String): List<BaseText> {
        val out = mutableListOf<BaseText>()
        parseInline(text, 0, text.length, emptySet(), out)
        return out
    }

    private fun parseInline(s: String, start: Int, end: Int, marks: Set<Mark>, out: MutableList<BaseText>) {
        val sb = StringBuilder()
        fun flush() {
            if (sb.isNotEmpty()) {
                out += Text(text = sb.toString(), marks = marks)
                sb.clear()
            }
        }

        var i = start
        while (i < end) {
            val ch = s[i]
            when {
                ch == '\\' && i + 1 < end -> {
                    sb.append(s[i + 1])
                    i += 2
                }

                ch == '&' -> {
                    val entity = ENTITIES.entries.firstOrNull { s.startsWith(it.key, i) }
                    if (entity != null) {
                        sb.append(entity.value)
                        i += entity.key.length
                    } else {
                        sb.append(ch)
                        i++
                    }
                }

                ch == '<' -> {
                    val consumed = htmlToken(s, i, end, marks, out, sb, ::flush)
                    if (consumed > 0) i += consumed else { sb.append(ch); i++ }
                }

                ch == '`' -> {
                    // A code span's inner text verbatim — the editor has no code mark, so the
                    // content survives as plain text (the documented degrade).
                    val close = indexOfUnescaped(s, '`', i + 1, end)
                    if (close >= 0) {
                        sb.append(s, i + 1, close)
                        i = close + 1
                    } else {
                        sb.append(ch)
                        i++
                    }
                }

                ch == '!' && i + 1 < end && s[i + 1] == '[' -> {
                    // An inline image degrades to its alt text (block-level images are embeds).
                    val link = linkSpan(s, i + 1, end)
                    if (link != null) {
                        parseInline(s, i + 2, link.labelEnd, marks, out.alsoFlush(sb, marks))
                        i = link.destEnd + 1
                    } else {
                        sb.append(ch)
                        i++
                    }
                }

                ch == '[' -> {
                    val link = linkSpan(s, i, end)
                    if (link != null) {
                        flush()
                        val href = unescapeDestination(s.substring(link.destStart, link.destEnd))
                        parseInline(s, i + 1, link.labelEnd, marks + LinkMark(LinkAttrs(href = href)), out)
                        i = link.destEnd + 1
                    } else {
                        sb.append(ch)
                        i++
                    }
                }

                ch == '*' || ch == '_' || ch == '~' -> {
                    val run = delimiterRun(s, i, end, ch)
                    val consumed = if (run > 0) emphasis(s, i, end, ch, run, marks, out, sb, ::flush) else 0
                    if (consumed > 0) i += consumed else { sb.append(ch); i++ }
                }

                else -> {
                    sb.append(ch)
                    i++
                }
            }
        }
        flush()
    }

    private fun MutableList<BaseText>.alsoFlush(sb: StringBuilder, marks: Set<Mark>): MutableList<BaseText> {
        if (sb.isNotEmpty()) {
            this += Text(text = sb.toString(), marks = marks)
            sb.clear()
        }
        return this
    }

    /** `<br>`, `<u>…</u>`, `<mark>…</mark>` are meaningful; any other tag is stripped around its
     *  content. Returns the characters consumed, or 0 when this `<` is not a tag at all. */
    private fun htmlToken(
        s: String,
        i: Int,
        end: Int,
        marks: Set<Mark>,
        out: MutableList<BaseText>,
        sb: StringBuilder,
        flush: () -> Unit,
    ): Int {
        val tagEnd = s.indexOf('>', i + 1)
        if (tagEnd < 0 || tagEnd >= end) return 0
        val tag = s.substring(i + 1, tagEnd)
        if (tag.isEmpty() || !(tag[0].isLetter() || tag[0] == '/')) return 0

        val name = tag.trimStart('/').substringBefore(' ').trimEnd('/').lowercase()
        // Only an opening/void <br> is a break; a stray closing </br> is invalid HTML and is
        // stripped like any other unknown tag.
        if (name == "br" && !tag.startsWith("/")) {
            flush()
            out += HardBreak(marks = marks)
            return tagEnd - i + 1
        }
        val mark = when (name) {
            "u" -> UnderlineMark()
            "mark" -> HighlightMark()
            else -> null
        }
        if (mark != null && !tag.startsWith("/")) {
            val close = s.indexOf("</$name>", tagEnd + 1)
            if (close in 0 until end) {
                flush()
                parseInline(s, tagEnd + 1, close, marks + mark, out)
                return close + name.length + 3 - i
            }
        }
        // Unknown tag (or an unmatched known one): strip the tag, keep the content flowing.
        return tagEnd - i + 1
    }

    private data class LinkSpan(val labelEnd: Int, val destStart: Int, val destEnd: Int)

    /** `[label](dest)` starting at [i] (which must hold `[`), or null when it does not close. */
    private fun linkSpan(s: String, i: Int, end: Int): LinkSpan? {
        val labelEnd = indexOfUnescaped(s, ']', i + 1, end)
        if (labelEnd < 0 || labelEnd + 1 >= end || s[labelEnd + 1] != '(') return null
        val destEnd = indexOfUnescaped(s, ')', labelEnd + 2, end)
        if (destEnd < 0) return null
        return LinkSpan(labelEnd = labelEnd, destStart = labelEnd + 2, destEnd = destEnd)
    }

    /**
     * An emphasis span: `**`/`__` bold, `*`/`_` italic, `~~` strikethrough. The opener must touch
     * the following text and the closer the preceding text; an `_` opener additionally requires a
     * non-word character before it, so snake_case stays literal. Returns the characters consumed,
     * or 0 when no valid closer exists and the delimiter is literal text.
     */
    private fun emphasis(
        s: String,
        i: Int,
        end: Int,
        ch: Char,
        run: Int,
        marks: Set<Mark>,
        out: MutableList<BaseText>,
        sb: StringBuilder,
        flush: () -> Unit,
    ): Int {
        val length = if (ch == '~') 2 else minOf(run, 2)
        if (ch == '~' && run < 2) return 0
        if (i + length >= end || s[i + length].isWhitespace()) return 0
        if (ch == '_' && i > 0 && s[i - 1].isLetterOrDigit()) return 0

        val delimiter = ch.toString().repeat(length)
        var close = indexOfUnescaped(s, ch, i + length, end)
        while (close >= 0) {
            if (s.startsWith(delimiter, close) && !s[close - 1].isWhitespace()) break
            close = indexOfUnescaped(s, ch, close + 1, end)
        }
        if (close < 0 || close == i + length) return 0

        val mark = when {
            ch == '~' -> StrikeMark()
            length == 2 -> BoldMark()
            else -> ItalicMark()
        }
        flush()
        parseInline(s, i + length, close, marks + mark, out)
        return close + length - i
    }

    private fun delimiterRun(s: String, i: Int, end: Int, ch: Char): Int {
        var run = 0
        while (i + run < end && s[i + run] == ch) run++
        return run
    }

    /** The first [target] at or after [from] that is not backslash-escaped, or -1. */
    private fun indexOfUnescaped(s: String, target: Char, from: Int, end: Int): Int {
        var i = from
        while (i < end) {
            when {
                s[i] == '\\' -> i += 2
                s[i] == target -> return i
                else -> i++
            }
        }
        return -1
    }

    private fun unescape(text: String): String = buildString {
        var i = 0
        while (i < text.length) {
            if (text[i] == '\\' && i + 1 < text.length) {
                append(text[i + 1])
                i += 2
            } else {
                append(text[i])
                i++
            }
        }
    }

    private fun unescapeDestination(dest: String): String = unescape(dest.trim())

    private companion object {
        val HEADING = Regex("""^(#{1,6}) (.*)$""")
        // Literal `]` stays escaped everywhere: the JS RegExp `u` flag rejects a lone bracket
        // that the JVM engine accepts ("Lone quantifier brackets").
        val TASK = Regex("""^- \[([ xX])\] (.*)$""")
        val BULLET = Regex("""^[-*+] (.*)$""")
        val ORDERED = Regex("""^(\d+)[.)] (.*)$""")
        val IMAGE_LINE = Regex("""^!\[(.*)\]\((.*)\)\s*$""")
        val DELIMITER_ROW = Regex("""^\s*\|?[\s:\-|]*-[\s:\-|]*\|?\s*$""")
        // CommonMark allows up to three leading spaces before a fence.
        val CODE_FENCE = Regex("""^ {0,3}(```+)(.*)$""")

        /** A one-line `<p …>…</p>` / `<hN …>…</hN>` block: tag, attributes, inner text. */
        val HTML_BLOCK_LINE = Regex("""^\s*<(p|h[1-6])((?:\s+[a-zA-Z-]+="[^"]*")*)\s*>(.*)</\1>\s*$""", RegexOption.IGNORE_CASE)
        val HTML_ATTRIBUTE = Regex("""([a-zA-Z-]+)="([^"]*)""")
        /** The exporter's direction wrapper: `<div dir="rtl">` alone on its line. */
        val DIR_DIV_OPEN = Regex("""^\s*<div\s+dir="([a-zA-Z]+)"\s*>\s*$""", RegexOption.IGNORE_CASE)
        /** Any other opening `<div …>` alone on its line — only tracked for nesting. */
        val DIV_OPEN = Regex("""^\s*<div\b[^>]*>\s*$""", RegexOption.IGNORE_CASE)
        val DIV_CLOSE = Regex("""^\s*</div>\s*$""", RegexOption.IGNORE_CASE)

        /** One nesting level of the exporter's indented item content. */
        const val NESTED_INDENT = 4

        val ENTITIES = mapOf(
            "&amp;" to "&",
            "&lt;" to "<",
            "&gt;" to ">",
            "&quot;" to "\"",
            "&#39;" to "'",
            "&nbsp;" to " ",
        )
    }
}
