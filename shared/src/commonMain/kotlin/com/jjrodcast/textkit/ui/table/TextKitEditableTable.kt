package com.jjrodcast.textkit.ui.table

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMerge
import androidx.compose.material.icons.automirrored.rounded.CallSplit
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jjrodcast.textkit.theme.TextKitTheme
import org.jetbrains.compose.resources.stringResource
import textkit.shared.generated.resources.Res
import textkit.shared.generated.resources.redo_text
import textkit.shared.generated.resources.table_add_text
import textkit.shared.generated.resources.table_clear_selection_text
import textkit.shared.generated.resources.table_delete_selection_text
import textkit.shared.generated.resources.table_merge_cells_text
import textkit.shared.generated.resources.table_selection_hint_text
import textkit.shared.generated.resources.table_split_cell_text
import textkit.shared.generated.resources.table_synced_json_text
import textkit.shared.generated.resources.table_toggle_header_text
import textkit.shared.generated.resources.undo_text

/**
 * An **editable**, Notion-style rich table for a `table` embed — designed to live inside a popup and
 * work equally well with touch (mobile/tablet) and pointer (web/desktop).
 *
 * The interaction is gutter-driven, like Notion:
 * - Tap a **column handle** (top strip) or a **row handle** (left strip) to select that line; tap
 *   several to grow the selection. The corner clears it.
 * - The side **icon rail** acts on the current selection: merge, split, toggle header, delete.
 * - The **+** handles at the far edges append a column / a row.
 * - Cells are plain editable text, so tapping a cell never fights with selecting it.
 *
 * Header cells use a more prominent color (`primaryContainer`) so they stand out from body rows.
 * Every change **auto-syncs**: [onSync] receives the updated ProseMirror JSON on each edit, and the
 * rail offers granular **undo/redo** of the table's own changes. The table is the source of truth and
 * never rebuilds from its own echo, so editing never loses focus/cursor/selection; genuine external
 * changes to [rawJson] (e.g. a document-level undo) are reloaded in place.
 *
 * Everything is themed through [TextKitTheme] (colors + font family), so it adapts to light/dark.
 *
 * @param rawJson The ProseMirror `table` JSON from the embed model (`EmbedInfo.rawJson`). Its content
 *   seeds the editor and an external change to it is reloaded. Malformed input falls back to 3×3.
 * @param onSync Called with the updated ProseMirror `table` JSON after every edit (auto-sync).
 * @param modifier Layout modifier for the root.
 */
@Composable
fun TextKitEditableTable(
    rawJson: String,
    onSync: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The state is the source of truth; it is NOT keyed on rawJson so our own auto-sync echo doesn't
    // rebuild it (which would drop focus/cursor/selection).
    val state = remember { TextKitEditableTableState.from(rawJson) }

    // Keep the auto-sync sink current.
    SideEffect { state.setOnChange(onSync) }

    // Reload only on a *genuine* external change. Fast path: exact match with what we last emitted is
    // our own echo → ignore. Otherwise compare canonically so cosmetic reformatting from the host
    // doesn't force a rebuild that would drop focus/selection.
    LaunchedEffect(rawJson) {
        if (rawJson != state.lastSyncedRaw &&
            TextKitEditableTableState.canonical(rawJson) != TextKitEditableTableState.canonical(state.lastSyncedRaw)
        ) {
            state.loadFrom(rawJson)
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(max = TextKitTableConstants.MaxTableHeight)
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState()),
            ) {
                TextKitTableGrid(state = state)
            }
            TextKitActionRail(
                state = state,
                modifier = Modifier.padding(start = 6.dp),
            )
        }

        Text(
            text = stringResource(Res.string.table_selection_hint_text),
            style = captionStyle(),
        )
    }
}

/** One action button in the rail. */
private class RailAction(
    val icon: ImageVector,
    val description: String,
    val enabled: Boolean,
    val onClick: () -> Unit,
)

