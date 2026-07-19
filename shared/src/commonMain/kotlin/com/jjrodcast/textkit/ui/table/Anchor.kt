package com.jjrodcast.textkit.ui.table

/**
 * A cell's top-left position and how many rows/columns it spans. Derived from the grid by
 * [TextKitEditableTableState.anchors]; never stored directly, so spans can't drift from the grid.
 */
internal data class Anchor(
    val id: Long,
    val row: Int,
    val col: Int,
    val rowSpan: Int,
    val colSpan: Int,
)
