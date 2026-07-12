package com.jjrodcast.textkit.editor.core.converters

import com.jjrodcast.textkit.editor.core.converters.models.PositionalParagraph
import com.jjrodcast.textkit.editor.core.interfaces.RichTextEditor
import com.jjrodcast.textkit.editor.core.models.TextEditorDocumentModel
import com.jjrodcast.textkit.editor.core.models.TextEditorModel
import com.jjrodcast.textkit.editor.core.models.TextEditorModel.Companion.getKey
import com.jjrodcast.textkit.editor.core.models.TextEditorParagraphModel
import com.jjrodcast.textkit.editor.core.parser.BaseParagraph
import com.jjrodcast.textkit.editor.core.parser.BaseText
import com.jjrodcast.textkit.editor.core.parser.BulletedList
import com.jjrodcast.textkit.editor.core.parser.EmptyDocument
import com.jjrodcast.textkit.editor.core.parser.EmptyParagraph
import com.jjrodcast.textkit.editor.core.parser.ListAttrs
import com.jjrodcast.textkit.editor.core.parser.ListItem
import com.jjrodcast.textkit.editor.core.parser.OrderedList
import com.jjrodcast.textkit.editor.core.parser.Paragraph
import com.jjrodcast.textkit.editor.core.parser.TaskList
import com.jjrodcast.textkit.editor.core.parser.TaskListAttrs
import com.jjrodcast.textkit.editor.core.parser.TaskListItem
import com.jjrodcast.textkit.editor.core.parser.Text
import com.jjrodcast.textkit.editor.core.parser.TextEditorDocument
import com.jjrodcast.textkit.editor.core.piecetable.models.TextDecoratorModel
import com.jjrodcast.textkit.editor.core.piecetable.models.TextDecoratorModel.BulletDecoratorModel.Companion.BULLETED_LIST_KEY
import com.jjrodcast.textkit.editor.core.piecetable.models.TextDecoratorModel.NumberDecoratorModel
import com.jjrodcast.textkit.editor.core.piecetable.models.TextDecoratorModel.NumberDecoratorModel.Companion.NUMBERED_LIST_KEY
import com.jjrodcast.textkit.editor.core.piecetable.models.TextDecoratorModel.TaskDecoratorModel
import com.jjrodcast.textkit.editor.core.piecetable.models.TextDecoratorModel.TaskDecoratorModel.Companion.TASK_LIST_KEY
import com.jjrodcast.textkit.editor.utils.endsWithLineBreak
import com.jjrodcast.textkit.editor.utils.fastForEach
import com.jjrodcast.textkit.editor.utils.fastMap
import com.jjrodcast.textkit.editor.utils.fastMapIndexed
import com.jjrodcast.textkit.editor.utils.isLineBreak
import com.jjrodcast.textkit.editor.utils.removeLineBreakSuffix

internal object PieceTableConverter {

    /**
     * Creates the new document representation based on the PieceTable information.
     * The final result is a [TextEditorDocument].
     *
     * @param pieceTable The piece table containing the annotated text and structure of the document.
     * @return A [TextEditorDocument] with the full document tree including paragraphs and nested lists.
     */
    internal fun getNewDocument(pieceTable: RichTextEditor<TextEditorDocumentModel, TextEditorModel>): TextEditorDocument {

        val paragraphModels = convertPieceTableToParagraphs(pieceTable)
        val localParagraphs = convertParagraphsToLocalParagraph(paragraphModels)

        // 3. Create the nested levels of Paragraphs
        val listParagraphs = createNestedLists(localParagraphs)

        // 4. Merge normal paragraphs and list of paragraphs.
        // Both sub-lists are already sorted by .index (document order), so a linear merge
        // replaces the previous O(L log L) sortedBy with an O(L) two-pointer pass.
        val finalParagraphs = mergeByIndex(localParagraphs.filter { it.level == 0 }, listParagraphs)

        // 5. Create the final TextEditorDocument
        return createFinalDocument(finalParagraphs)
    }

    /**
     * This method takes the entire Piece Table content and creates the Paragraphs needed for the document representation.
     * A pragraphs is made up of many text with styles, the styles are also called `marks` [TextEditorModel].
     *
     * At the end the result will be the paragraphs that are shown in the UI, but at this point they are now well-formed because
     * of list items, these types of elements need a special treatment to convert into a [BaseParagraph] model.
     *
     * @param pieceTable The piece table to read annotated text from.
     * @return A flat list of [TextEditorParagraphModel] representing each line of the document.
     */
    private fun convertPieceTableToParagraphs(pieceTable: RichTextEditor<TextEditorDocumentModel, TextEditorModel>): List<TextEditorParagraphModel> {
        return createInternalParagraphs(pieceTable.annotatedText)
    }

