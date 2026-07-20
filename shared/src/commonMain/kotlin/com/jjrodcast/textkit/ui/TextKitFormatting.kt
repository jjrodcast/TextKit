package com.jjrodcast.textkit.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.FormatColorFill
import androidx.compose.material.icons.rounded.FormatItalic
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.FormatStrikethrough
import androidx.compose.material.icons.rounded.FormatUnderlined
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jjrodcast.textkit.theme.TextKitTheme
import com.jjrodcast.textkit.ui.state.TextKitFormattingBarState
import com.jjrodcast.textkit.ui.state.rememberTextKitFormattingBarState
import com.jjrodcast.textkit.ui.utils.TextKitPickerPallete
import org.jetbrains.compose.resources.stringResource
import textkit.shared.generated.resources.Res
import textkit.shared.generated.resources.bold_text
import textkit.shared.generated.resources.bulleted_list_text
import textkit.shared.generated.resources.document_text
import textkit.shared.generated.resources.highlight_text
import textkit.shared.generated.resources.image_text
import textkit.shared.generated.resources.italic_text
import textkit.shared.generated.resources.link_text
import textkit.shared.generated.resources.ordered_list_text
import textkit.shared.generated.resources.redo_text
import textkit.shared.generated.resources.strikethrough_text
import textkit.shared.generated.resources.table_text
import textkit.shared.generated.resources.text_color_text
import textkit.shared.generated.resources.underline_text
import textkit.shared.generated.resources.undo_text

@Composable
fun TextKitScreen(
    modifier: Modifier = Modifier,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    TextKitTheme(darkTheme = darkTheme) {
        Scaffold(
            containerColor = TextKitTheme.colors.background,
            contentColor = TextKitTheme.colors.onBackground
        ) { innerPadding ->
            Column(
                modifier = modifier
                    .background(TextKitTheme.colors.background)
                    .padding(innerPadding)
            ) {
                content()
            }
        }
    }
}

@Composable
fun TextKitFormattingBar(
    modifier: Modifier = Modifier,
    barState: TextKitFormattingBarState = rememberTextKitFormattingBarState(),
    onBoldClick: (Boolean) -> Unit = {},
    onItalicClick: (Boolean) -> Unit = {},
    onUnderlineClick: (Boolean) -> Unit = {},
    onStrikeThroughClick: (Boolean) -> Unit = {},
    onHighlightClick: (Boolean) -> Unit = {},
    onLinkClick: (Boolean) -> Unit = {},
    onImageClick: (Boolean) -> Unit = {},
    onTableClick: (Boolean) -> Unit = {},
    onDocumentClick: (Boolean) -> Unit = {},
    onOrderedListClick: (Boolean) -> Unit = {},
    onBulletedListClick: (Boolean) -> Unit = {},
    onTextAndColorClick: (Rect) -> Unit = {},
    onUndoClick: () -> Unit = {},
    onRedoClick: () -> Unit = {},
    canUndo: Boolean = false,
    canRedo: Boolean = false
) {
    TextKitFormattingBarInternal(
        modifier = modifier,
        barState = barState,
        onBoldClick = onBoldClick,
        onItalicClick = onItalicClick,
        onUnderlineClick = onUnderlineClick,
        onStrikeThroughClick = onStrikeThroughClick,
        onHighlightClick = onHighlightClick,
        onSizeAndColorClick = onTextAndColorClick,
        onLinkClick = onLinkClick,
        onImageClick = onImageClick,
        onTableClick = onTableClick,
        onDocumentClick = onDocumentClick,
        onOrderedListClick = onOrderedListClick,
        onBulletedListClick = onBulletedListClick,
        onUndoClick = onUndoClick,
        onRedoClick = onRedoClick,
        canUndo = canUndo,
        canRedo = canRedo
    )
}

