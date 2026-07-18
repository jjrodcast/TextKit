package com.jjrodcast.textkit.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.jjrodcast.textkit.editor.utils.toHexWithAlpha
import com.jjrodcast.textkit.ui.state.TextKitState
import com.jjrodcast.textkit.ui.utils.TextKitPickerPallete
import com.jjrodcast.textkit.ui.utils.TextKitPopupAnchorProvider

/**
 * Popup that shows the text-color palette, anchored to wherever the color picker was opened
 * ([TextKitState.activeColorAnchor]). Follows the same pattern as [TextKitLinkPopup]: it renders
 * nothing while the picker is closed, observes the editor state to know when to show, writes the
 * pick back to the editor via [TextKitState.applyTextColor], and reflects the editor's current
 * color by marking it in the grid ([TextKitState.currentTextColor]).
 *
 * Open it from the formatting bar's palette button, then place this popup next to the editor:
 *
 * ```
 * TextKitFormattingBar(
 *     // …
 *     onTextAndColorClick = { bounds -> state.openColorPicker(bounds) },
 * )
 * Box {
 *     TextKitEditor(state = state)
 *     TextKitColorsPopup(state = state, colors = barState.colors)
 * }
 * ```
 *
 * @param colors palette shown in the grid. A leading "no color" swatch (emits null) is always added.
 * @param onColorSelected invoked with the chosen color (null = reset to the default color). The
 * default applies it to the editor and closes the popup.
 * @param onClose invoked when the popup is dismissed (tap outside). Defaults to closing the picker.
 */
@Composable
fun TextKitColorsPopup(
    state: TextKitState,
    colors: List<Color> = TextKitPickerPallete.DefaultPallete,
    modifier: Modifier = Modifier,
    onColorSelected: (Color?) -> Unit = { color ->
        state.applyTextColor(color?.toHexWithAlpha())
        state.dismissColorPicker()
    },
    onClose: () -> Unit = { state.dismissColorPicker() },
) {
    val anchor = state.activeColorAnchor ?: return

    Popup(
        popupPositionProvider = TextKitPopupAnchorProvider.positionProvider(anchor),
        onDismissRequest = onClose,
        properties = PopupProperties(focusable = true),
    ) {
        Card(
            modifier = modifier.widthIn(max = 320.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                Modifier.defaultMinSize(minHeight = 32.dp, minWidth = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Mark the current selection's color when it exists (currentTextColor != null);
                // when the selection has no color it is null and only the "no color" swatch marks.
                TextKitColorPickerGrid(
                    colors = colors,
                    selected = state.currentTextColor,
                    onColorSelected = onColorSelected,
                )
            }
        }
    }
}
