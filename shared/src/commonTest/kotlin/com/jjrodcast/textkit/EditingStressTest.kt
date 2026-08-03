package com.jjrodcast.textkit

import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.components.TextEditorListItem
import com.jjrodcast.textkit.editor.components.TextEditorStyleItem
import com.jjrodcast.textkit.editor.core.TextKitEditorManager
import com.jjrodcast.textkit.editor.core.parser.TextAlign
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Deterministic stress test: long, seeded sequences of every editing operation — typing, breaks,
 * deletes, replaces, multiline pastes, list toggles and type switches, styles, alignment and colour
 * — over regular paragraphs, ordered/unordered/task lists and deeply nested documents. This is the
 * kind of churn that surfaces an intermittent editing bug.
 *
 * After every operation, five invariants hold:
 *
 * 1. No operation throws (#82, #89, #95).
 * 2. A decorator piece only ever sits at the start of its paragraph — a mid-line decorator is the
 *    corruption behind #67/#74/#82/#85/#97.
 * 3. No decorator text leaks into an exported text node: a decorator is presentation-only, so its
 *    tabs must never serialize as ordinary characters (#97, #99).
 * 4. The export carries no empty text nodes and no empty list nodes (#81).
 * 5. The export is a fixed point: `toJson()` → load → `toJson()` is unchanged (#57–#61, #79, #81,
 *    #87, #93, #99).
 *
 * A failure names the seed, step and operation, so a "random, can't reproduce" error becomes an
 * exact repro. The committed size keeps the run inside a couple of seconds on every target; raising
 * [SEEDS] locally widens the search without any other change.
 */
class EditingStressTest {

    /**
     * A small cross-platform run. The full sweep lives in `EditingStressSweepTest` (JVM only): it
     * blocks the event loop far too long for the browser test runner, whose watchdog kills a tab
     * that cannot answer a ping for two seconds.
     */
    @Test
    fun random_edit_sequences_keep_the_document_consistent() {
        EditingStress.run(SMOKE_SEEDS, SMOKE_OPS_PER_SEED)
    }

    private companion object {
        val SMOKE_SEEDS = 0 until 12
        const val SMOKE_OPS_PER_SEED = 50
    }
}

/** The stress machinery, shared by the cross-platform smoke run and the JVM-only full sweep. */
internal object EditingStress {

    fun run(seeds: IntRange, opsPerSeed: Int) {
        for (seed in seeds) {
            val editor = editorFrom(START_DOCS[seed % START_DOCS.size])
            val rng = Random(seed)
            for (step in 0 until opsPerSeed) {
                val (desc, action) = decideOp(editor, rng)
                val where = "seed=$seed step=$step doc=${seed % START_DOCS.size} op '$desc'"
                try {
                    action()
                } catch (t: Throwable) {
                    throw AssertionError("threw at $where: ${t::class.simpleName}: ${t.message}", t)
                }
                editor.assertInvariants(where)
            }
        }
    }

    /** Checks every structural invariant on the current document. */
    private fun TextKitEditorManager.assertInvariants(where: String) {
        val visible = text.replace("\n", "\\n").replace("\t", "\\t")
        getParagraphs().forEach { paragraph ->
            assertTrue(
                paragraph.children.drop(1).none { it.decorator != null },
                "mid-line decorator at $where: $visible",
            )
        }
        val once = toJson()
        assertTrue(!once.contains("\\t"), "decorator text leaked into the export at $where: $once")
        assertTrue(!once.contains("\"text\":\"\""), "empty text node at $where: $once")
        LIST_TYPES_IN_JSON.forEach { type ->
            assertTrue(!once.contains("\"content\":[],\"type\":\"$type\""), "empty $type at $where: $once")
        }
        assertEquals(once, editorFrom(once).toJson(), "toJson not idempotent at $where")
    }

    /** Decides the next operation from [rng] and returns its description plus a thunk that runs it. */
    private fun decideOp(editor: TextKitEditorManager, rng: Random): Pair<String, () -> Unit> {
        val len = editor.text.length
        // 17 values for 16 explicit branches plus the colour op in `else`.
        return when (rng.nextInt(17)) {
            0 -> {
                val at = rng.nextInt(len + 1)
                val text = randomText(rng)
                "insert '$text' @$at" to { editor.typeText(at, text); Unit }
            }

            1 -> {
                val at = rng.nextInt(len + 1)
                "break @$at" to { editor.typeText(at, "\n"); Unit }
            }

            2 -> {
                if (len == 0) return "delete <empty>" to {}
                val at = rng.nextInt(len)
                val length = rng.nextInt(1, minOf(12, len - at) + 1)
                "delete @$at len=$length" to { editor.deleteText(at, length); Unit }
            }

            3 -> {
                if (len == 0) return "replace <empty>" to {}
                val at = rng.nextInt(len)
                val remove = rng.nextInt(0, minOf(9, len - at) + 1)
                val text = randomText(rng)
                "replace @$at remove=$remove '$text'" to { editor.replaceText(at, remove, text); Unit }
            }

            4 -> {
                val range = randomRange(rng, len)
                val to = LIST_TYPES[rng.nextInt(LIST_TYPES.size)]
                "toList $to $range" to { editor.toListItem(range, TextEditorListItem.None, to); Unit }
            }

            5 -> {
                val range = randomRange(rng, len)
                val from = LIST_TYPES[rng.nextInt(LIST_TYPES.size)]
                "unList $from $range" to { editor.toListItem(range, from, TextEditorListItem.None); Unit }
            }

            6 -> {
                // Direct type switch, with no plain-paragraph step in between.
                val range = randomRange(rng, len)
                val from = LIST_TYPES[rng.nextInt(LIST_TYPES.size)]
                val to = LIST_TYPES[rng.nextInt(LIST_TYPES.size)]
                "switchList $from->$to $range" to { editor.toListItem(range, from, to); Unit }
            }

            7 -> {
                val range = randomRange(rng, len)
                val style = STYLES[rng.nextInt(STYLES.size)]
                "style $style $range" to { editor.applyStyle(range, style); Unit }
            }

            8 -> {
                val range = randomRange(rng, len)
                val style = STYLES[rng.nextInt(STYLES.size)]
                "unstyle $style $range" to { editor.removeStyle(range, editor.marksAt(range), style); Unit }
            }

            9 -> {
                // Inner line breaks take the multiline-paste path rather than the typed-break one.
                val at = rng.nextInt(len + 1)
                val text = buildString {
                    append(randomText(rng)).append('\n').append(randomText(rng))
                    if (rng.nextBoolean()) append('\n').append(randomText(rng))
                }
                "paste multiline @$at" to { editor.typeText(at, text); Unit }
            }

            10 -> {
                val range = randomRange(rng, len)
                val align = ALIGNS[rng.nextInt(ALIGNS.size)]
                "align $align $range" to { editor.setTextAlign(range, align); Unit }
            }

            11 -> {
                val range = randomRange(rng, len)
                val href = if (rng.nextBoolean()) "https://example.dev" else ""
                "link '$href' $range" to { editor.setLink(range, href); Unit }
            }

            12 -> {
                // Tokens replace the trigger text the caller matched, which always lies inside one
                // paragraph's own content — never across a decorator.
                val range = contentRange(editor, rng) ?: return "mention <no content>" to {}
                "mention $range" to { editor.insertMention(id = "u1", label = "Jorge", replaceRange = range); Unit }
            }

            13 -> {
                val range = contentRange(editor, rng) ?: return "hashtag <no content>" to {}
                "hashtag $range" to {
                    editor.insertToken(nodeType = "hashtag", id = "h1", label = "kt", replaceRange = range)
                    Unit
                }
            }

            14 -> {
                val at = rng.nextInt(len + 1)
                "embed @$at" to { editor.insertEmbed("table", TABLE_BLOCK, label = "T", at = TextRange(at)); Unit }
            }

            15 -> {
                // Removal takes the placeholder's own range, the way the caller gets it.
                val at = if (len == 0) 0 else rng.nextInt(len)
                val embed = editor.embedAt(at) ?: return "unembed <none>" to {}
                "unembed @$at" to { editor.removeEmbedAt(embed.range); Unit }
            }

            else -> {
                val range = randomRange(rng, len)
                val color = if (rng.nextBoolean()) "#ff0000" else null
                "color $color $range" to { editor.setColor(range, color); Unit }
            }
        }
    }

    /**
     * A range inside one paragraph's own content — what a token's caller passes, having matched the
     * trigger text the user typed. Returns null while no paragraph has any content yet.
     */
    private fun contentRange(editor: TextKitEditorManager, rng: Random): TextRange? {
        var offset = 0
        val spans = editor.getParagraphs().map { paragraph ->
            val start = offset
            val length = paragraph.children.sumOf { it.text.length }
            offset += length
            val decorator = paragraph.children.first().decorator
            val contentStart = start + if (decorator != null) paragraph.children.first().text.length else 0
            val contentEnd = (start + length) - if (paragraph.children.last().text.endsWith("\n")) 1 else 0
            contentStart to maxOf(contentStart, contentEnd)
        }.filter { it.second > it.first }
        if (spans.isEmpty()) return null
        val (from, to) = spans[rng.nextInt(spans.size)]
        val start = from + rng.nextInt(to - from)
        return TextRange(start, minOf(to, start + rng.nextInt(0, 4)))
    }

    private fun randomText(rng: Random): String {
        val n = rng.nextInt(1, 5)
        return buildString {
            repeat(n) { append(ALPHABET[rng.nextInt(ALPHABET.length)]) }
        }
    }

    private fun randomRange(rng: Random, len: Int): TextRange {
        if (len == 0) return TextRange(0)
        val a = rng.nextInt(len)
        val b = a + rng.nextInt(0, minOf(10, len - a) + 1)
        return TextRange(a, b)
    }

    private const val ALPHABET = "abc de"

    private const val TABLE_BLOCK = """{"type":"table","content":[{"type":"tableRow","content":[]}]}"""

    private val LIST_TYPES = listOf(
            TextEditorListItem.NumberedList,
            TextEditorListItem.BulletedList,
            TextEditorListItem.CheckList,
        )

    private val STYLES = listOf(
            TextEditorStyleItem.Bold,
            TextEditorStyleItem.Italic,
            TextEditorStyleItem.Underline,
            TextEditorStyleItem.Strikethrough,
            TextEditorStyleItem.Highlight,
        )

    private val ALIGNS = listOf(TextAlign.Left, TextAlign.Center, TextAlign.Right)

    private val LIST_TYPES_IN_JSON = listOf("taskList", "bulletList", "orderedList")

    /** Each seed starts from one of these, so the ops churn empty, flat and nested documents. */
    private val START_DOCS = listOf(
            "{}",
            // Flat lists of every kind, alongside a plain paragraph.
            """{"type":"doc","content":[
              {"type":"paragraph","content":[{"type":"text","text":"plain"}]},
              {"type":"orderedList","attrs":{"start":1},"content":[
                {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"one"}]}]},
                {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"two"}]}]}
              ]},
              {"type":"taskList","content":[
                {"type":"taskItem","attrs":{"checked":true},"content":[{"type":"paragraph","content":[{"type":"text","text":"do"}]}]}
              ]}
            ]}""",
            // Deep nesting through listItem chains, plus a task list.
            """{"type":"doc","content":[
              {"type":"bulletList","content":[
                {"type":"listItem","content":[
                  {"type":"paragraph","content":[{"type":"text","text":"a"}]},
                  {"type":"orderedList","attrs":{"start":1},"content":[
                    {"type":"listItem","content":[
                      {"type":"paragraph","content":[{"type":"text","text":"b"}]},
                      {"type":"bulletList","content":[
                        {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"c"}]}]}
                      ]}
                    ]}
                  ]}
                ]}
              ]},
              {"type":"taskList","content":[
                {"type":"taskItem","attrs":{"checked":false},"content":[{"type":"paragraph","content":[{"type":"text","text":"t"}]}]}
              ]}
            ]}""",
        )
}