@Composable
fun TextKitFormattingBarInternal(
    modifier: Modifier = Modifier,
    selectedColor: Color = TextKitTheme.colors.primary.copy(alpha = 0.45f),
    barState: TextKitFormattingBarState = rememberTextKitFormattingBarState(),
    onBoldClick: (Boolean) -> Unit = {},
    onItalicClick: (Boolean) -> Unit = {},
    onUnderlineClick: (Boolean) -> Unit = {},
    onStrikeThroughClick: (Boolean) -> Unit = {},
    onHighlightClick: (Boolean) -> Unit = {},
    onSizeAndColorClick: (Rect) -> Unit = {},
    onLinkClick: (Boolean) -> Unit = {},
    onImageClick: (Boolean) -> Unit = {},
    onTableClick: (Boolean) -> Unit = {},
    onDocumentClick: (Boolean) -> Unit = {},
    onOrderedListClick: (Boolean) -> Unit = {},
    onBulletedListClick: (Boolean) -> Unit = {},
    onUndoClick: () -> Unit = {},
    onRedoClick: () -> Unit = {},
    canUndo: Boolean = false,
    canRedo: Boolean = false
) {
    var textSizeAndColorBounds by remember { mutableStateOf(Rect.Zero) }

    Card(
        modifier = modifier.padding(4.dp),
        colors = CardDefaults.cardColors(
            containerColor = TextKitTheme.colors.surface,
            contentColor = TextKitTheme.colors.onSurface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .height(IntrinsicSize.Min)
                .padding(horizontal = 4.dp, vertical = 4.dp)
        ) {
            TextKitTooltipFormattingItem(
                tooltipText = stringResource(Res.string.bold_text),
                rememberVectorPainter(Icons.Rounded.FormatBold),
                onClick = onBoldClick,
                value = barState.isBold,
                backgroundColor = selectedColor
            )
            TextKitFormattingSeparator()
            TextKitTooltipFormattingItem(
                tooltipText = stringResource(Res.string.italic_text),
                rememberVectorPainter(Icons.Rounded.FormatItalic),
                onClick = onItalicClick,
                value = barState.isItalic,
                backgroundColor = selectedColor
            )
            TextKitFormattingSeparator()
            TextKitTooltipFormattingItem(
                tooltipText = stringResource(Res.string.underline_text),
                rememberVectorPainter(Icons.Rounded.FormatUnderlined),
                onClick = onUnderlineClick,
                value = barState.isUnderline,
                backgroundColor = selectedColor
            )
            TextKitFormattingSeparator()
            TextKitTooltipFormattingItem(
                tooltipText = stringResource(Res.string.strikethrough_text),
                rememberVectorPainter(Icons.Rounded.FormatStrikethrough),
                onClick = onStrikeThroughClick,
                value = barState.isStrikethrough,
                backgroundColor = selectedColor
            )
            TextKitFormattingSeparator()
            TextKitTooltipFormattingItem(
                tooltipText = stringResource(Res.string.highlight_text),
                rememberVectorPainter(Icons.Rounded.FormatColorFill),
                onClick = onHighlightClick,
                value = barState.isHighlight,
                backgroundColor = selectedColor
            )
            TextKitFormattingSeparator()
            TextKitTooltipFormattingItem(
                tooltipText = stringResource(Res.string.text_color_text),
                rememberVectorPainter(Icons.Outlined.Palette),
                value = false,
                isExpandable = true,
                onClick = { onSizeAndColorClick(textSizeAndColorBounds) },
                modifier = Modifier.onGloballyPositioned {
                    textSizeAndColorBounds = it.boundsInWindow()
                }
            )
            TextKitFormattingDivider()
            TextKitFormattingSeparator()
            TextKitTooltipFormattingItem(
                tooltipText = stringResource(Res.string.link_text),
                rememberVectorPainter(Icons.Rounded.Link),
                onClick = onLinkClick,
                value = barState.isLink,
                backgroundColor = selectedColor
            )
            TextKitFormattingSeparator()
            TextKitTooltipFormattingItem(
                tooltipText = stringResource(Res.string.ordered_list_text),
                rememberVectorPainter(Icons.Rounded.FormatListNumbered),
                onClick = onOrderedListClick,
                value = barState.isNumberedList,
                backgroundColor = selectedColor
            )
            TextKitFormattingSeparator()
            TextKitTooltipFormattingItem(
                tooltipText = stringResource(Res.string.bulleted_list_text),
                rememberVectorPainter(Icons.AutoMirrored.Rounded.FormatListBulleted),
                onClick = onBulletedListClick,
                value = barState.isBulletedList,
                backgroundColor = selectedColor
            )
            TextKitFormattingSeparator()
            TextKitFormattingDivider()
            TextKitFormattingSeparator()
            TextKitTooltipFormattingItem(
                tooltipText = stringResource(Res.string.image_text),
                painter = rememberVectorPainter(Icons.Outlined.Image),
                value = barState.isImage,
                onClick = onImageClick,
                backgroundColor = selectedColor
            )
            TextKitFormattingSeparator()
            TextKitTooltipFormattingItem(
                tooltipText = stringResource(Res.string.table_text),
                painter = rememberVectorPainter(Icons.Outlined.TableChart),
                value = barState.isTable,
                onClick = onTableClick,
                backgroundColor = selectedColor
            )
            TextKitFormattingSeparator()
            TextKitTooltipFormattingItem(
                tooltipText = stringResource(Res.string.document_text),
                painter = rememberVectorPainter(Icons.AutoMirrored.Outlined.Article),
                value = barState.isDocument,
                onClick = onDocumentClick,
                backgroundColor = selectedColor
            )
            TextKitFormattingSeparator()
            TextKitFormattingDivider()
            TextKitTooltipFormattingItem(
                tooltipText = stringResource(Res.string.undo_text),
                rememberVectorPainter(Icons.AutoMirrored.Rounded.Undo),
                onClick = { onUndoClick() },
                value = false,
                backgroundColor = selectedColor,
                enabled = canUndo
            )
            TextKitFormattingSeparator()
            TextKitTooltipFormattingItem(
                tooltipText = stringResource(Res.string.redo_text),
                rememberVectorPainter(Icons.AutoMirrored.Rounded.Redo),
                onClick = { onRedoClick() },
                value = false,
                backgroundColor = selectedColor,
                enabled = canRedo
            )
        }
    }
}

