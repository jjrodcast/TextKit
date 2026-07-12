package com.jjrodcast.textkit.editor.core.interfaces

import com.jjrodcast.textkit.editor.components.TextEditorDecoratorItem
import com.jjrodcast.textkit.editor.core.parser.Mark
import com.jjrodcast.textkit.editor.core.piecetable.models.RichPieceTransaction
import com.jjrodcast.textkit.editor.core.transactions.models.TextEditorRange
import com.jjrodcast.textkit.editor.core.transactions.models.TextEditorTransactionType

internal interface TextEditorEditInfoTransaction<Model> {

    fun insert(model: Model, offset: Int): Boolean

    fun delete(offset: Int, length: Int): Boolean

    fun update(offset: Int, length: Int, model: Model): Boolean

    fun updateMarks(
        leftModel: Model?,
        centralModel: Model,
        rightModel: Model?,
        offset: Int,
        length: Int,
        marks: Set<Mark>
    ): Boolean

    fun upsertMarks(transactions: List<RichPieceTransaction>): Boolean

    fun updateDocument(
        prevMarks: Set<Mark>,
        currMarks: Set<Mark>,
        prevListItem: TextEditorDecoratorItem,
        currListItem: TextEditorDecoratorItem,
        range: TextEditorRange,
        transactionType: TextEditorTransactionType
    ): Pair<Boolean, TextEditorRange>

    fun onDecoratorChange(offset: Int): Boolean
}
