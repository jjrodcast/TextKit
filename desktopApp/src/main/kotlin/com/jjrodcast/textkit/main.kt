package com.jjrodcast.textkit

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.jjrodcast.textkit.sample.TextKitSampleNonMobile

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "TextKit",
    ) {
        TextKitSampleNonMobile()
    }
}