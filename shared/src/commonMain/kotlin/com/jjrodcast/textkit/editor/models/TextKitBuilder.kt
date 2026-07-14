package com.jjrodcast.textkit.editor.models

import androidx.compose.ui.graphics.Color

class TextKitBuilder {

    private var highlightColor: Color = Color.Yellow
    // Full-alpha (0xFF…) Long literals. Without the alpha byte these are Int literals and hit the
    // Color(Int) constructor with alpha 0x00 → fully transparent, so links/text render invisibly.
    private var linkColor: Color = Color(0xFF1B75D0)
    private var fontSize: Int = 14

    private var textColor: Color = Color(0xFF000000)

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
