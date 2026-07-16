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
import com.jjrodcast.textkit.ui.model.TextKitCommand
import com.jjrodcast.textkit.ui.state.TextKitState
import kotlin.math.roundToInt

/**
 * Popup for an ephemeral command trigger (e.g. `/`). Shows while a command trigger is being composed,
 * filters [commands] by [TextKitState.tokenQuery], and on tap runs the command via
 * [TextKitState.runCommand] — which removes the `/query` and then executes the command's action
 * (applying a heading/list, or any [TextKitCommand.custom] callback). Renders nothing when no command
 * trigger is active or nothing matches.
 *
 * ```
 * Box {
 *     TextKitEditor(state = state)
 *     TextKitTokenPopup(state = state) { … }           // @/# atomic tokens
 *     TextKitSlashCommandPopup(                          // / commands
 *         state = state,
 *         commands = listOf(
 *             TextKitCommand.heading(1),
 *             TextKitCommand.bulletList(),
 *             TextKitCommand.custom("date", "Insert date") { it.insertText("2026-07-15") },
 *         ),
 *     )
 * }
 * ```
 *
 * @param commands the commands to offer for the active command trigger.
 * @param filter how a command is matched against the query (default: case-insensitive label match).
 */
@Composable
fun TextKitSlashCommandPopup(
    state: TextKitState,
    commands: List<TextKitCommand>,
    modifier: Modifier = Modifier,
    filter: (TextKitCommand, String) -> Boolean = { c, q ->
        c.label.contains(q, ignoreCase = true)
    },
) {
    val trigger = state.activeTrigger ?: return
    // Only ephemeral command triggers; atomic-token triggers are served by TextKitTokenPopup.
    if (trigger.isToken) return
    val query = state.tokenQuery ?: return
    val anchor = state.tokenAnchorBounds() ?: return
    val matches = remember(query, commands) {
        commands.filter { filter(it, query) }
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
                matches.forEach { command ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { state.runCommand(command) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Text(
                            text = command.label,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}
