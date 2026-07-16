package com.jjrodcast.textkit.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.jjrodcast.textkit.editor.models.TextKitTrigger
import com.jjrodcast.textkit.ui.model.TextKitTokenSuggestion
import com.jjrodcast.textkit.ui.state.TextKitState

/**
 * Backward-compatible mention popup: a thin wrapper over [TextKitTokenPopup] that only shows its
 * [candidates] while a mention (`@`) is being composed. New code should prefer [TextKitTokenPopup],
 * which serves every trigger (mentions, hashtags, slash commands, …) from one instance.
 */
@Composable
fun TextKitMentionPopup(
    state: TextKitState,
    candidates: List<TextKitTokenSuggestion>,
    modifier: Modifier = Modifier,
    filter: (TextKitTokenSuggestion, String) -> Boolean = { s, q ->
        s.label.contains(q, ignoreCase = true)
    },
) {
    TextKitTokenPopup(state = state, modifier = modifier, filter = filter) { trigger ->
        if (trigger is TextKitTrigger.TextKitMentionTrigger) candidates else emptyList()
    }
}
