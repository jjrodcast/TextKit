package com.jjrodcast.textkit.sample

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import com.jjrodcast.textkit.editor.core.parser.EmbedTypes
import com.jjrodcast.textkit.editor.models.TextKitTrigger
import com.jjrodcast.textkit.editor.models.createTextKitConfiguration
import com.jjrodcast.textkit.editor.utils.DocumentUtils
import com.jjrodcast.textkit.theme.TextKitTheme
import com.jjrodcast.textkit.ui.TextKitColorsPopup
import com.jjrodcast.textkit.ui.TextKitEditor
import com.jjrodcast.textkit.ui.TextKitEmbedPopup
import com.jjrodcast.textkit.ui.TextKitFormattingBar
import com.jjrodcast.textkit.ui.TextKitLinkPopup
import com.jjrodcast.textkit.ui.TextKitScreen
import com.jjrodcast.textkit.ui.TextKitSlashCommandPopup
import com.jjrodcast.textkit.ui.TextKitTokenPopup
import com.jjrodcast.textkit.ui.model.TextKitCommand
import com.jjrodcast.textkit.ui.model.TextKitTokenSuggestion
import com.jjrodcast.textkit.ui.state.rememberTextKitFormattingBarState
import com.jjrodcast.textkit.ui.state.rememberTextKitState
import com.jjrodcast.textkit.ui.utils.TextKitPickerPallete

// A demo table (ProseMirror shape) inserted by the "Insertar tabla" button. It is stored verbatim on
// the placeholder piece and re-emitted on toJson(); the editor only shows the "📊 Tabla" chip.
private val DEMO_TABLE_JSON = """
    {"type":"table","content":[
      {"type":"tableRow","content":[
        {"type":"tableHeader","attrs":{"colspan":1,"rowspan":1,"colwidth":null},"content":[{"type":"paragraph","content":[{"type":"text","text":"Nombre"}]}]},
        {"type":"tableHeader","attrs":{"colspan":1,"rowspan":1,"colwidth":null},"content":[{"type":"paragraph","content":[{"type":"text","text":"Edad"}]}]}
      ]},
      {"type":"tableRow","content":[
        {"type":"tableCell","attrs":{"colspan":1,"rowspan":1,"colwidth":null},"content":[{"type":"paragraph","content":[{"type":"text","text":"Juan"}]}]},
        {"type":"tableCell","attrs":{"colspan":1,"rowspan":1,"colwidth":null},"content":[{"type":"paragraph","content":[{"type":"text","text":"30"}]}]}
      ]},
      {"type":"tableRow","content":[
        {"type":"tableCell","attrs":{"colspan":1,"rowspan":1,"colwidth":null},"content":[{"type":"paragraph","content":[{"type":"text","text":"Ana"}]}]},
        {"type":"tableCell","attrs":{"colspan":1,"rowspan":1,"colwidth":null},"content":[{"type":"paragraph","content":[{"type":"text","text":"25"}]}]}
      ]}
    ]}
""".trimIndent()

// A demo local image embed. `src` names the bundled drawable (text_kit_banner.png); the popup maps it
// to Res.drawable.text_kit_banner. Stored verbatim and round-tripped like any other embed.
private const val DEMO_IMAGE_JSON = """{"type":"image","attrs":{"src":"text_kit_banner"}}"""

