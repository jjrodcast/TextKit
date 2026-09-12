package com.jjrodcast.textkit

import com.jjrodcast.textkit.editor.core.html.HtmlParser
import com.jjrodcast.textkit.editor.core.html.htmlToJson
import com.jjrodcast.textkit.editor.core.parser.BaseText
import com.jjrodcast.textkit.editor.core.parser.BoldMark
import com.jjrodcast.textkit.editor.core.parser.BulletedList
import com.jjrodcast.textkit.editor.core.parser.Heading
import com.jjrodcast.textkit.editor.core.parser.ItalicMark
import com.jjrodcast.textkit.editor.core.parser.ListItem
import com.jjrodcast.textkit.editor.core.parser.Paragraph
import com.jjrodcast.textkit.editor.core.parser.Text
import com.jjrodcast.textkit.editor.core.parser.TextEditorDocument
import com.jjrodcast.textkit.editor.core.parser.TextStyleMark
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The importer against real captured clipboard HTML (#152). Every fixture must import with its
 * visible structure and formatting intact — and, just as important, WITHOUT formatting the
 * producer's dialect merely implies: Google Docs wraps whole documents in
 * `<b style="font-weight:normal">` and stamps a default `color:#000000` on every span.
 */
class WildPasteTest {

    private fun allTexts(doc: TextEditorDocument): List<Text> {
        val texts = mutableListOf<Text>()
        fun visitInline(nodes: List<BaseText>) {
            nodes.forEach { node ->
                when (node) {
                    is Text -> texts += node
                    is ListItem -> node.content.forEach { visitBlock(it) }
                    else -> Unit
                }
            }
        }
        doc.content.forEach { visitBlock(it, ::visitInline) }
        return texts
    }

    private fun visitBlock(
        block: com.jjrodcast.textkit.editor.core.parser.BaseParagraph,
        visitInline: (List<BaseText>) -> Unit = { },
    ) {
        when (block) {
            is Paragraph -> visitInline(block.content)
            is Heading -> visitInline(block.content)
            is BulletedList -> visitInline(block.content)
            is com.jjrodcast.textkit.editor.core.parser.Blockquote -> block.content.forEach { visitBlock(it, visitInline) }
            else -> Unit
        }
    }

    @Test
    fun google_docs_paste_keeps_structure_and_formatting() {
        val doc = HtmlParser().parse(PasteFixtures.GOOGLE_DOCS)

        // structure: the template's headings and bullet lists survive
        val headings = doc.content.filterIsInstance<Heading>()
        assertTrue(headings.isNotEmpty(), "headings must survive")
        val lists = doc.content.filterIsInstance<BulletedList>()
        assertTrue(lists.isNotEmpty(), "bullet lists must survive")
        assertTrue(lists.sumOf { it.content.size } >= 6, "list items must survive")

        // content: nothing visible dropped
        val text = allTexts(doc).joinToString(separator = " ") { it.text }
        for (phrase in listOf("Hello", "Your Name", "Skills", "Experience", "Aenean ac interdum nisi")) {
            assertTrue(phrase in text, "'$phrase' must survive the import")
        }
    }

    @Test
    fun google_docs_dialect_implies_no_phantom_formatting() {
        val doc = HtmlParser().parse(PasteFixtures.GOOGLE_DOCS)
        val texts = allTexts(doc)

        // the font-weight:normal document wrapper must not bold the world; the template's body
        // text is regular weight
        val lorem = texts.first { "Lorem ipsum" in it.text }
        assertTrue(lorem.marks.none { it is BoldMark }, "body text must not be bold, got ${lorem.marks}")

        // the styled greeting IS colored — real formatting survives (font sizes too: Google's
        // pt values arrive as whole px)
        val hello = texts.first { it.text == "Hello" }
        val style = hello.marks.filterIsInstance<TextStyleMark>().single()
        assertEquals("#f75d5d", style.attrs.color)
        assertEquals(19, style.attrs.fontSize)

        // producers write punctuation as named entities; they must decode
        assertTrue(texts.any { "I\u2019m Your Name" in it.text }, "&rsquo; must decode to a real apostrophe")

        // the default black Google stamps on everything is NOT imported as an explicit color
        assertTrue(
            texts.none { t -> t.marks.filterIsInstance<TextStyleMark>().any { it.attrs.color == "#000000" } },
            "the producer's default #000000 must not become explicit color marks",
        )

        // font-weight:700 spans read as bold, font-style:italic as italic
        assertTrue(texts.any { t -> t.marks.any { it is BoldMark } }, "700-weight spans must read as bold")
        assertTrue(texts.any { t -> t.marks.any { it is ItalicMark } }, "italic spans must read as italic")
    }

    @Test
    fun the_fixture_imports_loads_and_round_trips() {
        val e = editorFrom(htmlToJson(PasteFixtures.GOOGLE_DOCS))
        assertTrue(e.text.contains("Experience"))
        assertEquals(e.toJson(), editorFrom(e.toJson()).toJson())
    }

    @Test
    fun medium_article_paste_keeps_structure_without_the_site_theme() {
        val doc = HtmlParser().parse(PasteFixtures.MEDIUM_ARTICLE)
        val texts = allTexts(doc)
        val text = texts.joinToString(separator = " ") { it.text }

        // structure and content survive the div soup
        assertTrue(doc.content.filterIsInstance<Heading>().size >= 2, "title and subtitle headings must survive")
        for (phrase in listOf(
            "What Should Programmers Do While the AI Writes Code?",
            "Most every programmer is suffering the same growing pains right now.",
        )) {
            assertTrue(phrase in text, "'$phrase' must survive the import")
        }

        // the site theme must not become authored formatting: computed styles ride on every
        // container (rgb(36,36,36), 20px), and honoring them would coat the whole paste
        val body = texts.first { "growing pains" in it.text }
        assertTrue(body.marks.none { it is TextStyleMark }, "container theme styles must not import, got ${body.marks}")

        // genuinely inline formatting still reads: Medium emphasizes with <em>
        assertTrue(texts.any { t -> t.marks.any { it is ItalicMark } }, "em spans must read as italic")

        // svg icon chrome contributes no text
        assertTrue("Press enter or click" in text, "figcaption prose is kept")
    }

    @Test
    fun the_medium_fixture_imports_loads_and_round_trips() {
        val e = editorFrom(htmlToJson(PasteFixtures.MEDIUM_ARTICLE))
        assertTrue(e.text.contains("growing pains"))
        assertEquals(e.toJson(), editorFrom(e.toJson()).toJson())
    }
}
