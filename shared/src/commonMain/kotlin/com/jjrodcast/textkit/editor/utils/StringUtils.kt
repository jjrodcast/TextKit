package com.jjrodcast.textkit.editor.utils

internal fun String.replaceLineBreakWith(newValue: String) = replace("$LINE_BREAK", newValue)

internal fun String.addLineBreak() = plus(LINE_BREAK)

internal fun String.removeLineBreakSuffix() = removeSuffix("$LINE_BREAK")

internal fun String.endsWithLineBreak() = endsWith(LINE_BREAK)

internal fun String.isLineBreak() = this == "$LINE_BREAK"

internal fun Char.isLineBreak() = this == LINE_BREAK
