# TextKit Editor Engine

A **rope-backed piece-table rich-text editor engine** for Kotlin Multiplatform / Compose Multiplatform, targeting Android, iOS, Web (Wasm + JS), and Desktop (JVM).

The engine lives in `shared/src/commonMain/kotlin/com/jjrodcast/textkit/editor/` and manages the document model, formatting, and (de)serialization. A thin Compose state layer in `shared/src/commonMain/kotlin/com/jjrodcast/textkit/ui/` (`TextKitState`) bridges the engine to a Compose `TextField`, but the engine itself stays UI-agnostic and can be driven directly.

---

## Overview

The engine loads and saves a **ProseMirror-style JSON document** and exposes a small, stateful facade — `TextKitEditorManager` — to query and mutate the document. For Compose apps, `TextKitState` (created via `rememberTextKitState(...)`) wraps the manager with `TextFieldValue` binding, selection tracking, and viewer rendering.

Key characteristics:

- **Piece table storage** — a classic two-buffer design (immutable `originalBuffer` + append-only `addedBuffer`) so edits never copy the whole text.
- **Rope index** — a balanced rope (`PieceRope` / `RopeNode`) is the single source of truth for the piece sequence, keeping lookups and mutations at **O(log P)** in the number of pieces.
- **Rich formatting** — bold, italic, underline, strikethrough, highlight, links, and text style (color + font size), plus block structures: headings (h1–h6), ordered / bullet / task lists, and blockquotes.
- **Incremental plain-text cache** — plain text is cached and patched in place rather than rebuilt on every edit.
- **Compose integration** — `TextKitState` binds the engine to a `BasicTextField`, exposing `textFieldValue`, selection helpers, link info, and a ready-to-render `AnnotatedString` for viewer mode.
- **Configurable** — colors (highlight, link, text) and base font size are supplied via a `TextKitConfiguration`, built with a small DSL.
- **Multiplatform** — pure `commonMain` logic, with a few `expect`/`actual` seams (`Platform.kt`, `utils/Constants.kt`, `transactions/text/TextDecoratorTransaction.kt`).

---

## Internal architecture (`textkit.editor` + `textkit.ui`)

The engine is organized in layers, from the public API down to raw storage. Everything below `TextKitEditorManager` is `internal`.

