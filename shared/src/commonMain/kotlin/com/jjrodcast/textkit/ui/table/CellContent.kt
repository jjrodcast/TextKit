package com.jjrodcast.textkit.ui.table

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Mutable content of one table cell. [text] and [isHeader] are Compose state so editing a cell
 * recomposes only that cell, not the whole grid.
 */
internal class CellContent(text: String, isHeader: Boolean) {
    var text by mutableStateOf(text)
    var isHeader by mutableStateOf(isHeader)
}
