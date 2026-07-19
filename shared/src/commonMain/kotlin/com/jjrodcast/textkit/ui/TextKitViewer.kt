package com.jjrodcast.textkit.ui

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import com.jjrodcast.textkit.editor.utils.DocumentUtils
import com.jjrodcast.textkit.theme.TextKitTheme
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
    // Highlight-mark background tracks the theme (unless the config pinned its own color).
    val highlightColor = TextKitTheme.colors.highlight
    SideEffect { state.setThemeHighlightColor(highlightColor) }

    val (text, inlineContent) = state.viewerTextValue
    BasicText(
        modifier = modifier,
        text = text,
        inlineContent = inlineContent
    )
}