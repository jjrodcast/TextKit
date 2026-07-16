# TextKit

TextKit is a rope-backed, piece-table rich-text editor engine for **Compose Multiplatform**
(Android, iOS, Desktop/JVM and Web — Wasm & JS). It ships a `TextKitState` holder, ready-made
editor composables, a formatting bar, link and mention popups, and lossless (de)serialization to a
**ProseMirror-style JSON document**.

Unlike editors that round-trip through HTML or Markdown, TextKit persists a structured JSON document
(`type: "doc"` → block nodes → inline runs with marks), so styling, lists, links and mentions
survive an exact load → edit → export cycle.

## Table of contents

- [Quick start](#quick-start)
- [Editor state](#editor-state)
- [Reading the content](#reading-the-content)
- [Inline styling](#inline-styling)
- [Text style (color & size)](#text-style-color--size)
- [Lists](#lists)
- [Links](#links)
- [Mentions](#mentions)
- [Formatting bar](#formatting-bar)
- [Configuration](#configuration)
- [Read-only / viewer mode](#read-only--viewer-mode)
- [Document format](#document-format)

## Quick start

Create a state with `rememberTextKitState` and hand it to `TextKitEditor`:

```kotlin
import androidx.compose.runtime.Composable
import com.jjrodcast.textkit.ui.TextKitEditor
import com.jjrodcast.textkit.ui.state.rememberTextKitState

@Composable
fun MyEditor() {
    // `json` is a ProseMirror-style document. "{}" starts an empty editor.
    val state = rememberTextKitState(json = "{}")

    TextKitEditor(state = state)
}
```

`TextKitEditor` is built on `BasicTextField`; there is also an outlined variant,
`TextKitEditorOutlined`, with the same parameters (`modifier`, `onUrlClicked`, `state`).

## Editor state

`TextKitState` is the single source of truth. Keep one instance per editor and observe its snapshot
state to drive your UI:

```kotlin
val state = rememberTextKitState(
    json = document,          // initial document (ProseMirror-style JSON)
    isViewer = false,         // true = read-only
    // configuration = ...    // colors, default font size, triggers (see Configuration)
)
```

Observable properties you can read in composition:

| Property | Type | Description |
|---|---|---|
| `textFieldValue` | `TextFieldValue` | Current rendered text + selection. |
| `lastMarks` | `Set<Mark>` | Marks active at the caret/selection (drives toggle highlights). |
| `lastListItem` | `TextEditorDecoratorItem` | List kind at the caret (numbered / bulleted / task / none). |
| `activeLink` | `TextKitLinkInfo?` | Link under the caret, or `null`. Observe it to show a link popup. |
| `mentionQuery` | `String?` | Text typed after the mention trigger, or `null` when no mention is being composed. |
| `annotatedStringForViewer` | `Pair<AnnotatedString, Map<String, InlineTextContent>>` | Rendered content for read-only display. |

## Reading the content

```kotlin
// Plain text of the current document:
val plain: String = state.textFieldValue.text

// The full structured document (ProseMirror-style JSON) — use this to persist:
val json: String = state.toJson()
```

`toJson()` is lossless: marks, lists, links and mentions are all preserved.

## Inline styling

Each toggle takes the **desired** on/off value (`true` = apply, `false` = remove) and returns whether
the document changed. With a collapsed caret the change is remembered as a *stored mark*, so the next
typed characters inherit it; with a selection it is applied to the selected range.

```kotlin
state.applyBold(selected = true)
state.applyItalic(selected = true)
state.applyUnderline(selected = true)
state.applyStrikeThrough(selected = true)
state.applyHighlight(selected = true)
```

## Text style (color & size)

`applyTextStyle` sets the font size (in the document's font-size units) and an optional hex color
(`#RRGGBB` / `#AARRGGBB`, or `null` to leave the color unset) over the current selection:

```kotlin
state.applyTextStyle(fontSize = 20, color = "#EC4A41")
state.applyTextStyle(fontSize = 16, color = null) // size only
```

## Lists

Toggle ordered (numbered) and unordered (bulleted) lists over the paragraph(s) the selection touches.
Like the mark toggles, they take the desired state and return whether the document changed:

```kotlin
state.toggleOrderedList(selected = true)   // convert to a numbered list
state.toggleUnorderedList(selected = true) // convert to a bulleted list
state.toggleOrderedList(selected = false)  // back to plain paragraphs
```

The document format also supports **task lists** (checkbox items); these render (including their
checkboxes in viewer mode) when present in the loaded document. The current caret's list kind is
exposed via `state.lastListItem`.

## Links

Links are edited through a small popup. Wire `TextKitLinkPopup` inside the same container as the
editor, and use the state APIs to apply/remove:

```kotlin
import androidx.compose.foundation.layout.Box
import com.jjrodcast.textkit.ui.TextKitEditor
import com.jjrodcast.textkit.ui.TextKitLinkPopup

Box {
    TextKitEditor(
        state = state,
        // Fires when a link is clicked/opened (e.g. open it in a browser):
        onUrlClicked = { url, text, range -> /* ... */ },
    )
    TextKitLinkPopup(
        state = state,
        onEdit = { link -> state.updateLinkText(newText = link.text, url = link.url, range = link.range) },
        onRemove = { link -> state.removeLink(link.range) },
    )
}
```

State APIs:

```kotlin
state.applyLink()                                  // open the link editor for the selection/word
state.updateLink(url = "https://…", range = range) // add/replace a link over a range
state.removeLink(range = range)                    // remove a link
state.dismissLinkPopup()                           // hide the popup
```

`state.activeLink` (a `TextKitLinkInfo` with `text`, `url`, `range`) is non-null while the caret sits
on a link — that is what `TextKitLinkPopup` observes.

## Mentions

Mentions are a first-class, **atomic** inline node: `@Someone` behaves as a single unit (the caret
skips over it, Backspace removes the whole chip) and serializes as:

```json
{ "type": "mention", "attrs": { "id": "111", "label": "Jorge Rodriguez" } }
```

### 1. Enable the trigger

Register a mention trigger in the configuration (default trigger char is `@`):

```kotlin
import androidx.compose.runtime.remember
import com.jjrodcast.textkit.editor.models.TextKitTrigger
import com.jjrodcast.textkit.editor.models.createTextKitConfiguration

val configuration = remember {
    createTextKitConfiguration {
        addTrigger { TextKitTrigger.TextKitMentionTrigger() } // optionally: TextKitMentionTrigger(color = ...)
    }
}
val state = rememberTextKitState(json = document, configuration = configuration)
```

### 2. Show the popup

Provide the candidate list; the popup filters it by `state.mentionQuery` and inserts the chosen
mention on tap. Place it in the same `Box` as the editor:

```kotlin
import com.jjrodcast.textkit.ui.TextKitMentionPopup
import com.jjrodcast.textkit.ui.model.TextKitMentionSuggestion

val people = listOf(
    TextKitMentionSuggestion(id = "111", label = "Jorge Rodriguez"),
    TextKitMentionSuggestion(id = "222", label = "Ada Lovelace"),
)

Box {
    TextKitEditor(state = state)
    TextKitMentionPopup(
        state = state,
        candidates = people,
        // Optional custom matching (default: case-insensitive label match):
        // filter = { suggestion, query -> suggestion.label.contains(query, ignoreCase = true) },
    )
}
```

Under the hood the popup calls `state.selectMention(id, label)`, which replaces the `@query` the user
typed with the atomic mention. `state.dismissMention()` cancels an in-progress mention. Mentions can
also carry marks (bold, italic, color, …) applied over the whole chip.

## Formatting bar

TextKit ships a ready-made toolbar. Keep a `TextKitFormattingBarState` and sync it from the editor so
the toggles reflect the caret; forward each toolbar action to the matching `state` method:

```kotlin
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import com.jjrodcast.textkit.ui.TextKitFormattingBar
import com.jjrodcast.textkit.ui.TextKitScreen
import com.jjrodcast.textkit.ui.state.rememberTextKitFormattingBarState

val barState = rememberTextKitFormattingBarState()

// Mirror the caret's active marks/list into the bar so toggles highlight correctly:
LaunchedEffect(state.lastMarks, state.lastListItem) {
    barState.syncFrom(state.lastMarks, state.lastListItem)
}

TextKitScreen { // MaterialTheme + Scaffold wrapper (optional)
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
    Box {
        TextKitEditor(state = state)
        TextKitLinkPopup(state = state, /* onEdit / onRemove */)
        TextKitMentionPopup(state = state, candidates = people)
    }
}
```

Each `on…Click` receives a `Boolean` (the new toggle value). `TextKitFormattingBarState` exposes
`isBold`, `isItalic`, `isUnderline`, `isStrikethrough`, `isHighlight`, `isLink`, `isNumberedList`,
`isBulletedList`, `isCheckList`.

## Configuration

`createTextKitConfiguration { }` builds the editor configuration with a small DSL:

```kotlin
import androidx.compose.ui.graphics.Color
import com.jjrodcast.textkit.editor.models.TextKitTrigger
import com.jjrodcast.textkit.editor.models.createTextKitConfiguration

val configuration = createTextKitConfiguration {
    highlightColor { Color.Yellow }
    linkColor { Color(0xFF1B75D0) }
    textColor { Color(0xFF000000) }
    fontSize { 14 }                                       // default font size
    addTrigger { TextKitTrigger.TextKitMentionTrigger() } // enable @-mentions
}
```

Pass it to `rememberTextKitState(json = …, configuration = configuration)`. The mention trigger's
color is configurable via `TextKitMentionTrigger(color = …)`.

## Read-only / viewer mode

Set `isViewer = true` to load a document for display without editing, and render
`annotatedStringForViewer` (it includes inline content such as task-list checkboxes):

```kotlin
val state = rememberTextKitState(json = document, isViewer = true)

val (annotated, inlineContent) = state.annotatedStringForViewer
Text(text = annotated, inlineContent = inlineContent)
```

## Document format

TextKit reads and writes a **ProseMirror-style JSON document**: a root `doc` whose `content` is a
list of block nodes; each block holds inline runs, and inline runs carry `marks`.

```json
{
  "type": "doc",
  "content": [
    {
      "type": "paragraph",
      "content": [
        { "type": "text", "text": "Hello " },
        { "type": "text", "marks": [{ "type": "bold" }], "text": "world" },
        { "type": "mention", "attrs": { "id": "111", "label": "Jorge Rodriguez" } }
      ]
    },
    {
      "type": "bulletList",
      "content": [
        { "type": "listItem", "content": [
          { "type": "paragraph", "content": [{ "type": "text", "text": "First item" }] }
        ]}
      ]
    }
  ]
}
```

**Block nodes:** `paragraph`, `heading` (`attrs.level` 1–6), `orderedList`, `bulletList`, `taskList`,
`blockquote`, plus `listItem` / `taskItem` (`attrs.checked`) inside lists.

**Inline nodes:** `text`, `hardBreak`, `mention` (`attrs.id`, `attrs.label`).

**Marks** (on inline runs): `bold`, `italic`, `underline`, `strike`, `highlight`, `link`
(`attrs.href`, `attrs.target`), and `textStyle` (`attrs.color`, `attrs.fontSize`).

Unknown node or mark types are ignored on load, so documents remain forward-compatible.