@Composable
private fun TextKitActionRail(
    state: TextKitEditableTableState,
    modifier: Modifier = Modifier,
) {
    val actions = listOf(
        RailAction(Icons.AutoMirrored.Rounded.CallMerge, stringResource(Res.string.table_merge_cells_text), state.canMerge()) { state.mergeSelection() },
        RailAction(Icons.AutoMirrored.Rounded.CallSplit, stringResource(Res.string.table_split_cell_text), state.canSplit()) { state.splitSelection() },
        RailAction(Icons.Rounded.Title, stringResource(Res.string.table_toggle_header_text), state.hasSelection()) { state.toggleHeaderBlock() },
        RailAction(Icons.Rounded.DeleteOutline, stringResource(Res.string.table_delete_selection_text), state.hasSelection()) { state.deleteSelection() },
        RailAction(Icons.AutoMirrored.Rounded.Undo, stringResource(Res.string.undo_text), state.canUndo) { state.undo() },
        RailAction(Icons.AutoMirrored.Rounded.Redo, stringResource(Res.string.redo_text), state.canRedo) { state.redo() },
    )

    val spacing = 6.dp
    BoxWithConstraints(modifier) {
        // Height a single column of every action would need; when the popup is too short for it
        // (typically mobile landscape) fall back to two columns so all actions stay visible without
        // relying on scrolling. A scroll + height cap remain as a safety net for extreme sizes.
        val singleColumnHeight =
            TextKitTableConstants.RailButtonSize * actions.size + spacing * (actions.size - 1)
        val columns = if (maxHeight >= singleColumnHeight) 1 else 2

        Column(
            modifier = Modifier
                .heightIn(max = TextKitTableConstants.MaxTableHeight)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing),
        ) {
            actions.chunked(columns).forEach { rowActions ->
                Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                    rowActions.forEach { action ->
                        TextKitRailButton(
                            icon = action.icon,
                            contentDescription = action.description,
                            enabled = action.enabled,
                            onClick = action.onClick,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TextKitRailButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    primary: Boolean = false,
    onClick: () -> Unit,
) {
    val background = when {
        primary -> TextKitTheme.colors.primary
        else -> TextKitTheme.colors.surfaceVariant
    }
    val content = when {
        primary -> TextKitTheme.colors.onPrimary
        enabled -> TextKitTheme.colors.onSurfaceVariant
        else -> TextKitTheme.colors.onSurfaceVariant.copy(alpha = 0.35f)
    }
    Surface(
        onClick = onClick,
        enabled = enabled || primary,
        shape = CircleShape,
        color = background,
        contentColor = content,
        modifier = Modifier.size(TextKitTableConstants.RailButtonSize),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun TextKitTableGrid(
    state: TextKitEditableTableState,
    modifier: Modifier = Modifier
) {
    // Read `version` so structural mutations trigger a recomposition + re-measure, and `blockRect`
    // (which reads the selection) so cell highlighting refreshes when the selection changes.
    @Suppress("UNUSED_VARIABLE") val version = state.version
    val rect = state.blockRect()
    val anchors = state.anchors()
    val rowCount = state.rows()
    val colCount = state.cols()

    fun highlighted(a: Anchor): Boolean = rect != null &&
            a.row <= rect[2] && a.row + a.rowSpan - 1 >= rect[0] &&
            a.col <= rect[3] && a.col + a.colSpan - 1 >= rect[1]

    Layout(
        modifier = modifier,
        content = {
            key("corner") { Box(Modifier.gridChild(CornerChild)) { TextKitCornerHandle(state) } }
            for (c in 0 until colCount) {
                key("col$c") {
                    Box(Modifier.gridChild(ColHandleChild(c))) {
                        TextKitColumnHandle(
                            state,
                            c
                        )
                    }
                }
            }
            key("addCol") {
                Box(Modifier.gridChild(AddColChild)) {
                    TextKitAddHandle {
                        state.addColumn(
                            state.cols()
                        )
                    }
                }
            }
            for (r in 0 until rowCount) {
                key("row$r") {
                    Box(Modifier.gridChild(RowHandleChild(r))) {
                        TextKitRowHandle(
                            state,
                            r
                        )
                    }
                }
            }
            key("addRow") {
                Box(Modifier.gridChild(AddRowChild)) {
                    TextKitAddHandle {
                        state.addRow(
                            state.rows()
                        )
                    }
                }
            }
            anchors.forEach { a ->
                key("cell${a.id}") {
                    Box(Modifier.gridChild(CellChild(a.row, a.col, a.rowSpan, a.colSpan))) {
                        TextKitTableCellView(state = state, id = a.id, highlighted = highlighted(a))
                    }
                }
            }
        },
    ) { measurables, _ ->
        val gutterPx = TextKitTableConstants.GutterSize.roundToPx()
        val addPx = TextKitTableConstants.AddSize.roundToPx()
        val colPx = TextKitTableConstants.ColumnWidth.roundToPx()
        val minRowPx = TextKitTableConstants.MinRowHeight.roundToPx()
        val kids = measurables.map { it.parentData as GridChild }

        // Row heights come from the cells (single-row cells set them; multi-row cells grow the last
        // spanned row). Intrinsics give the natural height since a Measurable can be measured once.
        val rowH = IntArray(rowCount) { minRowPx }
        measurables.forEachIndexed { i, m ->
            val k = kids[i]
            if (k is CellChild && k.rowSpan == 1) {
                rowH[k.row] = maxOf(rowH[k.row], m.maxIntrinsicHeight(k.colSpan * colPx))
            }
        }
        measurables.forEachIndexed { i, m ->
            val k = kids[i]
            if (k is CellChild && k.rowSpan > 1) {
                val h = m.maxIntrinsicHeight(k.colSpan * colPx)
                val have = (k.row until k.row + k.rowSpan).sumOf { rowH[it] }
                if (h > have) rowH[k.row + k.rowSpan - 1] += h - have
            }
        }
        val rowY = IntArray(rowCount)
        for (r in 1 until rowCount) rowY[r] = rowY[r - 1] + rowH[r - 1]
        val cellsH = rowH.sum()
        val gridW = colCount * colPx

        val placeables = measurables.mapIndexed { i, m ->
            when (val k = kids[i]) {
                is CellChild -> {
                    val h = (k.row until k.row + k.rowSpan).sumOf { rowH[it] }
                    m.measure(Constraints.fixed(k.colSpan * colPx, h))
                }

                is ColHandleChild -> m.measure(Constraints.fixed(colPx, gutterPx))
                is RowHandleChild -> m.measure(Constraints.fixed(gutterPx, rowH[k.row]))
                CornerChild -> m.measure(Constraints.fixed(gutterPx, gutterPx))
                AddColChild -> m.measure(Constraints.fixed(addPx, cellsH.coerceAtLeast(minRowPx)))
                AddRowChild -> m.measure(Constraints.fixed(gridW, addPx))
            }
        }

        val totalW = gutterPx + gridW + addPx
        val totalH = gutterPx + cellsH + addPx
        layout(totalW, totalH) {
            placeables.forEachIndexed { i, p ->
                val pos = when (val k = kids[i]) {
                    is CellChild -> Pair(gutterPx + k.col * colPx, gutterPx + rowY[k.row])
                    is ColHandleChild -> Pair(gutterPx + k.col * colPx, 0)
                    is RowHandleChild -> Pair(0, gutterPx + rowY[k.row])
                    CornerChild -> Pair(0, 0)
                    AddColChild -> Pair(gutterPx + gridW, gutterPx)
                    AddRowChild -> Pair(gutterPx, gutterPx + cellsH)
                }
                p.place(pos.first, pos.second)
            }
        }
    }
}

@Composable
private fun TextKitTableCellView(
    state: TextKitEditableTableState,
    id: Long,
    highlighted: Boolean
) {
    val content = state.cells[id] ?: return
    val isHeader = content.isHeader

    val background = when {
        isHeader && highlighted -> TextKitTheme.colors.primaryContainer.copy(alpha = 0.7f)
        isHeader -> TextKitTheme.colors.primaryContainer
        highlighted -> TextKitTheme.colors.primary.copy(alpha = 0.12f)
        else -> TextKitTheme.colors.surface
    }
    val border = if (highlighted) {
        BorderStroke(2.dp, TextKitTheme.colors.primary)
    } else {
        BorderStroke(1.dp, TextKitTheme.colors.outlineVariant)
    }

    // Keep the field's own TextFieldValue (cursor/selection) locally so live typing is never disturbed
    // by recompositions. Reflect *external* content changes (undo/redo/reload) only when they differ.
    var value by remember(id) { mutableStateOf(TextFieldValue(content.text)) }
    LaunchedEffect(content.text) {
        if (content.text != value.text) {
            value = TextFieldValue(content.text, TextRange(content.text.length))
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(background).border(border)) {
        BasicTextField(
            value = value,
            onValueChange = {
                value = it
                state.editCell(id, it.text)
            },
            textStyle = cellTextStyle(isHeader),
            cursorBrush = SolidColor(TextKitTheme.colors.primary),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
        )
    }
}


@Composable
private fun TextKitCornerHandle(state: TextKitEditableTableState) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TextKitTheme.colors.surfaceVariant)
            .clickable { state.clearSelection() },
        contentAlignment = Alignment.Center,
    ) {
        if (state.hasSelection()) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = stringResource(Res.string.table_clear_selection_text),
                tint = TextKitTheme.colors.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun TextKitColumnHandle(state: TextKitEditableTableState, col: Int) {
    val selected = col in state.selectedCols
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (selected) TextKitTheme.colors.primary else TextKitTheme.colors.surfaceVariant)
            .clickable { state.toggleCol(col) },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 16.dp, height = 3.dp)
                .background(
                    color = if (selected) TextKitTheme.colors.onPrimary else TextKitTheme.colors.onSurfaceVariant,
                    shape = RoundedCornerShape(2.dp),
                ),
        )
    }
}

@Composable
private fun TextKitRowHandle(state: TextKitEditableTableState, row: Int) {
    val selected = row in state.selectedRows
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (selected) TextKitTheme.colors.primary else TextKitTheme.colors.surfaceVariant)
            .clickable { state.toggleRow(row) },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 3.dp, height = 16.dp)
                .background(
                    color = if (selected) TextKitTheme.colors.onPrimary else TextKitTheme.colors.onSurfaceVariant,
                    shape = RoundedCornerShape(2.dp),
                ),
        )
    }
}

