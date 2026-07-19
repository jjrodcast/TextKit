package com.jjrodcast.textkit.ui.table

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ParentDataModifier
import androidx.compose.ui.unit.Density

/**
 * Parent data identifying what a child of the table [androidx.compose.ui.layout.Layout] is, so the
 * measure/place pass can size and position it: a cell (with its span), a gutter handle, the corner,
 * or one of the "+" add handles.
 */
internal sealed interface GridChild

internal data class CellChild(val row: Int, val col: Int, val rowSpan: Int, val colSpan: Int) : GridChild
internal data class ColHandleChild(val col: Int) : GridChild
internal data class RowHandleChild(val row: Int) : GridChild
internal data object CornerChild : GridChild
internal data object AddColChild : GridChild
internal data object AddRowChild : GridChild

private class GridChildModifier(private val child: GridChild) : ParentDataModifier {
    override fun Density.modifyParentData(parentData: Any?): Any = child
}

/** Tags a composable with its [GridChild] role for the table layout. */
internal fun Modifier.gridChild(child: GridChild): Modifier = this.then(GridChildModifier(child))
