package com.jjrodcast.textkit.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.jjrodcast.textkit.editor.core.TextKitEditorManager
import com.jjrodcast.textkit.editor.core.parser.EmbedTypes
import com.jjrodcast.textkit.theme.TextKitTheme
import com.jjrodcast.textkit.ui.state.TextKitState
import com.jjrodcast.textkit.ui.table.TextKitEditableTable
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import textkit.shared.generated.resources.Res
import textkit.shared.generated.resources.close_text
import textkit.shared.generated.resources.remove_label
import textkit.shared.generated.resources.text_kit_banner
import kotlin.math.roundToInt

/**
 * Popup anchored to the embedded-block placeholder whose popup is open ([TextKitState.activeEmbed]).
 * Renders nothing when none is active.
 *
 * For a `table` it renders the actual table (parsed from the stored JSON) so the user can *see* it
 * without the editor having to render it inline; other embed types fall back to showing their raw
 * JSON. A **Eliminar** action removes the placeholder (and its JSON node) from the document.
 *
 * Place it in the same `Box` as the editor so it shares the coordinate space:
 * ```
 * Box {
 *     TextKitEditor(state = state)
 *     TextKitEmbedPopup(state = state)
 * }
 * ```
 */
@Composable
fun TextKitEmbedPopup(
    state: TextKitState,
    modifier: Modifier = Modifier,
    onClose: () -> Unit = { state.dismissEmbedPopup() },
    onRemove: () -> Unit = { state.removeActiveEmbed() },
    onSync: (String) -> Unit = { state.updateActiveEmbed(it) },
) {
    val embed = state.activeEmbed ?: return
    val anchor = state.activeEmbedBoundingBox() ?: return

    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val gap = with(density) { 6.dp.roundToPx() }
        val maxW = constraints.maxWidth
        val maxH = constraints.maxHeight
        var cardSize by remember { mutableStateOf(IntSize.Zero) }
        val dragOffset = state.embedPopupOffset

        // Anchored base position: below the placeholder, flipping above if it would overflow.
        val baseX = anchor.left.roundToInt()
        val below = anchor.bottom.roundToInt() + gap
        val above = anchor.top.roundToInt() - gap - cardSize.height
        val baseY = if (below + cardSize.height <= maxH || above < 0) below else above

        // Add the user's drag, then clamp so the card stays fully on-screen — this is what keeps a
        // large table popup reachable in landscape, where the anchor may sit off the visible area.
        val x = (baseX + dragOffset.x.roundToInt()).coerceIn(0, (maxW - cardSize.width).coerceAtLeast(0))
        val y = (baseY + dragOffset.y.roundToInt()).coerceIn(0, (maxH - cardSize.height).coerceAtLeast(0))

        // Never let the card exceed the viewport height; its content scrolls within this cap.
        val availableHeight = with(density) { maxH.toDp() } - 16.dp

        EmbedPopupContent(
            embed = embed,
            onClose = onClose,
            onRemove = onRemove,
            onSync = onSync,
            onDrag = { state.dragEmbedPopup(it) },
            maxHeight = availableHeight,
            modifier = Modifier
                .offset { IntOffset(x, y) }
                .onSizeChanged { cardSize = it },
        )
    }
}

@Composable
private fun EmbedPopupContent(
    embed: TextKitEditorManager.EmbedInfo,
    onClose: () -> Unit,
    onRemove: () -> Unit,
    onSync: (String) -> Unit,
    onDrag: (Offset) -> Unit,
    maxHeight: Dp,
    modifier: Modifier = Modifier,
) {
    // Tables carry an inline editor (with a side rail), so give them more room than image/document.
    val maxWidth = if (embed.embedType == EmbedTypes.Table) 460.dp else 360.dp
    Card(
        modifier = modifier.widthIn(max = maxWidth).heightIn(max = maxHeight),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = TextKitTheme.colors.surface,
            contentColor = TextKitTheme.colors.onSurface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Header doubles as a drag handle so the popup can be moved anywhere on screen.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(embed.range) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount)
                        }
                    },
            ) {
                Icon(
                    Icons.Rounded.DragIndicator,
                    contentDescription = null,
                    tint = TextKitTheme.colors.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = embed.embedType.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f).padding(start = 6.dp),
                )
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(Res.string.close_text))
                }
            }
            HorizontalDivider()

            // The body flexes to the available height (fill = false keeps small embeds compact) and
            // its own scroll handles anything taller than the capped popup.
            Box(modifier = Modifier.weight(1f, fill = false)) {
                when (embed.embedType) {
                    EmbedTypes.Image -> Image(
                        painter = painterResource(Res.drawable.text_kit_banner),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
                    )

                    EmbedTypes.Table -> TextKitEditableTable(
                        rawJson = embed.rawJson,
                        onSync = onSync,
                    )

                    // Any other embed: show the stored JSON so it is at least inspectable.
                    else -> Text(text = embed.rawJson, style = MaterialTheme.typography.bodySmall)
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(
                    onClick = onRemove,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = TextKitTheme.colors.error
                    ),
                ) {
                    Text(stringResource(Res.string.remove_label))
                }
            }
        }
    }
}

