package com.jjrodcast.textkit.ui.table

/**
 * An immutable deep copy of a table used as a single undo/redo point: the grid of cell ids, each
 * cell's `(text, isHeader)`, and the next free id. Produced and consumed by
 * [TextKitEditableTableState]'s history.
 */
internal class TableSnapshot(
    val grid: List<List<Long>>,
    val cells: Map<Long, Pair<String, Boolean>>,
    val nextId: Long,
)
