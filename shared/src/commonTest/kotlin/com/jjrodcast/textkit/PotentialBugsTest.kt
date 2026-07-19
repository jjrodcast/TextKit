package com.jjrodcast.textkit

import com.jjrodcast.textkit.editor.core.parser.LinkMark
import com.jjrodcast.textkit.editor.utils.DocumentUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Characterization tests for **suspected bugs**. Each test pins down the *current* (possibly wrong)
 * behavior so the suite stays green, and documents what the behavior *should* be. When a bug is
 * fixed the matching test here will start failing — that is the signal to update it to assert the
 * corrected behavior.
 */
class PotentialBugsTest {

    /**
     * BUG: Re-serializing [DocumentUtils.complexJsonV3] is not idempotent.
     *
     * The first `load -> toJson` emits a trailing empty paragraph (`{"content":[],"type":"paragraph"}`)
     * that the next `load -> toJson` drops, so `once != twice`. A round-trip should reach a fixed
     * point after the first pass.
     */
    @Test
    fun sample_v3_reserialization_is_not_idempotent() {
        val once = editorFrom(DocumentUtils.complexJsonV3).toJson()
        val twice = editorFrom(once).toJson()
        assertTrue(
            once != twice,
            "if this fails, V3 serialization is now stable — move it into LargeDocumentTest.idempotentSamples"
        )
    }

    /**
     * BUG: Re-serializing [DocumentUtils.complexJsonV6] is not idempotent either (same trailing
     * empty-paragraph symptom as V3).
     */
    @Test
    fun sample_v6_reserialization_is_not_idempotent() {
        val once = editorFrom(DocumentUtils.complexJsonV6).toJson()
        val twice = editorFrom(once).toJson()
        assertTrue(
            once != twice,
            "if this fails, V6 serialization is now stable — move it into LargeDocumentTest.idempotentSamples"
        )
    }

    /**
     * BUG: Removing an existing link duplicates text / merges the linked piece with its neighbor.
     *
     * Removing only the `link` mark must not change the character stream nor the number of pieces:
     * the linked word sits in its own paragraph in [DocumentUtils.complexJsonV2], so dropping its
     * link leaves a plain piece with no same-paragraph neighbor to merge with.
     */
    @Test
    fun removing_link_keeps_text_and_piece_count() {
        val editor = editorFrom(DocumentUtils.complexJsonV2)
        val range = editor.rangeOf("link")

        val textBefore = editor.text
        val piecesBefore = editor.pieceCount()
        assertTrue(editor.marksAt(range).has<LinkMark>(), "precondition: range must be a link")

        assertTrue(editor.setLink(range, ""), "removing the link should report a change")

        assertFalse(editor.marksAt(range).has<LinkMark>(), "link mark should be gone")
        assertEquals(textBefore, editor.text, "text must be identical after removing the link")
        assertEquals(
            piecesBefore,
            editor.pieceCount(),
            "piece count must be unchanged after removing the link"
        )
    }
}
