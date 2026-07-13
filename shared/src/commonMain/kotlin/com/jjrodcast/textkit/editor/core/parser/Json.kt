package com.jjrodcast.textkit.editor.core.parser

import kotlinx.serialization.json.Json

internal val TEXT_EDITOR_JSON: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    useAlternativeNames = true
    // A `null` value on a non-nullable property that has a default (e.g. `"fontSize": null`) is
    // coerced to that default instead of throwing. Combined with the default on
    // TextStyleAttrs.fontSize this lets documents omit or null-out the font size.
    coerceInputValues = true
}
