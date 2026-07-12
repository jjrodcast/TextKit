package com.jjrodcast.textkit.editor.core.piecetable.rope

import com.jjrodcast.textkit.editor.components.TextEditorDecoratorItem
import com.jjrodcast.textkit.editor.components.TextEditorListItem
import com.jjrodcast.textkit.editor.core.piecetable.models.RichPiece
import com.jjrodcast.textkit.editor.core.piecetable.models.TextDecoratorModel.Companion.toTextEditorListItem

/**
 * Rope data structure over [RichPiece] elements.
 *
 * Replaces the previous [ArrayDeque]<[RichPiece]> backing store. A height-balanced
 * binary tree where each [RopeNode.Branch] stores the aggregate [RopeNode.totalLength]
 * and [RopeNode.size] of its subtree, enabling O(log P) structural mutations
 * instead of O(P) array shifts.
 *
 * Core operations:
 * - [buildFrom]             : O(P)       — balanced tree from a list.
 * - [get]                   : O(log P)   — random access by rank.
 * - [splice]                : O(K log P) — replace a rank range with new pieces.
 * - [replaceAt]             : O(log P)   — replace a single piece by rank.
 * - [findByDocumentOffset]  : O(log P)   — locate the piece containing a document offset.
 * - [forEach] / [forEachIndexed] : O(P)  — in-order traversal with accumulated offset.
 * - [toList]                : O(P)       — snapshot as a flat list.
 *
 * Offset semantics in [findByDocumentOffset]: returns the LEFTMOST piece whose
 * cumulative end length ≥ docOffset, matching the original binary-search contract.
 */
internal class PieceRope {

    private var root: RopeNode? = null

    /** Number of pieces in the rope. O(1). */
    val size: Int get() = root?.size ?: 0

    /** Total character length of all pieces. O(1). */
    val totalLength: Int get() = root?.totalLength ?: 0

    /** O(1). */
    fun isEmpty(): Boolean = root == null

    // ── Build ──────────────────────────────────────────────────────────────

    /**
     * Rebuilds the tree from [pieces] in O(P) using recursive mid-split,
     * producing a perfectly balanced tree.
     */
    fun buildFrom(pieces: List<RichPiece>) {
        root = buildBalanced(pieces, 0, pieces.size)
    }

    /** Appends a single piece. O(log P). */
    fun addSingle(piece: RichPiece) {
        root = merge(root, RopeNode.Leaf(piece))
    }

    // ── Point access ───────────────────────────────────────────────────────

    /** Returns the piece at 0-indexed [index]. O(log P). */
    fun get(index: Int): RichPiece {
        var node: RopeNode = root ?: throw IndexOutOfBoundsException("Rope is empty")
        var remaining = index
        while (node is RopeNode.Branch) {
            val leftSize = node.left.size
            node = if (remaining < leftSize) node.left
            else {
                remaining -= leftSize
                node.right
            }
        }
        return (node as RopeNode.Leaf).piece
    }

    /** Returns the first piece. O(log P). */
    fun first(): RichPiece {
        var node: RopeNode = root ?: throw NoSuchElementException("Rope is empty")
        while (node is RopeNode.Branch) node = node.left
        return (node as RopeNode.Leaf).piece
    }

    /** Returns the first piece, or `null` if the rope is empty. O(log P). */
    fun firstOrNull(): RichPiece? = if (root == null) null else first()

    /** Returns the last piece. O(log P). */
    fun last(): RichPiece {
        var node: RopeNode = root ?: throw NoSuchElementException("Rope is empty")
        while (node is RopeNode.Branch) node = node.right
        return (node as RopeNode.Leaf).piece
    }

    // ── Structural mutations ───────────────────────────────────────────────

