package com.jjrodcast.textkit.editor.core.interfaces

import com.jjrodcast.textkit.editor.core.piecetable.models.Piece

internal interface TextEditor<Document, Model> {

    val pieces: List<Piece>

    val text: String

    fun build(document: Document)

    fun insert(model: Model, offset: Int): Boolean

    fun delete(offset: Int, length: Int): Boolean
}
