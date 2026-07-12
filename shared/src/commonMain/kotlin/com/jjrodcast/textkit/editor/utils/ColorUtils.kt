package com.jjrodcast.textkit.editor.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

fun Color.toHex(): String {
    val rgb = toArgb() and 0xFFFFFF
    return "#" + rgb.toString(16).padStart(6, '0')
}

fun Color.toHexWithAlpha(): String =
    "#" + (toArgb().toLong() and 0xFFFFFFFFL).toString(16).padStart(8, '0')