    /**
     * This method creates each paragraph based on [TextEditorPieceTable].
     *
     * @param models The flat list of [TextEditorModel] elements representing all text pieces.
     * @return A list of [TextEditorParagraphModel] grouped by line break boundaries.
     */
    private fun createInternalParagraphs(models: List<TextEditorModel>): List<TextEditorParagraphModel> {
        val paragraphs = arrayListOf<TextEditorParagraphModel>()
        val texts = arrayListOf<TextEditorModel>()
        models.fastForEach { model ->
            texts.add(model)
            if (model.text.endsWithLineBreak()) {
                paragraphs.add(TextEditorParagraphModel(texts.toList()))
                texts.clear()
            }
        }

        when (texts.count { it.text.isNotEmpty() }) {
            0 -> texts.clear()
            else -> {
                paragraphs.add(TextEditorParagraphModel(texts.toList()))
                texts.clear()
            }
        }
        return paragraphs
    }

    /**
     * This method convert from normal list of [TextEditorParagraphModel] to list of [PositionalParagraph], because this structure will
     * allow us to create group of elements for lists ([OrderedList], [BulletedList], [TaskList]). We use the positional elements and
     * this class is similar to a LinkedList node.
     *
     * @param paragraphModels The flat list of [TextEditorParagraphModel] to convert.
     * @return A list of [PositionalParagraph] with level and nesting metadata assigned.
     * @see PositionalParagraph
     */
    private fun convertParagraphsToLocalParagraph(paragraphModels: List<TextEditorParagraphModel>): List<PositionalParagraph> {
        var nestedCountValue: Pair<Int, Int> = Pair(0, 0)

        return paragraphModels.fastMapIndexed { index, paragraph ->
            val firstItem = paragraph.styledText.firstOrNull()
            val hasDecorator = firstItem?.isDecorator ?: false

            if (nestedCountValue.second > 0) {
                nestedCountValue = Pair(nestedCountValue.first, nestedCountValue.second - 1)
                PositionalParagraph(
                    index = index,
                    level = nestedCountValue.first,
                    key = firstItem.getKey(),
                    textStyled = paragraph.styledText
                )
            } else if (!hasDecorator) {
                PositionalParagraph(
                    index = index,
                    level = 0,
                    key = firstItem.getKey(),
                    textStyled = paragraph.styledText
                )
            } else {
                nestedCountValue = when (val decorator = firstItem.piece.decorator) {
                    is TaskDecoratorModel -> {
                        if (decorator.nestedCount > 0) {
                            Pair(decorator.level + 1, decorator.nestedCount - 1)
                        } else {
                            nestedCountValue
                        }
                    }

                    else -> nestedCountValue
                }
                PositionalParagraph(
                    index = index,
                    level = firstItem.piece.decorator?.level ?: 0,
                    key = firstItem.getKey(),
                    textStyled = paragraph.styledText.filter { !it.text.isLineBreak() }
                )
            }
        }
    }

