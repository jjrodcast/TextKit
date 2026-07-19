package com.jjrodcast.textkit

import com.jjrodcast.textkit.editor.core.piecetable.models.RichPiece
import com.jjrodcast.textkit.editor.core.piecetable.models.Source
import com.jjrodcast.textkit.editor.core.piecetable.rope.PieceRope
import com.jjrodcast.textkit.editor.core.piecetable.rope.RopeNode
import kotlin.math.ceil
import kotlin.math.log2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Direct unit tests for [PieceRope], the AVL-balanced rope that backs the piece table.
 *
 * The rope was previously exercised only indirectly through `TextKitEditorManager`; these tests
 * drive it in isolation and assert its documented contract. Every input is fixed and explicit —
 * there is no randomness, so each run is identical and reproducible.
 *
 * [RichPiece.offset] is not used by the rope for structure (only [RichPiece.length] is), so it is
 * used here as a stable identity tag: `toList().map { it.offset }` tells us the piece order.
 */
class PieceRopeTest {

    // ── Fixtures ─────────────────────────────────────────────────────────────

    /**
     * Builds a piece whose [id] is stored in [RichPiece.offset] purely as an identity tag for
     * assertions. The rope ignores [RichPiece.offset]; only [length] affects document offsets.
     */
    private fun piece(id: Int, length: Int = 1): RichPiece =
        RichPiece(source = Source.ADDED, offset = id, length = length)

    private fun ropeOf(vararg pieces: RichPiece): PieceRope =
        PieceRope().apply { buildFrom(pieces.toList()) }

    /** Identity tags of every piece in document order. */
    private fun ids(rope: PieceRope): List<Int> = rope.toList().map { it.offset }

    // ── Structural correctness ───────────────────────────────────────────────

    @Test
    fun build_from_reports_size_length_and_order() {
        val rope = ropeOf(
            piece(id = 10, length = 3),
            piece(id = 11, length = 1),
            piece(id = 12, length = 4),
            piece(id = 13, length = 1),
            piece(id = 14, length = 5),
        )

        assertEquals(5, rope.size)
        assertEquals(14, rope.totalLength)
        assertEquals(listOf(10, 11, 12, 13, 14), ids(rope))
    }

    @Test
    fun get_returns_the_piece_at_each_rank() {
        val rope = ropeOf(piece(20), piece(21), piece(22), piece(23))

        assertEquals(20, rope.get(0).offset)
        assertEquals(21, rope.get(1).offset)
        assertEquals(22, rope.get(2).offset)
        assertEquals(23, rope.get(3).offset)
    }

    @Test
    fun first_and_last_return_the_endpoints() {
        val rope = ropeOf(piece(30), piece(31), piece(32))

        assertEquals(30, rope.first().offset)
        assertEquals(30, rope.firstOrNull()?.offset)
        assertEquals(32, rope.last().offset)
    }

    @Test
    fun get_offset_at_accumulates_lengths_before_the_index() {
        val rope = ropeOf(
            piece(id = 40, length = 3),
            piece(id = 41, length = 1),
            piece(id = 42, length = 4),
            piece(id = 43, length = 1),
            piece(id = 44, length = 5),
        )

        assertEquals(0, rope.getOffsetAt(0))
        assertEquals(3, rope.getOffsetAt(1))
        assertEquals(4, rope.getOffsetAt(2))
        assertEquals(8, rope.getOffsetAt(3))
        assertEquals(9, rope.getOffsetAt(4))
    }

    @Test
    fun add_single_appends_at_the_end() {
        val rope = ropeOf(piece(50), piece(51))

        rope.addSingle(piece(52))
        rope.addSingle(piece(53))

        assertEquals(4, rope.size)
        assertEquals(listOf(50, 51, 52, 53), ids(rope))
    }

    @Test
    fun replace_at_swaps_one_piece_and_preserves_the_rest() {
        val rope = ropeOf(piece(60), piece(61), piece(62), piece(63))

        rope.replaceAt(2, piece(id = 99, length = 7))

        assertEquals(4, rope.size)
        assertEquals(listOf(60, 61, 99, 63), ids(rope))
        assertEquals(7, rope.get(2).length)
    }