@Composable
fun TextKitTooltipFormattingItem(
    tooltipText: String,
    painter: Painter,
    value: Boolean,
    isExpandable: Boolean = false,
    onClick: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Unspecified,
    enabled: Boolean = true,
) {
    TooltipBox(
        modifier = modifier,
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            TooltipAnchorPosition.Above
        ),
        state = rememberTooltipState(),
        tooltip = {
            PlainTooltip {
                Text(tooltipText)
            }
        }
    ) {
        val indicatorColor =
            TextKitTheme.colors.secondary.copy(alpha = if (enabled) 1f else 0.38f)
        Box {
            TextKitFormattingItem(
                painter = painter,
                value = value,
                onValueChange = onClick,
                backgroundColor = backgroundColor,
                enabled = enabled
            )
            if (isExpandable) {
                TextKitItemExpandableIndicator(indicatorColor)
            }
        }
    }
}

@Composable
private fun BoxScope.TextKitItemExpandableIndicator(indicatorColor: Color) {
    Canvas(
        modifier = Modifier
            .padding(2.dp)
            .size(6.dp)
            .align(Alignment.BottomEnd)
    ) {
        val path = Path().apply {
            moveTo(size.width, size.height)     // right corner
            lineTo(0f, size.height)             // lower left
            lineTo(size.width, 0f)              // upper right
            close()
        }

        drawPath(path = path, color = indicatorColor)
    }
}

@Composable
fun TextKitFormattingItem(
    painter: Painter,
    value: Boolean,
    onValueChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Unspecified,
    enabled: Boolean = true
) {
    Box(
        modifier = modifier
            // Do not steal focus from the editor: a focusable toolbar button would pull focus off the
            // BasicTextField on click, collapsing the active selection before the format action runs,
            // so marks would never reach the selected range.
            .focusProperties { canFocus = false }
            // Outer margin so a selected item's highlight rect doesn't touch its neighbors and each
            // item has breathing room in its normal state.
            .padding(horizontal = 2.dp, vertical = 2.dp)
            .toggleable(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                indication = ripple(bounded = true),
                interactionSource = remember { MutableInteractionSource() }
            )
            .background(
                color = if (value) backgroundColor else Color.Unspecified,
                shape = RoundedCornerShape(6.dp)
            )
            .padding(8.dp)
    ) {
        Icon(
            painter = painter,
            contentDescription = null
        )
    }
}

@Composable
fun TextKitFormattingDivider(
    modifier: Modifier = Modifier,
    color: Color = TextKitTheme.colors.outlineVariant
) {
    VerticalDivider(
        modifier = modifier.fillMaxHeight().padding(vertical = 4.dp),
        color = color
    )
}

@Composable
private fun TextKitFormattingSeparator(
    modifier: Modifier = Modifier
) {
    Spacer(
        modifier = modifier.size(2.dp)
    )
}


@Preview(showBackground = true)
@Composable
private fun TextKitFormattingBarPreview() {
    TextKitTheme {
        TextKitFormattingBar(
            barState = rememberTextKitFormattingBarState(colors = TextKitPickerPallete.DefaultPallete),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TextKitFormattingItemPreview() {
    TextKitTheme {
        var toggle by remember { mutableStateOf(false) }
        TextKitFormattingItem(
            painter = rememberVectorPainter(Icons.Rounded.FormatBold),
            value = toggle,
            onValueChange = { toggle = it },
            backgroundColor = Color.Yellow
        )
    }
}