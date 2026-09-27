package com.jjrodcast.textkit.ui

import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatAlignLeft
import androidx.compose.material.icons.automirrored.rounded.FormatAlignRight
import androidx.compose.material.icons.automirrored.rounded.FormatTextdirectionLToR
import androidx.compose.material.icons.automirrored.rounded.FormatTextdirectionRToL
import androidx.compose.material.icons.rounded.AlignHorizontalCenter
import androidx.compose.material.icons.rounded.FormatAlignJustify
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.jjrodcast.textkit.editor.core.parser.TextAlign
import com.jjrodcast.textkit.editor.core.parser.TextDirection
import com.jjrodcast.textkit.theme.TextKitTheme
import com.jjrodcast.textkit.ui.state.TextKitState
import com.jjrodcast.textkit.ui.utils.TextKitPopupAnchorProvider
import org.jetbrains.compose.resources.stringResource
import textkit.shared.generated.resources.Res
import textkit.shared.generated.resources.center_align_text
import textkit.shared.generated.resources.justify_align_text
import textkit.shared.generated.resources.left_align_text
import textkit.shared.generated.resources.ltr_direction_text
import textkit.shared.generated.resources.right_align_text
import textkit.shared.generated.resources.rtl_direction_text

@Composable
fun TextKitAlignPopup(
    state: TextKitState,
    modifier: Modifier = Modifier,
    selectedColor: Color = TextKitTheme.colors.primary.copy(alpha = 0.45f),
    onTextAlignmentSelected: (TextAlign) -> Unit = { textAlign ->
        state.applyTextAlignment(textAlign)
        state.dismissAlignPicker()
    },
    onTextDirectionSelected: (TextDirection) -> Unit = { textDirection ->
        state.applyTextDirection(textDirection)
        state.dismissAlignPicker()
    },
    onClose: () -> Unit = { state.dismissAlignPicker() }
) {
    val anchor = state.activeAlignAnchor ?: return

    Popup(
        popupPositionProvider = TextKitPopupAnchorProvider.positionProvider(anchor),
        onDismissRequest = onClose,
        properties = PopupProperties(focusable = true)
    ) {
        Card(
            modifier = modifier.widthIn(min = 64.dp),
            colors = CardDefaults.cardColors(
                containerColor = TextKitTheme.colors.surface,
                contentColor = TextKitTheme.colors.onSurface
            ),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp)
        ) {
            Row(
                modifier = Modifier.height(IntrinsicSize.Min)
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            ) {
                TextKitTooltipFormattingItem(
                    tooltipText = stringResource(Res.string.left_align_text),
                    painter = rememberVectorPainter(Icons.AutoMirrored.Rounded.FormatAlignLeft),
                    value = state.currentTextAlign == TextAlign.Left,
                    onClick = { onTextAlignmentSelected(TextAlign.Left) },
                    backgroundColor = selectedColor
                )
                TextKitFormattingSeparator()
                TextKitTooltipFormattingItem(
                    tooltipText = stringResource(Res.string.center_align_text),
                    painter = rememberVectorPainter(Icons.Rounded.AlignHorizontalCenter),
                    value = state.currentTextAlign == TextAlign.Center,
                    onClick = { onTextAlignmentSelected(TextAlign.Center) },
                    backgroundColor = selectedColor
                )
                TextKitFormattingSeparator()
                TextKitTooltipFormattingItem(
                    tooltipText = stringResource(Res.string.right_align_text),
                    painter = rememberVectorPainter(Icons.AutoMirrored.Rounded.FormatAlignRight),
                    value = state.currentTextAlign == TextAlign.Right,
                    onClick = { onTextAlignmentSelected(TextAlign.Right) },
                    backgroundColor = selectedColor
                )
                TextKitFormattingSeparator()
                TextKitTooltipFormattingItem(
                    tooltipText = stringResource(Res.string.justify_align_text),
                    painter = rememberVectorPainter(Icons.Rounded.FormatAlignJustify),
                    value = state.currentTextAlign == TextAlign.Justify,
                    onClick = { onTextAlignmentSelected(TextAlign.Justify) },
                    backgroundColor = selectedColor
                )
                TextKitFormattingSeparator()
                TextKitTooltipFormattingItem(
                    tooltipText = stringResource(Res.string.ltr_direction_text),
                    painter = rememberVectorPainter(Icons.AutoMirrored.Rounded.FormatTextdirectionLToR),
                    value = state.currentTextDirection == TextDirection.Ltr,
                    onClick = { onTextDirectionSelected(TextDirection.Ltr) },
                    backgroundColor = selectedColor
                )
                TextKitFormattingSeparator()
                TextKitTooltipFormattingItem(
                    tooltipText = stringResource(Res.string.rtl_direction_text),
                    painter = rememberVectorPainter(Icons.AutoMirrored.Rounded.FormatTextdirectionRToL),
                    value = state.currentTextDirection == TextDirection.Rtl,
                    onClick = { onTextDirectionSelected(TextDirection.Rtl) },
                    backgroundColor = selectedColor
                )
            }
        }
    }
}