    @Test
    fun splice_inserts_without_removing() {
        val rope = ropeOf(piece(70), piece(71), piece(72))

        rope.splice(from = 1, to = 1, replacements = listOf(piece(80), piece(81)))

        assertEquals(listOf(70, 80, 81, 71, 72), ids(rope))
    }

    @Test
    fun splice_removes_a_rank_range() {
        val rope = ropeOf(piece(70), piece(71), piece(72), piece(73), piece(74))

        rope.splice(from = 1, to = 4, replacements = emptyList())

        assertEquals(listOf(70, 74), ids(rope))
    }

    @Test
    fun splice_replaces_a_rank_range_with_new_pieces() {
        val rope = ropeOf(piece(70), piece(71), piece(72), piece(73), piece(74))

        rope.splice(from = 1, to = 3, replacements = listOf(piece(90), piece(91)))

        assertEquals(listOf(70, 90, 91, 73, 74), ids(rope))
    }

    // ── Edge cases ───────────────────────────────────────────────────────────

    @Test
    fun a_fresh_rope_is_empty() {
        val rope = PieceRope()

        assertTrue(rope.isEmpty())
        assertEquals(0, rope.size)
        assertEquals(0, rope.totalLength)
        assertNull(rope.firstOrNull())
        assertEquals(emptyList(), rope.toList())
    }

    @Test
    fun endpoint_access_on_an_empty_rope_fails() {
        val rope = PieceRope()

        assertFailsWith<IndexOutOfBoundsException> { rope.get(0) }
        assertFailsWith<NoSuchElementException> { rope.first() }
        assertFailsWith<NoSuchElementException> { rope.last() }
    }

    @Test
    fun building_from_an_empty_list_yields_an_empty_rope() {
        val rope = PieceRope().apply { buildFrom(emptyList()) }

        assertTrue(rope.isEmpty())
        assertEquals(0, rope.size)
    }

    @Test
    fun a_single_piece_rope_reports_that_piece_at_every_endpoint() {
        val rope = ropeOf(piece(id = 100, length = 6))

        assertEquals(1, rope.size)
        assertEquals(6, rope.totalLength)
        assertEquals(100, rope.get(0).offset)
        assertEquals(100, rope.first().offset)
        assertEquals(100, rope.last().offset)
        assertEquals(0, rope.getOffsetAt(0))
    }

    @Test
    fun find_by_document_offset_maps_offsets_to_the_containing_piece() {
        // Lengths 3, 4, 5 → cumulative ends 3, 7, 12. Piece start offsets 0, 3, 7.
        val rope = ropeOf(
            piece(id = 110, length = 3),
            piece(id = 111, length = 4),
            piece(id = 112, length = 5),
        )

        // Offset 0 and offsets inside the first piece resolve to piece 0 (start 0).
        assertEquals(0 to 0, rope.findByDocumentOffset(0))
        assertEquals(0 to 0, rope.findByDocumentOffset(3))
        // Offset 4 falls in the second piece (start 3).
        assertEquals(1 to 3, rope.findByDocumentOffset(4))
        assertEquals(1 to 3, rope.findByDocumentOffset(7))
        // Offset 8 falls in the third piece (start 7).
        assertEquals(2 to 7, rope.findByDocumentOffset(8))
    }

    @Test
    fun splice_can_empty_the_rope() {
        val rope = ropeOf(piece(120), piece(121), piece(122))

        rope.splice(from = 0, to = 3, replacements = emptyList())

        assertTrue(rope.isEmpty())
        assertEquals(0, rope.size)
    }

    @Test
    fun snapshot_and_restore_round_trips_the_document() {
        val rope = ropeOf(piece(130), piece(131), piece(132))
        val snapshot = rope.snapshot()

        // Mutate away from the snapshot…
        rope.splice(from = 1, to = 2, replacements = listOf(piece(140), piece(141)))
        assertEquals(listOf(130, 140, 141, 132), ids(rope))

        // …then restore it exactly.
        rope.restore(snapshot)
        assertEquals(listOf(130, 131, 132), ids(rope))
        assertSame(snapshot, rope.snapshot())
    }

