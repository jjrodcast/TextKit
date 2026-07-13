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
data class TextStyleAttrs(val color: String? = "", val fontSize: Int = UNSET_FONT_SIZE) {

    companion object {
        /**
         * Sentinel meaning "no font size was provided in the document". A missing or `null`
         * `fontSize` decodes to this value (see [TEXT_EDITOR_JSON]'s `coerceInputValues`), and the
         * converter later resolves it to the configured default font size.
         */
        const val UNSET_FONT_SIZE = 0
    }
}

