package com.jjrodcast.textkit

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.jjrodcast.textkit.editor.utils.DocumentUtils
import com.jjrodcast.textkit.sample.TextKitSample
import com.jjrodcast.textkit.ui.TextKitEditor
import com.jjrodcast.textkit.ui.TextKitFormattingBar
import com.jjrodcast.textkit.ui.TextKitScreen
import com.jjrodcast.textkit.ui.state.rememberTextKitFormattingBarState
import com.jjrodcast.textkit.ui.state.rememberTextKitState

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "TextKit",
    ) {
        TextKitSample()
    }
}