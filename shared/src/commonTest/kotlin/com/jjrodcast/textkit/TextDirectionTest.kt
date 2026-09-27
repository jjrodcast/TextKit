package com.jjrodcast.textkit

import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.core.export.MarkdownSerializer
import com.jjrodcast.textkit.editor.core.html.htmlToJson
import com.jjrodcast.textkit.editor.core.markdown.markdownToJson
import com.jjrodcast.textkit.editor.core.parser.Blockquote
import com.jjrodcast.textkit.editor.core.parser.BlockquoteAttrs
import com.jjrodcast.textkit.editor.core.parser.BulletedList
import com.jjrodcast.textkit.editor.core.parser.ListItem
import com.jjrodcast.textkit.editor.core.parser.Paragraph
import com.jjrodcast.textkit.editor.core.parser.Text
import com.jjrodcast.textkit.editor.core.parser.TextDirection
import com.jjrodcast.textkit.editor.core.parser.TextEditorDocument
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TextDirectionTest {

    /** The top-level block nodes of the exported document. */
    private fun blocksOf(json: String): List<JsonObject> =
        Json.parseToJsonElement(json).jsonObject["content"]!!.jsonArray.map { it.jsonObject }

    private val JsonObject.type get() = this["type"]!!.jsonPrimitive.content

    private val JsonObject.dir get() = this["attrs"]?.jsonObject?.get("dir")?.jsonPrimitive?.content

    private val JsonObject.children get() = this["content"]!!.jsonArray.map { it.jsonObject }

    @Test
    fun paragraphDefaultsToLtr() {
        val blocks = blocksOf(editorFrom(SampleDocuments.SINGLE_PARAGRAPH).toJson())
        assertEquals("ltr", blocks.first().dir)
    }

    @Test
    fun unknownDirectionCoercesToLtr() {
        val doc = """{"type":"doc","content":[
            {"type":"paragraph","attrs":{"dir":"sideways"},"content":[{"type":"text","text":"x"}]}
        ]}"""
        assertEquals("ltr", blocksOf(editorFrom(doc).toJson()).first().dir)
    }

    @Test
    fun rtlParagraphAndHeadingSurviveRoundTrip() {
        val doc = """{"type":"doc","content":[
            {"type":"paragraph","attrs":{"dir":"rtl"},"content":[{"type":"text","text":"مرحبا"}]},
            {"type":"heading","attrs":{"level":1,"dir":"rtl"},"content":[{"type":"text","text":"عنوان"}]},
            {"type":"paragraph","content":[{"type":"text","text":"hello"}]}
        ]}"""
        val blocks = blocksOf(editorFrom(doc).toJson())
        assertEquals(listOf("rtl", "rtl", "ltr"), blocks.map { it.dir })
    }

    @Test
    fun rtlListsSurviveRoundTripOnTheListNode() {
        val doc = """{"type":"doc","content":[
            {"type":"bulletList","attrs":{"dir":"rtl"},"content":[
              {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"واحد"}]}]},
              {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"اثنان"}]}]}
            ]},
            {"type":"paragraph","content":[{"type":"text","text":"between"}]},
            {"type":"orderedList","attrs":{"start":1,"dir":"rtl"},"content":[
              {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"one"}]}]}
            ]}
        ]}"""
        val blocks = blocksOf(editorFrom(doc).toJson())
        assertEquals(listOf("bulletList", "paragraph", "orderedList"), blocks.map { it.type })
        assertEquals(listOf("rtl", "ltr", "rtl"), blocks.map { it.dir })
    }

    @Test
    fun rtlBlockquoteSurvivesRoundTrip() {
        val doc = """{"type":"doc","content":[
            {"type":"blockquote","attrs":{"dir":"rtl"},"content":[
              {"type":"paragraph","content":[{"type":"text","text":"اقتباس"}]},
              {"type":"paragraph","content":[{"type":"text","text":"ثاني"}]}
            ]}
        ]}"""
        val quote = blocksOf(editorFrom(doc).toJson()).single()
        assertEquals("blockquote", quote.type)
        assertEquals("rtl", quote.dir)
        // The paragraphs inside inherit the quote's direction.
        assertEquals(listOf("rtl", "rtl"), quote.children.map { it.dir })
    }

    /** A bullet list whose first item holds a nested ordered list: flat order p1, c1, c2, p2. */
    private fun nestedListDoc(parentDir: String = "ltr", nestedDir: String = "ltr") = """{"type":"doc","content":[
        {"type":"bulletList","attrs":{"dir":"$parentDir"},"content":[
          {"type":"listItem","content":[
            {"type":"paragraph","content":[{"type":"text","text":"parent one"}]},
            {"type":"orderedList","attrs":{"start":1,"dir":"$nestedDir"},"content":[
              {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"child one"}]}]},
              {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"child two"}]}]}
            ]}
          ]},
          {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"parent two"}]}]}
        ]},
        {"type":"paragraph","content":[{"type":"text","text":"after"}]}
    ]}"""

    /** (parent list dir, nested list dir) read from an exported [nestedListDoc]. */
    private fun nestedDirsOf(json: String): Pair<String?, String?> {
        val list = blocksOf(json).first()
        val nested = list.children.first().children.first { it.type == "orderedList" }
        return list.dir to nested.dir
    }

    private val rtl = TextDirection.Rtl
    private val ltr = TextDirection.Ltr

    @Test
    fun nestedListKeepsItsOwnDirectionOnLoad() {
        // A nested list is an independent `dir` node: it does not inherit its parent list's RTL.
        val editor = editorFrom(nestedListDoc(parentDir = "rtl", nestedDir = "ltr"))
        assertEquals(listOf(rtl, ltr, ltr, rtl, ltr), editor.getParagraphs().map { it.textDirection })
        assertEquals("rtl" to "ltr", nestedDirsOf(editor.toJson()))

        val both = editorFrom(nestedListDoc(parentDir = "rtl", nestedDir = "rtl"))
        assertEquals("rtl" to "rtl", nestedDirsOf(both.toJson()))
    }

    @Test
    fun caretInNestedListOnlyFlipsTheNestedList() {
        val editor = editorFrom(nestedListDoc())
        editor.setTextDirection(TextRange(editor.offsetOf("child two") + 1), TextDirection.Rtl)

        assertEquals(listOf(ltr, rtl, rtl, ltr, ltr), editor.getParagraphs().map { it.textDirection })
        assertEquals("ltr" to "rtl", nestedDirsOf(editor.toJson()))
        // Survives a reload: the parent is not pulled to RTL by its nested list.
        assertEquals("ltr" to "rtl", nestedDirsOf(editorFrom(editor.toJson()).toJson()))
    }

    @Test
    fun caretInParentListSkipsTheNestedList() {
        val editor = editorFrom(nestedListDoc())
        // Caret on the item AFTER the nested list: the walk back must skip the nested items.
        editor.setTextDirection(TextRange(editor.offsetOf("parent two") + 1), TextDirection.Rtl)

        assertEquals(listOf(rtl, ltr, ltr, rtl, ltr), editor.getParagraphs().map { it.textDirection })
        assertEquals("rtl" to "ltr", nestedDirsOf(editor.toJson()))
        val reloaded = editorFrom(editor.toJson())
        assertEquals(listOf(rtl, ltr, ltr, rtl, ltr), reloaded.getParagraphs().map { it.textDirection })
    }

    @Test
    fun selectionAcrossParentAndNestedFlipsBoth() {
        val editor = editorFrom(nestedListDoc())
        val from = editor.offsetOf("parent one") + 1
        val to = editor.offsetOf("child one") + 1
        editor.setTextDirection(TextRange(from, to), TextDirection.Rtl)
        assertEquals(listOf(rtl, rtl, rtl, rtl, ltr), editor.getParagraphs().map { it.textDirection })
        assertEquals("rtl" to "rtl", nestedDirsOf(editor.toJson()))
    }

    @Test
    fun adjacentListOfAnotherKindIsASeparateNode() {
        val doc = """{"type":"doc","content":[
            {"type":"bulletList","content":[
              {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"bullet"}]}]}
            ]},
            {"type":"orderedList","attrs":{"start":1},"content":[
              {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"number"}]}]}
            ]}
        ]}"""
        val editor = editorFrom(doc)
        editor.setTextDirection(TextRange(editor.offsetOf("number") + 1), TextDirection.Rtl)
        val blocks = blocksOf(editor.toJson())
        assertEquals(listOf("bulletList", "orderedList"), blocks.map { it.type })
        assertEquals(listOf("ltr", "rtl"), blocks.map { it.dir })
    }

    @Test
    fun nestedListDirectionRoundTripsThroughMarkdownAndHtml() {
        for ((parent, nested) in listOf("rtl" to "ltr", "ltr" to "rtl", "rtl" to "rtl")) {
            val editor = editorFrom(nestedListDoc(parent, nested))
            val md = editor.toMarkdown()
            assertEquals(parent to nested, nestedDirsOf(editorFrom(markdownToJson(md)).toJson()), md)
            val html = editor.toHtml()
            assertEquals(parent to nested, nestedDirsOf(editorFrom(htmlToJson(html)).toJson()), html)
        }
    }

    @Test
    fun markdownStatesLtrOnANestedListInsideAnRtlList() {
        // The outer <div dir="rtl"> would otherwise make Markdown consumers render the LTR nested
        // list right-to-left — same reason the HTML export states dir="ltr".
        val md = editorFrom(nestedListDoc(parentDir = "rtl", nestedDir = "ltr")).toMarkdown()
        assertTrue(md.startsWith("<div dir=\"rtl\">\n\n- parent one\n    <div dir=\"ltr\">\n\n    1. child one"), md)
        assertEquals("rtl" to "ltr", nestedDirsOf(editorFrom(markdownToJson(md)).toJson()), md)
    }

    @Test
    fun markdownKeepsAnRtlWrapperOnANestedRtlListInsideAnRtlList() {
        // A nested list never inherits on import, so an RTL one needs its own wrapper.
        val md = editorFrom(nestedListDoc(parentDir = "rtl", nestedDir = "rtl")).toMarkdown()
        assertTrue(md.contains("    <div dir=\"rtl\">\n\n    1. child one"), md)
        assertFalse(md.contains("dir=\"ltr\""), md)
    }

    @Test
    fun markdownLeavesAnLtrNestedListBareInsideAnLtrList() {
        val md = editorFrom(nestedListDoc()).toMarkdown()
        assertFalse(md.contains("<div"), md)
    }

    @Test
    fun markdownStatesLtrOnAListInsideAnRtlBlockquote() {
        val list = BulletedList(content = listOf(ListItem(listOf(Paragraph(content = listOf(Text("item")))))))
        val quote = Blockquote(content = listOf(list), attrs = BlockquoteAttrs(dir = TextDirection.Rtl))
        val md = MarkdownSerializer().serialize(TextEditorDocument(listOf(quote)))
        assertEquals("<div dir=\"rtl\">\n\n> <div dir=\"ltr\">\n>\n> - item\n>\n> </div>\n\n</div>", md)
        val reparsed = blocksOf(markdownToJson(md)).single()
        assertEquals("blockquote" to "rtl", reparsed.type to reparsed.dir)
        val inner = reparsed.children.single()
        assertEquals("bulletList" to "ltr", inner.type to inner.dir)
    }

    @Test
    fun markdownRtlWrapperAroundTaskListKeepsNestedListDirections() {
        // A task list has no `dir`, so the wrapper's RTL reaches its items' paragraphs — but a nested
        // bullet/ordered list is an independent node and keeps its own direction.
        val md = listOf(
            "<div dir=\"rtl\">",
            "",
            "- [ ] ltr nested",
            "    <div dir=\"ltr\">",
            "",
            "    - a",
            "",
            "    </div>",
            "- [x] rtl nested",
            "    <div dir=\"rtl\">",
            "",
            "    1. b",
            "",
            "    </div>",
            "- [ ] bare nested",
            "    - c",
            "",
            "</div>",
        ).joinToString("\n")
        val taskList = blocksOf(markdownToJson(md)).single()
        assertEquals("taskList", taskList.type)
        val items = taskList.children
        assertEquals(3, items.size, md)
        // Each item's own paragraph inherits the wrapper's RTL…
        assertEquals(listOf("rtl", "rtl", "rtl"), items.map { it.children.first().dir })
        // …while each nested list keeps its own dir: explicit LTR, explicit RTL, bare (default LTR).
        val nested = items.map { item -> item.children.first { it.type == "bulletList" || it.type == "orderedList" } }
        assertEquals(listOf("ltr", "rtl", "ltr"), nested.map { it.dir })
    }

    @Test
    fun taskListInsideRtlListWithLtrNestedListRoundTripsThroughMarkdown() {
        val doc = """{"type":"doc","content":[
            {"type":"bulletList","attrs":{"dir":"rtl"},"content":[
              {"type":"listItem","content":[
                {"type":"paragraph","content":[{"type":"text","text":"parent"}]},
                {"type":"taskList","content":[
                  {"type":"taskItem","attrs":{"checked":false},"content":[
                    {"type":"paragraph","content":[{"type":"text","text":"task"}]},
                    {"type":"bulletList","attrs":{"dir":"ltr"},"content":[
                      {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"leaf"}]}]}
                    ]}
                  ]}
                ]}
              ]}
            ]}
        ]}"""
        val editor = editorFrom(doc)
        val md = editor.toMarkdown()
        val reloaded = editorFrom(markdownToJson(md))
        assertEquals(
            editor.getParagraphs().map { it.textDirection },
            reloaded.getParagraphs().map { it.textDirection },
            md
        )
        assertEquals(TextDirection.Ltr, reloaded.getParagraphs().last().textDirection, md)
    }

    @Test
    fun htmlStatesLtrOnANestedListInsideAnRtlList() {
        // HTML inherits `dir`, so without it a browser would render the LTR nested list RTL.
        val html = editorFrom(nestedListDoc(parentDir = "rtl", nestedDir = "ltr")).toHtml()
        assertTrue(html.startsWith("<ul dir=\"rtl\">"), html)
        assertTrue(html.contains("<ol dir=\"ltr\">"), html)
    }

    @Test
    fun setDirectionOnParagraphWithCollapsedCaret() {
        val editor = editorFrom(SampleDocuments.TWO_PARAGRAPHS)
        val (changed, _) = editor.setTextDirection(TextRange(2), TextDirection.Rtl)
        assertTrue(changed)
        val blocks = blocksOf(editor.toJson())
        assertEquals(listOf("rtl", "ltr"), blocks.map { it.dir })
        assertEquals(TextDirection.Rtl, editor.getParagraphs()[0].textDirection)
        assertEquals(TextDirection.Ltr, editor.getParagraphs()[1].textDirection)
    }

    @Test
    fun reapplyingSameDirectionIsNoOp() {
        val editor = editorFrom(SampleDocuments.SINGLE_PARAGRAPH)
        assertFalse(editor.setTextDirection(TextRange(1), TextDirection.Ltr).first)
        assertTrue(editor.setTextDirection(TextRange(1), TextDirection.Rtl).first)
        assertFalse(editor.setTextDirection(TextRange(1), TextDirection.Rtl).first)
    }

    @Test
    fun setDirectionOnOneListItemFlipsTheWholeList() {
        val doc = """{"type":"doc","content":[
            {"type":"paragraph","content":[{"type":"text","text":"before"}]},
            {"type":"bulletList","content":[
              {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"alpha"}]}]},
              {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"beta"}]}]},
              {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"gamma"}]}]}
            ]},
            {"type":"paragraph","content":[{"type":"text","text":"after"}]}
        ]}"""
        val editor = editorFrom(doc)
        editor.setTextDirection(TextRange(editor.offsetOf("beta") + 1), TextDirection.Rtl)

        val directions = editor.getParagraphs().map { it.textDirection }
        assertEquals(
            listOf(TextDirection.Ltr, TextDirection.Rtl, TextDirection.Rtl, TextDirection.Rtl, TextDirection.Ltr),
            directions
        )
        assertEquals(listOf("ltr", "rtl", "ltr"), blocksOf(editor.toJson()).map { it.dir })

        // And back: LTR on the last item clears the whole list again.
        editor.setTextDirection(TextRange(editor.offsetOf("gamma") + 1), TextDirection.Ltr)
        assertEquals(listOf("ltr", "ltr", "ltr"), blocksOf(editor.toJson()).map { it.dir })
    }

    @Test
    fun setDirectionInsideBlockquoteFlipsTheWholeQuote() {
        val doc = """{"type":"doc","content":[
            {"type":"paragraph","content":[{"type":"text","text":"before"}]},
            {"type":"blockquote","content":[
              {"type":"paragraph","content":[{"type":"text","text":"first"}]},
              {"type":"paragraph","content":[{"type":"text","text":"second"}]}
            ]}
        ]}"""
        val editor = editorFrom(doc)
        editor.setTextDirection(TextRange(editor.offsetOf("first") + 1), TextDirection.Rtl)
        val blocks = blocksOf(editor.toJson())
        assertEquals(listOf("ltr", "rtl"), blocks.map { it.dir })
        assertEquals(listOf("rtl", "rtl"), blocks[1].children.map { it.dir })
    }

    @Test
    fun directionSurvivesTypingAndEnter() {
        val doc = """{"type":"doc","content":[
            {"type":"paragraph","attrs":{"dir":"rtl"},"content":[{"type":"text","text":"مرحبا"}]}
        ]}"""
        val editor = editorFrom(doc)
        val end = editor.text.length
        val caret = editor.typeText(end, " عالم")
        editor.typeText(caret.start, "\n")
        editor.typeText(editor.text.length, "سطر")
        val blocks = blocksOf(editor.toJson())
        assertEquals(2, blocks.size, editor.toJson())
        assertEquals(listOf("rtl", "rtl"), blocks.map { it.dir })
    }

    @Test
    fun enterInRtlListKeepsTheListRtl() {
        val doc = """{"type":"doc","content":[
            {"type":"bulletList","attrs":{"dir":"rtl"},"content":[
              {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"واحد"}]}]}
            ]}
        ]}"""
        val editor = editorFrom(doc)
        val caret = editor.typeText(editor.text.length, "\n")
        editor.typeText(caret.start, "اثنان")
        val list = blocksOf(editor.toJson()).single()
        assertEquals("bulletList", list.type)
        assertEquals(2, list.children.size, editor.toJson())
        assertEquals("rtl", list.dir)
        assertTrue(editor.getParagraphs().all { it.textDirection == TextDirection.Rtl })
    }

    @Test
    fun htmlExportEmitsDirOnlyForRtlBlocks() {
        val doc = """{"type":"doc","content":[
            {"type":"paragraph","attrs":{"dir":"rtl"},"content":[{"type":"text","text":"a"}]},
            {"type":"paragraph","content":[{"type":"text","text":"b"}]},
            {"type":"bulletList","attrs":{"dir":"rtl"},"content":[
              {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"c"}]}]}
            ]},
            {"type":"blockquote","attrs":{"dir":"rtl"},"content":[
              {"type":"paragraph","content":[{"type":"text","text":"d"}]}
            ]}
        ]}"""
        val html = editorFrom(doc).toHtml()
        assertTrue(html.startsWith("<p dir=\"rtl\">a</p><p>b</p><ul dir=\"rtl\">"), html)
        assertTrue(html.contains("<blockquote dir=\"rtl\">"), html)
    }

    @Test
    fun htmlImportReadsDirAttribute() {
        val json = htmlToJson(
            "<p dir=\"rtl\">مرحبا</p><h2 dir=\"RTL\">عنوان</h2><ol dir=\"rtl\"><li>x</li></ol>" +
                "<blockquote dir=\"rtl\"><p>q</p></blockquote><p>ltr</p>"
        )
        val blocks = blocksOf(editorFrom(json).toJson())
        assertEquals(listOf("rtl", "rtl", "rtl", "rtl", "ltr"), blocks.map { it.dir })
    }

    /** A document with every block kind that supports `dir`, all RTL, plus one LTR paragraph. */
    private val allRtlDoc = """{"type":"doc","content":[
        {"type":"paragraph","attrs":{"dir":"rtl"},"content":[{"type":"text","text":"مرحبا"}]},
        {"type":"heading","attrs":{"level":2,"dir":"rtl"},"content":[{"type":"text","text":"عنوان"}]},
        {"type":"bulletList","attrs":{"dir":"rtl"},"content":[
          {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"واحد"}]}]},
          {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"اثنان"}]}]}
        ]},
        {"type":"orderedList","attrs":{"start":1,"dir":"rtl"},"content":[
          {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"أول"}]}]}
        ]},
        {"type":"blockquote","attrs":{"dir":"rtl"},"content":[
          {"type":"paragraph","content":[{"type":"text","text":"اقتباس"}]}
        ]},
        {"type":"paragraph","content":[{"type":"text","text":"ltr"}]}
    ]}"""

    private val allRtlTypes = listOf("paragraph", "paragraph", "bulletList", "orderedList", "blockquote", "paragraph")
    private val allRtlDirs = listOf("rtl", "rtl", "rtl", "rtl", "rtl", "ltr")

    @Test
    fun markdownExportCarriesDirection() {
        val md = editorFrom(allRtlDoc).toMarkdown()
        // Paragraph / heading: inline-HTML block (GFM has no direction syntax).
        assertTrue(md.contains("<p dir=\"rtl\">مرحبا</p>"), md)
        // Headings export as paragraphs with a font size — the editor has no heading node on export.
        assertTrue(md.contains("dir=\"rtl\""), md)
        // Lists and quotes: one `<div dir="rtl">` wrapper, not a dir per item.
        assertTrue(md.contains("<div dir=\"rtl\">\n\n- واحد\n- اثنان\n\n</div>"), md)
        assertTrue(md.contains("<div dir=\"rtl\">\n\n1. أول\n\n</div>"), md)
        assertTrue(md.contains("<div dir=\"rtl\">\n\n> اقتباس\n\n</div>"), md)
        // LTR is the default and never emitted.
        assertTrue(md.endsWith("\n\nltr"), md)
    }

    @Test
    fun markdownExportCombinesDirAndAlignment() {
        val doc = """{"type":"doc","content":[
            {"type":"paragraph","attrs":{"dir":"rtl","textAlign":"center"},"content":[{"type":"text","text":"x"}]}
        ]}"""
        assertEquals("<p dir=\"rtl\" style=\"text-align:center\">x</p>", editorFrom(doc).toMarkdown())
    }

    @Test
    fun markdownImportReadsDirectionFallbacks() {
        val md = listOf(
            "<p dir=\"rtl\">مرحبا</p>",
            "<h2 dir=\"rtl\" style=\"text-align:center\">عنوان</h2>",
            "<div dir=\"rtl\">\n\n- واحد\n- اثنان\n\n</div>",
            "<div dir=\"rtl\">\n\n> اقتباس\n\n</div>",
            "plain",
        ).joinToString("\n\n")
        val blocks = blocksOf(markdownToJson(md))
        assertEquals(listOf("paragraph", "heading", "bulletList", "blockquote", "paragraph"), blocks.map { it.type })
        assertEquals(listOf("rtl", "rtl", "rtl", "rtl", "ltr"), blocks.map { it.dir })
        assertEquals("center", blocks[1]["attrs"]!!.jsonObject["textAlign"]!!.jsonPrimitive.content)
        assertEquals(2, blocks[2].children.size)
    }

    @Test
    fun markdownRoundTripPreservesDirection() {
        val md = editorFrom(allRtlDoc).toMarkdown()
        val reloaded = editorFrom(markdownToJson(md))
        val blocks = blocksOf(reloaded.toJson())
        assertEquals(allRtlTypes, blocks.map { it.type }, md)
        assertEquals(allRtlDirs, blocks.map { it.dir }, md)
        // No exact text comparison: the Markdown import drops `<span style>` (the exported heading's
        // font size), a pre-existing Markdown loss unrelated to direction.
    }

    @Test
    fun htmlRoundTripPreservesDirection() {
        val html = editorFrom(allRtlDoc).toHtml()
        val reloaded = editorFrom(htmlToJson(html))
        val blocks = blocksOf(reloaded.toJson())
        assertEquals(allRtlTypes, blocks.map { it.type }, html)
        assertEquals(allRtlDirs, blocks.map { it.dir }, html)
        assertEquals(html, reloaded.toHtml())
    }

    @Test
    fun searchMarkTypeReportsDirection() {
        val doc = """{"type":"doc","content":[
            {"type":"paragraph","attrs":{"dir":"rtl"},"content":[{"type":"text","text":"مرحبا"}]},
            {"type":"paragraph","content":[{"type":"text","text":"hello"}]}
        ]}"""
        val editor = editorFrom(doc)
        assertEquals(TextDirection.Rtl, editor.getSearchMarkType(TextRange(2)).textDirection)
        assertEquals(TextDirection.Ltr, editor.getSearchMarkType(editor.rangeOf("hello")).textDirection)
        // A selection over both paragraphs is "mixed".
        assertEquals(null, editor.getSearchMarkType(TextRange(0, editor.text.length)).textDirection)
    }
}
