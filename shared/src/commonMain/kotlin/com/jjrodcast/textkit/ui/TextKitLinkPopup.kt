package com.jjrodcast.textkit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.min
import com.jjrodcast.textkit.theme.TextKitTheme
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
        val density = LocalDensity.current
        val gap = with(density) { 4.dp.roundToPx() }
        val pointerWidthPx = with(density) { PointerWidth.toPx() }
        val pointerHeightPx = with(density) { PointerHeight.toPx() }
        val cornerRadiusPx = with(density) { CardCornerRadius.toPx() }
        val maxWidth = constraints.maxWidth
        val maxHeight = constraints.maxHeight
        var cardSize by remember { mutableStateOf(IntSize.Zero) }
        val containerColor = TextKitTheme.colors.surface

        // Keep the card inside the container: clamp X to the right edge, and place it below the
        // link — flipping above when it would overflow the bottom. The card's own height already
        // includes the pointer strip (reserved as content padding), so the beak points at the link
        // without covering it.
        val x = anchor.left.roundToInt().coerceIn(0, (maxWidth - cardSize.width).coerceAtLeast(0))
        val below = anchor.bottom.roundToInt() + gap
        val above = anchor.top.roundToInt() - gap - cardSize.height
        val fitsBelow = below + cardSize.height <= maxHeight
        val fitsAbove = above >= 0
        // Point up when the card sits below the link, down when it flips above it.
        val pointingUp = fitsBelow || !fitsAbove
        // In the last-resort clamp branch the card is not adjacent to the link, so no beak.
        val showPointer = fitsBelow || fitsAbove
        val y = when {
            fitsBelow -> below
            fitsAbove -> above
            else -> (maxHeight - cardSize.height).coerceAtLeast(0)
        }

        // Horizontal center of the beak, in the card's own coordinate space. The shape clamps it so
        // the tip stays on the straight edge, clear of the rounded corners.
        val pointerCenterLocal = (anchor.left + anchor.right) / 2f - x

        val shape = if (showPointer) {
            PopupBubbleShape(
                pointingUp = pointingUp,
                pointerCenterX = pointerCenterLocal,
                pointerWidthPx = pointerWidthPx,
                pointerHeightPx = pointerHeightPx,
                cornerRadiusPx = cornerRadiusPx,
            )
        } else {
            RoundedCornerShape(CardCornerRadius)
        }

        // Reserve the beak's height on the side it sits so content never overlaps the triangle.
        val pointerPadding = if (showPointer) PointerHeight else 0.dp
        val contentPadding = PaddingValues(
            start = ContentPadding,
            end = ContentPadding,
            top = ContentPadding + if (pointingUp) pointerPadding else 0.dp,
            bottom = ContentPadding + if (!pointingUp) pointerPadding else 0.dp,
        )

        // Key on the link so the editable text/URL fields reset to the newly selected link instead
        // of keeping the previous one's remembered contents.
        key(link.range.start, link.range.end, link.text, link.url) {
            LinkPopupContent(
                link = link,
                containerColor = containerColor,
                shape = shape,
                contentPadding = contentPadding,
                onEdit = onEdit,
                onRemove = onRemove,
                onClose = onClose,
                modifier = Modifier
                    .offset { IntOffset(x, y) }
                    .onSizeChanged { cardSize = it },
            )
        }
    }
}

private val PointerWidth = 20.dp
private val PointerHeight = 10.dp
private val CardCornerRadius = 12.dp
private val ContentPadding = 16.dp

/**
 * Card outline for [TextKitLinkPopup] with the pointer "beak" baked into the same shape, so the
 * card's background, border and elevation shadow all follow the triangle rather than it being a
 * separate overlay. The body is a rounded rectangle inset by [pointerHeightPx] on the pointer's
 * side; the triangle (rounded tip) is unioned onto that edge at [pointerCenterX].
 *
 * [pointingUp] puts the beak on the top edge (card below the link); otherwise the bottom edge.
 * [pointerCenterX] is the desired tip center in the card's local X; it is clamped here so the beak
 * never rides onto the rounded corners.
 */
