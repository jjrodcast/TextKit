package com.jjrodcast.textkit.editor.models

import androidx.compose.ui.graphics.Color

class TextKitBuilder {

    private var highlightColor: Color = Color.Yellow
    private var linkColor: Color = Color(0x1B75D0)
    private var fontSize: Int = 14

    private var textColor: Color = Color(0x000000)

    fun highlightColor(block: () -> Color) {
        this.highlightColor = block()
    }

    fun linkColor(block: () -> Color) {
        this.linkColor = block()
    }

    fun fontSize(block: () -> Int) {
        this.fontSize = block()
    }

    fun textColor(block: () -> Color) {
        this.textColor = block()
    }

    fun build(): TextKitConfiguration {
        require(fontSize > 0) { "Font size cannot be less than 1" }
        return TextKitConfiguration(highlightColor, linkColor, textColor, fontSize)
    }
}

fun createTextKitConfiguration(
    block: TextKitBuilder.() -> Unit = { TextKitConfiguration() }
): TextKitConfiguration {
    val builder = TextKitBuilder()
    builder.block()
    return builder.build()
}
