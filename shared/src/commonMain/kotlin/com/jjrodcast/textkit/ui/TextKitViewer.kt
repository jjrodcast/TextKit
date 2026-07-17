package com.jjrodcast.textkit.ui

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.jjrodcast.textkit.editor.utils.DocumentUtils
import com.jjrodcast.textkit.ui.state.TextKitState
import com.jjrodcast.textkit.ui.state.rememberTextKitState

@Composable
fun TextKitVierScreen() {
    TextKitScreen {
        TextKitViewer(rememberTextKitState(DocumentUtils.complexJsonV2))
    }
}

@Composable
fun TextKitViewer(
    state: TextKitState,
    modifier: Modifier = Modifier
) {
    val (text, inlineContent) = state.viewerTextValue
    BasicText(
        modifier = modifier,
        text = text,
        inlineContent = inlineContent
    )
}