    /**
     * Replaces the piece at [index] with [piece]. **O(log P), allocation-optimal.**
     *
     * Unlike `splice(index, index+1, listOf(piece))`, this does **not** call [splitAt]
     * or [merge]: it descends directly to the target leaf and path-copies only the H
     * ancestor [RopeNode.Branch] nodes on the spine (one new node per level).
     *
     * Replacing one leaf with another never changes tree structure (sizes and heights
     * are identical), so no AVL rebalancing is required. Aggregates ([RopeNode.totalLength],
     * [RopeNode.lastLineBreakDecorator], [RopeNode.containsLineBreakEnd]) are recomputed
     * automatically by [RopeNode.Branch.Branch.invoke] as the spine is rebuilt on the way up.
     *
     * Savings vs the old splice path on a 27 K-piece tree (H ≈ 15):
     * - Eliminates 2 × [splitAt] calls (≈ 30 [Pair] allocations)
     * - Eliminates 2 × [avlMerge] calls and the intermediate `listOf(piece)`
     * - Creates H + 1 nodes instead of ~2H + 3
     */
    fun replaceAt(index: Int, piece: RichPiece) {
        root = replaceLeaf(root ?: throw IndexOutOfBoundsException("Rope is empty"), index, piece)
    }

    private fun replaceLeaf(node: RopeNode, index: Int, piece: RichPiece): RopeNode {
        return when (node) {
            is RopeNode.Leaf -> RopeNode.Leaf(piece)
            is RopeNode.Branch -> {
                val leftSize = node.left.size
                if (index < leftSize) {
                    RopeNode.Branch(replaceLeaf(node.left, index, piece), node.right)
                } else {
                    RopeNode.Branch(node.left, replaceLeaf(node.right, index - leftSize, piece))
                }
            }
        }
    }

    /**
     * Removes the rank range [from, to) and inserts [replacements] at that position.
     * O(K log P) where K = replacements.size.
     *
     * Uses [splitLeft] and [splitRight] directly on [root], eliminating the
     * intermediate [Pair] allocations of the previous two-pass `splitAt` approach.
     * Both functions operate on the same immutable tree, so no intermediate node
     * is created between the two splits.
     *
     * Examples:
     * - Pure insert at `i`:   `splice(i, i, newPieces)`
     * - Pure removal of `[a,b)`: `splice(a, b, emptyList())`
     * - Replace `[a,b)` with `K` pieces: `splice(a, b, newPieces)`
     */
    fun splice(from: Int, to: Int, replacements: List<RichPiece>) {
        val left = splitLeft(root, from)
        val right = splitRight(root, to)
        val replacementNode = buildBalanced(replacements, 0, replacements.size)
        root = merge(merge(left, replacementNode), right)
    }

    // ── Traversal ──────────────────────────────────────────────────────────

    /**
     * In-order traversal; delivers each piece with its document start offset.
     * O(P).
     */
    fun forEach(action: (piece: RichPiece, offsetInDocument: Int) -> Unit) {
        traverseForEach(root, 0, action)
    }

    private fun traverseForEach(
        node: RopeNode?,
        offset: Int,
        action: (RichPiece, Int) -> Unit
    ) {
        when (node) {
            null -> return
            is RopeNode.Leaf -> action(node.piece, offset)
            is RopeNode.Branch -> {
                traverseForEach(node.left, offset, action)
                traverseForEach(node.right, offset + node.left.totalLength, action)
            }
        }
    }

    /**
     * In-order traversal with rank index and document start offset. O(P).
     */
    fun forEachIndexed(action: (index: Int, piece: RichPiece, offsetInDocument: Int) -> Unit) {
        traverseForEachIndexed(root, 0, 0, action)
    }

    private fun traverseForEachIndexed(
        node: RopeNode?,
        index: Int,
        offset: Int,
        action: (Int, RichPiece, Int) -> Unit
    ) {
        when (node) {
            null -> return
            is RopeNode.Leaf -> action(index, node.piece, offset)
            is RopeNode.Branch -> {
                traverseForEachIndexed(node.left, index, offset, action)
                traverseForEachIndexed(
                    node.right,
                    index + node.left.size,
                    offset + node.left.totalLength,
                    action
                )
            }
        }
    }

    /** Flattens the rope into a [List] in O(P). */
    fun toList(): List<RichPiece> = buildList(size) {
        forEach { piece, _ -> add(piece) }
    }

