package com.jjrodcast.textkit.editor.core.piecetable.rope

import com.jjrodcast.textkit.editor.components.TextEditorDecoratorItem
import com.jjrodcast.textkit.editor.core.piecetable.models.RichPiece
import com.jjrodcast.textkit.editor.core.piecetable.models.TextDecoratorModel.Companion.toTextEditorListItem

/**
 * Node in the [PieceRope] AVL-balanced binary tree.
 *
 * - [Leaf]   — wraps a single [RichPiece].
 * - [Branch] — internal node that stores six aggregates computed **once at construction**
 *              so every traversal step is O(1) per node:
 *              [totalLength], [size], [lastLineBreakDecorator], [containsLineBreakEnd],
 *              [lastLineBreakRank], [firstLineBreakRank], [height].
 *
 * All structural mutations create new nodes along the spine only
 * (path-copying / persistent semantics) — existing nodes are never modified.
 *
 * The [height] aggregate is used by [PieceRope.merge] to keep the tree AVL-balanced
 * after every [PieceRope.splice], guaranteeing O(log P) height at all times.
 */
internal sealed class RopeNode {

    /** Total character length of all pieces under this node. */
    abstract val totalLength: Int

    /** Number of leaf (piece) nodes under this node. */
    abstract val size: Int

    /**
     * The decorator of the LAST line-break piece in this subtree, or `null` if the
     * subtree contains no line-break pieces.
     *
     * Used by [PieceRope.getOffsetAndParagraphTypeAt] / [PieceRope.forRange] to compute
     * paragraph types in O(log P) without a full O(P) forward scan.
     */
    abstract val lastLineBreakDecorator: TextEditorDecoratorItem?

    /**
     * `true` if at least one piece in this subtree has [RichPiece.endsWithLineBreak] == `true`.
     *
     * Used by [PieceRope.findFirstLineBreakEndFrom] and [PieceRope.findLastLineBreakEndBefore]
     * to prune entire subtrees during O(log P) paragraph-boundary search.
     */
    abstract val containsLineBreakEnd: Boolean

    /**
     * Rank (0-indexed, relative to this subtree's start) of the **rightmost** piece in
     * this subtree whose [RichPiece.endsWithLineBreak] is `true`, or `-1` if none.
     *
     * Used by [PieceRope.findParagraphStartAt] to find the paragraph-start boundary from
     * a document offset in a single O(log P) descent — eliminating a separate
     * [PieceRope.findLastLineBreakEndBefore] call.
     */
    abstract val lastLineBreakRank: Int

    /**
     * Rank (0-indexed, relative to this subtree's start) of the **leftmost** piece in
     * this subtree whose [RichPiece.endsWithLineBreak] is `true`, or `-1` if none.
     *
     * Used by [PieceRope.findParagraphEndAt] to find the paragraph-end boundary from
     * a document offset in a single O(log P) descent — eliminating a separate
     * [PieceRope.findFirstLineBreakEndFrom] call.
     */
    abstract val firstLineBreakRank: Int

    /**
     * Height of this subtree (1 for a [Leaf], `max(left.height, right.height) + 1` for a [Branch]).
     *
     * Used by [PieceRope.merge] to enforce the AVL balance invariant: after every merge the
     * height difference between any two sibling subtrees is at most 1.
     */
    abstract val height: Int

    class Leaf(val piece: RichPiece) : RopeNode() {
        override val totalLength: Int = piece.length
        override val size: Int = 1
        override val lastLineBreakDecorator: TextEditorDecoratorItem? =
            if (piece.isLineBreak) piece.decorator.toTextEditorListItem() else null
        override val containsLineBreakEnd: Boolean = piece.endsWithLineBreak
        override val lastLineBreakRank: Int = if (piece.endsWithLineBreak) 0 else -1
        override val firstLineBreakRank: Int = if (piece.endsWithLineBreak) 0 else -1
        override val height: Int = 1
    }

    class Branch(
        val left: RopeNode,
        val right: RopeNode,
        override val totalLength: Int,
        override val size: Int,
        override val lastLineBreakDecorator: TextEditorDecoratorItem?,
        override val containsLineBreakEnd: Boolean,
        override val lastLineBreakRank: Int,
        override val firstLineBreakRank: Int,
        override val height: Int
    ) : RopeNode() {
        companion object {
            /** Convenience constructor — derives all aggregates automatically. */
            operator fun invoke(left: RopeNode, right: RopeNode): Branch {
                // lastLineBreakRank: rightmost LB in subtree. Prefer right side first.
                val rightLastLB = right.lastLineBreakRank
                val lastLBRank = if (rightLastLB >= 0) left.size + rightLastLB else left.lastLineBreakRank

                // firstLineBreakRank: leftmost LB in subtree. Prefer left side first.
                val leftFirstLB = left.firstLineBreakRank
                val firstLBRank = if (leftFirstLB >= 0) leftFirstLB else {
                    val rightFirstLB = right.firstLineBreakRank
                    if (rightFirstLB >= 0) left.size + rightFirstLB else -1
                }

                return Branch(
                    left, right,
                    left.totalLength + right.totalLength,
                    left.size + right.size,
                    right.lastLineBreakDecorator ?: left.lastLineBreakDecorator,
                    left.containsLineBreakEnd || right.containsLineBreakEnd,
                    lastLBRank,
                    firstLBRank,
                    maxOf(left.height, right.height) + 1
                )
            }
        }
    }
}
