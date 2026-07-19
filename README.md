![TextKit](imgs/text_kit_banner.png)

# TextKit

TextKit is a rope-backed, piece-table rich-text editor engine for **Compose Multiplatform**
(Android, iOS, Desktop/JVM and Web — Wasm & JS). It ships a `TextKitState` holder, ready-made
editor composables, a formatting bar, link popups, a generalized **trigger** system (mentions,
hashtags, slash commands), **embedded blocks** (tables, images and other non-renderable content
as clickable placeholders), full **undo/redo** with coalescing, and lossless (de)serialization
to a **ProseMirror-style JSON document**.

Unlike editors that round-trip through HTML or Markdown, TextKit persists a structured JSON document
(`type: "doc"` → block nodes → inline runs with marks), so styling, lists, links and inline tokens
(mentions, hashtags) survive an exact load → edit → export cycle.


## Table of contents

- [Installation](#installation)
- [Quick start](#quick-start)
- [Editor state](#editor-state)
- [Reading the content](#reading-the-content)
- [Inline styling](#inline-styling)
- [Text style (color & size)](#text-style-color--size)
- [Lists](#lists)
- [Links](#links)
- [Triggers: mentions, hashtags & slash commands](#triggers-mentions-hashtags--slash-commands)
- [Embedded blocks](#embedded-blocks)
- [Undo / redo](#undo--redo)
- [Formatting bar](#formatting-bar)
- [Theming (light & dark)](#theming-light--dark)
- [Configuration](#configuration)
- [Read-only / viewer mode](#read-only--viewer-mode)
- [Document format](#document-format)

## Installation
[![Publish to Maven Central](https://github.com/jjrodcast/TextKit/actions/workflows/publish.yml/badge.svg)](https://github.com/jjrodcast/TextKit/actions/workflows/publish.yml)
![GitHub Release](https://img.shields.io/github/v/release/jjrodcast/textkit)

Text Kit is available on `mavenCentral()`.

Check the latest version

```kotlin
implementation("io.github.jjrodcast:textkit:$version_code")
```

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
    // configuration = ...    // colors, default font size, triggers, viewerMode (see Configuration)
)
```

Read-only (viewer) mode, colors, the default font size and triggers are all set on the
`configuration` — see [Configuration](#configuration) and [Read-only / viewer mode](#read-only--viewer-mode).

Observable properties you can read in composition:

| Property | Type | Description |
|---|---|---|
| `textFieldValue` | `TextFieldValue` | Current rendered text + selection. |
| `lastMarks` | `Set<Mark>` | Marks active at the caret/selection (drives toggle highlights). |
| `lastListItem` | `TextEditorDecoratorItem` | List kind at the caret (numbered / bulleted / task / none). |
| `activeLink` | `TextKitLinkInfo?` | Link under the caret, or `null`. Observe it to show a link popup. |
| `activeTrigger` | `TextKitTrigger?` | The trigger currently being composed (`@`/`#`/`/`…), or `null`. Drives which candidate set the popup shows. |
| `tokenQuery` | `String?` | Text typed after the active trigger char, or `null` when no trigger is being composed. |
| `activeEmbed` | `EmbedInfo?` | The embedded block under the caret/tap, or `null`. Observe it to show an embed popup. |
| `activeColorAnchor` | `Rect?` | Window bounds the color picker is anchored to while open, or `null`. Observe it to show `TextKitColorsPopup`. |
| `currentTextColor` | `Color?` | The selection's current text color, or `null` when it has none. Seeds the color picker's marked swatch. |
| `canUndo` / `canRedo` | `Boolean` | Whether an undo / redo step is available (drives toolbar button enablement). |
| `viewerTextValue` | `Pair<AnnotatedString, Map<String, InlineTextContent>>` | Rendered content for read-only display. |

## Reading the content

```kotlin
// Plain text of the current document:
val plain: String = state.textFieldValue.text

// The full structured document (ProseMirror-style JSON) — use this to persist:
val json: String = state.toJson()
```

`toJson()` is lossless: marks, lists, links and inline tokens (mentions, hashtags) are all preserved.

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

## Triggers: mentions, hashtags & slash commands

A **trigger** is the generalized "type a character to open a popup" mechanism. Every trigger shares
the same detection, query tracking and popup anchoring; they only differ in what picking a suggestion
does:

- **Atomic token triggers** insert an *indivisible inline node* that is persisted to JSON and
  round-trips exactly. `@` **mentions** and `#` **hashtags** are built in. A token behaves as a single
  unit (the caret skips over it, Backspace removes the whole chip) and can carry marks (bold, italic,
  color, …) over the whole chip.
- **Ephemeral command triggers** run an *action* and persist nothing. `/` **slash commands** are built
  in (apply a heading/list, or run any custom callback).

A mention serializes as `{ "type": "mention", "attrs": { "id": "111", "label": "Jorge Rodriguez" } }`
and a hashtag as `{ "type": "hashtag", "attrs": { "id": "1", "label": "kotlin" } }`. The trigger char
(`@`, `#`, `/`) is presentation only — it is never stored in `attrs`.

### 1. Enable the triggers

Register the triggers you want; each trigger's chip color is configurable and each trigger char must
be unique (the builder validates this):

```kotlin
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.jjrodcast.textkit.editor.models.TextKitTrigger
import com.jjrodcast.textkit.editor.models.createTextKitConfiguration

val configuration = remember {
    createTextKitConfiguration {
        addTrigger { TextKitTrigger.TextKitMentionTrigger() }                            // '@' (blue)
        addTrigger { TextKitTrigger.TextKitHashtagTrigger(color = Color(0xFF2E7D32)) }   // '#' (green)
        addTrigger { TextKitTrigger.TextKitSlashTrigger() }                              // '/' commands
    }
}
val state = rememberTextKitState(json = document, configuration = configuration)
```

Observe the in-progress trigger with `state.activeTrigger` and the typed query with `state.tokenQuery`.

### 2. Atomic tokens (@ mentions, # hashtags)

Use a single `TextKitTokenPopup` for every atomic-token trigger and pick the candidate list from the
active trigger. On tap it commits via `state.selectToken(id, label)`, replacing the `<char>query` with
the atomic node. Place it in the same `Box` as the editor:

```kotlin
import com.jjrodcast.textkit.ui.TextKitTokenPopup
import com.jjrodcast.textkit.ui.model.TextKitTokenSuggestion

val users = listOf(
    TextKitTokenSuggestion(id = "111", label = "Jorge Rodriguez"),
    TextKitTokenSuggestion(id = "222", label = "Ada Lovelace"),
)
val tags = listOf(
    TextKitTokenSuggestion(id = "1", label = "kotlin"),
    TextKitTokenSuggestion(id = "2", label = "compose"),
)

Box {
    TextKitEditor(state = state)
    // One popup serves every atomic-token trigger; choose candidates by the active trigger.
    TextKitTokenPopup(state = state) { trigger ->
        when (trigger) {
            is TextKitTrigger.TextKitHashtagTrigger -> tags
            else -> users
        }
    }
}
```

`state.dismissToken()` cancels an in-progress token. The popup accepts an optional `filter`
(default: case-insensitive label match) and ignores ephemeral command triggers (those are served by
`TextKitSlashCommandPopup`, below).

> **Backward compatibility:** the mention-only helpers still work as thin aliases —
> `TextKitMentionPopup(state, candidates)`, `TextKitMentionSuggestion`, `state.selectMention(...)`,
> and `state.dismissMention()`.

### 3. Slash commands (ephemeral)

Slash commands run an action instead of inserting a node. Use `TextKitSlashCommandPopup` with a list
of `TextKitCommand`. Picking one removes the `/query` the user typed and then runs the command:

```kotlin
import com.jjrodcast.textkit.ui.TextKitSlashCommandPopup
import com.jjrodcast.textkit.ui.model.TextKitCommand

val commands = listOf(
    TextKitCommand.heading(1),                     // built-in: apply Heading 1
    TextKitCommand.heading(2, label = "Title 2"),  // built-in with a custom label
    TextKitCommand.bulletList(),                    // built-in: bulleted list
    TextKitCommand.orderedList(),                   // built-in: numbered list
    // Custom: the action receives the live TextKitState — do anything the editor exposes:
    TextKitCommand.custom(id = "date", label = "Insert date") { it.insertText("2026-07-15") },
)

Box {
    TextKitEditor(state = state)
    TextKitTokenPopup(state = state) { /* users / tags */ emptyList() }
    TextKitSlashCommandPopup(state = state, commands = commands)
}
```

**Built-in command factories:** `TextKitCommand.heading(level, label)`,
`TextKitCommand.bulletList(label)`, `TextKitCommand.orderedList(label)`, and
`TextKitCommand.custom(id, label, action)`. The custom `action: (TextKitState) -> Unit` gets the live
state, so it can call `insertText`, `applyHeading`, `toggleOrderedList`, apply marks, or anything else
the editor exposes.

**Related state APIs:** `state.runCommand(command)` (remove `/query` + run the action),
`state.insertText(text)` (insert plain text at the caret), `state.applyHeading(level)` (apply an H1–H6
font size).

## Embedded blocks

An **embedded block** is a non-renderable node (a table, an image, a linked document, …) that lives
inline in the text as a single **clickable placeholder**. The editor stores the block's full JSON
verbatim and shows only a one-line label where it sits; tapping the placeholder opens a popup so you
can view, edit or remove it. The block behaves as one atomic unit and its JSON round-trips losslessly.

Insert, update and remove embeds through `TextKitState`:

```kotlin
import androidx.compose.ui.geometry.Offset
import com.jjrodcast.textkit.editor.core.parser.EmbedTypes

// Insert at the caret as a single undo step. `rawJson` is the whole block object as a String.
state.insertEmbed(
    embedType = EmbedTypes.Table,   // "table", "image", "document", or any custom type string
    rawJson = tableJson,
    label = "📊 Table",             // one-line placeholder shown in the editor
)

state.updateActiveEmbed(rawJson = newTableJson) // replace the JSON of the open embed
state.removeActiveEmbed()                        // remove the open embed and close the popup
state.dismissEmbedPopup()                        // just close the popup

state.openEmbedAt(position = Offset(x, y))       // open the popup if a tap hit a placeholder
```

Built-in embed types live in `EmbedTypes`: `Table` (`"table"`), `Image` (`"image"`) and
`Document` (`"document"`). Any other type string works too — TextKit stores and returns it unchanged.

`TextKitEditor` already detects taps on placeholders (it calls `openEmbedAt`), so you only need to
render `TextKitEmbedPopup` in the same `Box` as the editor. It observes `state.activeEmbed` (an
`EmbedInfo` with `range`, `embedType` and `rawJson`) and anchors itself over the placeholder:

```kotlin
import com.jjrodcast.textkit.ui.TextKitEmbedPopup

Box {
    TextKitEditor(state = state)
    TextKitEmbedPopup(state = state) // defaults onClose → dismissEmbedPopup(), onRemove → removeActiveEmbed()
}
```

To let users insert embeds from the `/` menu, wrap `insertEmbed` in a custom slash command:

```kotlin
TextKitCommand.custom(id = "table", label = "Insert table") { it.insertEmbed(EmbedTypes.Table, tableJson, "📊 Table") }
```

**Enabling / disabling embeds.** Set `embedsEnabled { false }` in the configuration (see
[Configuration](#configuration)) to turn the feature off: `insertEmbed` and `openEmbedAt` become
no-ops, so no new embeds can be inserted and tapping an existing placeholder won't open its popup.
Existing embeds still render as placeholders and round-trip losslessly in JSON. To drive it from a UI
button, build two states and swap the one you hand to `rememberTextKitState`, or gate your own insert
button on the same flag.

## Undo / redo

TextKit keeps a bounded edit history (default 100 steps) that restores both the document and the
caret. Consecutive same-kind edits (typing, deleting) **coalesce** into one step and break on word
boundaries; discrete actions (formatting, list toggles, token inserts, embed and link operations)
are each their own step. History is always on — no configuration required — and is cleared on `load`.

```kotlin
state.undo()   // returns false if there is nothing to undo
state.redo()   // returns false if there is nothing to redo

state.canUndo  // observable; use it to enable/disable a toolbar button
state.canRedo
```

`TextKitEditor` and `TextKitEditorOutlined` already handle the keyboard shortcuts: **Ctrl/Cmd+Z**
(undo), **Ctrl/Cmd+Shift+Z** and **Ctrl+Y** (redo). See the formatting bar below for wiring the
toolbar buttons.

## Formatting bar

TextKit ships a ready-made toolbar. Keep a `TextKitFormattingBarState` and sync it from the editor so
the toggles reflect the caret; forward each toolbar action to the matching `state` method:

```kotlin
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import com.jjrodcast.textkit.ui.TextKitFormattingBar
import com.jjrodcast.textkit.ui.TextKitScreen
import com.jjrodcast.textkit.ui.state.rememberTextKitFormattingBarState

// `colors` is the palette exposed as `barState.colors` for the text-color popup (see below);
// it defaults to TextKitPickerPallete.DefaultPallete.
val barState = rememberTextKitFormattingBarState(colors = TextKitPickerPallete.DefaultPallete)

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
        onTextAndColorClick = { state.openColorPicker(it) },
        onOrderedListClick = state::toggleOrderedList,
        onBulletedListClick = state::toggleUnorderedList,
        onUndoClick = { state.undo() },
        onRedoClick = { state.redo() },
        canUndo = state.canUndo,
        canRedo = state.canRedo,
    )
    Box {
        TextKitEditor(state = state)
        TextKitLinkPopup(state = state, /* onEdit / onRemove */)
        TextKitColorsPopup(state = state, colors = barState.colors)
        TextKitTokenPopup(state = state) { /* users / tags by active trigger */ users }
        TextKitSlashCommandPopup(state = state, commands = commands)
    }
}
```

Each `on…Click` receives a `Boolean` (the new toggle value). `TextKitFormattingBarState` exposes
`isBold`, `isItalic`, `isUnderline`, `isStrikethrough`, `isHighlight`, `isLink`, `isNumberedList`,
`isBulletedList`, `isCheckList`.

### Text color popup

The palette button is different: `onTextAndColorClick` is **not** a toggle — it hands you the button's
bounds in window coordinates (`Rect`). Wire it to `state.openColorPicker(bounds)` and render
`TextKitColorsPopup` next to the editor. It follows the same pattern as [Links](#links): the popup is
driven by editor state (`state.activeColorAnchor`), applies the pick back to the document, and marks
the selection's current color (`state.currentTextColor`) so it round-trips both ways.

```kotlin
import com.jjrodcast.textkit.ui.TextKitColorsPopup

TextKitFormattingBar(
    barState = barState,
    selectedColor = Color.Yellow,
    onTextAndColorClick = { bounds -> state.openColorPicker(bounds) }, // open the picker
    // … other on…Click handlers
)

Box {
    TextKitEditor(state = state)
    // Palette to show; a leading "no color" swatch (emits null → reset to default) is always added.
    TextKitColorsPopup(state = state, colors = barState.colors)
}
```

`TextKitColorsPopup` positions itself relative to the button (below it, flipping above when it would
overflow the bottom and clamping horizontally, so it always fits the screen) and closes automatically
once a color is picked. By default it applies the pick with
`state.applyTextColor(hex)` (or resets to the configured color on the "no color" swatch); override
`onColorSelected` / `onClose` to customize. The palette also has a default
(`TextKitPickerPallete.DefaultPallete`), so `colors` is optional.

**Related state APIs:** `state.openColorPicker(bounds)` / `state.dismissColorPicker()` (show/hide),
`state.activeColorAnchor` (observable anchor, non-null while open), `state.applyTextColor(hex)`
(set only the color, keeping the current font size; `null` resets to the default color), and
`state.currentTextColor` (`Color?`, the selection's current color or `null`).

## Theming (light & dark)

TextKit ships a light/dark-aware theme built on a Material 3–style role system. Every TextKit UI
piece — the editor, formatting bar and popups — reads its colors, typography and shapes from
`TextKitTheme`, so the whole editor follows one palette and switches with the system.

| Light | Dark |
|-------|------|
| ![TextKit light theme](imgs/text_kit_light.png) | ![TextKit dark theme](imgs/text_kit_dark.png) |

Wrap your UI in the `TextKitTheme` provider (or use `TextKitScreen`, which already does), then read
tokens through the `TextKitTheme` accessor:

```kotlin
TextKitTheme(darkTheme = isSystemInDarkTheme()) {
    Box {
        TextKitFormattingBar(barState = barState, /* … */)
        TextKitEditor(state = state)
        // popups…
    }
}

// Read tokens anywhere inside that scope:
TextKitTheme.colors.primary
TextKitTheme.typography.fontFamily
TextKitTheme.shapes.medium
```

Override individual color roles without touching the rest — the light/dark factories expose every
role as a parameter that defaults to its token:

```kotlin
TextKitTheme(
    darkTheme = isDark,
    colors = TextKitColors.dark(primary = myBrandColor),   // only primary changes
) { /* … */ }
```

Reading `TextKitTheme.colors` must happen inside a `TextKitTheme { }` scope (there is no silent
fallback). For the full color-role reference, the pairing rule, how to choose between similar roles,
and guidance on customizing typography/shapes, see **[docs/theming](docs/theming/README.md)**.

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
    fontSize { 14 }                                          // default font size
    viewerMode { false }                                     // true = read-only viewer
    embedsEnabled { true }                                   // false = disable inserting/opening embeds
    addTrigger { TextKitTrigger.TextKitMentionTrigger() }    // '@' mentions
    addTrigger { TextKitTrigger.TextKitHashtagTrigger() }    // '#' hashtags
    addTrigger { TextKitTrigger.TextKitSlashTrigger() }      // '/' slash commands
}
```

Pass it to `rememberTextKitState(json = …, configuration = configuration)`. Each trigger's chip color
is configurable via its constructor (e.g. `TextKitMentionTrigger(color = …)`), and each trigger char
must be unique (the builder throws on duplicates). At runtime you can resolve a trigger with
`configuration.triggerFor('#')` (by char) or `configuration.triggerForType("hashtag")` (by node type).

## Read-only / viewer mode

Set `viewerMode { true }` on the configuration to load a document for display without editing, and
render `annotatedStringForViewer` (it includes inline content such as task-list checkboxes):

```kotlin
val configuration = createTextKitConfiguration { viewerMode { true } }
val state = rememberTextKitState(json = document, configuration = configuration)

val (annotated, inlineContent) = state.viewerTextValue
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
        { "type": "mention", "attrs": { "id": "111", "label": "Jorge Rodriguez" } },
        { "type": "hashtag", "attrs": { "id": "1", "label": "kotlin" } }
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

**Inline nodes:** `text`, `hardBreak`, and atomic trigger tokens `mention` and `hashtag` (both with
`attrs.id`, `attrs.label`). Slash (`/`) commands are ephemeral actions and are **not** persisted as
nodes.

**Embedded blocks:** rendered inline as clickable placeholders but persisted as their own block node,
keyed on `type` — built-in kinds are `table`, `image` and `document` (any custom type is preserved).
The block's original JSON is stored verbatim, so it round-trips unchanged:

```json
{ "type": "image", "attrs": { "src": "text_kit_banner" } }
```

**Marks** (on inline runs): `bold`, `italic`, `underline`, `strike`, `highlight`, `link`
(`attrs.href`, `attrs.target`), and `textStyle` (`attrs.color`, `attrs.fontSize`).

Unknown node or mark types are ignored on load, so documents remain forward-compatible.
