# TextKit Theme

Design tokens for TextKit UI (formatting bar, popups, editor). The theme is light/dark aware and
follows a Material 3–style role system: each color has a **container** role and a matching **`on`
role** for content drawn on top of it. Always pair them (e.g. text/icons over `surface` use
`onSurface`) so contrast holds in both light and dark.

## Accessing the theme

`TextKitTheme` is both a **provider** (function) and an **accessor** (object), like `MaterialTheme`.

```kotlin
// Provide the theme once, near the top of your UI. Reactively switches light/dark.
TextKitTheme(darkTheme = isSystemInDarkTheme()) {
    // ...UI that reads the theme...
}

// Read tokens anywhere inside that scope:
TextKitTheme.colors.primary
TextKitTheme.typography.fontFamily
TextKitTheme.shapes.medium
```

Reading `TextKitTheme.colors` **must** happen inside a `TextKitTheme { }` scope; otherwise
`LocalTextKitTheme` throws (no silent fallback). `TextKitScreen` already wraps its content in the
provider.

Override colors per role via the wrapper. `TextKitColors.light()` / `dark()` expose every role as a
parameter, so you override the ones you want and the rest keep their token defaults:

```kotlin
TextKitTheme(
    darkTheme = isDark,
    colors = TextKitColors.dark(primary = myBrandColor),   // only primary changes; other roles stay default
) { /* ... */ }
```

## Color roles

Defined in [`TextKitColors.kt`](../../shared/src/commonMain/kotlin/com/jjrodcast/textkit/theme/TextKitColors.kt). Each role's default value comes from the token
objects (`TextKitLightTokens` / `TextKitDarkTokens` in the `tokens` package); `TextKitColors.light()`
and `dark()` read those tokens as parameter defaults, so a role falls back to its token unless you
override it.

| Role | Use it for | Content color |
|------|------------|---------------|
| `primary` | Main accent: active/selected states (e.g. the toggled formatting-bar button background, cursor/caret), primary actions | `onPrimary` |
| `primaryContainer` | Softer accent surfaces tied to primary: highlighted chips, selected embed placeholders, subtle emphasis | `onPrimaryContainer` |
| `secondary` | Supporting accent for smaller UI cues (e.g. the expandable-item indicator) | `onSecondary` |
| `highlight` | Background of text carrying the **highlight mark**. A warm amber, deliberately off the teal `primary` family so it stays distinct from the text selection painted over it | `onHighlight` |
| `background` | The editor’s base surface (the `BasicTextField` area) | `onBackground` |
| `surface` | Raised containers: the formatting bar `Card`, popups, tooltips, menus | `onSurface` |
| `surfaceVariant` | Secondary/muted surfaces and muted content: popup sections, placeholder text, disabled backgrounds | `onSurfaceVariant` |
| `outline` | Prominent borders: outlined text fields, focused strokes | — |
| `outlineVariant` | Subtle separators: dividers in the formatting bar, hairlines between rows | — |
| `error` | Error states: validation messages, destructive actions | `onError` |

### Pairing rule

Draw content with the `on*` color of whatever surface it sits on:

```kotlin
Surface(color = TextKitTheme.colors.surface) {
    Text("Hi", color = TextKitTheme.colors.onSurface)          // ✅ contrast holds in light & dark
}
Box(Modifier.background(TextKitTheme.colors.primary)) {
    Icon(..., tint = TextKitTheme.colors.onPrimary)            // ✅
}
```

### Choosing between similar roles

- **`background` vs `surface`** — `background` is the flat editing canvas; `surface` is anything that
  visually sits *above* it (cards, popups). They may share a value but express different intent.
- **`surface` vs `surfaceVariant`** — use `surfaceVariant` for a secondary layer or muted content
  (placeholder/hint text via `onSurfaceVariant`) so it reads quieter than primary content.
- **`outline` vs `outlineVariant`** — `outline` for deliberate borders the user should notice
  (input fields); `outlineVariant` for low-emphasis separators (dividers).
- **`primary` vs `primaryContainer`** — `primary` is a strong fill with light content on top;
  `primaryContainer` is a tinted, low-contrast fill for subtle highlights.
- **`highlight` vs `primaryContainer`** — both are soft fills, but `highlight` is a warm amber for the
  *text highlight mark* specifically, chosen to contrast with the teal selection drawn on top of it;
  `primaryContainer` stays in the teal family for accent chips/embeds. Don't swap them, or a range
  selection over highlighted text becomes hard to see.

### Text highlight

The **highlight mark** (`state.applyHighlight(...)`) paints its background from the `highlight` role by
default, so it adapts to light/dark automatically. The color is applied translucently so a range
selection stays visible over highlighted text. You can pin a fixed highlight color per editor via the
configuration's `highlightColor { … }` (see the main README's *Configuration*), which overrides the
theme; leave it unset to track `highlight`.

## Typography & shapes

- [`TextKitTypography`](../../shared/src/commonMain/kotlin/com/jjrodcast/textkit/theme/TextKitTypography.kt) — `fontFamily`. `default()` loads the bundled Noto Sans
  and accepts a custom `FontFamily` for overrides.
- [`TextKitShapes`](../../shared/src/commonMain/kotlin/com/jjrodcast/textkit/theme/TextKitShapes.kt) — `small` / `medium` / `large` corner shapes.

## Notes

- All values follow the Material 3 tonal scale for TextKit’s teal scheme, so light/dark stay
  coherent. When adding a role, define its value in **both** token objects (`TextKitLightTokens` and
  `TextKitDarkTokens`) and expose it as a parameter in `TextKitColors.light()` / `dark()`.
- Every color must include the alpha channel (`0xFF……`); a value like `0x4A635D` renders fully
  transparent.
