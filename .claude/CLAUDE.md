# App Overview

This file provides guidance to TextKit a Rich Text Editor for Compose Multiplatform.

## What this is

TextKit — a Kotlin Multiplatform / Compose Multiplatform project targeting Android, iOS, Web (Wasm + JS), and Desktop (JVM). The substance of the codebase is a **rope-backed piece-table rich-text editor engine** living in `shared/src/commonMain/kotlin/com/jjrodcast/textkit/editor/`. The Compose UI (`App.kt`) is still the generated CMP starter template and is not yet wired to the editor engine — treat the editor engine as the real project.

Root Gradle project name is `TextKit`; base package is `com.jjrodcast.textkit`. Some doc comments reference `com.plangrid.pgfoundation` / PlanGrid — that's the legacy origin of the design, not a real dependency.

## Modules

- `:shared` — all shared logic + UI. The editor engine and the `App()` composable live here.
- `:androidApp`, `:desktopApp`, `:webApp` — thin platform entry points that call `App()`.
- `iosApp/` — Xcode project (open in Xcode to run iOS); consumes `:shared` as a static framework named `Shared`.

## Common commands

Run apps:
- Android: `./gradlew :androidApp:assembleDebug`
- Desktop: `./gradlew :desktopApp:run` (hot reload: `./gradlew :desktopApp:hotRun --auto`)
- Web (Wasm): `./gradlew :webApp:wasmJsBrowserDevelopmentRun`
- Web (JS): `./gradlew :webApp:jsBrowserDevelopmentRun`
- iOS: open `iosApp/` in Xcode and run.

Run tests (editor logic lives in `:shared`, so target that module):
- Desktop/JVM: `./gradlew :shared:jvmTest`
- Android host: `./gradlew :shared:testAndroidHostTest`
- Web: `./gradlew :shared:wasmJsTest` / `./gradlew :shared:jsTest`
- iOS sim: `./gradlew :shared:iosSimulatorArm64Test`
- Single test: append `--tests "com.jjrodcast.textkit.ClassName.methodName"` to the JVM/Android task.

Existing test files are placeholder stubs (`assertEquals(3, 1 + 2)`); there is no real test suite for the editor engine yet.

Dependencies are managed via the version catalog at `gradle/libs.versions.toml` — add/upgrade libraries there, not inline in `build.gradle.kts`. Configuration cache and build cache are enabled.

## Editor engine architecture

The layers, from public API down to storage:

1. **`editor/core/TextEditorManager`** — the public entry point. Stateful facade: `load(json, isViewer)`, `getText()`, `toJson()`, `getParagraphs()`, `updateMarks`, `updateColor`, `updateLink`, `getSearchMarkType`, `checkDecorator`, `onDecoratorChange`. Everything below it is `internal`.

2. **`editor/core/transactions/TextEditorTransaction`** (implements `interfaces/TextEditorInitTransaction`) — orchestrates edits. `updateDocument(...)` is the central dispatch: list-item toggles route to `ListItemTransaction`, mark/format changes route to `FormatTransaction`. Owns the piece table instance.

3. **`editor/core/piecetable/RichTextEditorPieceTable` → `RichTextEditorBasePieceTable`** — the piece table. Classic two-buffer design: `originalBuffer` (immutable, from the loaded document) + `addedBuffer` (append-only `StringBuilder`). Each `RichPiece` (in `piecetable/models/`) references a `Source` + offset + length, plus its `marks` and `decorator`. Plain text is cached in `_cachedText` and patched incrementally (`patchCache`) rather than rebuilt on every edit.

4. **`editor/core/piecetable/rope/PieceRope` (+ `RopeNode`)** — a balanced rope is the **single source of truth for the piece sequence**. `RichTextEditorBasePieceTable` is written to keep piece lookups/mutations at O(log P) via rope walks (`findByDocumentOffset`, `forRange`, `findParagraphStartAt`, `splice`, etc.). When editing the base piece table, preserve these complexity guarantees — the extensive comments there document why each walk is fused/single-pass. Avoid reintroducing O(P) scans.

### Document format & conversion

The persisted format is a **ProseMirror-style JSON document** (`type: "doc"` → `content` of block paragraphs → inline text with marks). Parsing/serialization lives in `editor/core/parser/`:

- Block nodes (`Paragraphs.kt`, `Lists.kt`, `Blockquote.kt`): `paragraph`, `heading` (h1–h6 map to fixed font sizes in `HeadingLevelsValues`), `orderedList`, `bulletList`, `taskList`, `blockquote`.
- Inline (`Texts.kt`): `text`, `hardBreak`, `listItem`, `taskItem`.
- Marks (`Marks.kt`): `bold`, `italic`, `underline`, `strike`, `highlight`, `link` (href), `textStyle` (color + fontSize).
- Polymorphic (de)serialization uses custom `JsonContentPolymorphicSerializer`s keyed on the `"type"` discriminator; unknown types fall back to `None`. Use the shared `TEXT_EDITOR_JSON` instance (`parser/Json.kt`).

Converters bridge the two worlds:
- **`converters/TextEditorConverter`** — JSON document → flat `TextEditorModel` list (text + marks), inserting decorator marker characters (list bullets/numbers/checkboxes) and normalizing line breaks so the piece table stores a linear character stream.
- **`converters/PieceTableConverter`** — piece table → JSON document (the inverse; used by `toJson()`).
- **`converters/ListsConverter`** + `converters/utils/` — reconstruct nested list structure from the flat stream.

Line-break handling and decorator markers are load-bearing: paragraphs are delimited by trailing line breaks in the flat stream, and list/task decorators are represented as leading marker pieces. Changes to converters, `getParagraphs`, or the paragraph-walking rope methods must keep these invariants consistent.

## Platform-specific code

`expect`/`actual` declarations are split across source sets (`commonMain` + `androidMain`/`jvmMain`/`iosMain`/`webMain`/`jsMain`/`wasmJsMain`): `Platform.kt`, `editor/utils/Constants.kt`, and `editor/core/transactions/text/TextDecoratorTransaction.kt`. Note `webMain` is a shared intermediate source set for both JS and Wasm targets.
