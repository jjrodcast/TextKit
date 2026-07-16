package com.jjrodcast.textkit.sample

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jjrodcast.textkit.editor.models.TextKitTrigger
import com.jjrodcast.textkit.editor.models.createTextKitConfiguration
import com.jjrodcast.textkit.editor.utils.DocumentUtils
import com.jjrodcast.textkit.ui.TextKitEditor
import com.jjrodcast.textkit.ui.TextKitFormattingBar
import com.jjrodcast.textkit.ui.TextKitLinkPopup
import com.jjrodcast.textkit.ui.TextKitMentionPopup
import com.jjrodcast.textkit.ui.TextKitScreen
import com.jjrodcast.textkit.ui.model.TextKitMentionSuggestion
import com.jjrodcast.textkit.ui.state.rememberTextKitFormattingBarState
import com.jjrodcast.textkit.ui.state.rememberTextKitState

private val sampleUsers = listOf(
    TextKitMentionSuggestion(id = "111", label = "Jorge Rodriguez"),
    TextKitMentionSuggestion(id = "222", label = "Ada Lovelace"),
    TextKitMentionSuggestion(id = "333", label = "Alan Turing"),
    TextKitMentionSuggestion(id = "444", label = "Grace Hopper"),
    TextKitMentionSuggestion(id = "555", label = "Margaret Hamilton"),
)

@Composable
fun TextKitSample() {
    val barState = rememberTextKitFormattingBarState()
    // Enable the mention flow: typing '@' opens the mention popup.
    val configuration = remember {
        createTextKitConfiguration {
            addTrigger { TextKitTrigger.TextKitMentionTrigger() }
        }
    }
    val state = rememberTextKitState(json = DocumentUtils.complexJsonV2, configuration = configuration)

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
            onLinkClick = { state.applyLink() },
            onOrderedListClick = state::toggleOrderedList,
            onBulletedListClick = state::toggleUnorderedList
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
                    state.updateLinkText(newText = link.text, url = link.url, range = link.range)
                },
                onRemove = { link -> state.removeLink(link.range) },
            )
            TextKitMentionPopup(
                state = state,
                candidates = sampleUsers,
            )
        }
    }
}