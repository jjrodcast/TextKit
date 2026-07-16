package com.jjrodcast.textkit.editor.core.piecetable.models

import com.jjrodcast.textkit.editor.core.parser.TokenAttrs
import kotlinx.serialization.Serializable

/**
 * Metadata carried by an atomic token piece (see [RichPiece.token]). [type] is the persisted node
 * type (`"mention"`, `"hashtag"`, or `"embed"`) so the piece can be reserialized back to the right
 * node, and [attrs] holds its identity (id + label). Selection/editing treats such a piece as
 * indivisible.
 *
 * [payload] is only set for **embedded blocks** (`type == "embed"`): it holds the original block JSON
 * (a table, image, document, …) verbatim so the block round-trips even though the editor only shows a
 * one-line placeholder for it. It stays `null` for inline tokens like mentions/hashtags.
 */
@Serializable
internal data class RichToken(
    val type: String,
    val attrs: TokenAttrs,
    val payload: String? = null,
)
