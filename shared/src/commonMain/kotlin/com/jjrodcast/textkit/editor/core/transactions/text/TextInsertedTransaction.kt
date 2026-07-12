package com.jjrodcast.textkit.editor.core.transactions.text

import com.jjrodcast.textkit.editor.components.TextEditorListItem
import com.jjrodcast.textkit.editor.core.converters.ListsConverter
import com.jjrodcast.textkit.editor.core.converters.models.PositionalListItem
import com.jjrodcast.textkit.editor.core.converters.utils.PositionalListItemUtils
import com.jjrodcast.textkit.editor.core.converters.utils.createTransactions
import com.jjrodcast.textkit.editor.core.converters.utils.flatten
import com.jjrodcast.textkit.editor.core.models.MultiPieceParagraph
import com.jjrodcast.textkit.editor.core.models.PieceParagraph
import com.jjrodcast.textkit.editor.core.models.TextEditorModel
import com.jjrodcast.textkit.editor.core.parser.LinkMark
import com.jjrodcast.textkit.editor.core.parser.Mark
import com.jjrodcast.textkit.editor.core.piecetable.models.TextDecoratorModel
import com.jjrodcast.textkit.editor.core.piecetable.models.TextDecoratorModel.Companion.createDecoratorString
import com.jjrodcast.textkit.editor.core.piecetable.models.TextDecoratorModel.Companion.toLevel
import com.jjrodcast.textkit.editor.core.piecetable.models.TextDecoratorModel.Companion.toNewDecoratorModel
import com.jjrodcast.textkit.editor.core.transactions.lists.models.TextEditorDecoratorTransactionType
import com.jjrodcast.textkit.editor.core.transactions.lists.models.TextEditorListItemTransaction
import com.jjrodcast.textkit.editor.core.transactions.models.TextEditorAction
import com.jjrodcast.textkit.editor.core.transactions.models.TextEditorRange
import com.plangrid.pgfoundation.texteditor.core.validator.ListItemValidator
import com.plangrid.pgfoundation.texteditor.core.validator.TextInputResult
import com.jjrodcast.textkit.editor.utils.TABS
import com.jjrodcast.textkit.editor.utils.isLineBreak
import com.jjrodcast.textkit.editor.utils.replaceLineBreakWith

internal object TextInsertedTransaction {

    internal fun addText(
        lines: MultiPieceParagraph,
        actionModel: TextEditorAction.TextAdded
    ): Pair<TextEditorRange, List<TextEditorListItemTransaction>> {
        val currentParagraph = lines.paragraphsInSelectedRange.firstOrNull()
        val isAtEndOfParagraph = currentParagraph?.isAtEndOfParagraph(actionModel.offset, actionModel.offset) ?: false
        val updatedSelectedParagraph = if (isAtEndOfParagraph) {
            lines.paragraphs.firstOrNull { it.findPiecesInRange(actionModel.offset, actionModel.offset).isNotEmpty() }
        } else {
            currentParagraph
        }

        val isAddingTextToListItem = updatedSelectedParagraph?.isListItem ?: false

        return if (updatedSelectedParagraph != null && isAddingTextToListItem) {
            insertTextInListParagraph(updatedSelectedParagraph, lines, actionModel)
        } else {
            insertTextInParagraph(updatedSelectedParagraph, actionModel)
        }
    }

    private fun getMarks(preselectedMarks: Set<Mark>, previousItemMarks: Set<Mark>, isNewParagraph: Boolean): Set<Mark> {
        return if (isNewParagraph) {
            preselectedMarks.plus(previousItemMarks)
        } else {
            preselectedMarks
        }
    }

