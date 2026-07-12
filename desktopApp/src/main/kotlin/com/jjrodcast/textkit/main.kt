package com.jjrodcast.textkit

import androidx.compose.material.Text
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "TextKit",
    ) {
        Text("Desktop App")
    }
}