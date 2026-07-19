# TextKitEditableTable

`TextKitEditableTable` is an **editable, Notion-style rich table** for a `table` embed. Where the
embed placeholder shows a one-line label in the text and the embed popup usually renders content
read-only, this component lets the user actually *mutate* the table: add/remove rows and columns,
merge and split cells (true `colspan`/`rowspan`), toggle header cells, and edit cell text inline.

It is designed to live **inside the embed popup** and to work equally well with touch (mobile/tablet)
and pointer (web/desktop). Every change **auto-syncs** back to the host document — there is no manual
"save" step — and the component carries its **own undo/redo** for the table's structure and content.

- Source: [`shared/.../ui/table/`](../../shared/src/commonMain/kotlin/com/jjrodcast/textkit/ui/table/)
- Public composable: `com.jjrodcast.textkit.ui.table.TextKitEditableTable`
- Serialized shape: a ProseMirror-style `table` node (see [JSON format](#json-format)).

## Table of contents

- [How it fits in](#how-it-fits-in)
- [Zero-config: through the embed popup](#zero-config-through-the-embed-popup)
- [Standalone usage](#standalone-usage)
- [Interaction model](#interaction-model)
- [Auto-sync (no manual save)](#auto-sync-no-manual-save)
- [Undo / redo](#undo--redo)
- [Merge & split rules](#merge--split-rules)
- [Header cells](#header-cells)
- [JSON format](#json-format)
- [Theming](#theming)
- [Layout dimensions](#layout-dimensions)
- [Localization](#localization)
- [Behavior notes & invariants](#behavior-notes--invariants)

## How it fits in

A table is an [**embedded block**](../../README.md#embedded-blocks): a `table` node stored verbatim in
the document and rendered inline as a clickable placeholder. Tapping the placeholder opens
`TextKitEmbedPopup`, and for `EmbedTypes.Table` the popup body *is* `TextKitEditableTable`. The chain
is:

```
document JSON ──▶ EmbedInfo.rawJson ──▶ TextKitEditableTable(rawJson, onSync)
       ▲                                          │
       └──────────── onSync(newJson) ◀────────────┘   (state.updateActiveEmbed)
```

So editing a cell updates the table's JSON, which flows through `onSync` back into the embed, which
updates the document — and it all round-trips losslessly.

## Zero-config: through the embed popup

If you already render `TextKitEmbedPopup` next to the editor (see the main README), **table editing is
on automatically** — `TextKitEmbedPopup` wires `TextKitEditableTable` for `EmbedTypes.Table` and
defaults `onSync` to `state.updateActiveEmbed(...)`:

```kotlin
import com.jjrodcast.textkit.ui.TextKitEditor
import com.jjrodcast.textkit.ui.TextKitEmbedPopup

Box {
    TextKitEditor(state = state)
    TextKitEmbedPopup(state = state) // table embeds become editable, changes auto-sync to the document
}
```

To insert a table so there's something to edit, use `insertEmbed` (e.g. from a `/` slash command):

```kotlin
import com.jjrodcast.textkit.editor.core.parser.EmbedTypes

state.insertEmbed(embedType = EmbedTypes.Table, rawJson = tableJson, label = "📊 Table")
```

Everything below is only needed if you want to host the table yourself or understand what happens
under the hood.

## Standalone usage

`TextKitEditableTable` is a self-contained composable with just two required parameters:

```kotlin
import com.jjrodcast.textkit.ui.table.TextKitEditableTable

@Composable
fun MyTableEditor(tableJson: String) {
    var current by remember { mutableStateOf(tableJson) }

    TextKitEditableTable(
        rawJson = current,             // ProseMirror `table` JSON
        onSync = { updated -> current = updated }, // called after EVERY edit
        modifier = Modifier.padding(12.dp),
    )
}
```

| Parameter | Type | Description |
|---|---|---|
| `rawJson` | `String` | The ProseMirror `table` JSON. It **seeds** the editor; a genuine *external* change to it is reloaded in place. Malformed input falls back to a starter 3×3 grid. |
| `onSync` | `(String) -> Unit` | Called with the updated `table` JSON **after every edit** (auto-sync). |
| `modifier` | `Modifier` | Layout modifier for the root. |

Wrap it in `TextKitTheme { }` (or `TextKitScreen`) so it can read colors and typography — see
[Theming](#theming).

## Interaction model

The UI is **gutter-driven**, like Notion, so selecting a line never fights with editing a cell:

| Element | Where | What it does |
|---|---|---|
| **Column handle** | thin strip along the top | Tap to select that column; tap more to grow the selection. |
| **Row handle** | thin strip along the left | Tap to select that row; tap more to grow the selection. |
| **Corner handle** | top-left corner | Clears the current selection (shows a ✕ while a selection exists). |
| **`+` handles** | far right (column) and bottom (row) edges | Append a new column / row. |
| **Cell body** | inside each cell | Plain inline editable text. |
| **Action rail** | icon column to the right of the grid | Acts on the current selection (see below). |

**Action rail** buttons (each enabled only when applicable):

| Icon | Action | Enabled when |
|---|---|---|
| Merge | Merge the selected rectangle into one cell | The selection is a clean rectangle covering >1 cell |
| Split | Split a merged cell back into single cells | Exactly one merged cell is selected |
| Header (Title) | Toggle header styling on the selected block | Anything is selected |
| Delete | Delete the selected rows or columns | Anything is selected |
| Undo | Undo the last table change | There is history to undo |
| Redo | Redo the last undone change | There is a redo step |

The rail is a single column when the popup is tall enough, and **falls back to two columns** in short
layouts (typically mobile landscape) so every action stays reachable without scrolling.

Selection is **line-based**: you pick whole rows and/or columns, and the acted-upon region is their
bounding box. A hint under the grid reminds users how to select.

## Auto-sync (no manual save)

There is **no "Sync"/"Save" button** — `onSync` fires after every committed change (typing, add,
delete, merge, split, header toggle, undo, redo). The state is the single source of truth and is
deliberately **not** rebuilt from its own emitted JSON, so:

- typing never loses focus, cursor, selection, or scroll to a recomposition;
- consecutive keystrokes in one cell are one logical change;
- a **genuine external** change to `rawJson` (e.g. a document-level undo from the host editor) *is*
  detected and reloaded in place.

Echo detection is exact-match first (the string it last emitted), then **canonical** comparison
(re-serialize both and compare), so cosmetic reformatting by the host doesn't trigger a needless
rebuild.

## Undo / redo

The table keeps its **own** bounded history (default **100** steps) covering both structure and cell
text, exposed to the rail via observable `canUndo` / `canRedo` flags. Consecutive keystrokes in the
**same** cell **coalesce** into a single undo step (mirroring how the text editor groups typing);
structural actions (add/delete/merge/split/header) are each their own step. History is cleared when
the table is reloaded from an external change.

> This is independent of the main editor's undo/redo. Inside the table popup, undo/redo affects the
> table; the document-level history still sees each synced table change as one embed update.

## Merge & split rules

- **Merge** requires a selection that forms a *fully-covered rectangle* (no cell straddles its edges)
  spanning more than one cell. The merged cell keeps the **top-left** cell's content and header flag;
  spans are emitted as `colspan` / `rowspan`.
- **Split** requires a selection that corresponds **exactly** to one merged cell. The top-left slot
  keeps the original content; the freed slots become fresh empty single cells (inheriting the header
  flag).
- Adding a row/column *inside* a merged region **grows** that merged cell (Notion behavior) rather
  than punching a hole; elsewhere it inserts fresh empty single cells.
- The grid is always kept **dense and rectangular** — every row has the same width, and at least one
  row and one column always remain (delete is a no-op past that floor).

Spans are never stored directly: the model stores a cell id in every grid slot the cell covers and
**derives** spans from that grid, so a merge/split/add/delete can't leave spans inconsistent.

## Header cells

Toggle header styling on any selected block with the Header rail action. Header cells serialize as
`tableHeader` (vs `tableCell`) and render with a more prominent color (`primaryContainer` +
semibold), so they stand out from body rows. Toggling is all-or-nothing over the selection: if every
selected cell is already a header, it turns them all off; otherwise it turns them all on.

## JSON format

`TextKitEditableTable` reads and writes a ProseMirror-style `table` node. This is exactly the
`rawJson` carried by a `table` embed, so it round-trips through the document unchanged:

```json
{
  "type": "table",
  "content": [
    { "type": "tableRow", "content": [
      { "type": "tableHeader", "attrs": { "colspan": 1, "rowspan": 1, "colwidth": null },
        "content": [{ "type": "paragraph", "content": [{ "type": "text", "text": "Product" }] }] },
      { "type": "tableHeader", "attrs": { "colspan": 1, "rowspan": 1, "colwidth": null },
        "content": [{ "type": "paragraph", "content": [{ "type": "text", "text": "Region" }] }] },
      { "type": "tableHeader", "attrs": { "colspan": 1, "rowspan": 1, "colwidth": null },
        "content": [{ "type": "paragraph", "content": [{ "type": "text", "text": "Sales" }] }] }
    ]},
    { "type": "tableRow", "content": [
      { "type": "tableCell", "attrs": { "colspan": 2, "rowspan": 1, "colwidth": null },
        "content": [{ "type": "paragraph", "content": [{ "type": "text", "text": "Subtotal" }] }] },
      { "type": "tableCell", "attrs": { "colspan": 1, "rowspan": 1, "colwidth": null },
        "content": [{ "type": "paragraph", "content": [{ "type": "text", "text": "1200" }] }] }
    ]}
  ]
}
```

**Nodes**

| Node | Role | Notable attrs |
|---|---|---|
| `table` | Root of the block | — |
| `tableRow` | One row; `content` is a list of cells | — |
| `tableCell` | A body cell | `colspan`, `rowspan`, `colwidth` |
| `tableHeader` | A header cell (styled) | `colspan`, `rowspan`, `colwidth` |

- Cell text is stored as a single `paragraph` of `text` runs; an empty cell serializes an empty
  paragraph.
- `colspan` / `rowspan` default to `1`. Only the **anchor** (top-left) slot of a merged cell is
  emitted; the covered slots are implied by the spans.
- `colwidth` is written as `null` (fixed column width is used for layout; per-column widths are not
  edited by this component).

Parsing is tolerant: unknown keys are ignored, ragged rows are padded, holes are filled with empty
cells, and completely malformed input falls back to a 3×3 starter grid.

## Theming

Every part of the table — cells, gutters, handles, the action rail — reads its colors and font from
[`TextKitTheme`](../theming/README.md), so it adapts to light/dark automatically. Key role usage:

| Element | Role |
|---|---|
| Body cell background / text | `surface` / `onSurface` |
| Header cell background / text | `primaryContainer` / `onPrimaryContainer` |
| Selected line handle / highlighted cell | `primary` (translucent fill for highlighted cells) |
| Cell borders & hairlines | `outlineVariant` |
| Gutters, rail buttons | `surfaceVariant` / `onSurfaceVariant` |
| Disabled rail button content | `onSurfaceVariant` at reduced alpha |

Provide the theme somewhere above the table:

```kotlin
TextKitTheme(darkTheme = isSystemInDarkTheme()) {
    TextKitEditableTable(rawJson = json, onSync = { /* ... */ })
}
```

## Layout dimensions

Sizes live in one place (`TextKitTableConstants`) so the grid, gutters and rail size from a single
source of truth:

| Constant | Default | Meaning |
|---|---|---|
| `ColumnWidth` | `132.dp` | Fixed width of every column |
| `MinRowHeight` | `44.dp` | Minimum row height (rows grow to fit content) |
| `GutterSize` | `26.dp` | Thickness of the row/column selection gutters |
| `AddSize` | `26.dp` | Thickness of the `+` add-row / add-column handles |
| `MaxTableHeight` | `360.dp` | Max height of the scrollable table area (so it never overflows the popup) |
| `RailButtonSize` | `42.dp` | Diameter of a rail action button |

The grid uses a custom `Layout` so merged cells span multiple rows/columns correctly, and both axes
scroll when the table is larger than the popup.

## Localization

All user-facing strings are in `composeResources/values/strings.xml` and resolved via
`stringResource`, so they localize with the rest of your app:

| Key | Default |
|---|---|
| `table_merge_cells_text` | "Merge cells" |
| `table_split_cell_text` | "Split cell" |
| `table_toggle_header_text` | "Toggle header" |
| `table_delete_selection_text` | "Delete selection" |
| `table_clear_selection_text` | "Clear selection" |
| `table_add_text` | "Add" |
| `table_selection_hint_text` | "Tap the edges to select rows or columns; use the icons to merge, split, or delete." |
| `undo_text` / `redo_text` | "Undo" / "Redo" |

## Behavior notes & invariants

- **State is the source of truth.** The composable state is *not* keyed on `rawJson`, so the auto-sync
  echo can't rebuild it and drop focus/cursor/selection.
- **Spans are derived, never stored.** The grid holds a cell id per slot; anchors + spans are computed
  from it, keeping every mutation consistent by construction.
- **Dense rectangle.** All rows share the same width; at least one row and one column always remain.
- **Bounded history.** 100 undo steps, keystroke coalescing per cell, cleared on external reload.
- **Lossless round-trip.** The emitted JSON matches the embed's `table` shape, so it flows straight
  back into the document.
