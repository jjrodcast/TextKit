package com.jjrodcast.textkit.ui.model

import com.jjrodcast.textkit.ui.state.TextKitState

/**
 * A single entry in a slash-command popup (an ephemeral `/` trigger). Unlike an atomic token
 * suggestion, picking a command runs [onSelect] instead of inserting a persisted node: the editor
 * first removes the `/query` the user typed, then invokes [onSelect] at the resulting caret.
 *
 * Use the [Companion] factories for the built-in block commands, or [custom] to launch any action —
 * [onSelect] receives the live [TextKitState], so a command can apply marks, toggle lists, insert
 * text ([TextKitState.insertText]), or drive anything else the editor exposes.
 *
 * @param id stable identity (used as the list key).
 * @param label the text shown in the popup.
 * @param onSelect the action to run after the `/query` is removed.
 */
class TextKitCommand(
    val id: String,
    val label: String,
    val onSelect: (TextKitState) -> Unit,
) {
    companion object {
        /**
         * Applies a heading of [level] (1..6) to the caret's paragraph. See
         * [TextKitState.applyHeading].
         */
        fun heading(level: Int, label: String = "Heading $level"): TextKitCommand =
            TextKitCommand(id = "heading-$level", label = label) { it.applyHeading(level) }

        /** Turns the caret's paragraph into a bulleted list. */
        fun bulletList(label: String = "Bulleted list"): TextKitCommand =
            TextKitCommand(id = "bullet-list", label = label) { it.toggleUnorderedList(true) }

        /** Turns the caret's paragraph into a numbered list. */
        fun orderedList(label: String = "Numbered list"): TextKitCommand =
            TextKitCommand(id = "ordered-list", label = label) { it.toggleOrderedList(true) }

        /** A command running an arbitrary [action] with the live editor state. */
        fun custom(id: String, label: String, action: (TextKitState) -> Unit): TextKitCommand =
            TextKitCommand(id = id, label = label, onSelect = action)
    }
}
