package com.jjrodcast.textkit.editor.core.transactions

import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.components.TextEditorDecoratorItem
import com.jjrodcast.textkit.editor.core.converters.PieceTableConverter
import com.jjrodcast.textkit.editor.core.converters.TextEditorConverter
import com.jjrodcast.textkit.editor.core.interfaces.TextEditorInitTransaction
import com.jjrodcast.textkit.editor.core.models.TextEditorDocumentModel
import com.jjrodcast.textkit.editor.core.models.TextEditorModel
import com.jjrodcast.textkit.editor.core.parser.BlockquoteType
import com.jjrodcast.textkit.editor.core.parser.LinkMark
import com.jjrodcast.textkit.editor.core.parser.Mark
import com.jjrodcast.textkit.editor.core.parser.TEXT_EDITOR_JSON
import com.jjrodcast.textkit.editor.core.parser.TextEditorDocument
import com.jjrodcast.textkit.editor.core.piecetable.PieceTableSnapshot
import com.jjrodcast.textkit.editor.core.piecetable.RichTextEditorBasePieceTable
import com.jjrodcast.textkit.editor.core.piecetable.RichTextEditorPieceTable
import com.jjrodcast.textkit.editor.core.piecetable.models.RichPieceTransaction
import com.jjrodcast.textkit.editor.models.MarkSearchType
import com.jjrodcast.textkit.editor.core.transactions.lists.ListItemTransaction
import com.jjrodcast.textkit.editor.core.transactions.marks.FormatTransaction
import com.jjrodcast.textkit.editor.core.transactions.models.TextEditorItem
import com.jjrodcast.textkit.editor.core.transactions.models.TextEditorParagraph
import com.jjrodcast.textkit.editor.core.transactions.models.TextEditorTransactionType
import com.jjrodcast.textkit.editor.models.TextKitConfiguration
import com.jjrodcast.textkit.editor.utils.EMPTY_JSON
import com.jjrodcast.textkit.editor.utils.endsWithLineBreak
import com.jjrodcast.textkit.editor.utils.fastForEach

