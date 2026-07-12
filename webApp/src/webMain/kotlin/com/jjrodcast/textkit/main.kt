package com.jjrodcast.textkit

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import org.w3c.dom.Text

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport {
        Text("Web App")
    }
}