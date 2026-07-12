package com.jjrodcast.textkit.editor.core.models

import com.jjrodcast.textkit.editor.utils.fastForEach
import kotlinx.serialization.Serializable

@Serializable
internal data class TextEditorDocumentModel(val paragraph: List<TextEditorParagraphModel> = emptyList())

@Serializable
internal data class TextEditorParagraphModel(val styledText: List<TextEditorModel> = emptyList()) {

    fun getParagraphText() = buildString {
        styledText.fastForEach { model ->
            if (!model.isDecorator) {
                append(model.text)
            }
        }
    }
}
