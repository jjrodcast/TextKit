package com.jjrodcast.textkit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.jjrodcast.textkit.ui.model.TextKitLinkInfo
import com.jjrodcast.textkit.ui.state.TextKitState
import org.jetbrains.compose.resources.stringResource
import textkit.shared.generated.resources.Res
import textkit.shared.generated.resources.close_text
import textkit.shared.generated.resources.edit_label
import textkit.shared.generated.resources.remove_label
import textkit.shared.generated.resources.text_label
import textkit.shared.generated.resources.url_label
import kotlin.math.roundToInt

/**
 * Popup anchored to the link currently under the caret / selection ([TextKitState.activeLink]).
 *
 * Renders nothing when no link is active. It shows two read-only fields (the link text and URL)
 * plus **Edit** and **Remove** actions. Place it inside the same container (a `Box`) that hosts the
 * editor so it shares its coordinate space and stays within the component bounds — the popup fills
 * that container and clamps itself next to the link (flipping above it when there is no room below).
 *
 * ```
 * Box {
 *     TextKitEditor(state = state)
 *     TextKitLinkPopup(
 *         state = state,
 *         onEdit = { link -> /* open your link editor for link.range */ },
 *         onRemove = { link -> /* clear the link over link.range */ },
 *     )
 * }
 * ```
 *
 * @param onEdit invoked with the active link when the user taps **Edit**.
 * @param onRemove invoked with the active link when the user taps **Remove**.
 */
@Composable
fun TextKitLinkPopup(
    state: TextKitState,
    modifier: Modifier = Modifier,
    onEdit: (TextKitLinkInfo) -> Unit = {},
    onRemove: (TextKitLinkInfo) -> Unit = {},
    onClose: () -> Unit = { state.dismissLinkPopup() },
) {
    val link = state.activeLink ?: return
    val anchor = state.linkBoundingBox(link.range) ?: return

    BoxWithConstraints(modifier.fillMaxSize()) {
        val gap = with(LocalDensity.current) { 4.dp.roundToPx() }
        val maxWidth = constraints.maxWidth
        val maxHeight = constraints.maxHeight
        var cardSize by remember { mutableStateOf(IntSize.Zero) }

        // Keep the card inside the container: clamp X to the right edge, and place it below the
        // link — flipping above when it would overflow the bottom.
        val x = anchor.left.roundToInt().coerceIn(0, (maxWidth - cardSize.width).coerceAtLeast(0))
        val below = anchor.bottom.roundToInt() + gap
        val above = anchor.top.roundToInt() - cardSize.height - gap
        val y = when {
            below + cardSize.height <= maxHeight -> below
            above >= 0 -> above
            else -> (maxHeight - cardSize.height).coerceAtLeast(0)
        }

        LinkPopupContent(
            link = link,
            onEdit = onEdit,
            onRemove = onRemove,
            onClose = onClose,
            modifier = Modifier
                .offset { IntOffset(x, y) }
                .onSizeChanged { cardSize = it },
        )
    }
}

/**
 * Stateless card body of [TextKitLinkPopup]: the two read-only fields and the Edit / Remove
 * actions. Kept separate from the anchoring logic so it can be previewed on its own.
 */
@Composable
private fun LinkPopupContent(
    link: TextKitLinkInfo,
    onEdit: (TextKitLinkInfo) -> Unit,
    onRemove: (TextKitLinkInfo) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val urlState = rememberTextFieldState(link.url)
    Card(
        modifier = modifier.widthIn(max = 320.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Link",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.size(16.dp))
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(Res.string.close_text),
                    )
                }
            }
            HorizontalDivider()
            OutlinedTextField(
                value = link.text,
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                label = { Text(stringResource(Res.string.text_label)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                state = urlState,
                lineLimits = TextFieldLineLimits.SingleLine,
                label = { Text(stringResource(Res.string.url_label)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { onRemove(link) }) { Text(stringResource(Res.string.remove_label)) }
                Spacer(Modifier.size(8.dp))
                Button(
                    onClick = { onEdit(link.copy(url = urlState.text.toString())) }
                ) {
                    Text(
                        stringResource(Res.string.edit_label)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TextKitLinkPopupPreview() {
    MaterialTheme {
        LinkPopupContent(
            link = TextKitLinkInfo(
                text = "TextKit repository",
                url = "https://github.com/jjrodcast/textkit",
                range = TextRange(0, 17),
            ),
            onEdit = {},
            onRemove = {},
            onClose = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
