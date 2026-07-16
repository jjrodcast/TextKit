package com.jjrodcast.textkit.editor.core.piecetable

import com.jjrodcast.textkit.editor.core.piecetable.rope.RopeNode

/**
 * An immutable, O(1)-captured version of a [RichTextEditorBasePieceTable]'s document state, used as
 * a restore point for undo/redo.
 *
 * It holds only two references, both immutable, so capturing and restoring a snapshot is O(1) in
 * time and adds no per-snapshot memory beyond the retained (structurally shared) tree:
 * - [root]: the piece-sequence tree. [RopeNode] uses path-copying, so different document versions
 *   share almost all of their nodes.
 * - [cachedText]: the plain-text cache for [root] (a [String], which is immutable). Restoring it
 *   alongside [root] avoids an O(N) rebuild on undo/redo.
 *
 * The character buffers are intentionally **not** captured: `originalBuffer` never changes except on
 * `build` (which clears history), and `addedBuffer` is append-only, so any offset referenced by an
 * older [root] stays valid forever.
 */
internal class PieceTableSnapshot(
    val root: RopeNode?,
    val cachedText: String?,
)
