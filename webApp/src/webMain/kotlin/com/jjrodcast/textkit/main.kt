package com.jjrodcast.textkit

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.jjrodcast.textkit.sample.TextKitSample

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport {
        TextKitSample()
    }
}