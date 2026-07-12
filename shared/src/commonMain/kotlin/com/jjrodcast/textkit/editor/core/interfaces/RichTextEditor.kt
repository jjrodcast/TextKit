package com.jjrodcast.textkit.editor.core.interfaces

import com.jjrodcast.textkit.editor.core.models.MultiPieceParagraph
import com.jjrodcast.textkit.editor.core.models.TextEditorDocumentModel
import com.jjrodcast.textkit.editor.core.models.TextEditorModel
import com.jjrodcast.textkit.editor.core.parser.Mark
import com.jjrodcast.textkit.editor.core.piecetable.models.RichPieceTransaction

internal interface RichTextEditor<Document, Model> : TextEditor<Document, Model> {

    val annotatedText: List<Model>

    fun updateMarks(transaction: RichPieceTransaction): Boolean

    fun updateMarks(transactions: List<RichPieceTransaction>): Boolean

    fun getTransactionMarks(
        leftModel: Model?,
        centralModel: Model,
        rightModel: Model?,
        offset: Int,
        length: Int,
        marks: Set<Mark>
    ): RichPieceTransaction

    fun getTextAt(offset: Int): Model

    fun getLineContent(start: Int, end: Int): MultiPieceParagraph

    fun getLineContentWithNeighborListItems(start: Int, end: Int): MultiPieceParagraph
}
