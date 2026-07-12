package com.jjrodcast.textkit.editor.core.parser

import kotlinx.serialization.Serializable

@Serializable
data class LinkAttrs(val href: String, val target: String = "")

@Serializable
data class TaskListAttrs(val checked: Boolean = false)

@Serializable
data class ListAttrs(val start: Int = 1)

@Serializable
data class HeadingAttrs(val level: Int = HeadingLevels.H4)

@Serializable
data class TextStyleAttrs(val color: String? = "", val fontSize: Int) {

    companion object {
    }
}

