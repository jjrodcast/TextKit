package com.jjrodcast.textkit.editor.core.interfaces

import com.jjrodcast.textkit.editor.core.models.TextEditorDocumentModel
import com.jjrodcast.textkit.editor.core.models.TextEditorModel
import com.jjrodcast.textkit.editor.models.TextKitConfiguration

internal interface TextEditorInitTransaction : TextEditorGetInfoTransaction<TextEditorModel>,
    TextEditorEditInfoTransaction<TextEditorModel> {

    val text: String

    val json: String

    fun loadWith(initialJson: String, isViewer: Boolean)

    fun fromDocument(document: TextEditorDocumentModel)
}
