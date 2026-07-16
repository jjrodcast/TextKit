package com.jjrodcast.textkit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.FormatColorFill
import androidx.compose.material.icons.rounded.FormatItalic
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.FormatStrikethrough
import androidx.compose.material.icons.rounded.FormatUnderlined
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jjrodcast.textkit.ui.state.TextKitFormattingBarState
import com.jjrodcast.textkit.ui.state.rememberTextKitFormattingBarState
import org.jetbrains.compose.resources.stringResource
import textkit.shared.generated.resources.Res
import textkit.shared.generated.resources.bold_text
import textkit.shared.generated.resources.bulleted_list_text
import textkit.shared.generated.resources.highlight_text
import textkit.shared.generated.resources.italic_text
import textkit.shared.generated.resources.link_text
import textkit.shared.generated.resources.ordered_list_text
import textkit.shared.generated.resources.strikethrough_text
import textkit.shared.generated.resources.text_color_text
import textkit.shared.generated.resources.underline_text

@Composable
fun TextKitScreen(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    MaterialTheme {
        // height(IntrinsicSize.Min) makes the Row as tall as its tallest child (a FormattingItem),
        // so the separator's fillMaxHeight resolves to the item height instead of the whole bar.
        Scaffold(modifier) { innerPadding ->
            Column(modifier = Modifier.padding(innerPadding)) {
                content()
            }
        }
    }
}

@Composable
fun TextKitFormattingBar(
    barState: TextKitFormattingBarState = rememberTextKitFormattingBarState(),
    selectedColor: Color,
    onBoldClick: (Boolean) -> Unit = {},
    onItalicClick: (Boolean) -> Unit = {},
    onUnderlineClick: (Boolean) -> Unit = {},
    onStrikeThroughClick: (Boolean) -> Unit = {},
    onHighlightClick: (Boolean) -> Unit = {},
    onLinkClick: (Boolean) -> Unit = {},
    onOrderedListClick: (Boolean) -> Unit = {},
    onBulletedListClick: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.height(IntrinsicSize.Min)) {
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
            tooltipText = stringResource(Res.string.text_color_text),
            rememberVectorPainter(Icons.Outlined.Palette),
            value = false,
            onClick = {}
        )
    }
}

@Composable
fun TextKitTooltipFormattingItem(
    tooltipText: String,
    painter: Painter,
    value: Boolean,
    onClick: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Unspecified,
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
        TextKitFormattingItem(
            painter = painter,
            value = value,
            onValueChange = onClick,
            backgroundColor = backgroundColor
        )
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
            .toggleable(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                indication = ripple(bounded = true),
                interactionSource = remember { MutableInteractionSource() }
            )
            .background(
                color = if (value) backgroundColor else Color.Unspecified,
                shape = RoundedCornerShape(4.dp)
            )
            .padding(6.dp)
    ) {
        Icon(
            painter = painter,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary
        )
    }
}

@Composable
fun TextKitFormattingDivider(
    modifier: Modifier = Modifier
) {
    VerticalDivider(
        modifier = modifier.fillMaxHeight().padding(vertical = 4.dp)
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
    MaterialTheme {
        TextKitFormattingBar(
            selectedColor = Color.Yellow
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TextKitFormattingItemPreview() {
    MaterialTheme {
        var toggle by remember { mutableStateOf(false) }
        TextKitFormattingItem(
            painter = rememberVectorPainter(Icons.Rounded.FormatBold),
            value = toggle,
            onValueChange = { toggle = it },
            backgroundColor = Color.Yellow
        )
    }
}