    /**
     * In-order traversal of pieces [[from], [to]] (inclusive) with **early-exit** support.
     *
     * Identical to [forRange] except [action] returns `Boolean`: `true` = continue,
     * `false` = stop immediately. Once [action] returns `false` no further pieces are
     * visited, making this O(log P + R) where R = pieces visited before the stop, vs
     * O(log P + total) for [forRange] when only a prefix of the range is needed.
     */
    fun forRangeWhile(
        from: Int,
        to: Int,
        action: (index: Int, piece: RichPiece, offsetInDocument: Int, paragraphType: TextEditorDecoratorItem) -> Boolean
    ) {
        if (from > to) return
        val initialContext = firstOrNull()?.decorator.toTextEditorListItem()
        traverseRangeWhile(root, 0, 0, initialContext, from, to, action)
    }

    private fun traverseRangeWhile(
        node: RopeNode?,
        nodeStartIndex: Int,
        nodeStartOffset: Int,
        contextBefore: TextEditorDecoratorItem,
        from: Int,
        to: Int,
        action: (Int, RichPiece, Int, TextEditorDecoratorItem) -> Boolean
    ): Boolean {
        if (node == null) return true
        val nodeEndIndex = nodeStartIndex + node.size - 1
        if (nodeEndIndex < from || nodeStartIndex > to) return true
        return when (node) {
            is RopeNode.Leaf -> action(nodeStartIndex, node.piece, nodeStartOffset, contextBefore)
            is RopeNode.Branch -> {
                val rightStartIndex = nodeStartIndex + node.left.size
                val rightStartOffset = nodeStartOffset + node.left.totalLength
                if (rightStartIndex > from) {
                    if (!traverseRangeWhile(node.left, nodeStartIndex, nodeStartOffset, contextBefore, from, to, action)) {
                        return false
                    }
                }
                if (rightStartIndex <= to) {
                    val rightContext = node.left.lastLineBreakDecorator ?: contextBefore
                    traverseRangeWhile(node.right, rightStartIndex, rightStartOffset, rightContext, from, to, action)
                } else true
            }
        }
    }

    // ── Offset lookup ──────────────────────────────────────────────────────

    /**
     * Returns `(pieceIndex, pieceStartDocumentOffset)` for the LEFTMOST piece
     * whose cumulative end length ≥ [docOffset].
     *
     * Algorithm: at each [RopeNode.Branch], go LEFT if `left.totalLength ≥ docOffset`
     * (the answer exists somewhere in the left subtree), otherwise subtract
     * `left.totalLength` and go RIGHT.
     *
     * Replicates the original binary-search semantics on pre-computed
     * `offsetInDocument` values, but requires no pre-computation. O(log P).
     */
    fun findByDocumentOffset(docOffset: Int): Pair<Int, Int> {
        var node: RopeNode = root ?: return Pair(0, 0)
        var offset = docOffset
        var pieceIndex = 0
        var accOffset = 0
        while (node is RopeNode.Branch) {
            val leftLen = node.left.totalLength
            if (leftLen >= offset) {
                node = node.left
            } else {
                offset -= leftLen
                accOffset += leftLen
                pieceIndex += node.left.size
                node = node.right
            }
        }
        return Pair(pieceIndex, accOffset)
    }

    // ── Lazy structural queries ────────────────────────────────────────

    /**
     * Returns the document start offset of the piece at [index]. O(log P).
     *
     * Walks root→leaf, accumulating [RopeNode.totalLength] of left subtrees
     * each time we descend right — equivalent to summing all piece lengths
     * before index without a linear scan.
     */
    fun getOffsetAt(index: Int): Int {
        var node: RopeNode = root ?: throw IndexOutOfBoundsException("Rope is empty")
        var remaining = index
        var accOffset = 0
        while (node is RopeNode.Branch) {
            val leftSize = node.left.size
            if (remaining < leftSize) {
                node = node.left
            } else {
                remaining -= leftSize
                accOffset += node.left.totalLength
                node = node.right
            }
        }
        return accOffset
    }

