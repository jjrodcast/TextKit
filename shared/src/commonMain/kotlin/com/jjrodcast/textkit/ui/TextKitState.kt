package com.jjrodcast.textkit.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.jjrodcast.textkit.editor.core.TextKitEditorManager
import com.jjrodcast.textkit.editor.core.parser.LinkMark
import com.jjrodcast.textkit.editor.core.parser.Mark
import com.jjrodcast.textkit.editor.core.piecetable.models.TextDecoratorModel
import com.jjrodcast.textkit.editor.core.piecetable.models.TextDecoratorModel.Companion.createDecoratorString
import com.jjrodcast.textkit.editor.core.transactions.models.TextEditorAction
import com.jjrodcast.textkit.editor.core.transactions.models.TextEditorSelectedMark
import com.jjrodcast.textkit.editor.core.transactions.models.TextEditorTransactionType
import com.jjrodcast.textkit.editor.core.transactions.text.TextTransaction
import com.jjrodcast.textkit.editor.models.MarkSearchType
import com.jjrodcast.textkit.editor.models.TextKitConfiguration
import com.jjrodcast.textkit.ui.utils.createStyle
import com.jjrodcast.textkit.ui.utils.restore
import com.jjrodcast.textkit.ui.utils.save


/**
 * A state object that can be used to remember the state of a RichText component across recomposition.
 *
 * @param json The JSON string representing the initial state of the RichText component.
 * @param isViewer Whether the component is in read-only viewer mode.
 * @param onUrlClicked The callback invoked when the user taps a URL in viewer mode.
 */
@Composable
fun rememberRichTextState(
    json: String,
    isViewer: Boolean,
    configuration: TextKitConfiguration,
    onUrlClicked: ((String) -> Unit)? = null
): RichTextState {
    val state = rememberSaveable(saver = RichTextState.Saver) {
        RichTextState(json, isViewer, configuration)
            .apply { setup() }
    }
    // Callbacks are not parcelable; re-attach on each composition after save/restore.
    state.onUrlClicked = onUrlClicked
    return state
}

/**
 * RichTextState is a state object that can be used to remember the state of a RichText component storing the initial JSON
 * and the configuration [TextKitConfiguration].
 *
 * @param json The JSON string representing the initial state of the RichText component.
 * @param isViewer Whether the component is in read-only viewer mode.
 * @param configuration The object that manages the configuration of colors and sizes. See [TextKitConfiguration].
 * @param onUrlClicked The callback invoked when the user taps a URL in viewer mode.
 */