    /**
     * This methos creates as list of [PositionalParagraph] only for the list items, this doesn't work for normal [Paragraph],
     * because [Paragraph] are flat elements and does not contain nested [Paragraph]s.
     *
     * Example:
     *  Imagine we have a list of elements that each one represents a level.
     *  Elements: [1, 1, 2, 3, 1, 2, 2, 2, 3, 1]
     *
     *  In the example above we know that each (1) represent a root level, so the first step will return add all the 1's into the final list
     *
     *  Iteration 1: We store the 1's and the position in the array.
     *
     *  [1, 0]
     *  [1, 1]
     *  [1, 4]
     *  [1, 9]
     *
     *  Iteration 2: We store the 2's in the corresponding list. As you can see the nested elements always belong to greater index of
     *               the parent list. This is the pattern we need to follow for the next iterations.
     *
     *  [1, 0]
     *  [1, 1]
     *       [2, 2]
     *  [1, 4]
     *       [2, 5]
     *       [2, 6]
     *       [2, 7]
     *  [1, 9]
     *
     *  Iteration 3: We store the 3's in the corresponding list.
     *
     *  [1, 0]
     *  [1, 1]
     *       [2, 2]
     *            [3, 3]
     *  [1, 4]
     *       [2, 5]
     *       [2, 6]
     *       [2, 7]
     *            [3, 8]
     *  [1, 9]
     *
     *
     *  In the Iteration 3 we have the final representation, so we know that we will have the number of 1's as size of the final list
     *  with nested elements.
     *
     * Builds the nested list tree from a flat list of [PositionalParagraph] in document order.
     *
     * Uses a stack to track the active path from root to the deepest open node.
     *
     * Stack invariant: entries are in strictly ascending level order.
     * Because elements arrive in document order, any stack entry whose level is >= the
     * current element's level can never become a parent again and is popped immediately.
     * Children appended to [stack.last] are directly visible in the tree since each stack
     * entry is the same object already stored in its parent's [positionalParagraphs] list.
     *
     * Complexity: O(L) — each element is pushed and popped at most once.
     *
     * @param elements The full flat list of [PositionalParagraph] including both root and nested items.
     * @return A list of root-level [PositionalParagraph] nodes with their children already attached.
     */
    private fun createNestedLists(elements: List<PositionalParagraph>): List<PositionalParagraph> {
        val localElements = elements.filter { it.level > 0 }
        if (localElements.isEmpty()) return emptyList()

        val roots = arrayListOf<PositionalParagraph>()
        val stack = ArrayDeque<PositionalParagraph>()

        for (element in localElements) {
            val node = element.copy(positionalParagraphs = arrayListOf())
            while (stack.isNotEmpty() && stack.last().level >= node.level) {
                stack.removeAt(stack.size - 1)
            }
            if (stack.isEmpty()) {
                roots.add(node)
            } else {
                stack.last().positionalParagraphs.add(node)
            }
            stack.addLast(node)
        }

        return roots
    }

    /**
     * This is the final step that creates a new [TextEditorDocument] that will contain the latest changes from the Piece Table.
     *
     * @param elements The merged list of root-level [PositionalParagraph] nodes in document order.
     * @return A [TextEditorDocument] built from the given elements, or [EmptyDocument] if the list is empty.
     */
    private fun createFinalDocument(elements: List<PositionalParagraph>): TextEditorDocument {
        if (elements.isEmpty()) return EmptyDocument

        val nodes = createNodeIndices(elements)
        val partialContent = nodes.dropLast(1).fastMapIndexed { index, value ->
            createParagraphModel(
                elements = elements,
                startIndex = value,
                endIndex = nodes[index + 1]
            )
        }
        val lastContent = createParagraphModel(
            elements = elements,
            startIndex = nodes.last(),
            endIndex = elements.size
        )
        return TextEditorDocument(partialContent + lastContent)
    }

    /**
     * This method creates the [BaseParagraph]s that will be inserted in the final [TextEditorDocument] as content.
     * We take all the [PositionalParagraph]s to create [Paragraph] or any kind of list ([OrderedList], [BulletedList], [TaskList]).
     *
     * @param elements The full list of [PositionalParagraph] elements.
     * @param startIndex The index of the first element belonging to this paragraph or list group.
     * @param endIndex The exclusive index marking the end of the group.
     * @return A [BaseParagraph] representing either a plain [Paragraph] or a list node.
     */
    private fun createParagraphModel(
        elements: List<PositionalParagraph>,
        startIndex: Int,
        endIndex: Int
    ): BaseParagraph {
        val item = elements[startIndex]
        val isDecorator = item.textStyled.firstOrNull()?.isDecorator ?: false
        return if (isDecorator) {
            when (item.key) {
                NUMBERED_LIST_KEY -> {
                    val listContent = createListItems(elements.subList(startIndex, endIndex))
                    listContent.firstOrNull() ?: EmptyParagraph
                }

                BULLETED_LIST_KEY -> {
                    val listContent = createListItems(elements.subList(startIndex, endIndex))
                    listContent.firstOrNull() ?: EmptyParagraph
                }

                TASK_LIST_KEY -> {
                    val taskListContent =
                        createTaskListContent(elements.subList(startIndex, endIndex))
                    TaskList(content = taskListContent)
                }

                else -> EmptyParagraph
            }
        } else {
            Paragraph(item.getParagraphContent())
        }
    }