    /**
     * In-order traversal of pieces [[from], [to]] (inclusive), delivering
     * `(index, piece, documentOffset, paragraphType)` for each piece in a single
     * **O(R + log P)** pass, where R = `to - from + 1`.
     *
     * Descends to the boundary of the range in O(log P), then visits the R pieces
     * in order in O(R) — avoiding the O(R log P) cost of calling
     * [getOffsetAndParagraphTypeAt] per piece.
     *
     * The `paragraphType` delivered for each piece is the same value that
     * `updatePieces()` used to stamp as `cachedParagraphType`.
     */
    fun forRange(
        from: Int,
        to: Int,
        action: (index: Int, piece: RichPiece, offsetInDocument: Int, paragraphType: TextEditorDecoratorItem) -> Unit
    ) {
        if (from > to) return
        val initialContext = firstOrNull()?.decorator.toTextEditorListItem()
        traverseRange(root, 0, 0, initialContext, from, to, action)
    }

    private fun traverseRange(
        node: RopeNode?,
        nodeStartIndex: Int,
        nodeStartOffset: Int,
        contextBefore: TextEditorDecoratorItem,
        from: Int,
        to: Int,
        action: (Int, RichPiece, Int, TextEditorDecoratorItem) -> Unit
    ) {
        if (node == null) return
        val nodeEndIndex = nodeStartIndex + node.size - 1
        if (nodeEndIndex < from || nodeStartIndex > to) return // no overlap with [from, to]

        when (node) {
            is RopeNode.Leaf -> action(nodeStartIndex, node.piece, nodeStartOffset, contextBefore)
            is RopeNode.Branch -> {
                val rightStartIndex = nodeStartIndex + node.left.size
                val rightStartOffset = nodeStartOffset + node.left.totalLength
                // Guard each child: skip entirely when it cannot overlap [from, to].
                // This avoids redundant function calls at boundary nodes and defers
                // rightContext computation to only when the right subtree is in range.
                if (rightStartIndex > from) {
                    traverseRange(node.left, nodeStartIndex, nodeStartOffset, contextBefore, from, to, action)
                }
                if (rightStartIndex <= to) {
                    // Context for the right child = last line-break decorator in the left
                    // subtree, or the inherited context if the left has no line breaks.
                    val rightContext = node.left.lastLineBreakDecorator ?: contextBefore
                    traverseRange(node.right, rightStartIndex, rightStartOffset, rightContext, from, to, action)
                }
            }
        }
    }

    /**
     * Returns `(documentOffset, paragraphType)` for the piece at [index] in a
     * **single O(log P) walk**, replacing the two independent calls to
     * [getOffsetAt] and [getParagraphTypeAt].
     *
     * At each [RopeNode.Branch] both accumulators are updated in the same step:
     * - Going right: `accOffset += left.totalLength` and
     *   `context = left.lastLineBreakDecorator ?: context`.
     * - Going left: neither accumulator changes.
     */
    fun getOffsetAndParagraphTypeAt(index: Int): Pair<Int, TextEditorDecoratorItem> {
        val first = firstOrNull() ?: return Pair(0, TextEditorListItem.None)
        var context: TextEditorDecoratorItem = first.decorator.toTextEditorListItem()

        var node: RopeNode = root!!
        var remaining = index
        var accOffset = 0
        while (node is RopeNode.Branch) {
            val leftSize = node.left.size
            if (remaining < leftSize) {
                node = node.left
            } else {
                remaining -= leftSize
                accOffset += node.left.totalLength
                context = node.left.lastLineBreakDecorator ?: context
                node = node.right
            }
        }
        return Pair(accOffset, context)
    }

    // ── Fused offset + paragraph-boundary lookup ──────────────────────────

