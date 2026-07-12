package com.jjrodcast.textkit.editor.core

import com.jjrodcast.textkit.editor.components.TextEditorListItem
import com.jjrodcast.textkit.editor.core.parser.LinkAttrs
import com.jjrodcast.textkit.editor.core.parser.LinkMark
import com.jjrodcast.textkit.editor.core.parser.TextStyleAttrs
import com.jjrodcast.textkit.editor.core.parser.TextStyleMark
import com.jjrodcast.textkit.editor.core.transactions.TextEditorTransaction
import com.jjrodcast.textkit.editor.core.transactions.models.TextEditorRange
import com.jjrodcast.textkit.editor.core.transactions.models.TextEditorSelectedMark
import com.jjrodcast.textkit.editor.core.transactions.models.TextEditorTransactionType
import com.jjrodcast.textkit.editor.models.MarkSearchType

class TextEditorManager {

    internal val transaction by lazy { TextEditorTransaction() }

    fun load(json: String, isViewer: Boolean) {
        transaction.loadWith(json, isViewer)
    }

    fun getText() = transaction.text

    fun toJson() = transaction.json

    val isViewer get() = transaction.isViewer

    fun getLink(start: Int, end: Int) = transaction.getLink(start, end)

    fun updateLink(
        selection: TextEditorRange,
        prevLink: LinkAttrs,
        currLink: LinkAttrs
    ): Pair<Boolean, TextEditorRange> {
        return transaction.updateDocument(
            prevMarks = setOf(LinkMark(prevLink)),
            currMarks = setOf(LinkMark(currLink)),
            prevListItem = TextEditorListItem.None,
            currListItem = TextEditorListItem.None,
            range = selection,
            transactionType = TextEditorTransactionType.Link(currLink.href)
        )
    }

    fun updateMarks(
        selection: TextEditorRange,
        prevSelectedMark: TextEditorSelectedMark,
        currSelectedMark: TextEditorSelectedMark
    ): Pair<Boolean, TextEditorRange> {
        //val prevListItem = if (prevState.listBarItemSelectedIndex >= 0) prevState.listBarItems[prevState.listBarItemSelectedIndex] else TextEditorListItem.None
        // val listItem = if (currState.listBarItemSelectedIndex >= 0) currState.listBarItems[currState.listBarItemSelectedIndex] else TextEditorListItem.None

        return transaction.updateDocument(
            prevMarks = prevSelectedMark.marks,
            currMarks = prevSelectedMark.marks,
            prevListItem = prevSelectedMark.listItemSelected,
            currListItem = currSelectedMark.listItemSelected,
            range = selection,
            transactionType = TextEditorTransactionType.Format
        )
    }

    fun updateColor(
        selection: TextEditorRange,
        color: String?
    ): Pair<Boolean, TextEditorRange> {
        val prevFormatMarks = transaction.getMarksWithType(selection.start, selection.end)

        val formatMarks = if (color != null) {
            val (prevMarks, prevTextStyle) = prevFormatMarks.marks.partition { it !is TextStyleMark }
            val prevTextStyleMark = prevTextStyle.firstOrNull() as? TextStyleMark
            val fontSize = prevTextStyleMark?.attrs?.fontSize ?: TextStyleAttrs.getDefaultFontSize()
            prevMarks
                .plus(TextStyleMark(TextStyleAttrs(color = color, fontSize = fontSize)))
                .toSet()
        } else prevFormatMarks.marks

        return transaction.updateDocument(
            prevMarks = prevFormatMarks.marks,
            currMarks = formatMarks,
            prevListItem = prevFormatMarks.listItem,
            currListItem = prevFormatMarks.listItem,
            range = selection,
            transactionType = TextEditorTransactionType.Format
        )
    }

    fun getSearchMarkType(selection: TextEditorRange): MarkSearchType {
        return transaction.getMarksWithType(selection.min, selection.max)
    }

    fun checkDecorator(start: Int, end: Int) = transaction.containsDecorator(start, end)

    fun getParagraphs() = transaction.getParagraphs()

    fun onDecoratorChange(offset: Int) = transaction.onDecoratorChange(offset)
}
