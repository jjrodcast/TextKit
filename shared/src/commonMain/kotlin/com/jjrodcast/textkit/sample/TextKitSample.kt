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
import com.jjrodcast.textkit.ui.TextKitSlashCommandPopup
import com.jjrodcast.textkit.ui.TextKitTokenPopup
import com.jjrodcast.textkit.ui.TextKitScreen
import com.jjrodcast.textkit.ui.model.TextKitCommand
import com.jjrodcast.textkit.ui.model.TextKitTokenSuggestion
import com.jjrodcast.textkit.ui.state.rememberTextKitFormattingBarState
import com.jjrodcast.textkit.ui.state.rememberTextKitState

// Candidates for the '@' mention trigger — persisted as atomic mention chips.
private val sampleUsers = listOf(
    TextKitTokenSuggestion(id = "111", label = "Jorge Rodriguez"),
    TextKitTokenSuggestion(id = "222", label = "Ada Lovelace"),
    TextKitTokenSuggestion(id = "333", label = "Alan Turing"),
    TextKitTokenSuggestion(id = "444", label = "Grace Hopper"),
    TextKitTokenSuggestion(id = "555", label = "Margaret Hamilton"),
)

// Candidates for the '#' hashtag trigger — persisted as atomic hashtag chips.
private val sampleTags = listOf(
    TextKitTokenSuggestion(id = "1", label = "kotlin"),
    TextKitTokenSuggestion(id = "2", label = "compose"),
    TextKitTokenSuggestion(id = "3", label = "multiplatform"),
    TextKitTokenSuggestion(id = "4", label = "android"),
    TextKitTokenSuggestion(id = "5", label = "ios"),
)

// Commands for the '/' slash trigger — ephemeral actions (no persisted token). Built-in block
// commands (heading/list) plus a custom callback that inserts text.
private val sampleCommands = listOf(
    TextKitCommand.heading(1),
    TextKitCommand.heading(2),
    TextKitCommand.heading(3),
    TextKitCommand.bulletList(),
    TextKitCommand.orderedList(),
    TextKitCommand.custom(id = "cmd-date", label = "Insert date") {
        it.insertText("2026-07-15")
    },
    TextKitCommand.custom(id = "cmd-sig", label = "Insert signature") {
        it.insertText("Best regards,")
    },
)

@Composable
fun TextKitSample() {
    val barState = rememberTextKitFormattingBarState()
    // Enable the trigger flows: '@' mentions, '#' hashtags (atomic chips) and '/' slash commands
    // (ephemeral text insertion).
    val configuration = remember {
        createTextKitConfiguration {
            addTrigger { TextKitTrigger.TextKitMentionTrigger() }
            addTrigger { TextKitTrigger.TextKitHashtagTrigger() }
            addTrigger { TextKitTrigger.TextKitSlashTrigger() }
        }
    }
    val state =
        rememberTextKitState(json = DocumentUtils.complexJsonV2, configuration = configuration)

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
            onBulletedListClick = state::toggleUnorderedList,
            onUndoClick = { state.undo() },
            onRedoClick = { state.redo() },
            canUndo = state.canUndo,
            canRedo = state.canRedo
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
            // Atomic-token popup for '@' mentions and '#' hashtags (candidates by active trigger).
            TextKitTokenPopup(state = state) { trigger ->
                when (trigger) {
                    is TextKitTrigger.TextKitHashtagTrigger -> sampleTags
                    else -> sampleUsers
                }
            }
            // Slash-command popup for '/': runs actions (heading/list/custom) instead of inserting.
            TextKitSlashCommandPopup(state = state, commands = sampleCommands)
        }
    }
}