    /**
     * Returns the index of the **first piece of the paragraph** containing [docOffset]
     * in a single **O(log P)** descent.
     *
     * Fuses [findByDocumentOffset] + [findLastLineBreakEndBefore] into one walk by reading
     * [RopeNode.lastLineBreakRank] from each left subtree as we descend right — an O(1)
     * aggregate lookup instead of a second O(log P) pass.
     *
     * At each [RopeNode.Branch] where we descend RIGHT (target is in the right subtree),
     * the entire left subtree is before the target. Its [RopeNode.lastLineBreakRank]
     * (absolute rank = [pieceIndex] + [RopeNode.lastLineBreakRank]) is recorded as the
     * new last-line-break candidate.  The final result is `candidate + 1`, or `0` if no
     * line break preceded the target piece.
     */
    fun findParagraphStartAt(docOffset: Int): Int {
        var node: RopeNode = root ?: return 0
        var offset = docOffset
        var pieceIndex = 0
        var lastLineBreakBeforeTarget = -1
        while (node is RopeNode.Branch) {
            val leftLen = node.left.totalLength
            val leftSize = node.left.size
            if (leftLen >= offset) {
                node = node.left
            } else {
                offset -= leftLen
                val leftLastLB = node.left.lastLineBreakRank
                if (leftLastLB >= 0) lastLineBreakBeforeTarget = pieceIndex + leftLastLB
                pieceIndex += leftSize
                node = node.right
            }
        }
        return if (lastLineBreakBeforeTarget < 0) 0 else lastLineBreakBeforeTarget + 1
    }

    /**
     * Returns the index of the **last piece of the paragraph** containing [docOffset]
     * in a single **O(log P)** descent.
     *
     * Fuses [findByDocumentOffset] + [findFirstLineBreakEndFrom] into one walk by reading
     * [RopeNode.firstLineBreakRank] from each right subtree as we descend left — an O(1)
     * aggregate lookup instead of a second O(log P) pass.
     *
     * At each [RopeNode.Branch] where we descend LEFT (target is in the left subtree),
     * the entire right subtree is after the target. Its [RopeNode.firstLineBreakRank]
     * (absolute rank = [pieceIndex] + [leftSize] + [RopeNode.firstLineBreakRank]) is
     * recorded as the minimum first-line-break candidate after the target.  At the
     * target leaf, if the piece itself ends with a line break it is returned immediately;
     * otherwise the minimum candidate is returned (or `size - 1` if none).
     */
    fun findParagraphEndAt(docOffset: Int): Int {
        var node: RopeNode = root ?: return (size - 1).coerceAtLeast(0)
        var offset = docOffset
        var pieceIndex = 0
        var firstLBCandidate = Int.MAX_VALUE
        while (node is RopeNode.Branch) {
            val leftLen = node.left.totalLength
            val leftSize = node.left.size
            if (leftLen >= offset) {
                val rightFirstLB = node.right.firstLineBreakRank
                if (rightFirstLB >= 0) {
                    val candidate = pieceIndex + leftSize + rightFirstLB
                    if (candidate < firstLBCandidate) firstLBCandidate = candidate
                }
                node = node.left
            } else {
                offset -= leftLen
                pieceIndex += leftSize
                node = node.right
            }
        }
        if ((node as RopeNode.Leaf).piece.endsWithLineBreak) return pieceIndex
        return if (firstLBCandidate == Int.MAX_VALUE) (size - 1).coerceAtLeast(0) else firstLBCandidate
    }

    // ── Paragraph-boundary search ─────────────────────────────────────────

    /**
     * Returns the index of the **first** piece at or after [fromIndex] whose
     * [RichPiece.endsWithLineBreak] is `true`, or `size - 1` if none exists.
     *
     * Uses the [RopeNode.containsLineBreakEnd] aggregate to prune entire subtrees,
     * giving O(log P) amortised complexity instead of O(R) linear scan.
     */
    fun findFirstLineBreakEndFrom(fromIndex: Int): Int {
        if (root == null || fromIndex >= size) return (size - 1).coerceAtLeast(0)
        return traverseFindFirst(root!!, 0, fromIndex) ?: (size - 1)
    }

