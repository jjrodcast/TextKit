package com.jjrodcast.textkit.editor.models

import androidx.compose.ui.graphics.Color

class TextKitBuilder {

    private var highlightColor: Color = Color.Yellow

    // Full-alpha (0xFF…) Long literals. Without the alpha byte these are Int literals and hit the
    // Color(Int) constructor with alpha 0x00 → fully transparent, so links/text render invisibly.
    private var linkColor: Color = Color(0xFF1B75D0)
    private var fontSize: Int = 14

    private var textColor: Color = Color(0xFF000000)

    private var triggers: Set<TextKitTrigger> = emptySet()

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

    fun addTrigger(trigger: () -> TextKitTrigger) {
        this.triggers = triggers.plus(trigger())
    }

    fun build(): TextKitConfiguration {
        require(fontSize > 0) { "Font size cannot be less than 1" }
        // Trigger detection resolves a trigger by its char, so each char must be unique.
        val duplicateKeys = triggers.groupBy { it.triggerKey }.filterValues { it.size > 1 }.keys
        require(duplicateKeys.isEmpty()) {
            "Duplicate trigger characters registered: $duplicateKeys"
        }
        return TextKitConfiguration(
            highlightColor,
            linkColor,
            textColor,
            fontSize,
            triggers
        )
    }
}

fun createTextKitConfiguration(
    block: TextKitBuilder.() -> Unit = { TextKitConfiguration() }
): TextKitConfiguration {
    val builder = TextKitBuilder()
    builder.block()
    return builder.build()
}
