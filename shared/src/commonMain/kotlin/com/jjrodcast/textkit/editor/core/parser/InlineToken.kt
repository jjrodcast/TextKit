package com.jjrodcast.textkit.editor.core.parser

/**
 * Common shape of every atomic inline "trigger token" node (`mention`, `hashtag`, …). Each concrete
 * node is still its own [BaseText] sealed subclass with its own `@SerialName` (so the JSON `"type"`
 * discriminator stays correct), but they all expose the same [attrs]/[marks]/[type] so the converters
 * can treat them uniformly instead of branching per node type.
 */
internal interface InlineToken {
    val attrs: TokenAttrs
    val marks: Set<Mark>

    /** The persisted node type (e.g. `"mention"`, `"hashtag"`); matches the node's `@SerialName`. */
    val type: String
}
