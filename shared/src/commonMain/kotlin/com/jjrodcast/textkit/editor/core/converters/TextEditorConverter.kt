package com.jjrodcast.textkit.editor.core.converters

import com.jjrodcast.textkit.editor.core.models.TextEditorDocumentModel
import com.jjrodcast.textkit.editor.core.models.TextEditorModel
import com.jjrodcast.textkit.editor.core.models.TextEditorParagraphModel
import com.jjrodcast.textkit.editor.core.parser.BaseParagraph
import com.jjrodcast.textkit.editor.core.parser.BaseText
import com.jjrodcast.textkit.editor.core.parser.Blockquote
import com.jjrodcast.textkit.editor.core.parser.BulletedList
import com.jjrodcast.textkit.editor.core.parser.HardBreak
import com.jjrodcast.textkit.editor.core.parser.Heading
import com.jjrodcast.textkit.editor.core.parser.HeadingLevels
import com.jjrodcast.textkit.editor.core.parser.HeadingLevelsValues
import com.jjrodcast.textkit.editor.core.parser.ListItem
import com.jjrodcast.textkit.editor.core.parser.Mark
import com.jjrodcast.textkit.editor.core.parser.Mention
import com.jjrodcast.textkit.editor.core.parser.MentionType.DEFAULT_MENTION_CHAR
import com.jjrodcast.textkit.editor.core.parser.OrderedList
import com.jjrodcast.textkit.editor.core.parser.Paragraph
import com.jjrodcast.textkit.editor.core.parser.ParagraphNone
import com.jjrodcast.textkit.editor.core.parser.TaskList
import com.jjrodcast.textkit.editor.core.parser.TaskListItem
import com.jjrodcast.textkit.editor.core.parser.Text
import com.jjrodcast.textkit.editor.core.parser.TextEditorDocument
import com.jjrodcast.textkit.editor.core.parser.TextStyleAttrs
import com.jjrodcast.textkit.editor.core.parser.TextStyleMark
import com.jjrodcast.textkit.editor.core.piecetable.models.TextDecoratorModel
import com.jjrodcast.textkit.editor.core.piecetable.models.TextDecoratorModel.Companion.createDecoratorString
import com.jjrodcast.textkit.editor.models.TextKitConfiguration
import com.jjrodcast.textkit.editor.utils.EMPTY
import com.jjrodcast.textkit.editor.utils.RegexUtils
import com.jjrodcast.textkit.editor.utils.addLineBreak
import com.jjrodcast.textkit.editor.utils.fastForEach
import com.jjrodcast.textkit.editor.utils.fastMapIndexed
import com.jjrodcast.textkit.editor.utils.isLineBreak
import com.jjrodcast.textkit.editor.utils.removeLineBreakSuffix
import com.jjrodcast.textkit.editor.utils.toHex
import com.jjrodcast.textkit.editor.utils.toHexWithAlpha

internal object TextEditorConverter {

    internal fun geAsText(
        document: TextEditorDocument,
        configuration: TextKitConfiguration
    ): String {
        // Eliminate the intermediate flatMap list: iterate paragraphs and styledText directly,
        // avoiding one O(P×M) allocation before the append loop.
        val builder = StringBuilder()
        getAsTextWithMarks(document, configuration).paragraph.fastForEach { paragraph ->
            paragraph.styledText.fastForEach { model -> builder.append(model.text) }
        }
        return builder.toString()
    }

    internal fun getAsTextWithMarks(
        document: TextEditorDocument,
        configuration: TextKitConfiguration
    ): TextEditorDocumentModel {
        val lines = arrayListOf<TextEditorParagraphModel>()
        document.content.filterNot { it is ParagraphNone }.fastForEach { paragraph ->
            val items = paragraph.getParagraphContentWithMarkers(configuration = configuration)
                .filter { it.text.isNotEmpty() }
            lines.add(TextEditorParagraphModel(items))
        }
        return TextEditorDocumentModel(lines.removeLastBreakLine())
    }

