package com.jjrodcast.textkit.editor.core.interfaces

import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.models.MarkSearchType
import com.jjrodcast.textkit.editor.core.parser.Mark
import com.jjrodcast.textkit.editor.core.piecetable.models.RichPieceTransaction
import com.jjrodcast.textkit.editor.core.transactions.models.TextEditorParagraph
import com.jjrodcast.textkit.editor.models.TextKitConfiguration

internal interface TextEditorGetInfoTransaction<Model> {

    fun getTextAt(offset: Int): Model

    fun getParagraphs(): List<TextEditorParagraph>

    fun getTextInRange(start: Int, end: Int): List<Model>

    fun getLink(start: Int, end: Int, configuration: TextKitConfiguration): Pair<String?, TextRange>

    fun getMarksWithType(start: Int, end: Int, configuration: TextKitConfiguration): MarkSearchType

    fun getTransactionMarks(
        leftModel: Model?,
        centralModel: Model,
        rightModel: Model?,
        offset: Int,
        length: Int,
        marks: Set<Mark>
    ): RichPieceTransaction

    fun containsDecorator(start: Int, end: Int): Pair<Boolean, TextRange>
}
