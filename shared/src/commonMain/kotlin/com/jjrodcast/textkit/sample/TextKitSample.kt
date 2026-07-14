package com.jjrodcast.textkit.sample

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jjrodcast.textkit.editor.utils.DocumentUtils
import com.jjrodcast.textkit.ui.TextKitEditor
import com.jjrodcast.textkit.ui.TextKitFormattingBar
import com.jjrodcast.textkit.ui.TextKitLinkPopup
import com.jjrodcast.textkit.ui.TextKitScreen
import com.jjrodcast.textkit.ui.state.rememberTextKitFormattingBarState
import com.jjrodcast.textkit.ui.state.rememberTextKitState

@Composable
fun TextKitSample() {
    val barState = rememberTextKitFormattingBarState()
    val state = rememberTextKitState(json = DocumentUtils.complexJsonV2)

    LaunchedEffect(state.lastMarks, state.lastListItem) {
        barState.syncFrom(state.lastMarks, state.lastListItem)
    }


    TextKitScreen {
        TextKitFormattingBar(
            barState = barState,
            selectedColor = Color.Yellow,
            onBoldClick = state::applyBold,
            onItalicClick = state::applyItalic,
            onUnderlineClick = state::applyUnderline,
            onStrikeThroughClick = state::applyStrikeThrough,
            onHighlightClick = state::applyHighlight,
            onLinkClick = { },
            onOrderedListClick = {},
            onBulletedListClick = {}
        )
        Spacer(Modifier.size(6.dp))
        // Wrap the editor in a Box so the popup overlays it (shares its coordinate space) and
        // positions itself next to the tapped link.
        Box {
            TextKitEditor(
                state = state,
                modifier = Modifier.padding(10.dp)
            )
            TextKitLinkPopup(
                state = state,
                onEdit = { link ->
                    state.updateLink(url = link.url, range = link.range)
                },
                onRemove = { link -> state.removeLink(link.range) },
            )
        }
    }
}