// A demo document embed. Stored verbatim on the placeholder piece and re-emitted on toJson(); the
// editor shows the "📄 Documento" chip and the popup renders this JSON (there is no custom document
// renderer, so it falls back to showing the stored attrs).
private const val DEMO_DOCUMENT_JSON =
    """{"type":"document","attrs":{"name":"Reporte Q3.pdf","url":"https://example.com/reporte-q3.pdf"}}"""

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
    val barState = rememberTextKitFormattingBarState(colors = TextKitPickerPallete.DefaultPallete)
    val configuration = remember {
        createTextKitConfiguration {
            addTrigger { TextKitTrigger.TextKitMentionTrigger() }
            addTrigger { TextKitTrigger.TextKitHashtagTrigger() }
            addTrigger { TextKitTrigger.TextKitSlashTrigger() }
        }
    }
    val state =
        rememberTextKitState(json = DocumentUtils.complexJsonV1, configuration = configuration)

    LaunchedEffect(state.lastMarks, state.lastListItem, state.lastEmbedType) {
        barState.syncFrom(state.lastMarks, state.lastListItem, state.lastEmbedType)
    }

    TextKitScreen(modifier = Modifier.padding(16.dp)) {
        Column(modifier = Modifier.fillMaxSize()) {
            TextKitFormattingBar(
                barState = barState,
                onBoldClick = state::applyBold,
                onItalicClick = state::applyItalic,
                onUnderlineClick = state::applyUnderline,
                onStrikeThroughClick = state::applyStrikeThrough,
                onHighlightClick = state::applyHighlight,
                onLinkClick = { state.applyLink() },
                onImageClick = {
                    state.insertEmbed(EmbedTypes.Image, DEMO_IMAGE_JSON, "🖼 Imagen")
                },
                onTableClick = {
                    state.insertEmbed(EmbedTypes.Table, DEMO_TABLE_JSON, "📊 Tabla")
                },
                onDocumentClick = {
                    state.insertEmbed(EmbedTypes.Document, DEMO_DOCUMENT_JSON, "📄 Documento")
                },
                onTextAndColorClick = { state.openColorPicker(it) },
                onOrderedListClick = state::toggleOrderedList,
                onBulletedListClick = state::toggleUnorderedList,
                onUndoClick = { state.undo() },
                onRedoClick = { state.redo() },
                canUndo = state.canUndo,
                canRedo = state.canRedo
            )
            Spacer(Modifier.size(6.dp))
            Box {
                TextKitEditor(
                    state = state,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp)
                )
                // Popup for embedded blocks: renders the table and offers "Eliminar".
                TextKitEmbedPopup(state = state)
                TextKitColorsPopup(
                    state = state,
                    colors = barState.colors
                )
                TextKitLinkPopup(
                    state = state,
                    onEdit = { link ->
                        state.updateLinkText(
                            newText = link.text,
                            url = link.url,
                            range = link.range
                        )
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
}

@Composable
fun TextKitSampleNonMobile(
    isDarkTheme: Boolean = isSystemInDarkTheme()
) {
    var isDarkTheme by rememberSaveable { mutableStateOf(isDarkTheme) }
    val barState = rememberTextKitFormattingBarState(colors = TextKitPickerPallete.DefaultPallete)
    val configuration = remember {
        createTextKitConfiguration {
            addTrigger { TextKitTrigger.TextKitMentionTrigger() }
            addTrigger { TextKitTrigger.TextKitHashtagTrigger() }
            addTrigger { TextKitTrigger.TextKitSlashTrigger() }
        }
    }
    val state =
        rememberTextKitState(json = DocumentUtils.complexJsonV1, configuration = configuration)

    LaunchedEffect(state.lastMarks, state.lastListItem, state.lastEmbedType) {
        barState.syncFrom(state.lastMarks, state.lastListItem, state.lastEmbedType)
    }

    TextKitScreen(
        modifier = Modifier.padding(16.dp),
        darkTheme = isDarkTheme
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier.fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.TopEnd
            ) {
                TextKitLightDarkModeIcon(
                    isDarkTheme = isDarkTheme,
                    onChange = {
                        isDarkTheme = it
                    }
                )
            }
            TextKitFormattingBar(
                barState = barState,
                onBoldClick = state::applyBold,
                onItalicClick = state::applyItalic,
                onUnderlineClick = state::applyUnderline,
                onStrikeThroughClick = state::applyStrikeThrough,
                onHighlightClick = state::applyHighlight,
                onLinkClick = { state.applyLink() },
                onImageClick = {
                    state.insertEmbed(EmbedTypes.Image, DEMO_IMAGE_JSON, "🖼 Imagen")
                },
                onTableClick = {
                    state.insertEmbed(EmbedTypes.Table, DEMO_TABLE_JSON, "📊 Tabla")
                },
                onDocumentClick = {
                    state.insertEmbed(EmbedTypes.Document, DEMO_DOCUMENT_JSON, "📄 Documento")
                },
                onTextAndColorClick = { state.openColorPicker(it) },
                onOrderedListClick = state::toggleOrderedList,
                onBulletedListClick = state::toggleUnorderedList,
                onUndoClick = { state.undo() },
                onRedoClick = { state.redo() },
                canUndo = state.canUndo,
                canRedo = state.canRedo
            )
            Spacer(Modifier.size(6.dp))
            Box {
                TextKitEditor(
                    state = state,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp)
                )
                // Popup for embedded blocks: renders the table and offers "Eliminar".
                TextKitEmbedPopup(state = state)
                TextKitColorsPopup(
                    state = state,
                    colors = barState.colors
                )
                TextKitLinkPopup(
                    state = state,
                    onEdit = { link ->
                        state.updateLinkText(
                            newText = link.text,
                            url = link.url,
                            range = link.range
                        )
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
}


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TextKitLightDarkModeIcon(
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    onChange: (Boolean) -> Unit
) {
    var painter by remember {
        mutableStateOf(if (isDarkTheme) Icons.Rounded.DarkMode else Icons.Rounded.LightMode)
    }
    var isChecked by rememberSaveable { mutableStateOf(isDarkTheme) }

    ToggleButton(
        checked = isChecked,
        onCheckedChange = {
            painter = if (it) {
                Icons.Rounded.DarkMode
            } else {
                Icons.Rounded.LightMode
            }
            isChecked = it
            onChange(isChecked)
        },
        colors = ToggleButtonDefaults.toggleButtonColors(
            containerColor = TextKitTheme.colors.primary,
            checkedContainerColor = TextKitTheme.colors.primary,
            contentColor = TextKitTheme.colors.onPrimary,
            checkedContentColor = TextKitTheme.colors.onPrimary
        )
    ) {
        Icon(
            painter = rememberVectorPainter(painter),
            contentDescription = null
        )
    }
}
