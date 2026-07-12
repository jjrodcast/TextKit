# TextKit Editor Engine

A **rope-backed piece-table rich-text editor engine** for Kotlin Multiplatform / Compose Multiplatform, targeting Android, iOS, Web (Wasm + JS), and Desktop (JVM).

It lives entirely in `shared/src/commonMain/kotlin/com/jjrodcast/textkit/editor/` and is designed to be UI-agnostic: it manages the document model, formatting, and (de)serialization, while the presentation layer stays free to render however it likes.

---

## Overview

The engine loads and saves a **ProseMirror-style JSON document** and exposes a small, stateful facade — `TextEditorManager` — to query and mutate the document.

Key characteristics:

- **Piece table storage** — a classic two-buffer design (immutable `originalBuffer` + append-only `addedBuffer`) so edits never copy the whole text.
- **Rope index** — a balanced rope (`PieceRope` / `RopeNode`) is the single source of truth for the piece sequence, keeping lookups and mutations at **O(log P)** in the number of pieces.
- **Rich formatting** — bold, italic, underline, strikethrough, highlight, links, and text style (color + font size), plus block structures: headings (h1–h6), ordered / bullet / task lists, and blockquotes.
- **Incremental plain-text cache** — plain text is cached and patched in place rather than rebuilt on every edit.
- **Multiplatform** — pure `commonMain` logic, with a few `expect`/`actual` seams (`Platform.kt`, `utils/Constants.kt`, `transactions/text/TextDecoratorTransaction.kt`).

---

## Internal architecture (`textkit.editor`)

The engine is organized in layers, from the public API down to raw storage. Everything below `TextEditorManager` is `internal`.

```
TextEditorManager                     (core/)                 ← public facade
      │
      ▼
TextEditorTransaction                 (core/transactions/)    ← edit orchestration / dispatch
      │        ├── ListItemTransaction         (transactions/lists/)   ← list & task toggles
      │        └── FormatTransaction           (transactions/marks/)   ← mark / color / link edits
      ▼
RichTextEditorPieceTable
   → RichTextEditorBasePieceTable      (core/piecetable/)      ← piece table (2 buffers + cache)
      │
      ▼
PieceRope  +  RopeNode                 (core/piecetable/rope/) ← balanced rope: piece sequence
```

### 1. `core/TextEditorManager`

The public entry point (see next section). A thin, stateful wrapper that delegates every call to a lazily created `TextEditorTransaction`.

### 2. `core/transactions/TextEditorTransaction`

Implements `interfaces/TextEditorInitTransaction` and owns the piece-table instance. Its `updateDocument(...)` is the central dispatch:

- List/task-item toggles (when `prevListItem != currListItem`) route to **`ListItemTransaction`**.
- Mark / format / link / color changes route to **`FormatTransaction`**.

It also implements loading (`loadWith`), serialization (`json`), text access (`text`), paragraph extraction (`getParagraphs`), and mark/decorator queries.

### 3. `core/piecetable/RichTextEditorPieceTable` → `RichTextEditorBasePieceTable`

The piece table. Two buffers:

- `originalBuffer` — immutable, populated from the loaded document.
- `addedBuffer` — append-only `StringBuilder` for new text.

Each **`RichPiece`** (`piecetable/models/`) references a `Source` + offset + length, plus its `marks` and optional `decorator`. Plain text is held in `_cachedText` and patched incrementally (`patchCache`).

### 4. `core/piecetable/rope/PieceRope` (+ `RopeNode`)

A balanced rope is the **single source of truth for the piece sequence**. All piece lookups and mutations go through single-pass rope walks — `findByDocumentOffset`, `forRange`, `findParagraphStartAt`, `splice`, etc. — to preserve **O(log P)** complexity. Avoid reintroducing O(P) scans here.

### Document format & conversion (`core/parser/`, `core/converters/`)

The persisted format is a **ProseMirror-style JSON tree**: `type: "doc"` → `content` of block paragraphs → inline text with marks.

- **Parser** (`parser/`): block nodes (`Paragraphs.kt`, `Lists.kt`, `Blockquote.kt`), inline nodes (`Texts.kt`), and marks (`Marks.kt`). Polymorphic (de)serialization is keyed on the `"type"` discriminator via custom `JsonContentPolymorphicSerializer`s; unknown types fall back to `None`. Use the shared `TEXT_EDITOR_JSON` instance (`parser/Json.kt`).
- **`converters/TextEditorConverter`**: JSON document → flat `TextEditorModel` list, inserting decorator marker characters (list bullets / numbers / checkboxes) and normalizing line breaks into a linear character stream.
- **`converters/PieceTableConverter`**: piece table → JSON document (the inverse; used by `toJson()`).
- **`converters/ListsConverter`** + `converters/utils/`: reconstruct nested list structure from the flat stream.

> Line-break handling and decorator markers are load-bearing: paragraphs are delimited by trailing line breaks in the flat stream, and list/task decorators are stored as leading marker pieces.

### Supporting packages

