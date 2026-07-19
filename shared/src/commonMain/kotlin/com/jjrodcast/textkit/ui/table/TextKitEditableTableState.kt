package com.jjrodcast.textkit.ui.table

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Holds the editable table. `grid[r][c]` stores the id of the cell occupying that logical slot; a
 * merged cell simply has its id in every slot it covers. Spans are always *derived* from the grid
 * (see [anchors]), which keeps every mutation (add/remove/merge/split) a simple grid edit that can't
 * leave the model inconsistent. The rectangle is kept dense (all rows share the same width).
 *
 * Selection is line-based: [selectedRows] / [selectedCols] hold whole-line indices (chosen via the
 * gutter handles). The acted-upon rectangle is their bounding box ([blockRect]).
 */
@Stable
internal class TextKitEditableTableState(
    private val grid: MutableList<MutableList<Long>>,
    val cells: MutableMap<Long, CellContent>,
    startId: Long,
) {
    private var nextId = startId

    /** Bumped on structural changes (rows/cols/spans) to invalidate the grid layout. */
    var version by mutableIntStateOf(0)
        private set

    val selectedRows = mutableStateListOf<Int>()
    val selectedCols = mutableStateListOf<Int>()

    private fun newId(): Long = nextId++
    private fun bump() { version++ }

    fun rows(): Int = grid.size
    fun cols(): Int = grid.firstOrNull()?.size ?: 0

    /** Derive every anchor (top-left + span) from the grid, sorted row-major for stable rendering. */
    fun anchors(): List<Anchor> {
        val box = HashMap<Long, IntArray>() // id -> [minR, minC, maxR, maxC]
        for (r in grid.indices) {
            val row = grid[r]
            for (c in row.indices) {
                val id = row[c]
                val b = box[id]
                if (b == null) {
                    box[id] = intArrayOf(r, c, r, c)
                } else {
                    if (r < b[0]) b[0] = r
                    if (c < b[1]) b[1] = c
                    if (r > b[2]) b[2] = r
                    if (c > b[3]) b[3] = c
                }
            }
        }
        return box.entries
            .map { (id, b) -> Anchor(id, b[0], b[1], b[2] - b[0] + 1, b[3] - b[1] + 1) }
            .sortedWith(compareBy({ it.row }, { it.col }))
    }

    private fun anchorMap(): Map<Long, Anchor> = anchors().associateBy { it.id }

    // --- selection --------------------------------------------------------------------------

    fun toggleRow(r: Int) { if (r in selectedRows) selectedRows.remove(r) else selectedRows.add(r) }
    fun toggleCol(c: Int) { if (c in selectedCols) selectedCols.remove(c) else selectedCols.add(c) }
    fun clearSelection() { selectedRows.clear(); selectedCols.clear() }
    fun hasSelection(): Boolean = selectedRows.isNotEmpty() || selectedCols.isNotEmpty()

    /** Bounding box [r0, c0, r1, c1] of the current selection (whole rows/cols fall back to full). */
    fun blockRect(): IntArray? {
        if (!hasSelection()) return null
        val r0 = if (selectedRows.isEmpty()) 0 else selectedRows.min()
        val r1 = if (selectedRows.isEmpty()) rows() - 1 else selectedRows.max()
        val c0 = if (selectedCols.isEmpty()) 0 else selectedCols.min()
        val c1 = if (selectedCols.isEmpty()) cols() - 1 else selectedCols.max()
        return intArrayOf(r0, c0, r1, c1)
    }

    /** True when no cell straddles the rectangle's edges (so it can be merged/split cleanly). */
    private fun rectValid(r0: Int, c0: Int, r1: Int, c1: Int): Boolean {
        val am = anchorMap()
        for (r in r0..r1) for (c in c0..c1) {
            val a = am[grid[r][c]] ?: return false
            if (a.row < r0 || a.col < c0 || a.row + a.rowSpan - 1 > r1 || a.col + a.colSpan - 1 > c1) {
                return false
            }
        }
        return true
    }

    // --- structural mutations ---------------------------------------------------------------

    fun addRow(at: Int) {
        val n = cols()
        val newRow = ArrayList<Long>(n)
        for (c in 0 until n) {
            val above = if (at > 0) grid[at - 1][c] else -1L
            val below = if (at < grid.size) grid[at][c] else -1L
            // Inserting *inside* a vertically-merged cell grows it (Notion behavior); otherwise the
            // new slot is a fresh empty single cell.
            if (at in 1 until grid.size && above == below) {
                newRow.add(above)
            } else {
                val id = newId()
                cells[id] = CellContent("", false)
                newRow.add(id)
            }
        }
        grid.add(at, newRow)
        clearSelection()
        bump()
    }

    fun addColumn(at: Int) {
        for (row in grid) {
            val left = if (at > 0) row[at - 1] else -1L
            val right = if (at < row.size) row[at] else -1L
            if (at in 1 until row.size && left == right) {
                row.add(at, left) // grow a horizontally-merged cell
            } else {
                val id = newId()
                cells[id] = CellContent("", false)
                row.add(at, id)
            }
        }
        clearSelection()
        bump()
    }

    private fun deleteRows(indices: List<Int>) {
        val distinct = indices.distinct()
        if (rows() - distinct.size < 1) return // always keep at least one row
        distinct.sortedDescending().forEach { if (it in grid.indices) grid.removeAt(it) }
        purge()
        clearSelection()
        bump()
    }

    private fun deleteColumns(indices: List<Int>) {
        val distinct = indices.distinct()
        if (cols() - distinct.size < 1) return // always keep at least one column
        val desc = distinct.sortedDescending()
        for (row in grid) for (c in desc) if (c < row.size) row.removeAt(c)
        purge()
        clearSelection()
        bump()
    }

    fun deleteSelection() {
        when {
            selectedRows.isNotEmpty() -> deleteRows(selectedRows.toList())
            selectedCols.isNotEmpty() -> deleteColumns(selectedCols.toList())
        }
    }

    // --- merge / split ----------------------------------------------------------------------

    fun canMerge(): Boolean {
        val rect = blockRect() ?: return false
        val (r0, c0, r1, c1) = listOf(rect[0], rect[1], rect[2], rect[3])
        if ((r1 - r0 + 1) * (c1 - c0 + 1) <= 1) return false
        if (!rectValid(r0, c0, r1, c1)) return false
        val ids = HashSet<Long>()
        for (r in r0..r1) for (c in c0..c1) ids.add(grid[r][c])
        return ids.size > 1
    }

    fun mergeSelection() {
        if (!canMerge()) return
        val rect = blockRect() ?: return
        val target = grid[rect[0]][rect[1]] // keep the top-left cell's content
        for (r in rect[0]..rect[2]) for (c in rect[1]..rect[3]) grid[r][c] = target
        purge()
        clearSelection()
        bump()
    }

    fun canSplit(): Boolean {
        val rect = blockRect() ?: return false
        val a = anchorMap()[grid[rect[0]][rect[1]]] ?: return false
        // The selection must correspond exactly to one merged cell.
        return a.row == rect[0] && a.col == rect[1] &&
            a.row + a.rowSpan - 1 == rect[2] && a.col + a.colSpan - 1 == rect[3] &&
            (a.rowSpan > 1 || a.colSpan > 1)
    }

    fun splitSelection() {
        if (!canSplit()) return
        val rect = blockRect() ?: return
        val id = grid[rect[0]][rect[1]]
        val header = cells[id]?.isHeader == true
        var first = true
        for (r in rect[0]..rect[2]) for (c in rect[1]..rect[3]) {
            if (first) { first = false; continue } // top-left keeps the id + content
            val nid = newId()
            cells[nid] = CellContent("", header)
            grid[r][c] = nid
        }
        clearSelection()
        bump()
    }

    fun toggleHeaderBlock() {
        val rect = blockRect() ?: return
        val ids = HashSet<Long>()
        for (r in rect[0]..rect[2]) for (c in rect[1]..rect[3]) ids.add(grid[r][c])
        if (ids.isEmpty()) return
        val allHeader = ids.all { cells[it]?.isHeader == true }
        ids.forEach { cells[it]?.isHeader = !allHeader }
    }

    private fun purge() {
        val live = grid.flatten().toHashSet()
        cells.keys.retainAll(live)
    }

    // --- serialization ----------------------------------------------------------------------

    /** Serialize back to the ProseMirror `table` JSON shape carried by the embed's `rawJson`. */
    fun toProseMirrorJson(): String {
        val anchorMap = anchorMap()
        val root = buildJsonObject {
            put("type", "table")
            putJsonArray("content") {
                for (r in 0 until rows()) {
                    add(
                        buildJsonObject {
                            put("type", "tableRow")
                            putJsonArray("content") {
                                for (c in 0 until cols()) {
                                    val id = grid[r][c]
                                    val a = anchorMap[id] ?: continue
                                    if (a.row != r || a.col != c) continue // emit only the anchor slot
                                    val content = cells[id] ?: continue
                                    add(
                                        buildJsonObject {
                                            put("type", if (content.isHeader) "tableHeader" else "tableCell")
                                            putJsonObject("attrs") {
                                                put("colspan", a.colSpan)
                                                put("rowspan", a.rowSpan)
                                                put("colwidth", JsonNull)
                                            }
                                            putJsonArray("content") { add(paragraphJson(content.text)) }
                                        },
                                    )
                                }
                            }
                        },
                    )
                }
            }
        }
        return root.toString()
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun from(rawJson: String): TextKitEditableTableState =
            runCatching { parse(rawJson) }.getOrNull() ?: default()

        private fun default(): TextKitEditableTableState {
            val grid = MutableList(3) { r -> MutableList(3) { c -> (r * 3 + c).toLong() } }
            val cells = HashMap<Long, CellContent>()
            for (r in 0..2) for (c in 0..2) {
                val id = (r * 3 + c).toLong()
                cells[id] = CellContent(if (r == 0) "Encabezado ${c + 1}" else "", r == 0)
            }
            return TextKitEditableTableState(grid, cells, 9L)
        }

        private fun parse(rawJson: String): TextKitEditableTableState {
            val obj = json.parseToJsonElement(rawJson).jsonObject
            require(obj["type"]?.jsonPrimitive?.content == "table")
            val rowsJson = obj["content"]?.jsonArray ?: JsonArray(emptyList())

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
                val cs = rowEl.jsonObject["content"]?.jsonArray ?: JsonArray(emptyList())
                var c = 0
                cs.forEach { cellEl ->
                    val cell = cellEl.jsonObject
                    while (get(r, c) != -1L) c++
                    val attrs = cell["attrs"]?.jsonObject
                    val colspan = attrs?.get("colspan")?.jsonPrimitive?.content?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                    val rowspan = attrs?.get("rowspan")?.jsonPrimitive?.content?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                    val isHeader = cell["type"]?.jsonPrimitive?.content == "tableHeader"
                    val id = nextId++
                    cells[id] = CellContent(cellText(cell["content"]?.jsonArray), isHeader)
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
            return TextKitEditableTableState(grid, cells, nextId)
        }

        /** Concatenates all `text` leaves inside a cell's paragraph content. */
        private fun cellText(paragraphs: JsonArray?): String {
            if (paragraphs == null) return ""
            val builder = StringBuilder()
            paragraphs.forEach { paragraph ->
                paragraph.jsonObject["content"]?.jsonArray?.forEach { inline ->
                    inline.jsonObject["text"]?.jsonPrimitive?.content?.let { builder.append(it) }
                }
            }
            return builder.toString()
        }

        private fun paragraphJson(text: String): JsonObject = buildJsonObject {
            put("type", "paragraph")
            putJsonArray("content") {
                if (text.isNotEmpty()) {
                    add(buildJsonObject { put("type", "text"); put("text", text) })
                }
            }
        }
    }
}