internal class TextEditorTransaction(private val configuration: TextKitConfiguration) :
    TextEditorInitTransaction {

    internal val pieceTable: RichTextEditorBasePieceTable by lazy { RichTextEditorPieceTable() }
    internal var isViewer: Boolean = false

    override val text get() = pieceTable.text

    override val json: String
        get() {
            val document = PieceTableConverter.getNewDocument(pieceTable)
            return if (document.content.isEmpty()) EMPTY_JSON
            else TEXT_EDITOR_JSON.encodeToString(TextEditorDocument.serializer(), document)
        }

    override fun loadWith(
        initialJson: String,
        isViewer: Boolean
    ) {
        this.isViewer = isViewer
        val textEditorDocument =
            TEXT_EDITOR_JSON.decodeFromString(TextEditorDocument.serializer(), initialJson)
        val filteredContent = if (this.isViewer) {
            textEditorDocument.content
        } else {
            textEditorDocument.content.filter { it.type != BlockquoteType.Blockquote }
        }
        val document = TextEditorConverter.getAsTextWithMarks(
            TextEditorDocument(filteredContent),
            configuration
        )
        pieceTable.build(document)
    }

    override fun fromDocument(document: TextEditorDocumentModel) {
        pieceTable.build(document)
    }

    override fun insert(model: TextEditorModel, offset: Int) = pieceTable.insert(model, offset)

    override fun delete(offset: Int, length: Int) = pieceTable.delete(offset, length)

    override fun update(offset: Int, length: Int, model: TextEditorModel): Boolean {
        val resultDelete = pieceTable.delete(offset, length)
        val resultInsert = pieceTable.insert(model, offset)
        return resultDelete && resultInsert
    }

    override fun getTransactionMarks(
        leftModel: TextEditorModel?,
        centralModel: TextEditorModel,
        rightModel: TextEditorModel?,
        offset: Int,
        length: Int,
        marks: Set<Mark>
    ): RichPieceTransaction {
        return pieceTable.getTransactionMarks(
            leftModel,
            centralModel,
            rightModel,
            offset,
            length,
            marks
        )
    }

    override fun updateMarks(
        leftModel: TextEditorModel?,
        centralModel: TextEditorModel,
        rightModel: TextEditorModel?,
        offset: Int,
        length: Int,
        marks: Set<Mark>
    ): Boolean {
        val transaction =
            getTransactionMarks(leftModel, centralModel, rightModel, offset, length, marks)
        return pieceTable.updateMarks(transaction)
    }

    override fun upsertMarks(transactions: List<RichPieceTransaction>) =
        pieceTable.updateMarks(transactions)

    override fun getLink(
        start: Int,
        end: Int,
        configuration: TextKitConfiguration
    ): Pair<String?, TextRange> {
        val result = pieceTable.getLineContent(start, end).getMarksWithType(configuration)
        return when {
            result.hasLink -> {
                val link = result.marks.filterIsInstance<LinkMark>().first().attrs.href
                link to result.range
            }

            else -> null to TextRange(start, end)
        }
    }

    override fun getMarksWithType(
        start: Int,
        end: Int,
        configuration: TextKitConfiguration
    ): MarkSearchType {
        return pieceTable.getLineContent(start, end).getMarksWithType(configuration)
    }

    override fun containsDecorator(start: Int, end: Int): Pair<Boolean, TextRange> {
        val textLength = text.length

        if (start > textLength || end > textLength) {
            return Pair(false, TextRange.Zero)
        }

        val result = pieceTable.getLineContent(start, end).getAllModelsInRange()
            .filter { it.isDecorator }
            .map { it }

        return when (result.size) {
            1 -> {
                val model = result.first()
                val range = TextRange(
                    model.offsetInDocument,
                    model.offsetInDocument + model.piece.length
                )
                Pair(true, range)
            }

            else -> Pair(result.isNotEmpty(), TextRange.Zero)
        }
    }

    override fun getParagraphs(): List<TextEditorParagraph> {
        val paragraphs = arrayListOf<TextEditorParagraph>()
        val children = arrayListOf<TextEditorItem>()
        pieceTable.annotatedText.fastForEach { model ->
            val item = TextEditorItem.from(model)
            if (item.text.endsWithLineBreak()) {
                paragraphs.add(TextEditorParagraph(children.plus(item).toList()))
                children.clear()
            } else {
                children.add(item)
            }
        }
        if (children.isNotEmpty()) {
            paragraphs.add(TextEditorParagraph(children.toList()))
            children.clear()
        }
        return paragraphs
    }

    internal fun getLineContentModels(start: Int, end: Int) =
        getLineContent(start, end).getAllModelsInRange()

    internal fun getLineContent(start: Int, end: Int) = pieceTable.getLineContent(start, end)

    internal fun getLineContentWithNeigborParagraphs(start: Int, end: Int) =
        pieceTable.getLineContentWithNeighborListItems(start, end)

    override fun updateDocument(
        prevMarks: Set<Mark>,
        currMarks: Set<Mark>,
        prevListItem: TextEditorDecoratorItem,
        currListItem: TextEditorDecoratorItem,
        range: TextRange,
        transactionType: TextEditorTransactionType
    ) = when {
        prevListItem != currListItem -> {
            ListItemTransaction.toggleParagraphsToListItems(this, prevListItem, currListItem, range)
        }

        else -> {
            if (range.collapsed && transactionType == TextEditorTransactionType.Format) false to range
            else FormatTransaction.updateDocument(
                this,
                prevMarks,
                currMarks,
                range,
                configuration,
                transactionType
            )
        }
    }

    override fun getTextAt(offset: Int) = pieceTable.getTextAt(offset)

    override fun getTextInRange(start: Int, end: Int) =
        pieceTable.getLineContent(start, end).getAllModelsInRange()

    override fun onDecoratorChange(offset: Int): Boolean {
        val selectedModel =
            pieceTable.getLineContent(offset, offset + 1).getAllModelsInRange().firstOrNull()
                ?: return false
        return pieceTable.updateDecorator(selectedModel)
    }

    internal fun getLastOffset(): Int = pieceTable.getLastOffset()

    /** Captures the document state for undo/redo. O(1). See [RichTextEditorBasePieceTable.snapshot]. */
    internal fun snapshot() = pieceTable.snapshot()

    /** Restores a document state captured with [snapshot]. O(1). */
    internal fun restore(snapshot: PieceTableSnapshot) = pieceTable.restore(snapshot)
}
