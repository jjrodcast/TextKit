package com.jjrodcast.textkit.editor.components

import kotlinx.serialization.Serializable

@Serializable
public data class TextEditorColorModel(val name: String? = null, val value: String? = null) {

    val isValidModel get() = name != null && value != null
}