    // ── AVL balance invariant ────────────────────────────────────────────────

    /**
     * Recursively verifies the AVL invariant and the cached aggregates of every node under
     * [node], returning the `(size, totalLength)` it independently computed. Asserting these on
     * the tree behind `snapshot()` proves the rope stays O(log P) tall and that the `size` /
     * `totalLength` aggregates the O(log P) queries depend on are consistent with the leaves.
     */
    private fun assertBalanced(node: RopeNode?): Pair<Int, Int> = when (node) {
        null -> 0 to 0
        is RopeNode.Leaf -> {
            assertEquals(1, node.size, "leaf size")
            assertEquals(1, node.height, "leaf height")
            assertEquals(node.piece.length, node.totalLength, "leaf totalLength")
            1 to node.piece.length
        }

        is RopeNode.Branch -> {
            val (leftSize, leftLength) = assertBalanced(node.left)
            val (rightSize, rightLength) = assertBalanced(node.right)
            val balanceFactor = node.left.height - node.right.height
            assertTrue(balanceFactor in -1..1, "AVL balance broken (factor=$balanceFactor)")
            assertEquals(maxOf(node.left.height, node.right.height) + 1, node.height, "branch height")
            assertEquals(leftSize + rightSize, node.size, "branch size aggregate")
            assertEquals(leftLength + rightLength, node.totalLength, "branch totalLength aggregate")
            (leftSize + rightSize) to (leftLength + rightLength)
        }
    }

    /** The tightest known height bound for an AVL tree of [n] nodes: 1.4405·log2(n+2) − 0.3277. */
    private fun avlHeightCeiling(n: Int): Int = ceil(1.4405 * log2((n + 2).toDouble()) - 0.3277).toInt()

    @Test
    fun build_from_produces_a_balanced_tree() {
        val rope = PieceRope().apply { buildFrom((0 until 100).map { piece(it) }) }

        val (size, _) = assertBalanced(rope.snapshot())
        assertEquals(100, size)
        assertTrue(rope.snapshot()!!.height <= avlHeightCeiling(100))
    }

    @Test
    fun repeated_add_single_stays_balanced() {
        val rope = PieceRope()

        for (id in 0 until 64) rope.addSingle(piece(id))

        assertEquals((0 until 64).toList(), ids(rope))
        val (size, _) = assertBalanced(rope.snapshot())
        assertEquals(64, size)
        assertTrue(rope.snapshot()!!.height <= avlHeightCeiling(64))
    }

    @Test
    fun a_fixed_splice_script_keeps_the_tree_balanced() {
        // The rope and this plain list are kept in lock-step; comparing them proves the splices
        // land where expected, while assertBalanced proves the tree self-balances after each one.
        val rope = PieceRope().apply { buildFrom((0 until 50).map { piece(it) }) }
        val mirror = (0 until 50).toMutableList()
        var nextId = 1000

        // (from, to, howManyToInsert) — a fixed, hand-picked mix of inserts, removals and replaces.
        val script = listOf(
            Triple(10, 20, 3),   // replace a middle range with fewer pieces
            Triple(0, 0, 1),     // insert at the very front
            Triple(0, 5, 0),     // remove a run at the front
            Triple(5, 5, 12),    // fat insert in the middle
            Triple(30, 40, 0),   // remove a run near the end
        )

        for ((from, to, insertCount) in script) {
            val newPieces = (0 until insertCount).map { piece(nextId++) }
            rope.splice(from, to, newPieces)

            for (i in to - 1 downTo from) mirror.removeAt(i)
            mirror.addAll(from, newPieces.map { it.offset })

            assertEquals(mirror, ids(rope), "order after splice($from, $to, +$insertCount)")
            assertEquals(mirror.size, assertBalanced(rope.snapshot()).first, "balance after splice")
        }

        assertTrue(rope.snapshot()!!.height <= avlHeightCeiling(rope.size))
    }
}
