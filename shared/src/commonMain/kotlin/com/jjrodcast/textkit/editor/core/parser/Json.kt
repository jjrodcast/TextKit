package com.jjrodcast.textkit.editor.core.parser

import kotlinx.serialization.json.Json

internal val TEXT_EDITOR_JSON: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    useAlternativeNames = true
}