```
TextKitState                         (ui/)                   ← Compose state holder (optional)
      │
      ▼
TextKitEditorManager                  (editor/core/)          ← public facade
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

### 0. `ui/TextKitState` (Compose layer)

A `@Stable` state holder that wraps a `TextKitEditorManager` and adapts it to Compose. Created with the `rememberTextKitState(json, isViewer, configuration, onUrlClicked)` composable, it is `rememberSaveable`-backed (via a nested `Saver`) and exposes `textFieldValue`, `composition`, `lastMarks` / `lastListItem` (the caret's active formatting), `annotatedStringForViewer`, and the editing entry points — the `applyBold` / `applyItalic` / `applyUnderline` / `applyStrikeThrough` / `applyHighlight` / `applyTextStyle` mark toggles, plus `onTextFieldChange`, `onTextLayout`, and `toJson`. Rendering helpers live in `ui/utils/` (`TextEditorStyles`, `Savers`).

### 1. `core/TextKitEditorManager`

The public entry point (see below). A thin, stateful wrapper that takes a `TextKitConfiguration` and delegates every call to a lazily created `TextEditorTransaction`.

### 2. `core/transactions/TextEditorTransaction`

Implements `interfaces/TextEditorInitTransaction` and owns the piece-table instance. Its `updateDocument(...)` is the central dispatch:

- List/task-item toggles (when `prevListItem != currListItem`) route to **`ListItemTransaction`**.
- Mark / format / link / color changes route to **`FormatTransaction`**.

It also implements loading (`loadWith`, which strips blockquotes when not in viewer mode), serialization (`json`), text access (`text`), paragraph extraction (`getParagraphs`), and mark/decorator/link queries.

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
- `core/transactions/models/` — public value types: `TextEditorSelectedMark` (marks + selected list item), `TextEditorParagraph` / `TextEditorItem`, and the `TextEditorTransactionType` sealed class (`Format`, `Link(href)`, `Color(color?)`).
- `components/TextEditorFormatItems.kt` — format descriptors under a small type hierarchy: `TextEditorFormatItem` → `TextEditorDecoratorItem` → `TextEditorListItem` (`NumberedList`, `BulletedList`, `CheckList`, `None`) and `TextEditorDecorator` (`Blockquote`), plus `TextEditorStyleItem` (`Bold`, `Italic`, `Underline`, `Strikethrough`, `Highlight`, `TextStyle(color, fontSize)`) with `.toMark()`.
- `models/TextKitConfiguration` + `models/TextKitBuilder` — configuration (colors + base font size) and its `createTextKitConfiguration { }` DSL.
- `models/MarkSearchType` — the result of a mark/decorator query over a selection.
- `utils/` — string/list/math/regex helpers, multiplatform `Constants`, `ColorUtils` (`Color.toHex()` / `Color.toHexWithAlpha()`), and `DocumentUtils` (sample ProseMirror JSON fixtures: `complexJsonV1`–`complexJsonV6`, `emptyDocument`).

---

## Configuration

Colors and the base font size are carried by an immutable `TextKitConfiguration`:

```kotlin
data class TextKitConfiguration(
    val highlightColor: Color = Color.Yellow,
    val linkColor: Color = Color(0xFF1B75D0),
    val textColor: Color = Color(0xFF000000),
    val fontSize: Int = 14,
)
```

Build one with the DSL (all fields optional):

```kotlin
import androidx.compose.ui.graphics.Color
import com.jjrodcast.textkit.editor.models.createTextKitConfiguration

val configuration = createTextKitConfiguration {
    textColor { Color.Black }
    highlightColor { Color.Yellow }
    linkColor { Color.Blue }
    fontSize { 16 }          // must be > 0
}
```

---

## Using the engine directly (`TextKitEditorManager`)

`TextKitEditorManager` is the core public class. It is stateful — create one instance per document.

```kotlin
import com.jjrodcast.textkit.editor.core.TextKitEditorManager

val editor = TextKitEditorManager(configuration) // configuration is optional

// Load a ProseMirror-style JSON document.
// isViewer = true keeps blockquotes and other read-only content;
// isViewer = false strips blockquotes for editing.
editor.load(json = documentJson, isViewer = false)

// Read the plain text (with decorator markers / line breaks).
val plainText: String = editor.text

// Serialize the current document back to JSON.
val outJson: String = editor.toJson()
```

> Selections are `androidx.compose.ui.text.TextRange` — offsets index into the plain-text stream returned by `text`.

### API reference

| Member | Description |
| --- | --- |
| `TextKitEditorManager(configuration)` | Create a manager. `configuration` defaults to `createTextKitConfiguration()`. |
| `load(json: String, isViewer: Boolean)` | Parse and load a document. `isViewer = false` strips blockquotes for editing. |
| `text: String` | Current plain-text stream (with decorator markers / line breaks). |
| `toJson(): String` | Serialize the document to ProseMirror-style JSON. |
| `isViewer: Boolean` | Whether the manager was loaded in viewer mode. |
| `getParagraphs(): List<TextEditorParagraph>` | Document as a list of paragraphs, each holding `TextEditorItem`s (text, `start`/`end` offsets, marks, decorator). Ideal for rendering. |
| `getSearchMarkType(selection: TextRange): MarkSearchType` | Marks, list item, range, and text active over a selection. Use it to reflect toolbar state. |
| `getLink(start: Int, end: Int): Pair<String?, TextRange>` | The href (if any) covering a range, plus the range it spans. |
| `checkDecorator(start: Int, end: Int): Pair<Boolean, TextRange>` | Whether the range contains a decorator (list/task marker) and its range. |
| `onDecoratorChange(offset: Int)` | Toggle the decorator at an offset (e.g. check/uncheck a task item). |
| `updateDocument(selection, prevSelectedMark, currSelectedMark, transactionType)` | **Single entry point** for every format change — marks, list items, links, and colors. Returns `Pair<Boolean, TextRange>`: whether the edit applied and the resulting range. |

`updateDocument` dispatches on `transactionType` (`TextEditorTransactionType`):

- **`Format`** (default) — apply the mark/list-item difference between `prevSelectedMark` and `currSelectedMark`.
- **`Link(href)`** — put the `LinkMark` in `currSelectedMark.marks`; an empty href removes the link.
- **`Color(color)`** — set (or clear, with `null`) the text color, preserving the existing font size. `prev`/`curr` marks are ignored and resolved from the selection.

### Example: querying and formatting a selection

```kotlin
import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.components.TextEditorStyleItem
import com.jjrodcast.textkit.editor.core.transactions.models.TextEditorSelectedMark
import com.jjrodcast.textkit.editor.core.transactions.models.TextEditorTransactionType

