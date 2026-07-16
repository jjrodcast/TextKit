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

/**
 * Attributes shared by every atomic inline "trigger token" node (`mention`, `hashtag`, …).
 * Intentionally limited to `id` and `label` so a token round-trips as exactly
 * `{"type":"<node>","attrs":{"id":"…","label":"…"}}`. The trigger char (`@`, `#`, …) is a
 * presentation/config concern (see `TextKitTrigger`) and is never persisted here — adding a
 * defaulted field would leak into the output because [TEXT_EDITOR_JSON] encodes defaults.
 */
@Serializable
data class TokenAttrs(val id: String, val label: String? = "")

