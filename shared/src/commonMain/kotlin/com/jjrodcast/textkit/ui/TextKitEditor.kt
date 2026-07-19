package com.jjrodcast.textkit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.input.pointer.pointerInput
import com.jjrodcast.textkit.editor.models.createTextKitConfiguration
import com.jjrodcast.textkit.theme.TextKitTheme
import com.jjrodcast.textkit.ui.state.TextKitState
import com.jjrodcast.textkit.ui.state.rememberTextKitState
import org.jetbrains.compose.resources.stringResource
import textkit.shared.generated.resources.Res
import textkit.shared.generated.resources.type_text

@Composable
fun TextKitEditor(
    modifier: Modifier = Modifier,
    onUrlClicked: (url: String, text: String, range: TextRange) -> Unit = { _, _, _ -> },
    state: TextKitState = rememberTextKitState("{}", createTextKitConfiguration())
) {
    val focusRequester = remember { FocusRequester() }
    var isHoveringLink by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        state.onUrlClicked = onUrlClicked
    }

    // Highlight-mark background tracks the theme (unless the config pinned its own color).
    val highlightColor = TextKitTheme.colors.highlight
    SideEffect { state.setThemeHighlightColor(highlightColor) }

    BasicTextField(
        modifier = modifier
            .background(TextKitTheme.colors.background)
            .verticalScroll(rememberScrollState())
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { handleUndoRedoShortcut(it, state) }
            .pointerInput(state) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val position = event.changes.firstOrNull()?.position
                        // A press on an embed placeholder opens its popup and is consumed so the
                        // click does not move the caret into the atomic placeholder.
                        if (event.type == PointerEventType.Press && position != null &&
                            state.openEmbedAt(position)
                        ) {
                            event.changes.forEach { it.consume() }
                        }
                        isHoveringLink = when (event.type) {
                            PointerEventType.Move, PointerEventType.Enter ->
                                position != null && state.isLinkAtPosition(position)

                            PointerEventType.Exit -> false
                            else -> isHoveringLink
                        }
                    }
                }
            }
            .pointerHoverIcon(
                icon = if (isHoveringLink) PointerIcon.Hand else PointerIcon.Default,
                overrideDescendants = isHoveringLink
            ),
        value = state.textFieldValue,
        onTextLayout = state::onTextLayout,
        visualTransformation = state.visualTransformation,
        onValueChange = state::onTextFieldChange,
        textStyle = TextStyle(color = TextKitTheme.colors.onSurface),
        cursorBrush = SolidColor(TextKitTheme.colors.primary),
        decorationBox = { innerTextField ->
            Box {
                if (state.textFieldValue.text.isEmpty()) {
                    Text(
                        text = stringResource(Res.string.type_text),
                        color = TextKitTheme.colors.onSurfaceVariant
                    )
                }
                innerTextField()
            }
        }
    )
}

/**
 * Handles the undo/redo keyboard shortcuts on a key-down event, consuming the event when it triggers
 * one. Accepts Ctrl (desktop/web) or Cmd (macOS/iOS): Ctrl/Cmd+Z undoes, Ctrl/Cmd+Shift+Z and Ctrl+Y
 * redo. Returns false for anything else so normal typing is unaffected.
 */
private fun handleUndoRedoShortcut(event: KeyEvent, state: TextKitState): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val modifier = event.isCtrlPressed || event.isMetaPressed
    if (!modifier) return false
    return when (event.key) {
        Key.Z if !event.isShiftPressed -> state.undo()
        Key.Z if event.isShiftPressed -> state.redo()
        Key.Y -> state.redo()
        else -> false
    }
}

@Composable
fun TextKitEditorOutlined(
    modifier: Modifier = Modifier,
    onUrlClicked: (url: String, text: String, range: TextRange) -> Unit = { _, _, _ -> },
    state: TextKitState = rememberTextKitState("{}", createTextKitConfiguration())
) {
    val focusRequester = remember { FocusRequester() }
    var isHoveringLink by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        state.onUrlClicked = onUrlClicked
    }

    // Highlight-mark background tracks the theme (unless the config pinned its own color).
    val highlightColor = TextKitTheme.colors.highlight
    LaunchedEffect(highlightColor) { state.setThemeHighlightColor(highlightColor) }

    val interactionSource = remember { MutableInteractionSource() }
    val enabled = true
    val singleLine = false

    BasicTextField(
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { handleUndoRedoShortcut(it, state) }
            .pointerInput(state) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val position = event.changes.firstOrNull()?.position
                        isHoveringLink = when (event.type) {
                            PointerEventType.Move, PointerEventType.Enter ->
                                position != null && state.isLinkAtPosition(position)

                            PointerEventType.Exit -> false
                            else -> isHoveringLink
                        }
                    }
                }
            }
            .pointerHoverIcon(
                icon = if (isHoveringLink) PointerIcon.Hand else PointerIcon.Default,
                overrideDescendants = isHoveringLink
            ),
        value = state.textFieldValue,
        onValueChange = state::onTextFieldChange,
        onTextLayout = state::onTextLayout,
        visualTransformation = state.visualTransformation,
        interactionSource = interactionSource,
        enabled = enabled,
        singleLine = singleLine,
        textStyle = TextStyle(color = TextKitTheme.colors.onSurface),
        cursorBrush = SolidColor(TextKitTheme.colors.primary),
        decorationBox = { innerTextField ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = state.textFieldValue.text,
                innerTextField = innerTextField,
                enabled = enabled,
                singleLine = singleLine,
                visualTransformation = state.visualTransformation,
                interactionSource = interactionSource,
                placeholder = {
                    if (state.textFieldValue.text.isEmpty()) {
                        Text(
                            text = stringResource(Res.string.type_text),
                            color = TextKitTheme.colors.onSurfaceVariant
                        )
                    }
                },
                container = {
                    OutlinedTextFieldDefaults.Container(
                        enabled = enabled,
                        isError = false,
                        interactionSource = interactionSource,
                    )
                }
            )
        }
    )
}