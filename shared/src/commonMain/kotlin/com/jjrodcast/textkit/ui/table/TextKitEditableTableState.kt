package com.jjrodcast.textkit.ui.table

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Max number of undo snapshots kept per editing session. */
private const val MaxHistory = 100

/**
 * Holds the editable table. `grid[r][c]` stores the id of the cell occupying that logical slot; a
 * merged cell simply has its id in every slot it covers. Spans are always *derived* from the grid
 * (see [anchors]), which keeps every mutation (add/remove/merge/split) a simple grid edit that can't
 * leave the model inconsistent. The rectangle is kept dense (all rows share the same width).
 *
 * Selection is line-based: [selectedRows] / [selectedCols] hold whole-line indices (chosen via the
 * gutter handles). The acted-upon rectangle is their bounding box ([blockRect]).
 *
 * **Undo/redo & sync.** This state is the single source of truth. Every mutation:
 * 1. pushes a snapshot onto [undoStack] (consecutive keystrokes in one cell coalesce into a single
 *    snapshot, exactly like the text editor groups typing), and
 * 2. emits the new JSON via [onChange] so the host document stays in sync automatically — no manual
 *    "sync" step.
 *
 * Because the table drives the JSON (and never rebuilds itself from its own echo — see [loadFrom] /
 * [lastSyncedRaw]), editing never loses focus, cursor, selection or scroll. External changes
 * (e.g. a document-level undo) come back through [loadFrom], which reloads in place.
 */