    /**
     * This is a helper method to convert from list of [PositionalParagraph] to list of [Int]s. The list of [Int]s contains
     * only the [Paragraph] and the first [ListItem]s to know where we have a List to recreate later using the right serialized class.
     *
     * Imagine we represents [Paragraph]s as letter `P`, [OrderedList] as `OL`, [BulletedList] as `BL` and [TaskList] as `TL`. In the case
     * of [OrderedList] we will add a dash and a number that will represent if we have contiguous lists.
     *
     * For example: [P, P, LI-1, LI-2, LI-3, LI-1, LI-1, BL, BL, TL, BL, P, LI-1, LI-2]
     *
     * In the example above we have the following representation:
     *
     * P
     * P
     * [LI-1, LI-2, LI-3]
     * [LI-1]
     * [LI-1]
     * [BL,BL]
     * [TL]
     * [BL]
     * P
     * [LI-1, LI-2]
     *
     * In case of the list we add the square brackets that represents a group of elements, at the end the document will have 10 direct
     * children.
     *
     * So the algorithm create the indices where we need to go to create a Paragraph or a List, we just store the first index in case of
     * lists and the index of the paragraphs.
     *
     * The output of the algorithm will be:
     * [0,  1,     2,     5,     6,    7,    9,    10,    11,     12]
     * [P,  P,  LI-1,  LI-1,  LI-1,   BL,   TL,    BL,     P,   LI-1]
     *
     * @param elements The flat list of [PositionalParagraph] in document order.
     * @return A list of start indices, one per top-level paragraph or list group.
     */
    private fun createNodeIndices(elements: List<PositionalParagraph>): List<Int> {
        var currentIndex = 0
        val finalIndices = arrayListOf<Int>()
        while (currentIndex < elements.size) {
            val item = elements[currentIndex]
            val decorator = item.textStyled.firstOrNull()?.piece?.decorator
            if (decorator == null) {
                finalIndices.add(currentIndex)
                currentIndex += 1
            } else {
                finalIndices.add(currentIndex)
                currentIndex = checkNextIndex(currentIndex, elements, decorator)
            }
        }
        return finalIndices
    }

    /**
     * This is a helper method that will help with find the right first item of a list in the [createNodeIndices] method.
     * Basically the method find the next different type of list and return the index where the list is different.
     *
     * @param currentIndex The index to start scanning from.
     * @param elements The full list of [PositionalParagraph] elements.
     * @param decorator The [TextDecoratorModel] of the current list group used as reference for comparison.
     * @return The index of the first element whose decorator key differs from [decorator], or the end of the list.
     */
    private fun checkNextIndex(
        currentIndex: Int,
        elements: List<PositionalParagraph>,
        decorator: TextDecoratorModel
    ): Int {
        var index = currentIndex
        var isSame = true
        while (index < elements.size && isSame) {
            val newDecorator = elements[index].textStyled.firstOrNull()?.piece?.decorator
            if (decorator.key != newDecorator?.key) isSame = false
            else index++
        }
        return index
    }

    /**
     * This method creates all the content for the nested list, we take the list of [PositionalParagraph] that only are type of lists,
     * and we convert it into a list of [BaseText]. This works recursively to recreate the [ListItem]s.
     *
     * @param elements The list of [PositionalParagraph] that belong to the same list group, potentially with nested children.
     * @return A list of [BaseParagraph] representing the list nodes ([OrderedList], [BulletedList], or [TaskList]).
     */
    private fun createListItems(elements: List<PositionalParagraph>): List<BaseParagraph> {
        if (elements.isEmpty()) return emptyList()
        val groups = elements.groupBy { it.key }
        val finalList = arrayListOf<BaseParagraph>()
        groups.forEach { (key, value) ->
            val innerItems = value.fastMap { item ->
                val positionalParagraphs = item.positionalParagraphs
                val paragraphItems = getParagraphContent(item.textStyled)

                if (positionalParagraphs.isNotEmpty()) {
                    val nestedElements = createListItems(positionalParagraphs)
                    val nestedList =
                        when (if (positionalParagraphs.isNotEmpty()) positionalParagraphs.first().key else key) {
                            NUMBERED_LIST_KEY -> nestedElements
                            BULLETED_LIST_KEY -> nestedElements
                            TASK_LIST_KEY -> {
                                val taskListContent = createTaskListContent(positionalParagraphs)
                                listOf(TaskList(content = taskListContent))
                            }

                            else -> listOf(EmptyParagraph)
                        }
                    ListItem(paragraphItems.plus(nestedList))
                } else {
                    val firstItem = item.textStyled.firstOrNull()
                    when (val decorator = firstItem?.piece?.decorator) {
                        is TaskDecoratorModel -> {
                            TaskListItem(
                                attrs = TaskListAttrs(decorator.checked),
                                content = paragraphItems
                            )
                        }

                        else -> ListItem(paragraphItems)
                    }
                }
            }
            // Create the base items
            when (key) {
                NUMBERED_LIST_KEY -> {
                    val attrs =
                        value.first().textStyled.firstOrNull()?.piece?.decorator as? NumberDecoratorModel
                    finalList.add(
                        OrderedList(
                            attrs = ListAttrs(start = attrs?.count ?: 1),
                            content = innerItems
                        )
                    )
                }

                BULLETED_LIST_KEY -> {
                    finalList.add(BulletedList(content = innerItems))
                }

                TASK_LIST_KEY -> {
                    finalList.add(TaskList())
                }

                else -> Unit
            }
        }
        return finalList
    }

