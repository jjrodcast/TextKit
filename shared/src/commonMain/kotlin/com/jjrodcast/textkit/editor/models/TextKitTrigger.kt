package com.jjrodcast.textkit.editor.models

import androidx.compose.ui.graphics.Color

/**
 * A character that, when typed, activates a special inline flow in the editor.
 *
 * @property triggerKey the character that opens the flow (e.g. `@` for mentions).
 */
abstract class TextKitTrigger(val triggerKey: Char) {

    /**
     * Configures the mention flow: typing [triggerKey] opens the mention popup, and inserted
     * mentions render as a chip in [color].
     */
    data class TextKitMentionTrigger(val color: Color = Color(0xFF1B75D0)) : TextKitTrigger('@')
}