    private fun traverseFindFirst(node: RopeNode, nodeStartIndex: Int, fromIndex: Int): Int? {
        val nodeEndIndex = nodeStartIndex + node.size - 1
        if (nodeEndIndex < fromIndex) return null // entirely before the search window
        if (!node.containsLineBreakEnd) return null // no line-break end anywhere here
        return when (node) {
            is RopeNode.Leaf -> nodeStartIndex // in range and ends with line break
            is RopeNode.Branch -> {
                val rightStartIndex = nodeStartIndex + node.left.size
                // Prefer left first to get the earliest occurrence
                traverseFindFirst(node.left, nodeStartIndex, fromIndex)
                    ?: traverseFindFirst(node.right, rightStartIndex, fromIndex)
            }
        }
    }

    /**
     * Returns the index of the **last** piece strictly before [beforeIndex] whose
     * [RichPiece.endsWithLineBreak] is `true`, or `-1` if none exists.
     *
     * Uses the [RopeNode.containsLineBreakEnd] aggregate to prune entire subtrees,
     * giving O(log P) amortised complexity instead of O(R) linear scan.
     */
    fun findLastLineBreakEndBefore(beforeIndex: Int): Int {
        if (root == null || beforeIndex <= 0) return -1
        return traverseFindLast(root!!, 0, beforeIndex - 1) ?: -1
    }

    private fun traverseFindLast(node: RopeNode, nodeStartIndex: Int, toIndex: Int): Int? {
        if (nodeStartIndex > toIndex) return null // entirely after the search window
        if (!node.containsLineBreakEnd) return null // no line-break end anywhere here
        return when (node) {
            is RopeNode.Leaf -> nodeStartIndex // in range and ends with line break
            is RopeNode.Branch -> {
                val rightStartIndex = nodeStartIndex + node.left.size
                // Prefer right first to get the latest occurrence
                traverseFindLast(node.right, rightStartIndex, toIndex)
                    ?: traverseFindLast(node.left, nodeStartIndex, toIndex)
            }
        }
    }

    // ── Internal helpers ───────────────────────────────────────────────────

    /**
     * Builds a perfectly balanced [RopeNode] tree from [list][from, to) in O(n)
     * by splitting at the midpoint recursively.
     */
    private fun buildBalanced(list: List<RichPiece>, from: Int, to: Int): RopeNode? {
        if (from >= to) return null
        if (from + 1 == to) return RopeNode.Leaf(list[from])
        val mid = (from + to) ushr 1
        return merge(buildBalanced(list, from, mid), buildBalanced(list, mid, to))
    }

    /**
     * Returns a tree containing the first [k] pieces of [node] (ranks [0, k)).
     *
     * - Returns `null` if [k] ≤ 0 or [node] is `null`.
     * - Returns [node] unchanged if [k] ≥ [node.size] (no structural work needed).
     * - A [RopeNode.Leaf] is never reached at the recursive level: since `size == 1`,
     *   any `k` in `(0, 1)` is impossible for integers, so the guards above always
     *   short-circuit first.
     *
     * O(log P) — creates new [RopeNode.Branch] nodes along the left spine only.
     * No [Pair] allocations at any recursion level.
     */
    private fun splitLeft(node: RopeNode?, k: Int): RopeNode? {
        if (node == null || k <= 0) return null
        if (k >= node.size) return node
        node as RopeNode.Branch
        val leftSize = node.left.size
        return if (k <= leftSize) {
            splitLeft(node.left, k)
        } else {
            merge(node.left, splitLeft(node.right, k - leftSize))
        }
    }

    /**
     * Returns a tree containing pieces from rank [k] onwards (ranks [k, size)).
     *
     * - Returns [node] unchanged if [k] ≤ 0.
     * - Returns `null` if [k] ≥ [node.size] or [node] is `null`.
     * - A [RopeNode.Leaf] is never reached at the recursive level for the same
     *   reason as [splitLeft].
     *
     * O(log P) — creates new [RopeNode.Branch] nodes along the right spine only.
     * No [Pair] allocations at any recursion level.
     */
    private fun splitRight(node: RopeNode?, k: Int): RopeNode? {
        if (node == null || k >= node.size) return null
        if (k <= 0) return node
        node as RopeNode.Branch
        val leftSize = node.left.size
        return if (k >= leftSize) {
            splitRight(node.right, k - leftSize)
        } else {
            merge(splitRight(node.left, k), node.right)
        }
    }