@Stable
internal class TextKitEditableTableState(
    private val grid: MutableList<MutableList<Long>>,
    initialCells: Map<Long, CellContent>,
    startId: Long,
) {
    /**
     * Cell content by id. Backed by a **snapshot state map** so restoring a snapshot (undo/redo) or a
     * reload recomposes exactly the cells whose content changed — even when the grid layout and the
     * cell composable's parameters are otherwise identical (a plain map read wouldn't invalidate it).
     */
    val cells: SnapshotStateMap<Long, CellContent> =
        mutableStateMapOf<Long, CellContent>().also { it.putAll(initialCells) }

    private var nextId = startId

    /** Bumped on structural changes (rows/cols/spans) to invalidate the grid layout. */
    var version by mutableIntStateOf(0)
        private set

    val selectedRows = mutableStateListOf<Int>()
    val selectedCols = mutableStateListOf<Int>()

    // --- history + sync ---------------------------------------------------------------------

    private val undoStack = ArrayDeque<TableSnapshot>()
    private val redoStack = ArrayDeque<TableSnapshot>()

    /** Coalescing key of the run in progress (e.g. `"text:5"`); `null` = no run, next edit is atomic. */
    private var coalesceKey: Any? = null

    /** Whether an [undo] / [redo] is available. Observe them to enable/disable the rail buttons. */
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    /** Called with the new ProseMirror JSON after every committed change (auto-sync sink). */
    private var onChange: ((String) -> Unit)? = null

    /**
     * The exact JSON string of the last state we emitted. Used to recognize our own echo: an incoming
     * `rawJson` equal to this (or canonically equal to it) is *our* change and is ignored; anything
     * else is a genuine external change and triggers [loadFrom].
     */
    var lastSyncedRaw: String = ""
        private set

    fun setOnChange(callback: (String) -> Unit) { onChange = callback }

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
        pushUndo(null)
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
        emit()
    }

    fun addColumn(at: Int) {
        pushUndo(null)
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
        emit()
    }

    private fun deleteRows(indices: List<Int>) {
        val distinct = indices.distinct()
        if (rows() - distinct.size < 1) return // always keep at least one row
        pushUndo(null)
        distinct.sortedDescending().forEach { if (it in grid.indices) grid.removeAt(it) }
        purge()
        clearSelection()
        bump()
        emit()
    }

    private fun deleteColumns(indices: List<Int>) {
        val distinct = indices.distinct()
        if (cols() - distinct.size < 1) return // always keep at least one column
        pushUndo(null)
        val desc = distinct.sortedDescending()
        for (row in grid) for (c in desc) if (c < row.size) row.removeAt(c)
        purge()
        clearSelection()
        bump()
        emit()
    }

    fun deleteSelection() {
        when {
            selectedRows.isNotEmpty() -> deleteRows(selectedRows.toList())
            selectedCols.isNotEmpty() -> deleteColumns(selectedCols.toList())
        }
    }

    // --- text editing -----------------------------------------------------------------------

    /**
     * Sets [id]'s text. Consecutive edits to the *same* cell coalesce into one undo step (so undo
     * reverts the whole typing burst, like the text editor), and each change auto-syncs the JSON.
     */
    fun editCell(id: Long, text: String) {
        val cell = cells[id] ?: return
        if (cell.text == text) return
        pushUndo("text:$id")
        cell.text = text
        emit()
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
        pushUndo(null)
        val rect = blockRect() ?: return
        val target = grid[rect[0]][rect[1]] // keep the top-left cell's content
        for (r in rect[0]..rect[2]) for (c in rect[1]..rect[3]) grid[r][c] = target
        purge()
        clearSelection()
        bump()
        emit()
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
        pushUndo(null)
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
        emit()
    }

    fun toggleHeaderBlock() {
        val rect = blockRect() ?: return
        val ids = HashSet<Long>()
        for (r in rect[0]..rect[2]) for (c in rect[1]..rect[3]) ids.add(grid[r][c])
        if (ids.isEmpty()) return
        pushUndo(null)
        val allHeader = ids.all { cells[it]?.isHeader == true }
        ids.forEach { cells[it]?.isHeader = !allHeader }
        bump()
        emit()
    }

    private fun purge() {
        val live = grid.flatten().toHashSet()
        cells.keys.retainAll(live)
    }

    // --- undo / redo ------------------------------------------------------------------------

    private fun snapshot(): TableSnapshot = TableSnapshot(
        grid = grid.map { it.toList() },
        cells = cells.mapValues { (_, v) -> v.text to v.isHeader },
        nextId = nextId,
    )

    private fun restoreSnapshot(s: TableSnapshot) {
        grid.clear()
        s.grid.forEach { grid.add(it.toMutableList()) }
        cells.clear()
        s.cells.forEach { (id, v) -> cells[id] = CellContent(v.first, v.second) }
        nextId = s.nextId
        clearSelection()
        bump()
    }

    /**
     * Records the current state as an undo point before a mutation. Same non-null [coalesce] key as
     * the previous edit reuses that point (coalescing), so a run of edits collapses to one step.
     */
    private fun pushUndo(coalesce: Any?) {
        val coalesced = coalesce != null && coalesce == coalesceKey && undoStack.isNotEmpty()
        if (!coalesced) {
            undoStack.addLast(snapshot())
            if (undoStack.size > MaxHistory) undoStack.removeFirst()
            redoStack.clear()
        }
        coalesceKey = coalesce
        syncHistoryFlags()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.addLast(snapshot())
        restoreSnapshot(undoStack.removeLast())
        coalesceKey = null // end any coalescing run
        syncHistoryFlags()
        emit()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.addLast(snapshot())
        restoreSnapshot(redoStack.removeLast())
        coalesceKey = null
        syncHistoryFlags()
        emit()
    }

    private fun syncHistoryFlags() {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }

    // --- external sync ----------------------------------------------------------------------

    /** Emits the current table JSON to the host, remembering it so its echo is ignored. */
    private fun emit() {
        val json = toProseMirrorJson()
        lastSyncedRaw = json
        onChange?.invoke(json)
    }

    /**
     * Replaces the whole table from an external [rawJson] (e.g. after a document-level undo), in
     * place so the composable identity is kept. Clears history and selection; does not emit (the
     * change originated outside).
     */
    fun loadFrom(rawJson: String) {
        val fresh = from(rawJson)
        grid.clear()
        fresh.grid.forEach { grid.add(it.toMutableList()) }
        cells.clear()
        fresh.cells.forEach { (id, v) -> cells[id] = CellContent(v.text, v.isHeader) }
        nextId = fresh.nextId
        undoStack.clear()
        redoStack.clear()
        coalesceKey = null
        clearSelection()
        lastSyncedRaw = toProseMirrorJson()
        syncHistoryFlags()
        bump()
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

        /** Builds a fresh state by decoding [rawJson] (see [TableJsonCodec]). */
        fun from(rawJson: String): TextKitEditableTableState {
            val decoded = TableJsonCodec.decode(rawJson)
            val state = TextKitEditableTableState(decoded.grid, decoded.cells, decoded.nextId)
            state.lastSyncedRaw = state.toProseMirrorJson()
            return state
        }

        /** Canonical JSON of [rawJson] as this model would serialize it (for echo detection). */
        fun canonical(rawJson: String): String = from(rawJson).toProseMirrorJson()

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