@Composable
private fun TextKitAddHandle(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TextKitTheme.colors.surface)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.Add,
            contentDescription = stringResource(Res.string.table_add_text),
            tint = TextKitTheme.colors.primary,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun cellTextStyle(isHeader: Boolean): TextStyle = TextStyle(
    fontFamily = TextKitTheme.typography.fontFamily,
    fontSize = 14.sp,
    fontWeight = if (isHeader) FontWeight.SemiBold else FontWeight.Normal,
    color = if (isHeader) TextKitTheme.colors.onPrimaryContainer else TextKitTheme.colors.onSurface,
)

@Composable
private fun captionStyle(): TextStyle = TextStyle(
    fontFamily = TextKitTheme.typography.fontFamily,
    fontSize = 11.sp,
    color = TextKitTheme.colors.onSurfaceVariant,
)

@Preview(showBackground = true, backgroundColor = 0xFFF5FBF7, widthDp = 560, heightDp = 620)
@Composable
private fun TextKitEditableTablePreview() {
    var synced by remember { mutableStateOf("") }

    TextKitTheme(darkTheme = false) {
        Column(
            modifier = Modifier
                .background(TextKitTheme.colors.background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Rendered inside a card so it reads like the popup it will live in.
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = TextKitTheme.colors.surface,
                contentColor = TextKitTheme.colors.onSurface,
                border = BorderStroke(1.dp, TextKitTheme.colors.outlineVariant),
            ) {
                TextKitEditableTable(
                    rawJson = TextKitTableConstants.SampleTableJson,
                    onSync = { synced = it },
                    modifier = Modifier.padding(12.dp),
                )
            }
            if (synced.isNotEmpty()) {
                Text(
                    text = stringResource(Res.string.table_synced_json_text),
                    style = TextStyle(
                        fontFamily = TextKitTheme.typography.fontFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextKitTheme.colors.onBackground,
                    ),
                )
                Text(text = synced, style = captionStyle())
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0E1513, widthDp = 560, heightDp = 460)
@Composable
private fun TextKitEditableTableDarkPreview() {
    TextKitTheme(darkTheme = true) {
        Column(
            modifier = Modifier
                .background(TextKitTheme.colors.background)
                .padding(16.dp),
        ) {
            TextKitEditableTable(rawJson = TextKitTableConstants.SampleTableJson, onSync = {})
        }
    }
}