private data class PopupBubbleShape(
    private val pointingUp: Boolean,
    private val pointerCenterX: Float,
    private val pointerWidthPx: Float,
    private val pointerHeightPx: Float,
    private val cornerRadiusPx: Float,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val w = size.width
        val h = size.height
        val radius = cornerRadiusPx.coerceAtMost(min(w, h) / 2f)
        val halfPointer = pointerWidthPx / 2f
        // Clamp so both base corners of the beak stay on the straight part of the edge.
        val center = pointerCenterX.coerceIn(radius + halfPointer, w - radius - halfPointer)
        // How far down each slanted edge the rounding of the tip starts (0 = sharp, →1 = round).
        val tipRound = 0.42f

        val bodyTop = if (pointingUp) pointerHeightPx else 0f
        val bodyBottom = if (pointingUp) h else h - pointerHeightPx
        val body = Path().apply {
            addRoundRect(
                RoundRect(
                    left = 0f,
                    top = bodyTop,
                    right = w,
                    bottom = bodyBottom,
                    cornerRadius = CornerRadius(radius, radius),
                )
            )
        }

        val beak = Path().apply {
            if (pointingUp) {
                moveTo(center - halfPointer, bodyTop)
                lineTo(center - tipRound * halfPointer, tipRound * pointerHeightPx)
                quadraticTo(center, 0f, center + tipRound * halfPointer, tipRound * pointerHeightPx)
                lineTo(center + halfPointer, bodyTop)
                close()
            } else {
                moveTo(center - halfPointer, bodyBottom)
                lineTo(center - tipRound * halfPointer, h - tipRound * pointerHeightPx)
                quadraticTo(
                    center,
                    h,
                    center + tipRound * halfPointer,
                    h - tipRound * pointerHeightPx
                )
                lineTo(center + halfPointer, bodyBottom)
                close()
            }
        }

        // Union so the outline is a single closed path (border/shadow follow the beak cleanly).
        val merged = Path().apply { op(body, beak, PathOperation.Union) }
        return Outline.Generic(merged)
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
    modifier: Modifier = Modifier,
    containerColor: Color = TextKitTheme.colors.surface,
    shape: Shape = RoundedCornerShape(CardCornerRadius),
    contentPadding: PaddingValues = PaddingValues(ContentPadding),
) {
    val textState = rememberTextFieldState(link.text)
    val urlState = rememberTextFieldState(link.url)
    // Theme the text fields (input text, cursor, border and label) so they follow TextKit instead of
    // falling back to the default Material color scheme.
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = TextKitTheme.colors.onSurface,
        unfocusedTextColor = TextKitTheme.colors.onSurface,
        cursorColor = TextKitTheme.colors.primary,
        focusedBorderColor = TextKitTheme.colors.primary,
        unfocusedBorderColor = TextKitTheme.colors.outline,
        focusedLabelColor = TextKitTheme.colors.primary,
        unfocusedLabelColor = TextKitTheme.colors.onSurfaceVariant,
    )
    Card(
        modifier = modifier.widthIn(max = 320.dp),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = TextKitTheme.colors.onSurface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
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
                state = textState,
                lineLimits = TextFieldLineLimits.SingleLine,
                label = { Text(stringResource(Res.string.text_label)) },
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                state = urlState,
                lineLimits = TextFieldLineLimits.SingleLine,
                label = { Text(stringResource(Res.string.url_label)) },
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = { onRemove(link) },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = TextKitTheme.colors.error
                    ),
                ) {
                    Text(stringResource(Res.string.remove_label))
                }
                Spacer(Modifier.size(8.dp))
                Button(
                    onClick = {
                        onEdit(
                            link.copy(
                                text = textState.text.toString(),
                                url = urlState.text.toString(),
                            )
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TextKitTheme.colors.primary,
                        contentColor = TextKitTheme.colors.onPrimary,
                    ),
                ) {
                    Text(stringResource(Res.string.edit_label))
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TextKitLinkPopupPreview() {
    TextKitTheme {
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
