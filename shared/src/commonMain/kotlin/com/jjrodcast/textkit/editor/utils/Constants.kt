package com.jjrodcast.textkit.editor.utils

internal const val LINE_BREAK = '\n'

internal const val SEPARATOR = "-"
internal const val DOT = "."

internal const val EMPTY = ""
internal const val EMPTY_JSON = "{}"
internal const val BLOCKQUOTE = ">"

internal const val SPACE = " "

internal const val BULLET_DECORATOR_LEVEL_ONE = "•$SPACE"
internal const val BULLET_DECORATOR_LEVEL_TWO = "◦$SPACE"
internal const val BULLET_DECORATOR_LEVEL_THREE = "▪$SPACE"
internal const val TASK_DECORATOR_COMMON = "-[x]$SPACE"
internal const val TASK_DECORATOR_UNCHECKED_COMMON = "-[]$SPACE"

expect val TABS: String
expect val TASK_DECORATOR_INTERACTIVE: String
expect val TASK_DECORATOR_UNCHECKED_INTERACTIVE: String