    private fun insertTextInListParagraph(
        paragraph: PieceParagraph,
        lines: MultiPieceParagraph,
        actionModel: TextEditorAction.TextAdded
    ): Pair<TextEditorRange, List<TextEditorListItemTransaction>> {
        val listParagraphs = lines.paragraphs.filter { it.isListItem }
        val filteredParagraphs = MultiPieceParagraph(
            listParagraphs,
            actionModel.selection.start,
            actionModel.selection.end
        )

        val isLineBreak = actionModel.text.isLineBreak()

        return when {
            isLineBreak -> addLineBreakToListItem(paragraph, filteredParagraphs, actionModel)
            paragraph.text.isEmpty() -> insertTextInParagraph(
                actionModel,
                getMarksForInsertion(actionModel.marks, filterLinkMarks = true)
            )

            else -> {
                when (paragraph.piecesInSelectedRange.firstOrNull()?.piece?.decorator) {
                    is TextDecoratorModel.NumberDecoratorModel -> preventTextInsertionOnDecorator(paragraph)
                    is TextDecoratorModel.TaskDecoratorModel -> insertTextOnTaskListDecorator(paragraph, actionModel)
                    else -> insertTextInParagraph(actionModel, getMarksForInsertion(actionModel.marks))
                }
            }
        }
    }

    private fun insertTextInParagraph(
        paragraph: PieceParagraph?,
        actionModel: TextEditorAction.TextAdded
    ): Pair<TextEditorRange, List<TextEditorListItemTransaction>> {
        return when {
            paragraph == null || paragraph.text.isLineBreak() -> insertTextInParagraph(
                actionModel,
                getMarksForInsertion(actionModel.marks, filterLinkMarks = true)
            )

            paragraph.text.isNotEmpty() -> insertTextInNonEmptyParagraph(paragraph, actionModel)
            else -> insertTextInParagraph(actionModel, getMarksForInsertion(actionModel.marks))
        }
    }

    private fun insertTextInNonEmptyParagraph(
        paragraph: PieceParagraph,
        actionModel: TextEditorAction.TextAdded
    ): Pair<TextEditorRange, List<TextEditorListItemTransaction>> {
        // We need to validate if the text added matches with the regex
        val input = matchesListItemPattern(paragraph, actionModel)
        // It matches the regex, so we need to insert the decorator, otherwise just add the text
        return if (input != null) {
            val updateTransaction = TextDecoratorTransaction.getUpdateDecoratorTransaction(input, paragraph, actionModel)
            // TODO: Reorder elements on numbered lists.
            val modelLength = when (val type = updateTransaction.type) {
                is TextEditorDecoratorTransactionType.Delete -> 0
                is TextEditorDecoratorTransactionType.Insert -> type.model.text.length
                is TextEditorDecoratorTransactionType.Update -> type.model.text.length
            }
            Pair(
                TextEditorRange(paragraph.startOffset + modelLength),
                listOf(updateTransaction)
            )
        } else {
            val finalMarks = getMarksForInsertion(actionModel.marks)
            insertTextInParagraph(actionModel, finalMarks)
        }
    }

    private fun insertTextInParagraph(
        actionModel: TextEditorAction.TextAdded,
        marks: Set<Mark>
    ): Pair<TextEditorRange, List<TextEditorListItemTransaction>> {
        val model = TextEditorModel.create(text = actionModel.text, marks = marks, decorator = null)
        val transaction = TextTransactionsUtils.insertTransaction(actionModel.offset, model)
        val rangeOffset = actionModel.offset + actionModel.text.length

        return Pair(TextEditorRange(rangeOffset), listOf(transaction))
    }

    private fun addLineBreakToListItem(
        currentParagraph: PieceParagraph,
        lines: MultiPieceParagraph,
        actionModel: TextEditorAction.TextAdded
    ): Pair<TextEditorRange, List<TextEditorListItemTransaction>> {
        val listItems = ListsConverter.fromPieceMultiParagraph(lines)
        val currentDecorator = currentParagraph.startPiece.decorator
        val currentIndex = PositionalListItemUtils.findItemByOffset(listItems, currentParagraph.startOffset)?.index ?: 0

        return if (currentParagraph.text.isEmpty() || currentParagraph.text.isLineBreak()) {
            addLineBreakOnEmptyListItem(currentParagraph, currentIndex, lines, currentDecorator, actionModel)
        } else {
            addLineBreakOnNonEmptyListItem(currentIndex, listItems, currentDecorator, actionModel)
        }
    }

