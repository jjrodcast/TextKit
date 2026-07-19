package com.jjrodcast.textkit.ui.table

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Layout dimensions and sample data for [TextKitEditableTable].
 *
 * Kept in one place so the custom grid layout, the gutters and the side rail all size themselves
 * from a single source of truth.
 */
internal object TextKitTableConstants {

    /** Thickness of the row/column selection gutters. */
    val GutterSize: Dp = 26.dp

    /** Thickness of the "+" add-row / add-column handles at the table edges. */
    val AddSize: Dp = 26.dp

    /** Fixed width of every table column. */
    val ColumnWidth: Dp = 132.dp

    /** Minimum height of a table row. */
    val MinRowHeight: Dp = 44.dp

    /** Max height of the scrollable table area so it never overflows the host popup. */
    val MaxTableHeight: Dp = 360.dp

    /** Width of the side action rail. */
    val RailWidth: Dp = 48.dp

    /** Diameter of a rail action button. */
    val RailButtonSize: Dp = 42.dp

    /** A demo ProseMirror `table` document used by the previews. */
    const val SampleTableJson: String = """
{
  "type": "table",
  "content": [
    { "type": "tableRow", "content": [
      { "type": "tableHeader", "attrs": {"colspan": 1, "rowspan": 1, "colwidth": null}, "content": [{"type":"paragraph","content":[{"type":"text","text":"Producto"}]}] },
      { "type": "tableHeader", "attrs": {"colspan": 1, "rowspan": 1, "colwidth": null}, "content": [{"type":"paragraph","content":[{"type":"text","text":"Región"}]}] },
      { "type": "tableHeader", "attrs": {"colspan": 1, "rowspan": 1, "colwidth": null}, "content": [{"type":"paragraph","content":[{"type":"text","text":"Ventas"}]}] }
    ]},
    { "type": "tableRow", "content": [
      { "type": "tableCell", "attrs": {"colspan": 1, "rowspan": 1, "colwidth": null}, "content": [{"type":"paragraph","content":[{"type":"text","text":"Laptop"}]}] },
      { "type": "tableCell", "attrs": {"colspan": 1, "rowspan": 1, "colwidth": null}, "content": [{"type":"paragraph","content":[{"type":"text","text":"Norte"}]}] },
      { "type": "tableCell", "attrs": {"colspan": 1, "rowspan": 1, "colwidth": null}, "content": [{"type":"paragraph","content":[{"type":"text","text":"1200"}]}] }
    ]},
    { "type": "tableRow", "content": [
      { "type": "tableCell", "attrs": {"colspan": 2, "rowspan": 1, "colwidth": null}, "content": [{"type":"paragraph","content":[{"type":"text","text":"Total parcial"}]}] },
      { "type": "tableCell", "attrs": {"colspan": 1, "rowspan": 1, "colwidth": null}, "content": [{"type":"paragraph","content":[{"type":"text","text":"1200"}]}] }
    ]}
  ]
}
"""
}