    /**
     * This method creates a list of [TaskListItem] items given a list of [PositionalParagraph]'s entry.
     * When positional paragraphs have nested elements, recursion is used to create the content which is of type [BaseParagraph].
     *
     * @param elements The list of [PositionalParagraph] entries that represent task list items.
     * @return A list of [TaskListItem] with their checked state and nested content resolved.
     */
    private fun createTaskListContent(elements: List<PositionalParagraph>): List<TaskListItem> {
        if (elements.isEmpty()) return emptyList()

        return elements.fastMap { item ->
            val positionalParagraphs = item.positionalParagraphs
            val paragraphContent = getParagraphContent(item.textStyled)
            val attributes = item.textStyled.firstOrNull()?.piece?.decorator as? TaskDecoratorModel

            if (positionalParagraphs.isNotEmpty()) {
                val nestedElements = positionalParagraphs.map { positionalParagraph ->
                    createParagraphModel(listOf(positionalParagraph), startIndex = 0, endIndex = 0)
                }
                TaskListItem(
                    attrs = TaskListAttrs(attributes?.checked ?: false),
                    content = paragraphContent.plus(nestedElements)
                )
            } else {
                TaskListItem(
                    attrs = TaskListAttrs(attributes?.checked ?: false),
                    content = paragraphContent
                )
            }
        }
    }

    /**
     * This method creates a list of [PositionalParagraph] given a list of [TextEditorModel] as input.
     * When the list has more than one element we drop the first element, since this element is a mark and should not be added as part of the paragraphs, otherwise we take the whole list.
     * Then we create a [BaseParagraph] with the content or an empty list. This is necessary because a paragraph can be represented as empty.
     *
     * @param textStyled The list of [TextEditorModel] elements that make up the paragraph's styled text.
     * @return A list of [BaseParagraph] derived from the given text elements, using [EmptyParagraph] for blank content.
     */
    /**
     * Merges two [PositionalParagraph] lists that are each already sorted by [PositionalParagraph.index]
     * into a single sorted list in O(a.size + b.size) time, avoiding the O(N log N) cost of
     * sorting the concatenated list.
     *
     * @param a First sorted list of [PositionalParagraph].
     * @param b Second sorted list of [PositionalParagraph].
     * @return A merged list sorted by [PositionalParagraph.index].
     */
    private fun mergeByIndex(
        a: List<PositionalParagraph>,
        b: List<PositionalParagraph>
    ): List<PositionalParagraph> {
        val result = ArrayList<PositionalParagraph>(a.size + b.size)
        var i = 0;
        var j = 0
        while (i < a.size && j < b.size) {
            if (a[i].index <= b[j].index) result.add(a[i++]) else result.add(b[j++])
        }
        while (i < a.size) result.add(a[i++])
        while (j < b.size) result.add(b[j++])
        return result
    }

    private fun getParagraphContent(textStyled: List<TextEditorModel>): List<BaseParagraph> {
        // 1. Create internal Paragraph
        val internalParagraphs = createInternalParagraphs(textStyled)

        // 2. Return the list of pargraphs
        return internalParagraphs.fastMap { paragraph ->
            val texts = paragraph.styledText
                .mapNotNull { if (it.isDecorator) null else it }
                .fastMap {
                    Text(
                        text = it.text.removeLineBreakSuffix(),
                        marks = it.piece.marks.toSet()
                    )
                }

            when (texts.size) {
                0 -> EmptyParagraph
                1 -> { // When the text doesn't have content
                    if (texts.first().text.isEmpty()) EmptyParagraph
                    else Paragraph(content = texts)
                }

                else -> Paragraph(content = texts)
            }
        }
    }
}