    /**
     * This function adds a line break on a paragraph that only has a decorator
     *
     * When adding a line break over a paragraph that only contains a decorator,
     * we should not insert the line break, but rather remove the decorator from the current paragraph.
     *
     * @return A Pair with the new cursor position and a list of transactions [TextEditorListItemTransaction].
     */
    private fun addLineBreakOnEmptyListItem(
        currentParagraph: PieceParagraph,
        currentIndex: Int,
        lines: MultiPieceParagraph,
        currentDecorator: TextDecoratorModel?,
        actionModel: TextEditorAction.TextAdded
    ): Pair<TextEditorRange, List<TextEditorListItemTransaction>> {
        val transactions = mutableListOf<TextEditorListItemTransaction>()

        val positionalListItems = PositionalListItemUtils.decreaseLevels(lines, listOf(currentIndex))

        val flattenItems = when (currentDecorator) {
            is TextDecoratorModel.NumberDecoratorModel -> PositionalListItemUtils.reorderItems(positionalListItems)
            is TextDecoratorModel.BulletDecoratorModel -> PositionalListItemUtils.reorderItems(positionalListItems, coerceLevel = false)

            else -> positionalListItems
        }
        transactions.addAll(flattenItems.createTransactions())

        val length = currentDecorator.createDecoratorString().length
        val nextLevelTabsLength = if (currentParagraph.startPiece.decorator.toLevel() > 1) TABS.length else length
        val rangeOffset = actionModel.offset - nextLevelTabsLength

        return Pair(TextEditorRange(rangeOffset), transactions)
    }

    /**
     * This function adds a line break on a non empty list item.
     * We need to create a decorator that will appear in the next paragraph before the line break and then reorder the next items if needed.
     *
     * @return A Pair with the new cursor position and a list of transactions [TextEditorListItemTransaction].
     */
    private fun addLineBreakOnNonEmptyListItem(
        currentIndex: Int,
        listItems: List<PositionalListItem>,
        currentDecorator: TextDecoratorModel?,
        actionModel: TextEditorAction.TextAdded
    ): Pair<TextEditorRange, List<TextEditorListItemTransaction>> {
        val transactions = mutableListOf<TextEditorListItemTransaction>()
        val currentDecoratorCount = (currentDecorator?.toCount() ?: 1).plus(1)
        val text = currentDecorator.toNewDecoratorModel(currentDecoratorCount).createDecoratorString()
        val newDecoratorMarksModel =
            TextEditorModel.create(text = text, decorator = currentDecorator.toNewDecoratorModel(currentDecoratorCount))
        val textMarksModel = TextEditorModel.create(text = actionModel.text, marks = actionModel.marks)

        val nextListItems = PositionalListItemUtils.findSameLevelItems(listItems.flatten(), currentIndex)
        val nextListItemsTransactions = updateNextParagraphs(nextListItems)

        transactions.addAll(nextListItemsTransactions)
        transactions.add(TextTransactionsUtils.insertTransaction(actionModel.offset, newDecoratorMarksModel))
        transactions.add(TextTransactionsUtils.insertTransaction(actionModel.offset, textMarksModel))

        val length = currentDecorator.createDecoratorString().length
        val newRange = TextEditorRange(actionModel.offset + actionModel.text.length + length)

        return Pair(newRange, transactions)
    }

    private fun preventTextInsertionOnDecorator(paragraph: PieceParagraph): Pair<TextEditorRange, List<TextEditorListItemTransaction>> {
        val offset = paragraph.startOffset + paragraph.startPiece.length + 1
        return Pair(TextEditorRange(offset), emptyList())
    }

