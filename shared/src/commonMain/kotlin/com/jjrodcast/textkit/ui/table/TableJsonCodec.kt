package com.jjrodcast.textkit.ui.table

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Decodes a ProseMirror `table` JSON document into the dense grid model used by
 * [TextKitEditableTableState] (`grid[r][c]` = cell id; a merged cell repeats its id over the slots it
 * covers). Serialization back to JSON lives in the state itself, since it reads the live grid/cells.
 *
 * Malformed or non-`table` input falls back to a starter 3×3 table.
 */
internal object TableJsonCodec {

    private val json = Json { ignoreUnknownKeys = true }

    /** JSON keys and node-type values of the ProseMirror `table` shape, kept in one place to reuse. */
    private object Keys {
        const val TYPE = "type"
        const val CONTENT = "content"
        const val ATTRS = "attrs"
        const val COLSPAN = "colspan"
        const val ROWSPAN = "rowspan"
        const val TEXT = "text"

        const val TABLE = "table"
        const val TABLE_HEADER = "tableHeader"
    }

    /** A decoded table: a dense grid of cell ids, the cell content by id, and the next free id. */
    class Decoded(
        val grid: MutableList<MutableList<Long>>,
        val cells: MutableMap<Long, CellContent>,
        val nextId: Long,
    )

    fun decode(rawJson: String): Decoded = runCatching { parse(rawJson) }.getOrNull() ?: default()

    private fun default(): Decoded {
        val grid = MutableList(3) { r -> MutableList(3) { c -> (r * 3 + c).toLong() } }
        val cells = HashMap<Long, CellContent>()
        for (r in 0..2) for (c in 0..2) {
            val id = (r * 3 + c).toLong()
            cells[id] = CellContent(if (r == 0) "Header ${c + 1}" else "", r == 0)
        }
        return Decoded(grid, cells, 9L)
    }

    private fun parse(rawJson: String): Decoded {
        val obj = json.parseToJsonElement(rawJson).jsonObject
        require(obj[Keys.TYPE]?.jsonPrimitive?.content == Keys.TABLE)
        val rowsJson = obj[Keys.CONTENT]?.jsonArray ?: JsonArray(emptyList())

        val grid: MutableList<MutableList<Long>> = ArrayList()
        val cells = HashMap<Long, CellContent>()
        var nextId = 0L

        fun ensure(r: Int, c: Int) {
            while (grid.size <= r) grid.add(ArrayList())
            val row = grid[r]
            while (row.size <= c) row.add(-1L)
        }

        fun get(r: Int, c: Int): Long {
            if (r >= grid.size) return -1L
            val row = grid[r]
            if (c >= row.size) return -1L
            return row[c]
        }

        fun set(r: Int, c: Int, id: Long) { ensure(r, c); grid[r][c] = id }

        rowsJson.forEachIndexed { r, rowEl ->
            ensure(r, 0)
            val cs = rowEl.jsonObject[Keys.CONTENT]?.jsonArray ?: JsonArray(emptyList())
            var c = 0
            cs.forEach { cellEl ->
                val cell = cellEl.jsonObject
                while (get(r, c) != -1L) c++
                val attrs = cell[Keys.ATTRS]?.jsonObject
                val colspan = attrs?.get(Keys.COLSPAN)?.jsonPrimitive?.content?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                val rowspan = attrs?.get(Keys.ROWSPAN)?.jsonPrimitive?.content?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                val isHeader = cell[Keys.TYPE]?.jsonPrimitive?.content == Keys.TABLE_HEADER
                val id = nextId++
                cells[id] = CellContent(cellText(cell[Keys.CONTENT]?.jsonArray), isHeader)
                for (dr in 0 until rowspan) for (dc in 0 until colspan) set(r + dr, c + dc, id)
                c += colspan
            }
        }

        require(grid.isNotEmpty())
        // Rectangularize: pad every row to the widest and fill any holes with fresh single cells.
        val cols = grid.maxOf { it.size }.coerceAtLeast(1)
        for (r in grid.indices) {
            ensure(r, cols - 1)
            val row = grid[r]
            for (c in 0 until cols) {
                if (row[c] == -1L) {
                    val id = nextId++
                    cells[id] = CellContent("", false)
                    row[c] = id
                }
            }
        }
        return Decoded(grid, cells, nextId)
    }

    /** Concatenates all `text` leaves inside a cell's paragraph content. */
    private fun cellText(paragraphs: JsonArray?): String {
        if (paragraphs == null) return ""
        val builder = StringBuilder()
        paragraphs.forEach { paragraph ->
            paragraph.jsonObject[Keys.CONTENT]?.jsonArray?.forEach { inline ->
                inline.jsonObject[Keys.TEXT]?.jsonPrimitive?.content?.let { builder.append(it) }
            }
        }
        return builder.toString()
    }
}