    private fun List<TextEditorParagraphModel>.removeLastBreakLine(): List<TextEditorParagraphModel> {
        val paragraphs = toMutableList()
        if (paragraphs.isNotEmpty()) {
            val removedElement = paragraphs.removeAt(paragraphs.lastIndex)

            val lastText = removedElement.styledText.last()
            val newStyledTexts = removedElement.styledText.dropLast(1)

            val text = when (removedElement.styledText.size) {
                1 -> {
                    if (lastText.text.isLineBreak()) lastText.text
                    else lastText.text.removeLineBreakSuffix()
                }

                else -> lastText.text.removeLineBreakSuffix()
            }

            val newLastText = lastText.copy(text = text)

            val newLastElement = TextEditorParagraphModel(newStyledTexts.plus(newLastText))
            paragraphs.add(newLastElement)
        }
        return paragraphs
    }

    private fun BaseParagraph.getParagraphContentWithMarkers(
        decorator: TextDecoratorModel? = null,
        configuration: TextKitConfiguration
    ): List<TextEditorModel> {
        val items = arrayListOf<TextEditorModel>()
        when (this) {
            is Paragraph -> {
                if (this.content.isEmpty()) items.add(TextEditorModel.create(EMPTY))
                else {
                    this.content.fastForEach { text ->
                        items.addAll(
                            text.getTextContentWithMarkers(
                                decorator,
                                configuration,
                                null
                            )
                        )
                    }
                }
                when (decorator) {
                    is TextDecoratorModel.BlockquoteDecorator -> items.postProcessBlockquotes()
                    else -> items.postProcessParagraph(decorator)
                }
            }

            is Heading -> {
                if (this.content.isEmpty()) items.add(TextEditorModel.create(EMPTY))
                else {
                    this.content.fastForEach { text ->
                        items.addAll(
                            text.getTextContentWithMarkers(
                                decorator,
                                configuration,
                                attrs.level
                            )
                        )
                    }
                }
                items.postProcessParagraph(decorator)
            }

            is OrderedList -> {
                var localOrder = attrs.start
                this.content.fastForEach { text ->
                    items.addAll(
                        text.getTextContentWithMarkers(
                            TextDecoratorModel.NumberDecoratorModel(
                                count = localOrder,
                                level = decorator?.level ?: 0
                            ),
                            configuration
                        )
                    )
                    localOrder++
                }
            }

            is BulletedList -> {
                this.content.fastForEach { text ->
                    items.addAll(
                        text.getTextContentWithMarkers(
                            TextDecoratorModel.BulletDecoratorModel(
                                level = decorator?.level ?: 0
                            ),
                            configuration
                        )
                    )
                }
            }

            is TaskList -> {
                this.content.fastForEach { text ->
                    items.addAll(
                        text.getTextContentWithMarkers(
                            TextDecoratorModel.TaskDecoratorModel(
                                checked = text.attrs.checked,
                                level = decorator?.level ?: 0,
                                nestedCount = content.size
                            ),
                            configuration
                        )
                    )
                }
            }

            is Blockquote -> {
                var group = 1
                this.content.fastForEach { paragraph ->
                    items.addAll(
                        paragraph.getParagraphContentWithMarkers(
                            TextDecoratorModel.BlockquoteDecorator(
                                group
                            ),
                            configuration
                        )
                    )
                }
                group++
            }

            ParagraphNone -> emptyList<TextEditorModel>()
        }
        return items
    }

    private fun ArrayList<TextEditorModel>.postProcessParagraph(decorator: TextDecoratorModel?) {
        if (isNotEmpty()) {
            decorator?.let {
                add(
                    0,
                    TextEditorModel.create(text = decorator.createDecoratorString(), decorator = it)
                )
            }
            val lastItem = last()
            removeAt(lastIndex)
            val lastText =
                if (lastItem.text.isLineBreak()) lastItem.text else lastItem.text.addLineBreak()
            add(lastItem.copy(text = lastText))
        }
    }

    private fun ArrayList<TextEditorModel>.postProcessBlockquotes() {
        if (isNotEmpty()) {
            val lastItem = removeAt(lastIndex)
            val lastText =
                if (lastItem.text.isLineBreak()) lastItem.text else lastItem.text.addLineBreak()
            add(lastItem.copy(text = lastText))
        }
    }

