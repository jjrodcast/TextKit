package com.jjrodcast.textkit.ui.model

/**
 * A single candidate shown in the mention popup. [id] is what gets persisted in the `mention` node's
 * attrs; [label] is the display name (and the visible text of the inserted mention).
 */
data class TextKitMentionSuggestion(val id: String, val label: String)
