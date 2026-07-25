package com.jjrodcast.textkit.editor.core.export

import com.jjrodcast.textkit.editor.core.parser.TextStyleAttrs.Companion.UNSET_FONT_SIZE
import com.jjrodcast.textkit.editor.core.parser.TextStyleMark

/**
 * HTML escaping and sanitisation shared by the export serializers.
 *
 * [HtmlSerializer] emits HTML for the whole document; [MarkdownSerializer] falls back to inline HTML
 * for the marks Markdown cannot express (underline, highlight, a colour/size `textStyle`) and for
 * unknown embeds. Both put user-supplied values into places the browser *acts on* — a link `href`, a
 * `style` colour — so the value must be validated, not merely escaped. Keeping that single filter here
 * means the two exporters can never drift into disagreeing about what is safe.
 */
internal object ExportHtml {

    /** Escapes text content. `&` must be replaced first so the other entities are not double-escaped. */
    fun escapeText(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    /** Escapes a value destined for a double-quoted attribute. */
    fun escapeAttribute(value: String): String = escapeText(value).replace("\"", "&quot;")

    /** Returns [href] when its scheme is safe (or it is a relative URL), otherwise `null`. */
    fun safeHref(href: String): String? {
        val trimmed = href.trim()
        if (trimmed.isEmpty()) return null
        val scheme = schemeOf(trimmed) ?: return trimmed // no scheme → relative URL, safe
        return trimmed.takeIf { scheme in SAFE_SCHEMES }
    }

    /** Returns [color] when it is a valid `#rgb` / `#rgba` / `#rrggbb` / `#rrggbbaa` hex value. */
    fun safeColor(color: String): String? = color.trim().takeIf { HEX_COLOR.matches(it) }

    /**
     * The CSS declarations for a `textStyle` mark, or `null` when nothing valid is left to emit. The
     * colour is only emitted when it is a valid hex value ([safeColor]) and the font size only when it
     * is a sane positive number — otherwise that declaration is dropped, so nothing user-supplied can
     * inject extra CSS.
     */
    fun textStyleCss(mark: TextStyleMark): String? {
        val declarations = buildList {
            mark.attrs.color?.let(::safeColor)?.let { add("$COLOR:$it") }
            mark.attrs.fontSize
                .takeIf { it != UNSET_FONT_SIZE && it in MIN_FONT_SIZE..MAX_FONT_SIZE }
                ?.let { add("$FONT_SIZE:${it}px") }
        }
        return declarations.takeIf { it.isNotEmpty() }?.joinToString(separator = ";")
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

    const val COLOR = "color"
    const val FONT_SIZE = "font-size"

    private const val MIN_FONT_SIZE = 1
    private const val MAX_FONT_SIZE = 512

    /** URL schemes safe to keep on an exported link; anything else drops the link. */
    private val SAFE_SCHEMES = setOf("http", "https", "mailto", "tel")

    /** `#rgb`, `#rgba`, `#rrggbb` or `#rrggbbaa`. */
    private val HEX_COLOR = Regex("^#([0-9a-fA-F]{3,4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")
}
