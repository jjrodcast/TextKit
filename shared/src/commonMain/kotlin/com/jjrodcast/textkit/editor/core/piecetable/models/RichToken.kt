package com.jjrodcast.textkit.editor.core.piecetable.models

import com.jjrodcast.textkit.editor.core.parser.TokenAttrs
import kotlinx.serialization.Serializable

/**
 * Metadata carried by an atomic trigger-token piece (see [RichPiece.token]). [type] is the persisted
 * node type (`"mention"`, `"hashtag"`, …) so the piece can be reserialized back to the right inline
 * node, and [attrs] holds its identity (id + label). Selection/editing treats such a piece as
 * indivisible.
 */
@Serializable
internal data class RichToken(val type: String, val attrs: TokenAttrs)