    private fun getMarksForInsertion(marks: Set<Mark>, filterLinkMarks: Boolean = false): Set<Mark> {
        return if (filterLinkMarks) {
            marks.filterNot { it is LinkMark }.toSet()
        } else {
            marks
        }
    }

    /**
     * Validates if the input text matches the decorators regex defined in [ListItemValidator].
     * If it matches the expected regex it returns a [TextInputResult] with the new decorator marks model otherwise null
     */
    private fun matchesListItemPattern(currentParagraph: PieceParagraph, actionModel: TextEditorAction.TextAdded): TextInputResult? {
        val paragraphText = if (currentParagraph.startPiece.decorator is TextDecoratorModel.TaskDecoratorModel) {
            currentParagraph.startText
        } else {
            currentParagraph.text
        }

        val startOfsset = currentParagraph.startOffset
        val insertOffset = actionModel.offset - startOfsset
        val finalText = buildString {
            append(paragraphText, 0, insertOffset)
            append(actionModel.text)
            append(paragraphText, insertOffset, paragraphText.length)
        }.replaceLineBreakWith("")

        return if (currentParagraph.startPiece.decorator == null || currentParagraph.startPiece.decorator is TextDecoratorModel.TaskDecoratorModel) {
            ListItemValidator.validateInput(finalText)
        } else {
            null
        }
    }

    private fun updateNextParagraphs(paragraphs: List<PositionalListItem>): List<TextEditorListItemTransaction> {
        return paragraphs
            .filter { it.type == TextEditorListItem.NumberedList }
            .map {
                val count = (it.richPiece.decorator?.toCount() ?: 1) + 1
                val newDecorator = it.richPiece.decorator.toNewDecoratorModel(count)
                val text = newDecorator.createDecoratorString()
                val model = TextEditorModel.create(text = text, decorator = newDecorator)
                val type = TextEditorDecoratorTransactionType.Update(model, it.richPiece.length)
                TextEditorListItemTransaction(it.offsetInDocument, type)
            }
    }

    /**
     * This function insert text on a decorator of type task
     *
     * There are 2 possible scenarios, the first one, when we have a paragraph like this:
     * -[] Hello world
     * This is a representation for a un unchecked task decorator, so the user can convert this to a checked task like this:
     * -[x] Hello world
     * In this case we need to replace the uncheck decorator with a checked decorator due to a match with a regex decorator.
     *
     * The other scenarios is when we have a task decorator checked like the following:
     * -[x] Hello world
     * and the user insert any character inside the decorator like this:
     * - [xp] Hello world
     * In this case we need to replace the decorator with a simple text due to the new text doesn't match decorators regex.
     *
     * @return A Pair with the new cursor position and a list of transactions [TextEditorListItemTransaction].
     */
    private fun insertTextOnTaskListDecorator(
        paragraph: PieceParagraph,
        actionModel: TextEditorAction.TextAdded
    ): Pair<TextEditorRange, List<TextEditorListItemTransaction>> {
        val listItemValidationResult = matchesListItemPattern(paragraph, actionModel)

        return if (listItemValidationResult != null) {
            val transaction = TextTransactionsUtils.updateTransaction(
                paragraph.startOffset,
                listItemValidationResult.model,
                paragraph.startPiece.length
            )
            val length = listItemValidationResult.model.text.length
            Pair(TextEditorRange(paragraph.startOffset + length), listOf(transaction))
        } else {
            // Insert decorator content as text plus inserted text
            val cursorPosition = actionModel.offset - paragraph.startOffset
            val finalText = buildString {
                append(paragraph.startText, 0, cursorPosition)
                append(actionModel.text)
                append(paragraph.startText, cursorPosition, paragraph.startText.length)
            }

            val newModel = TextEditorModel.create(text = finalText)
            val updateTransaction = TextTransactionsUtils.updateTransaction(
                paragraph.startOffset,
                newModel,
                paragraph.startPiece.length
            )

            Pair(TextEditorRange(paragraph.startOffset + finalText.length), listOf(updateTransaction))
        }
    }
}
