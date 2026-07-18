package com.jjrodcast.textkit.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.jjrodcast.textkit.editor.models.TextKitTrigger
import com.jjrodcast.textkit.theme.TextKitTheme
import com.jjrodcast.textkit.ui.model.TextKitTokenSuggestion
import com.jjrodcast.textkit.ui.state.TextKitState
import kotlin.math.roundToInt

/**
 * Generic popup shown while the user is composing any trigger token (after typing a configured
 * trigger char such as `@`, `#` or `/`). It asks [candidatesFor] for the candidate list of the
 * [TextKitState.activeTrigger], filters it by [TextKitState.tokenQuery], and on tap commits via
 * [TextKitState.selectToken]. Renders nothing when no trigger is active or nothing matches.
 *
 * A single instance serves every trigger — the app decides per-trigger candidates:
 *
 * ```
 * Box {
 *     TextKitEditor(state = state)
 *     TextKitTokenPopup(state = state) { trigger ->
 *         when (trigger) {
 *             is TextKitTrigger.TextKitHashtagTrigger -> tags
 *             is TextKitTrigger.TextKitSlashTrigger -> commands
 *             else -> users
 *         }
 *     }
 * }
 * ```
 *
 * @param candidatesFor the candidate list to show for the active trigger.
 * @param filter how a candidate is matched against the query (default: case-insensitive label match).
 */
@Composable
fun TextKitTokenPopup(
    state: TextKitState,
    modifier: Modifier = Modifier,
    filter: (TextKitTokenSuggestion, String) -> Boolean = { s, q ->
        s.label.contains(q, ignoreCase = true)
    },
    candidatesFor: (TextKitTrigger) -> List<TextKitTokenSuggestion>,
) {
    val trigger = state.activeTrigger ?: return
    // Ephemeral command triggers (e.g. `/`) are served by TextKitSlashCommandPopup, not here.
    if (!trigger.isToken) return
    val query = state.tokenQuery ?: return
    val anchor = state.tokenAnchorBounds() ?: return
    val candidates = candidatesFor(trigger)
    val matches = remember(query, candidates) {
        candidates.filter { filter(it, query) }
    }
    if (matches.isEmpty()) return

    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val gap = with(density) { 4.dp.roundToPx() }
        val maxWidth = constraints.maxWidth
        val maxHeight = constraints.maxHeight
        var cardSize by remember { mutableStateOf(IntSize.Zero) }

        val x = anchor.left.roundToInt().coerceIn(0, (maxWidth - cardSize.width).coerceAtLeast(0))
        val below = anchor.bottom.roundToInt() + gap
        val above = anchor.top.roundToInt() - gap - cardSize.height
        val y = when {
            below + cardSize.height <= maxHeight -> below
            above >= 0 -> above
            else -> (maxHeight - cardSize.height).coerceAtLeast(0)
        }

        Card(
            modifier = Modifier
                .offset { IntOffset(x, y) }
                .widthIn(min = 180.dp, max = 280.dp)
                .onSizeChanged { cardSize = it },
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = TextKitTheme.colors.surface,
                contentColor = TextKitTheme.colors.onSurface
            ),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 220.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                matches.forEach { suggestion ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { state.selectToken(suggestion.id, suggestion.label) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Text(
                            text = suggestion.label,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}
