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
import com.jjrodcast.textkit.ui.model.TextKitMentionSuggestion
import com.jjrodcast.textkit.ui.state.TextKitState
import kotlin.math.roundToInt

/**
 * Popup shown while the user is composing a mention (i.e. after typing the configured trigger char).
 * It filters [candidates] by [TextKitState.mentionQuery] and, on tap, commits the mention via
 * [TextKitState.selectMention]. Renders nothing when no mention is being composed or nothing matches.
 *
 * Place it inside the same `Box` that hosts the editor so it shares its coordinate space:
 *
 * ```
 * Box {
 *     TextKitEditor(state = state)
 *     TextKitMentionPopup(state = state, candidates = users)
 * }
 * ```
 *
 * @param candidates the full set of people that can be mentioned; filtered here by the live query.
 * @param filter how a candidate is matched against the query (default: case-insensitive label match).
 */
@Composable
fun TextKitMentionPopup(
    state: TextKitState,
    candidates: List<TextKitMentionSuggestion>,
    modifier: Modifier = Modifier,
    filter: (TextKitMentionSuggestion, String) -> Boolean = { s, q ->
        s.label.contains(q, ignoreCase = true)
    },
) {
    val query = state.mentionQuery ?: return
    val anchor = state.mentionAnchorBounds() ?: return
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
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                            .clickable { state.selectMention(suggestion.id, suggestion.label) }
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
