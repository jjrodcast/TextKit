package com.jjrodcast.textkit.editor.models

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class TextKitConfiguration(
    val highlightColor: Color = Color.Yellow,
    val linkColor: Color = Color(0xFF1B75D0),
    val textColor: Color = Color(0xFF000000),
    val fontSize: Int = 14,
    val triggers: Set<TextKitTrigger> = emptySet(),
    /**
     * Whether embedded blocks (tables, images, …) are interactive. When false, [TextKitState.insertEmbed]
     * and [TextKitState.openEmbedAt] are no-ops, so no new embeds can be inserted and tapping an existing
     * placeholder won't open its popup. Existing embeds still render as placeholders and round-trip in JSON.
     */
    val embedsEnabled: Boolean = true
) {
    /** The trigger registered for [char] (e.g. `@`), or null when that character has no trigger. */
    fun triggerFor(char: Char): TextKitTrigger? = triggers.firstOrNull { it.triggerKey == char }

    /**
     * The trigger that produces the [type] persisted node (e.g. `"mention"`, `"hashtag"`), or null.
     * Used when serializing/rendering a token to resolve its char and color from config.
     */
    fun triggerForType(type: String?): TextKitTrigger? =
        type?.let { t -> triggers.firstOrNull { it.nodeType == t } }

    /** The configured mention trigger, or null when mentions are not enabled. */
    val mentionTrigger: TextKitTrigger.TextKitMentionTrigger?
        get() = triggers.filterIsInstance<TextKitTrigger.TextKitMentionTrigger>().firstOrNull()
}