@Stable
class RichTextState(
    private val json: String,
    private val isViewer: Boolean,
    private val configuration: TextKitConfiguration,
    internal var onUrlClicked: ((String) -> Unit)? = null,
) {

    private val manager by lazy { TextKitEditorManager(configuration) }

    internal var visualTransformation by mutableStateOf(VisualTransformation.None)

    var textFieldValue by mutableStateOf(TextFieldValue())
        private set

    internal var selection by mutableStateOf(TextRange.Zero)
        private set

    internal var textLayoutResult: TextLayoutResult? by mutableStateOf(null)
        private set

    val composition get() = textFieldValue.composition

    private var prevTextFieldValue = textFieldValue.copy(text = manager.text)

    private var annotatedString by mutableStateOf(AnnotatedString(text = ""))

    private val viewerAnnotatedStringState = derivedStateOf {
        annotatedString // establishes snapshot state dependency
        createViewerAnnotatedString()
    }

    val annotatedStringForViewer get() = viewerAnnotatedStringState.value

    private val isTyping get() = prevTextFieldValue.selection.collapsed && textFieldValue.selection.collapsed

    private val isUsingClipboard get() = prevTextFieldValue.selection.collapsed && !textFieldValue.selection.collapsed

    internal val paragraphs get() = manager.getParagraphs()

    val linkInfo get() = manager.getLink(selection.min, selection.max)

    internal fun setup() {
        manager.load(json, isViewer)
        val text = manager.text
        textFieldValue = textFieldValue.copy(text = text, selection = TextRange.Zero)
        updateAnnotatedString(textFieldValue)
    }

    fun toJson() = manager.toJson()

    fun onTextLayout(textLayoutResult: TextLayoutResult) {
        this.textLayoutResult = textLayoutResult
    }


    fun updateDocument(
        selection: TextRange,
        previousMarks: TextEditorSelectedMark,
        currentMarks: TextEditorSelectedMark,
        transactionType: TextEditorTransactionType
    ) {
        val (updated, selection) = manager.updateDocument(
            selection,
            previousMarks,
            currentMarks,
            transactionType
        )

        if (updated) {
            updateAnnotatedString(textFieldValue.copy(selection = selection))
        }
    }


    internal fun getSelectedMarksWithType(): MarkSearchType {
        return manager.getSearchMarkType(selection)
    }

    private fun checkDecorator(start: Int, end: Int) {
        val (hasDecorator, range) = manager.checkDecorator(start, end)
        if (start == end && hasDecorator) {
            textFieldValue = textFieldValue.copy(selection = TextRange(range.end))
        }
    }

    fun updateSelection(start: Int, end: Int) {
        onTextFieldChange(textFieldValue.copy(selection = TextRange(start, end)))
    }

    fun onTextFieldChange(
        newTextFieldValue: TextFieldValue = prevTextFieldValue,
        marks: Set<Mark> = emptySet()
    ) {
        prevTextFieldValue = newTextFieldValue

        if (prevTextFieldValue.text.length > textFieldValue.text.length) {
            handleAddingText(marks)
        } else if (prevTextFieldValue.text.length < textFieldValue.text.length) {
            handleRemovingText()
        } else {
            if (prevTextFieldValue.text == textFieldValue.text &&
                prevTextFieldValue.selection != textFieldValue.selection
            ) {
                // Update selection
                textFieldValue = prevTextFieldValue
                selection = textFieldValue.selection
                checkDecorator(textFieldValue.selection.start, textFieldValue.selection.end)
            } else {
                // Replacing the same length of characters using the clipboard
                updateAnnotatedString(prevTextFieldValue)
            }
        }
        prevTextFieldValue = TextFieldValue()
    }

    private fun handleAddingText(marks: Set<Mark> = emptySet()) {
        val action = createAddAction(marks)
        val (result, range) = TextTransaction.onTextUpdated(action, manager)
        if (result) {
            selection = range
            updateAnnotatedString(prevTextFieldValue.copy(selection = selection))
        }
    }

    private fun handleRemovingText() {
        val action = createRemoveAction()
        val (result, range) = TextTransaction.onTextUpdated(action, manager)
        if (result) {
            selection = range
            updateAnnotatedString(prevTextFieldValue.copy(selection = selection))
        }
    }

    private fun updateAnnotatedString(newTextFieldValue: TextFieldValue) {
        annotatedString = buildAnnotatedString {
            paragraphs.forEach { paragraph ->
                withStyle(DefaultParagraphStyle) {
                    paragraph.children.forEach { child ->
                        if (child.text.endsWith(lineBreak)) {
                            val text = child.text.dropLast(1)
                            withStyle(child.createStyle(manager.configuration)) {
                                append(text)
                            }
                            append(lineBreak.replace(lineBreak, " "))
                        } else {
                            withStyle(child.createStyle(manager.configuration)) {
                                append(child.text)
                            }
                        }
                    }
                }
            }
        }
        textFieldValue = newTextFieldValue.copy(text = annotatedString.text)
        visualTransformation =
            VisualTransformation { _ -> TransformedText(annotatedString, OffsetMapping.Identity) }
    }

    private fun createViewerAnnotatedString(): Pair<AnnotatedString, Map<String, InlineTextContent>> {
        val inlineContent = mutableMapOf<String, InlineTextContent>()
        val annotatedString = buildAnnotatedString {
            paragraphs.forEach { paragraph ->
                withStyle(DefaultParagraphStyle) {
                    paragraph.children.forEach { child ->
                        val id = "${child.start}-${child.end}"
                        val text = if (child.text.endsWith(lineBreak)) child.text.replace(
                            lineBreak,
                            " "
                        ) else child.text
                        if (child.decorator is TextDecoratorModel.TaskDecoratorModel) {
                            val taskDecorator = child.decorator
                            appendInlineContent(id, taskDecorator.createDecoratorString())
                            inlineContent[id] = InlineTextContent(
                                Placeholder(
                                    width = (1.8).em,
                                    height = (1.9).em,
                                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                                )
                            ) {
                                Checkbox(
                                    checked = taskDecorator.checked,
                                    onCheckedChange = {},
                                    modifier = Modifier.padding(start = 8.dp, end = 16.dp)
                                )
                            }
                        } else {
                            pushStringAnnotation(id, text)
                            if (child.marks.any { it is LinkMark }) {
                                withLink(
                                    link = LinkAnnotation.Url(
                                        url = child.marks.filterIsInstance<LinkMark>()
                                            .first().attrs.href,
                                        styles = TextLinkStyles(child.createStyle(manager.configuration)),
                                        linkInteractionListener = {
                                            val url = (it as LinkAnnotation.Url).url
                                            onUrlClicked?.invoke(url)
                                        }
                                    )
                                ) {
                                    append(text)
                                }
                            } else {
                                append(text)
                                addStyle(
                                    style = child.createStyle(manager.configuration),
                                    start = child.start,
                                    end = child.end
                                )
                            }
                            pop()
                        }
                    }
                }
            }
        }

        return Pair(annotatedString, inlineContent)
    }

    private fun createAddAction(marks: Set<Mark> = emptySet()): TextEditorAction {
        return when {
            isTyping -> {
                val typedTextCount = prevTextFieldValue.text.length - textFieldValue.text.length
                val start = prevTextFieldValue.selection.min - typedTextCount
                val typedText = prevTextFieldValue.text.substring(start, start + typedTextCount)
                TextEditorAction.TextAdded(
                    text = typedText,
                    marks = marks,
                    offset = start,
                    selection = prevTextFieldValue.selection
                )
            }

            isUsingClipboard -> {
                TextEditorAction.TextUpdated(
                    removeLength = textFieldValue.selection.length,
                    text = prevTextFieldValue.text.substring(
                        textFieldValue.selection.start,
                        prevTextFieldValue.selection.end
                    ),
                    offset = textFieldValue.selection.start,
                    selection = prevTextFieldValue.selection
                )
            }

            else -> {
                val typedTextCount = prevTextFieldValue.text.length - textFieldValue.text.length
                val start = prevTextFieldValue.selection.min - typedTextCount
                val typedText = prevTextFieldValue.text.substring(start, start + typedTextCount)
                TextEditorAction.TextAdded(
                    text = typedText,
                    offset = start,
                    selection = prevTextFieldValue.selection
                )
            }
        }
    }

    private fun createRemoveAction(): TextEditorAction {
        return when {
            isTyping -> {
                TextEditorAction.TextRemoved(
                    offset = prevTextFieldValue.selection.min,
                    length = textFieldValue.text.length - prevTextFieldValue.text.length,
                    selection = textFieldValue.selection
                )
            }

            isUsingClipboard -> {
                TextEditorAction.TextUpdated(
                    removeLength = textFieldValue.selection.length,
                    text = prevTextFieldValue.text.substring(
                        textFieldValue.selection.start,
                        prevTextFieldValue.selection.end
                    ),
                    offset = textFieldValue.selection.start,
                    selection = prevTextFieldValue.selection
                )
            }

            else -> TextEditorAction.TextRemoved(
                offset = prevTextFieldValue.selection.min,
                length = textFieldValue.text.length - prevTextFieldValue.text.length,
                selection = textFieldValue.selection
            )
        }
    }

    companion object {

        val Saver = Saver<RichTextState, Any>(
            save = {
                arrayListOf(
                    save(it.textFieldValue.text),
                    save(it.selection, TextRangeSaver, this),
                    save(it.configuration, ConfigurationSaver, this),
                    save(it.toJson()),
                    save(it.isViewer),
                )
            },
            restore = {
                @Suppress("UNCHECKED_CAST")
                val list = it as List<Any>
                val textFieldValue = TextFieldValue(
                    text = restore(list[0])!!,
                    selection = restore(list[1], TextRangeSaver)!!
                )
                val config: TextKitConfiguration = restore(list[2], ConfigurationSaver)!!
                val json: String = restore(list[3])!!
                val isViewer: Boolean = restore(list[4])!!

                val state = RichTextState(json, isViewer, config)
                    .also { state ->
                        state.textFieldValue = textFieldValue
                        state.updateAnnotatedString(state.textFieldValue)
                    }
                state
            }
        )

        internal val DefaultParagraphStyle = ParagraphStyle(
            textAlign = TextAlign.Left,
            lineBreak = LineBreak.Paragraph,
            lineHeight = (1.8).em, // 2.0.em,
        )

        private val ConfigurationSaver = Saver<TextKitConfiguration, Any>(
            save = {
                arrayListOf(
                    save(it.highlightColor),
                    save(it.linkColor),
                    save(it.textColor),
                    save(it.fontSize)
                )
            },
            restore = {
                @Suppress("UNCHECKED_CAST")
                val list = it as List<Any>
                TextKitConfiguration(
                    highlightColor = restore(list[0])!!,
                    linkColor = restore(list[1])!!,
                    textColor = restore(list[2])!!,
                    fontSize = restore(list[3])!!
                )
            }
        )

        private val TextRangeSaver = Saver<TextRange, Any>(
            save = { arrayListOf(save(it.start), save(it.end)) },
            restore = {
                @Suppress("UNCHECKED_CAST")
                val list = it as List<Any>
                TextRange(restore(list[0])!!, restore(list[1])!!)
            }
        )
    }

    private val lineBreak = "\n"
}