- `core/models/` — internal document models (`TextEditorModel`, `TextEditorDocumentModel`, `PieceParagraph`, `MultiPieceParagraph`).
- `core/transactions/models/` — public value types: `TextEditorRange`, `TextEditorSelectedMark`, `TextEditorParagraph` / `TextEditorItem`, `TextEditorTransactionType`.
- `components/` — format descriptors: `TextEditorStyleItem` (bold, italic, …), `TextEditorListItem` (numbered / bulleted / check list), `TextEditorDecorator` (blockquote), `TextEditorColorModel`.
- `models/MarkSearchType` — the result of a mark/decorator query over a selection.
- `utils/` — string, list, math, and regex helpers plus multiplatform `Constants`.

---

## Using `TextEditorManager`

`TextEditorManager` is the only public class you need. It is stateful — create one instance per document.

```kotlin
import com.jjrodcast.textkit.editor.core.TextEditorManager

val editor = TextEditorManager()

// Load a ProseMirror-style JSON document.
// isViewer = true keeps blockquotes and other read-only content;
// isViewer = false strips blockquotes for editing.
editor.load(json = documentJson, isViewer = false)

// Read the plain text (with decorator markers / line breaks).
val plainText: String = editor.getText()

// Serialize the current document back to JSON.
val outJson: String = editor.toJson()
```

### API reference

| Member | Description |
| --- | --- |
| `load(json: String, isViewer: Boolean)` | Parse and load a document. `isViewer = false` strips blockquotes for editing. |
| `getText(): String` | Current plain text stream. |
| `toJson(): String` | Serialize the document to ProseMirror-style JSON. |
| `isViewer: Boolean` | Whether the manager was loaded in viewer mode. |
| `getParagraphs(): List<TextEditorParagraph>` | Document as a list of paragraphs, each holding `TextEditorItem`s (text, `start`/`end` offsets, marks, decorator). Ideal for rendering. |
| `getSearchMarkType(selection): MarkSearchType` | Marks, list item, range, and text active over a selection. Use it to reflect toolbar state. |
| `getLink(start, end): Pair<String?, TextEditorRange>` | The href (if any) covering a range, plus the range it spans. |
| `checkDecorator(start, end): Pair<Boolean, TextEditorRange>` | Whether the range contains a decorator (list/task marker) and its range. |
| `onDecoratorChange(offset): Boolean` | Toggle the decorator at an offset (e.g. check/uncheck a task item). |
| `updateMarks(selection, prev, curr): Pair<Boolean, TextEditorRange>` | Apply mark/list changes over a selection. Returns success + the resulting range. |
| `updateColor(selection, color): Pair<Boolean, TextEditorRange>` | Set (or clear, with `null`) the text color, preserving the existing font size. |
| `updateLink(selection, prevLink, currLink): Pair<Boolean, TextEditorRange>` | Add, change, or remove a link over a selection. |

All mutating calls return `Pair<Boolean, TextEditorRange>`: whether the edit applied, and the range it now covers (useful to restore the UI selection).

### Example: querying and formatting a selection

```kotlin
import com.jjrodcast.textkit.editor.components.TextEditorStyleItem
import com.jjrodcast.textkit.editor.core.transactions.models.TextEditorRange
import com.jjrodcast.textkit.editor.core.transactions.models.TextEditorSelectedMark

val selection = TextEditorRange(start = 4, end = 12)

// 1. Inspect what's active on the selection (drive your toolbar with this).
val current = editor.getSearchMarkType(selection)
val isBold = current.marks.any { it is com.jjrodcast.textkit.editor.core.parser.BoldMark }

// 2. Toggle bold on the selection.
val prev = TextEditorSelectedMark(marks = current.marks)
val next = TextEditorSelectedMark(marks = current.marks + TextEditorStyleItem.Bold.toMark())

val (applied, newRange) = editor.updateMarks(
    selection = selection,
    prevSelectedMark = prev,
    currSelectedMark = next,
)
```

### Example: color and links

```kotlin
// Apply a color (hex string); pass null to clear it.
editor.updateColor(selection, color = "#E53935")

// Add or update a link.
import com.jjrodcast.textkit.editor.core.parser.LinkAttrs

val (linkHref, linkRange) = editor.getLink(selection.start, selection.end)
editor.updateLink(
    selection = selection,
    prevLink = LinkAttrs(href = linkHref.orEmpty()),
    currLink = LinkAttrs(href = "https://example.com"),
)
```

### Example: rendering paragraphs

```kotlin
editor.getParagraphs().forEach { paragraph ->
    paragraph.children.forEach { item ->
        // item.text, item.start, item.end, item.marks, item.decorator
        render(item)
    }
}
```

---

## Notes

- Offsets are indices into the plain-text stream returned by `getText()`; keep your UI selection in the same coordinate space.
- `TextEditorRange` normalizes direction (`min`/`max`), and exposes `length`, `collapsed`, `reversed`, `intersects`, and `contains`.
- The engine is UI-agnostic — the Compose `App()` composable in `:shared` is still the generated starter template and is not yet wired to this engine.
