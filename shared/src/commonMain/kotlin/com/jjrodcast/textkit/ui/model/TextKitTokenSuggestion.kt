package com.jjrodcast.textkit.ui.model

/**
 * A single candidate shown in a trigger popup (mentions, hashtags, slash commands, …). [id] is what
 * gets persisted in an atomic token node's attrs (ignored for ephemeral commands); [label] is the
 * display name — and the visible text of the inserted token (or the text a command inserts).
 */
data class TextKitTokenSuggestion(val id: String, val label: String)

/** Backward-compatible alias for the former mention-only suggestion type. */
typealias TextKitMentionSuggestion = TextKitTokenSuggestion
