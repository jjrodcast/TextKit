package com.jjrodcast.textkit.ui.model

import androidx.compose.ui.text.TextRange

/**
 * Snapshot of the link under the caret / selection, surfaced so a link popup can show its text and
 * URL and act on it. [range] is the manager-reported range the link spans.
 */
data class TextKitLinkInfo(val text: String, val url: String, val range: TextRange)