val selection = TextRange(start = 4, end = 12)

// 1. Inspect what's active on the selection (drive your toolbar with this).
val current = editor.getSearchMarkType(selection)

// 2. Toggle bold on the selection.
val prev = TextEditorSelectedMark(marks = current.marks)
val next = TextEditorSelectedMark(marks = current.marks + TextEditorStyleItem.Bold.toMark())

val (applied, newRange) = editor.updateDocument(
    selection = selection,
    prevSelectedMark = prev,
    currSelectedMark = next,
    transactionType = TextEditorTransactionType.Format,
)
```

### Example: color and links

```kotlin
// Apply a color (hex string); pass null to clear it.
editor.updateDocument(
    selection = selection,
    prevSelectedMark = TextEditorSelectedMark.NONE,
    currSelectedMark = TextEditorSelectedMark.NONE,
    transactionType = TextEditorTransactionType.Color("#E53935"),
)

// Add or update a link (empty href removes it).
import com.jjrodcast.textkit.editor.core.parser.LinkAttrs
import com.jjrodcast.textkit.editor.core.parser.LinkMark

val (currentHref, linkRange) = editor.getLink(selection.start, selection.end)
editor.updateDocument(
    selection = selection,
    prevSelectedMark = TextEditorSelectedMark.NONE,
    currSelectedMark = TextEditorSelectedMark(marks = setOf(LinkMark(LinkAttrs("https://example.com")))),
    transactionType = TextEditorTransactionType.Link("https://example.com"),
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

## Using the Compose layer (`TextKitState`)

For Compose apps, `TextKitState` wraps the manager and binds it to a `BasicTextField`. Create it with `rememberTextKitState(...)`:

```kotlin
import com.jjrodcast.textkit.ui.rememberTextKitState

val configuration = createTextKitConfiguration { /* … */ }

val state = rememberTextKitState(
    json = documentJson,
    isViewer = false,
    configuration = configuration,
    // Fired when a link is tapped (viewer) or the caret / selection lands on a link (editing).
    // `range` is the link's TextRange as reported by the manager.
    onUrlClicked = { url, range -> /* open the link / show link options */ },
)
```

`TextKitState` exposes:

| Member | Description |
| --- | --- |
| `textFieldValue` | The `TextFieldValue` to bind to a `BasicTextField`. |
| `onTextFieldChange(newValue)` | Feed edits back in (auto-detects insert / delete / replace, and applies `lastMarks` to typed text). |
| `onTextLayout(result)` | Forward the `TextLayoutResult` from the text field (needed for caret/link hit-testing). |
| `lastMarks` / `lastListItem` | The marks / list-item type active at the caret. Drive the formatting bar from these. |
| `applyBold`, `applyItalic`, `applyUnderline`, `applyStrikeThrough`, `applyHighlight` | Toggle a mark on the current selection — `apply…(true)` adds it, `apply…(false)` removes it. |
| `applyTextStyle(fontSize, color)` | Set the font size and/or color (`color` is a hex string, or `null` to clear). |
| `composition` | The current composition range. |
| `annotatedStringForViewer` | A pre-built `AnnotatedString` (+ inline task-checkbox content) for viewer/read-only rendering. |
| `toJson()` | Serialize the current document. |

The nested `TextKitState.Saver` persists text, selection, configuration, JSON, and the viewer flag across configuration changes; the non-serializable `onUrlClicked` callback is re-attached on restore by `rememberTextKitState`.

### Example: apply marks from a formatting bar

At the Compose layer you don't build `TextEditorSelectedMark`s by hand, and you don't call `updateDocument` — that method is **private** on `TextKitState`. Use the `apply…` toggles instead; each one adds the mark when `selected` is `true` and removes it when `false`, **preserving the other marks already active** at the selection/caret. When there is no selection, the toggle updates the *stored marks* (`lastMarks`) so the next typed characters inherit the formatting.

> Note: the public `updateDocument(...)` shown in [Using the engine directly](#using-the-engine-directly-textkiteditormanager) belongs to `TextKitEditorManager`, not `TextKitState`. Drop to the manager only when you need links/colors or are driving the engine without Compose.

```kotlin
import com.jjrodcast.textkit.ui.TextKitEditor
import com.jjrodcast.textkit.ui.TextKitFormattingBar
import com.jjrodcast.textkit.ui.TextKitScreen
import com.jjrodcast.textkit.ui.state.rememberTextKitFormattingBarState
import com.jjrodcast.textkit.ui.state.rememberTextKitState

@Composable
fun Editor(documentJson: String) {
    val state = rememberTextKitState(json = documentJson, isViewer = false)
    val barState = rememberTextKitFormattingBarState()

    // Mirror the caret's active formatting into the bar whenever the selection moves.
    LaunchedEffect(state.lastMarks, state.lastListItem) {
        barState.syncFrom(state.lastMarks, state.lastListItem)
    }

    TextKitScreen {
        TextKitFormattingBar(
            barState = barState,
            onBoldClick = { selected -> state.applyBold(selected) },
            onItalicClick = { selected -> state.applyItalic(selected) },
            onUnderlineClick = { selected -> state.applyUnderline(selected) },
            onStrikeThroughClick = { selected -> state.applyStrikeThrough(selected) },
            onHighlightClick = { selected -> state.applyHighlight(selected) },
        )
        TextKitEditor(state = state)
    }
}
```

### Example: change color / font size

```kotlin
// Set the color (hex string) and keep the current font size, or pass null to clear the color.
state.applyTextStyle(fontSize = 18, color = "#E53935")
state.applyTextStyle(fontSize = 18, color = null)
```

> Under the hood every `apply…` call routes to the manager's `updateDocument(...)` with a `Format` transaction (see [Using the engine directly](#using-the-engine-directly-textkiteditormanager)). Reach for the manager API directly only when you need links/colors or when driving the engine without Compose.

---

## Notes

- Offsets are indices into the plain-text stream returned by `text`; keep your UI selection in the same `TextRange` coordinate space.
- `getParagraphs()` / `TextEditorItem` expose `start`/`end` offsets and marks/decorator for custom rendering.
- Links render as plain styled spans in the editable field; `TextKitEditor` hit-tests the pointer against the stored `TextLayoutResult` to show a hand cursor while hovering a link. In viewer mode, `annotatedStringForViewer` uses real `LinkAnnotation`s (hand cursor and click handling come for free).
- The platform entry points (`androidApp`, `desktopApp`, `webApp`, `iosApp`) wire `TextKitEditor` + `TextKitFormattingBar` to a `TextKitState` — see `MainActivity` / `main.kt` for a full sample.
