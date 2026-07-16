# TextKit Editor Engine

This is an alpha version (work in progress)

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
- **Ready-made UI** — drop-in composables: `TextKitEditor` (the editable field), `TextKitFormattingBar` (bold / italic / underline / strike / highlight, links, ordered & bulleted lists), and `TextKitLinkPopup` — a speech-bubble card with a rounded pointer for adding, editing, and removing links.
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

A `@Stable` state holder that wraps a `TextKitEditorManager` and adapts it to Compose. Created with the `rememberTextKitState(json, isViewer, configuration)` composable, it is `rememberSaveable`-backed (via a nested `Saver`) and exposes `textFieldValue`, `composition`, `lastMarks` / `lastListItem` (the caret's active formatting), `activeLink`, and `annotatedStringForViewer`, plus the editing entry points:

- **Marks** — `applyBold` / `applyItalic` / `applyUnderline` / `applyStrikeThrough` / `applyHighlight` / `applyTextStyle`.
- **Lists** — `toggleOrderedList(selected)` / `toggleUnorderedList(selected)`.
- **Links** — `applyLink()` (opens the popup for the selection, or the word under a collapsed caret), `updateLink` / `updateLinkText` / `removeLink`, plus `activeLink`, `dismissLinkPopup`, and `linkBoundingBox` to drive the popup.
- **Plumbing** — `onTextFieldChange`, `onTextLayout`, `toJson`.

The companion composables `TextKitEditor`, `TextKitFormattingBar`, and `TextKitLinkPopup` (a card anchored to the link, its outline including a rounded pointer aimed at the link) live alongside it in `ui/`. Rendering helpers live in `ui/utils/` (`TextEditorStyles`, `Savers`).

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

### Document format (ProseMirror-style JSON)

`load(json, …)` expects a `type: "doc"` root whose `content` is a list of **block** nodes; blocks hold **inline** nodes (`text`, `hardBreak`), and inline text carries a set of **marks**. The document below exercises every supported node and mark type — headings (`level` 1–6), styled text (bold / italic / underline / strike / highlight, `link`, and `textStyle` with `color` + `fontSize`), a `hardBreak`, ordered / bullet / task lists, and a blockquote:

```json
{
  "type": "doc",
  "content": [
    {
      "type": "heading",
      "attrs": { "level": 1 },
      "content": [{ "type": "text", "text": "TextKit" }]
    },
    {
      "type": "paragraph",
      "content": [
        { "type": "text", "text": "Plain, " },
        { "type": "text", "marks": [{ "type": "bold" }], "text": "bold" },
        { "type": "text", "text": ", " },
        { "type": "text", "marks": [{ "type": "italic" }], "text": "italic" },
        { "type": "text", "text": ", " },
        { "type": "text", "marks": [{ "type": "underline" }], "text": "underline" },
        { "type": "text", "text": ", " },
        { "type": "text", "marks": [{ "type": "strike" }], "text": "strike" },
        { "type": "text", "text": ", " },
        { "type": "text", "marks": [{ "type": "highlight" }], "text": "highlight" },
        { "type": "text", "text": ", and a " },
        {
          "type": "text",
          "marks": [{ "type": "link", "attrs": { "href": "https://github.com/jjrodcast/textkit", "target": "_blank" } }],
          "text": "link"
        },
        { "type": "text", "text": "." },
        { "type": "hardBreak" },
        {
          "type": "text",
          "marks": [{ "type": "textStyle", "attrs": { "color": "#E53935", "fontSize": 18 } }],
          "text": "Red 18sp text after a line break."
        }
      ]
    },
    {
      "type": "orderedList",
      "attrs": { "start": 1 },
      "content": [
        { "type": "listItem", "content": [{ "type": "paragraph", "content": [{ "type": "text", "text": "First" }] }] },
        { "type": "listItem", "content": [{ "type": "paragraph", "content": [{ "type": "text", "text": "Second" }] }] }
      ]
    },
    {
      "type": "bulletList",
      "content": [
        { "type": "listItem", "content": [{ "type": "paragraph", "content": [{ "type": "text", "text": "Bullet one" }] }] },
        { "type": "listItem", "content": [{ "type": "paragraph", "content": [{ "type": "text", "text": "Bullet two" }] }] }
      ]
    },
    {
      "type": "taskList",
      "content": [
        { "type": "taskItem", "attrs": { "checked": true },  "content": [{ "type": "paragraph", "content": [{ "type": "text", "text": "Done" }] }] },
        { "type": "taskItem", "attrs": { "checked": false }, "content": [{ "type": "paragraph", "content": [{ "type": "text", "text": "Todo" }] }] }
      ]
    },
    {
      "type": "blockquote",
      "content": [{ "type": "paragraph", "content": [{ "type": "text", "text": "A quoted paragraph." }] }]
    }
  ]
}
```

Node / mark reference:

| Kind | `type` | Notable `attrs` / fields |
| --- | --- | --- |
| Block | `paragraph` | — |
| Block | `heading` | `level`: 1–6 (map to fixed font sizes) |
| Block | `orderedList` | `start`: first number (default `1`); `content` is `listItem`s |
| Block | `bulletList` | `content` is `listItem`s |
| Block | `taskList` | `content` is `taskItem`s |
| Block | `blockquote` | `content` is block nodes (kept only in `isViewer = true`) |
| Item | `listItem` | `content` is block nodes (usually a `paragraph`) |
| Item | `taskItem` | `attrs.checked`: `Boolean`; `content` is block nodes |
| Inline | `text` | `text`: `String`; optional `marks` |
| Inline | `hardBreak` | soft line break within a block |
| Mark | `bold` / `italic` / `underline` / `strike` / `highlight` | — |
| Mark | `link` | `attrs.href`: `String`, `attrs.target`: `String` |
| Mark | `textStyle` | `attrs.color`: hex `String?`, `attrs.fontSize`: `Int` |

> Unknown `type`s fall back to a `None` node/mark rather than failing to parse. Full, real-world fixtures (with nested lists, mixed colors, etc.) live in `editor/utils/DocumentUtils.kt` as `complexJsonV1`–`complexJsonV6` and `emptyDocument` — pass any of them straight to `load(...)`. Load an empty document with `"{}"`.

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
import com.jjrodcast.textkit.ui.state.rememberTextKitState

val configuration = createTextKitConfiguration { /* … */ }

val state = rememberTextKitState(
    json = documentJson,
    isViewer = false,
    configuration = configuration,
)
```

> The link callback `onUrlClicked` is a parameter of the `TextKitEditor` composable (not of `rememberTextKitState`). It fires with `(url, text, range)` when a link is tapped in viewer mode, or when the caret / selection lands on a link while editing.

`TextKitState` exposes:

| Member | Description |
| --- | --- |
| `textFieldValue` | The `TextFieldValue` to bind to a `BasicTextField`. |
| `onTextFieldChange(newValue)` | Feed edits back in (auto-detects insert / delete / replace, and applies `lastMarks` to typed text). |
| `onTextLayout(result)` | Forward the `TextLayoutResult` from the text field (needed for caret/link hit-testing). |
| `lastMarks` / `lastListItem` | The marks / list-item type active at the caret. Drive the formatting bar from these. |
| `applyBold`, `applyItalic`, `applyUnderline`, `applyStrikeThrough`, `applyHighlight` | Toggle a mark on the current selection — `apply…(true)` adds it, `apply…(false)` removes it. |
| `applyTextStyle(fontSize, color)` | Set the font size and/or color (`color` is a hex string, or `null` to clear). |
| `toggleOrderedList(selected)` / `toggleUnorderedList(selected)` | Convert the paragraph(s) the selection touches to a numbered / bulleted list (`true`) or back to a plain paragraph (`false`). Switches kind in place and works with a collapsed caret. |
| `applyLink()` | Open `TextKitLinkPopup` for the current selection, or for the word under a collapsed caret; pre-fills the URL when that text already has a link. |
| `updateLink(url, range)` | Add / replace the link over `range` (empty `url` removes it). Leaves a collapsed caret at the end and closes the popup. |
| `updateLinkText(newText, url, range)` | Set `url` on `range`, replacing its text with `newText` when it changed. Used by the popup's **Edit** action. |
| `removeLink(range)` | Remove the link over `range` (keeps other marks). |
| `activeLink` | The link currently shown in the popup (`TextKitLinkInfo?`), or `null`. Observe it to render `TextKitLinkPopup`. |
| `dismissLinkPopup()` | Close the popup (clears `activeLink`). |
| `linkBoundingBox(range)` | Local `Rect` of the link, used to anchor the popup. |
| `composition` | The current composition range. |
| `annotatedStringForViewer` | A pre-built `AnnotatedString` (+ inline task-checkbox content) for viewer/read-only rendering. |
| `toJson()` | Serialize the current document. |

The nested `TextKitState.Saver` persists text, selection, configuration, JSON, and the viewer flag across configuration changes.

At the Compose layer you don't build `TextEditorSelectedMark`s by hand, and you don't call `updateDocument` — that method is **private** on `TextKitState`. Use the `apply…` / `toggle…` / link helpers instead; the mark toggles add the mark when `selected` is `true` and remove it when `false`, **preserving the other marks already active** at the selection/caret. When there is no selection, a mark toggle updates the *stored marks* (`lastMarks`) so the next typed characters inherit the formatting.

> Note: the public `updateDocument(...)` shown in [Using the engine directly](#using-the-engine-directly-textkiteditormanager) belongs to `TextKitEditorManager`, not `TextKitState`. Drop to the manager only when you are driving the engine without Compose.

### Example: a full editor (toolbar + editor + link popup)

This mirrors the sample in `sample/TextKitSample.kt`. `TextKitFormattingBar` needs a `selectedColor` (the highlight used behind an active toggle); the link button opens the popup via `applyLink()`, and `TextKitLinkPopup` is overlaid in the **same `Box`** as the editor so it shares its coordinate space and anchors next to the link.

```kotlin
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jjrodcast.textkit.ui.TextKitEditor
import com.jjrodcast.textkit.ui.TextKitFormattingBar
import com.jjrodcast.textkit.ui.TextKitLinkPopup
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
            selectedColor = Color.Yellow,
            onBoldClick = state::applyBold,
            onItalicClick = state::applyItalic,
            onUnderlineClick = state::applyUnderline,
            onStrikeThroughClick = state::applyStrikeThrough,
            onHighlightClick = state::applyHighlight,
            onLinkClick = { state.applyLink() },
            onOrderedListClick = state::toggleOrderedList,
            onBulletedListClick = state::toggleUnorderedList,
        )
        Spacer(Modifier.size(6.dp))
        // The popup overlays the editor (same Box) so it shares coordinates and stays in bounds.
        Box {
            TextKitEditor(
                state = state,
                modifier = Modifier.padding(10.dp),
                onUrlClicked = { url, text, range -> /* open the link / analytics … */ },
            )
            TextKitLinkPopup(
                state = state,
                onEdit = { link -> state.updateLinkText(newText = link.text, url = link.url, range = link.range) },
                onRemove = { link -> state.removeLink(link.range) },
            )
        }
    }
}
```

### Example: links (add / edit / remove) with the popup

`TextKitLinkPopup` renders only while `state.activeLink != null`. It opens two ways:

- **Automatically** when the caret rests (collapsed) on an existing link — a selection *spanning* a link does not open it.
- **On demand** via `state.applyLink()` (wire it to the formatting bar's link button). With a selection it targets that text; with a collapsed caret it targets the word under it.

The popup shows the link **text** and **URL** (both editable) plus **Edit** and **Remove**. Route its callbacks back into the state:

```kotlin
TextKitLinkPopup(
    state = state,
    // Edit commits the (possibly changed) text + URL and leaves a collapsed caret at the end.
    onEdit = { link -> state.updateLinkText(newText = link.text, url = link.url, range = link.range) },
    onRemove = { link -> state.removeLink(link.range) },
    // onClose defaults to state.dismissLinkPopup().
)
```

`TextKitLinkInfo` is the snapshot passed to those callbacks:

```kotlin
data class TextKitLinkInfo(val text: String, val url: String, val range: TextRange)
```

After any of add / edit / remove, the caret is collapsed at the end of the affected text and the popup closes — no lingering selection or handles.

### Example: lists

```kotlin
// selected = true converts the paragraph(s) the selection touches to a list; false removes it.
state.toggleOrderedList(selected = true)     // 1. 2. 3. …
state.toggleUnorderedList(selected = true)   // • • •
state.toggleUnorderedList(selected = false)  // back to a plain paragraph
```

Calling `toggleOrderedList(true)` while the paragraph is a bulleted list switches it to numbered in place (and vice-versa). Both work with a collapsed caret, acting on the caret's paragraph.

### Example: change color / font size

```kotlin
// Set the color (hex string) and keep the current font size, or pass null to clear the color.
state.applyTextStyle(fontSize = 18, color = "#E53935")
state.applyTextStyle(fontSize = 18, color = null)
```

---

## Notes

- Offsets are indices into the plain-text stream returned by `text`; keep your UI selection in the same `TextRange` coordinate space.
- `getParagraphs()` / `TextEditorItem` expose `start`/`end` offsets and marks/decorator for custom rendering.
- Links render as plain styled spans in the editable field; `TextKitEditor` hit-tests the pointer against the stored `TextLayoutResult` to show a hand cursor while hovering a link. In viewer mode, `annotatedStringForViewer` uses real `LinkAnnotation`s (hand cursor and click handling come for free).
- `TextKitLinkPopup` is anchored via `linkBoundingBox(range)`, so it must live in the **same `Box` as `TextKitEditor`** (shared coordinate space). Its `Card` outline includes the rounded pointer as part of one shape — so the background, border and elevation shadow all follow the beak — and it flips above the link when there is no room below. While open it is "pinned" to the link's range, so focusing its own text fields (which makes the editor re-report its selection) does not dismiss it.
- The platform entry points (`androidApp`, `desktopApp`, `webApp`, `iosApp`) wire `TextKitEditor` + `TextKitFormattingBar` to a `TextKitState` — see `MainActivity` / `main.kt` for a full sample.
