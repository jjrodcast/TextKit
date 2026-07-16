package com.jjrodcast.textkit.editor.models

import androidx.compose.ui.graphics.Color

/**
 * A character that, when typed, activates a special inline flow in the editor.
 *
 * A trigger is either:
 * - an **atomic token** ([nodeType] non-null): picking a suggestion inserts an indivisible inline
 *   node (e.g. a mention chip) that is persisted to JSON as `nodeType` and round-trips exactly; or
 * - an **ephemeral command** ([nodeType] null): the popup runs an action (e.g. inserting plain text)
 *   and leaves no persisted token behind (e.g. the `/` slash menu).
 *
 * @property triggerKey the character that opens the flow (e.g. `@`, `#`, `/`).
 * @property nodeType the persisted JSON node type this trigger inserts, or null for an ephemeral
 *   command. Must match the node's `@SerialName` (e.g. `"mention"`, `"hashtag"`).
 * @property color the accent color used to render the inserted token chip.
 */
abstract class TextKitTrigger(
    val triggerKey: Char,
    val nodeType: String?,
) {
    abstract val color: Color

    /** Whether picking a suggestion inserts a persisted atomic token (vs. an ephemeral command). */
    val isToken: Boolean get() = nodeType != null

    /**
     * Mention flow: typing `@` opens the popup and inserts an atomic `mention` node rendered as a
     * chip in [color].
     */
    data class TextKitMentionTrigger(override val color: Color = Color(0xFF1B75D0)) :
        TextKitTrigger('@', "mention")

    /**
     * Hashtag flow: typing `#` opens the popup and inserts an atomic `hashtag` node rendered as a
     * chip in [color].
     */
    data class TextKitHashtagTrigger(override val color: Color = Color(0xFF2E7D32)) :
        TextKitTrigger('#', "hashtag")

    /**
     * Slash-command flow: typing `/` opens a command popup (see `TextKitSlashCommandPopup`). Picking
     * a command removes the `/query` and runs an action — a built-in block command (heading, list) or
     * any custom callback — persisting nothing, hence [nodeType] is null.
     */
    data class TextKitSlashTrigger(override val color: Color = Color(0xFF6A1B9A)) :
        TextKitTrigger('/', null)
}
