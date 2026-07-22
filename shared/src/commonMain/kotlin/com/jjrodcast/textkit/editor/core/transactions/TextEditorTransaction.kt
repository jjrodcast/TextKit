package com.jjrodcast.textkit.editor.core.transactions

import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.components.TextEditorDecoratorItem
import com.jjrodcast.textkit.editor.components.TextEditorListItem
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
            val current = document
            return if (current.content.isEmpty()) EMPTY_JSON
            else TEXT_EDITOR_JSON.encodeToString(TextEditorDocument.serializer(), current)
        }

    /**
     * The current document as a parsed AST. Exposed so export formats other than JSON (see
     * `editor.core.export.DocumentSerializer`) can walk the same tree the JSON encoder uses.
     */
    internal val document: TextEditorDocument
        get() = PieceTableConverter.getNewDocument(pieceTable)

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
        val base = pieceTable.getLineContent(start, end).getMarksWithType(configuration)
        // For a collapsed caret the line-content walk resolves to the paragraph that ENDS at the
        // caret, so a caret sitting right after a list item's trailing line break inherits that
        // item's list type — even when it actually rests on the following (often empty) paragraph.
        // The forward paragraph is NOT present in that walk's data (data.size == 1 at the boundary),
        // so it can't be derived in-layer; re-derive the list type from the paragraph that STARTS at
        // the caret (peek one char forward) so an empty paragraph after a list is not marked as a
        // list, while the start of a following list item still is.
        if (start != end) return base
        return base.copy(listItem = forwardListItemAt(start))
    }

    /**
     * List type of the paragraph the collapsed caret at [offset] belongs to, resolved forward: the
     * first piece of the range `[offset, offset + 1)` carries its paragraph's type (a bare line-break
     * piece for an empty paragraph yields [TextEditorListItem.None]; a list item's decorator yields
     * its list type). At the document end the range collapses and falls back to the last paragraph.
     */
    private fun forwardListItemAt(offset: Int): TextEditorDecoratorItem {
        val end = (offset + 1).coerceAtMost(text.length)
        return pieceTable.getLineContent(offset, end)
            .firstParagraphTypeInRange()
            ?: TextEditorListItem.None
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
