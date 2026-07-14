package com.jjrodcast.textkit.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.input.pointer.pointerInput
import com.jjrodcast.textkit.editor.models.createTextKitConfiguration
import com.jjrodcast.textkit.ui.state.TextKitState
import com.jjrodcast.textkit.ui.state.rememberTextKitState
import org.jetbrains.compose.resources.stringResource
import textkit.shared.generated.resources.Res
import textkit.shared.generated.resources.type_text

@Composable
fun TextKitEditor(
    modifier: Modifier = Modifier,
    onUrlClicked: (url: String, range: TextRange) -> Unit = { _, _ -> },
    state: TextKitState = rememberTextKitState(
        "{}", false, createTextKitConfiguration()
    )
) {
    val focusRequester = remember { FocusRequester() }
    var isHoveringLink by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        state.onUrlClicked = onUrlClicked
    }

    BasicTextField(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .fillMaxWidth()
            .focusRequester(focusRequester)
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
        onTextLayout = state::onTextLayout,
        visualTransformation = state.visualTransformation,
        onValueChange = state::onTextFieldChange,
        decorationBox = { innerTextField ->
            Box {
                if (state.textFieldValue.text.isEmpty()) {
                    Text(text = stringResource(Res.string.type_text))
                }
                innerTextField()
            }
        }
    )
}
