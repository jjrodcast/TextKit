package com.jjrodcast.textkit.editor.models

import androidx.compose.ui.graphics.Color

data class TextKitConfiguration(
    val highlightColor: Color = Color.Yellow,
    val linkColor: Color = Color(0xFF1B75D0),
    val textColor: Color = Color(0xFF000000),
    val fontSize: Int = 14
)