    // ── AVL-balanced merge ────────────────────────────────────────────────

    /**
     * Merges two AVL-balanced subtrees into a new balanced tree.
     * **O(|h(left) − h(right)|) ≤ O(log P).**
     *
     * When heights differ by at most 1 the result is a single new [RopeNode.Branch].
     * When one side is significantly taller, [avlMerge] walks down its outer spine
     * until it finds an attachment point where the shorter side fits (height within ±1),
     * then [avlBalance] fixes any ±2 imbalance with a single or double AVL rotation on
     * the way back up.  Each ancestor along the spine is visited exactly once, so the
     * total work is proportional to the height difference.
     *
     * Together with the perfectly balanced [buildBalanced], this guarantees the rope
     * height never exceeds O(log P) regardless of how many [splice] operations are
     * performed.
     */
    private fun merge(left: RopeNode?, right: RopeNode?): RopeNode? {
        if (left == null) return right
        if (right == null) return left
        return avlMerge(left, right)
    }

    /**
     * Merges two non-null AVL trees.
     *
     * - If heights differ by ≤ 1 → delegates directly to [avlBalance] (one node created).
     * - If [left] is taller → descends one level into [left]'s **right** spine and
     *   recursively merges at that level, then rebalances on the way back.
     * - If [right] is taller → symmetric descent into [right]'s **left** spine.
     *
     * Invariant: result is AVL-balanced with height = max(left.height, right.height) + O(1).
     */
    private fun avlMerge(left: RopeNode, right: RopeNode): RopeNode {
        val diff = left.height - right.height
        return when {
            diff > 1 -> {
                // Left is taller — walk one step down its right spine and recurse.
                left as RopeNode.Branch
                avlBalance(left.left, avlMerge(left.right, right))
            }
            diff < -1 -> {
                // Right is taller — walk one step down its left spine and recurse.
                right as RopeNode.Branch
                avlBalance(avlMerge(left, right.left), right.right)
            }
            else -> avlBalance(left, right)
        }
    }

    /**
     * Creates a [RopeNode.Branch] from two non-null children whose heights differ by
     * **at most 2**, applying a single or double AVL rotation if needed so the result
     * is always AVL-balanced (height difference ≤ 1).
     *
     * The four standard AVL cases:
     * ```
     * LL (left–left):   right-rotate root
     * LR (left–right):  left-rotate left child, then right-rotate root
     * RR (right–right): left-rotate root
     * RL (right–left):  right-rotate right child, then left-rotate root
     * ```
     */
    private fun avlBalance(left: RopeNode, right: RopeNode): RopeNode.Branch {
        val lh = left.height
        val rh = right.height
        return when {
            lh > rh + 1 -> {
                // Left-heavy by 2 — left must be a Branch (lh ≥ 3 since rh ≥ 1).
                left as RopeNode.Branch
                if (left.left.height >= left.right.height) {
                    // LL: single right rotation
                    RopeNode.Branch(left.left, RopeNode.Branch(left.right, right))
                } else {
                    // LR: double rotation (left.right becomes the new root)
                    val lr = left.right as RopeNode.Branch
                    RopeNode.Branch(
                        RopeNode.Branch(left.left, lr.left),
                        RopeNode.Branch(lr.right, right)
                    )
                }
            }
            rh > lh + 1 -> {
                // Right-heavy by 2 — right must be a Branch (rh ≥ 3 since lh ≥ 1).
                right as RopeNode.Branch
                if (right.right.height >= right.left.height) {
                    // RR: single left rotation
                    RopeNode.Branch(RopeNode.Branch(left, right.left), right.right)
                } else {
                    // RL: double rotation (right.left becomes the new root)
                    val rl = right.left as RopeNode.Branch
                    RopeNode.Branch(
                        RopeNode.Branch(left, rl.left),
                        RopeNode.Branch(rl.right, right.right)
                    )
                }
            }
            else -> RopeNode.Branch(left, right)
        }
    }
}
