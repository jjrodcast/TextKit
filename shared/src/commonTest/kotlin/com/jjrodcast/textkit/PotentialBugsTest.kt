package com.jjrodcast.textkit

import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.components.TextEditorListItem
import com.jjrodcast.textkit.editor.core.parser.LinkMark
import com.jjrodcast.textkit.editor.utils.DocumentUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
     * BUG: Converting the **first** list item in the document (which starts at offset 0) back to a
     * plain paragraph crashes.
     *
     * `ListItemTransaction.updateListItemToParagraph` computes the new cursor as
     * `TextRange(range.start - length, range.end - length)` where `length` is the removed decorator
     * width. For the first item `range.start` is at/near 0, so the subtraction underflows to a
     * negative value and `TextRange` throws `IllegalArgumentException: start and end cannot be negative`.
     *
     * EXPECTED: the item should convert to a paragraph and the cursor should be clamped to `>= 0`,
     * exactly like the (working) non-first-item case in
     * [ListsAndDecoratorsTest.converts_a_non_first_list_item_back_to_a_paragraph].
     */
    @Test
    fun converting_the_first_list_item_to_paragraph_currently_crashes() {
        val editor = editorFrom(SampleDocuments.ORDERED_LIST)

        assertFailsWith<IllegalArgumentException> {
            editor.toListItem(
                range = TextRange(0, 5),
                from = TextEditorListItem.NumberedList,
                to = TextEditorListItem.None
            )
        }
    }

    /**
     * Same underflow, reached through a bulleted list, to show it is not specific to numbered lists.
     */
    @Test
    fun converting_the_first_bulleted_item_to_paragraph_currently_crashes() {
        // Start from a plain paragraph, turn it into a bullet, then try to turn it back.
        val editor = editorFrom(SampleDocuments.SINGLE_PARAGRAPH)
        editor.toListItem(TextRange(0, 5), TextEditorListItem.None, TextEditorListItem.BulletedList)

        assertFailsWith<IllegalArgumentException> {
            editor.toListItem(TextRange(0, 5), TextEditorListItem.BulletedList, TextEditorListItem.None)
        }
    }

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