    private fun BaseText.getTextContentWithMarkers(
        decorator: TextDecoratorModel? = null,
        configuration: TextKitConfiguration,
        headingLevel: Int? = null,
    ): List<TextEditorModel> {
        val items = arrayListOf<TextEditorModel>()
        when (this) {
            is HardBreak -> {
                val newDecorator = decorator as? TextDecoratorModel.BlockquoteDecorator
                items.add(TextEditorModel.create(text = text, decorator = newDecorator))
            }

            is Text -> {
                val newMarks = recreateMarks(marks, configuration, headingLevel)
                val newDecorator = decorator as? TextDecoratorModel.BlockquoteDecorator
                items.add(
                    TextEditorModel.create(
                        text = text,
                        marks = newMarks,
                        decorator = newDecorator
                    )
                )
            }

            is Mention -> {
                // Atomic inline node: its visible text is "<triggerKey><label>" and its identity
                // (id + label) rides on the piece so it can be serialized back to a `mention` node.
                // Marks apply to the whole chip, resolved the same way as a Text node's marks.
                val newMarks = recreateMarks(marks, configuration, headingLevel)
                val newDecorator = decorator as? TextDecoratorModel.BlockquoteDecorator
                val triggerChar = configuration.mentionTrigger?.triggerKey ?: DEFAULT_MENTION_CHAR
                items.add(
                    TextEditorModel.create(
                        text = triggerChar + (attrs.label ?: EMPTY),
                        marks = newMarks,
                        mention = attrs,
                        decorator = newDecorator
                    )
                )
            }

            is ListItem -> {
                this.content.fastForEach { item ->
                    items.addAll(
                        item.getParagraphContentWithMarkers(
                            decorator?.copyValue(decorator.level + 1),
                            configuration
                        )
                    )
                }
            }

            is TaskListItem -> {
                this.content.fastMapIndexed { index, item ->
                    val itemDecorator =
                        if (content.size > 1 && index == 0 || content.size <= 1) decorator?.copyValue(
                            decorator.level + 1,
                            nestedCount = content.size
                        ) else null
                    items.addAll(item.getParagraphContentWithMarkers(itemDecorator, configuration))
                }
            }
        }
        return items
    }

    private fun recreateMarks(
        marks: Set<Mark>,
        configuration: TextKitConfiguration,
        headingLevel: Int?
    ): Set<Mark> {
        val styleMark = createTextStyleFromLevel(headingLevel, configuration)
        val (styleMarks, otherMarks) = marks.plus(styleMark).partition { it is TextStyleMark }
        val newStyleMarks = resolveTextStyleDefaults(styleMarks.firstOrNull() as? TextStyleMark, configuration)
        return otherMarks.toSet().plus(newStyleMarks)
    }

    private fun createTextStyleFromLevel(
        level: Int?,
        configuration: TextKitConfiguration
    ): Set<Mark> {
        val levelToValue = when (level) {
            HeadingLevels.H1 -> HeadingLevelsValues.H1
            HeadingLevels.H2 -> HeadingLevelsValues.H2
            HeadingLevels.H3 -> HeadingLevelsValues.H3
            HeadingLevels.H4 -> HeadingLevelsValues.H4
            HeadingLevels.H5 -> HeadingLevelsValues.H5
            HeadingLevels.H6 -> HeadingLevelsValues.H6
            else -> null
        }

        val mark = if (levelToValue == null) null
        else {
            configuration.textColor
            TextStyleMark.getDefault(
                color = configuration.textColor.toHex(),
                fontSize = levelToValue
            )
        }
        return setOfNotNull(mark)
    }

    /**
     * Normalizes a text-style mark coming from the document against the [configuration]:
     *
     * - When there is no text-style mark at all, none is added (a text node with e.g. only a
     *   `bold` mark stays without a `textStyle`).
     * - A missing / unset `fontSize` falls back to [TextKitConfiguration.fontSize].
     * - A missing / empty `color` falls back to [TextKitConfiguration.textColor].
     * - `rgb(...)` colors are converted to hex.
     */
    private fun resolveTextStyleDefaults(
        textStyle: TextStyleMark?,
        configuration: TextKitConfiguration
    ): Set<Mark> {
        if (textStyle == null) return emptySet()

        val hexColor = RegexUtils.rgbTextToHex(textStyle.attrs.color)
        val resolvedColor =
            if (hexColor.isNullOrEmpty()) configuration.textColor.toHex() else hexColor
        val resolvedFontSize =
            if (textStyle.attrs.fontSize <= TextStyleAttrs.UNSET_FONT_SIZE) configuration.fontSize
            else textStyle.attrs.fontSize

        return setOf(
            textStyle.copy(attrs = textStyle.attrs.copy(color = resolvedColor, fontSize = resolvedFontSize))
        )
    }
}
