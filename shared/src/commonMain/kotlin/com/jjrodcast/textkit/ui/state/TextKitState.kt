package com.jjrodcast.textkit.ui.state

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.jjrodcast.textkit.editor.components.TextEditorDecoratorItem
import com.jjrodcast.textkit.editor.components.TextEditorListItem
import com.jjrodcast.textkit.editor.core.TextKitEditorManager
import com.jjrodcast.textkit.editor.core.parser.BoldMark
import com.jjrodcast.textkit.editor.core.parser.HighlightMark
import com.jjrodcast.textkit.editor.core.parser.ItalicMark
import com.jjrodcast.textkit.editor.core.parser.LinkMark
import com.jjrodcast.textkit.editor.core.parser.Mark
import com.jjrodcast.textkit.editor.core.parser.StrikeMark
import com.jjrodcast.textkit.editor.core.parser.TextStyleAttrs
import com.jjrodcast.textkit.editor.core.parser.TextStyleMark
import com.jjrodcast.textkit.editor.core.parser.UnderlineMark
import com.jjrodcast.textkit.editor.core.piecetable.models.TextDecoratorModel
import com.jjrodcast.textkit.editor.core.piecetable.models.TextDecoratorModel.Companion.createDecoratorString
import com.jjrodcast.textkit.editor.core.transactions.models.TextEditorAction
import com.jjrodcast.textkit.editor.core.transactions.models.TextEditorParagraph
import com.jjrodcast.textkit.editor.core.transactions.models.TextEditorSelectedMark
import com.jjrodcast.textkit.editor.core.transactions.models.TextEditorTransactionType
import com.jjrodcast.textkit.editor.core.transactions.text.TextTransaction
import com.jjrodcast.textkit.editor.models.MarkSearchType
import com.jjrodcast.textkit.editor.models.TextKitConfiguration
import com.jjrodcast.textkit.editor.models.createTextKitConfiguration
import com.jjrodcast.textkit.ui.utils.createStyle
import com.jjrodcast.textkit.ui.utils.restore
import com.jjrodcast.textkit.ui.utils.save


/**
 * A state object that can be used to remember the state of a RichText component across recomposition.
 *
 * @param json The JSON string representing the initial state of the RichText component.
 * @param isViewer Whether the component is in read-only viewer mode.
 * @param configuration Configuration object that holds the colors and sizes for UI components.
 * @param onUrlClicked The callback invoked when the user taps a URL in viewer mode.
 */
@Composable
fun rememberTextKitState(
    json: String = "{}",
    isViewer: Boolean = false,
    configuration: TextKitConfiguration = remember { createTextKitConfiguration() },
    onUrlClicked: ((String) -> Unit)? = null
): TextKitState {
    val state = rememberSaveable(saver = TextKitState.Saver) {
        TextKitState(json, isViewer, configuration)
            .apply { setup() }
    }
    // Callbacks are not parcelable; re-attach on each composition after save/restore.
    state.onUrlClicked = onUrlClicked
    return state
}

/**
 * TextKitState is a state object that can be used to remember the state of a RichText component storing the initial JSON
 * and the configuration [TextKitConfiguration].
 *
 * @param json The JSON string representing the initial state of the RichText component.
 * @param isViewer Whether the component is in read-only viewer mode.
 * @param configuration The object that manages the configuration of colors and sizes. See [TextKitConfiguration].
 * @param onUrlClicked The callback invoked when the user taps a URL in viewer mode.
 */
@Stable
class TextKitState(
    private val json: String,
    private val isViewer: Boolean,
    private val configuration: TextKitConfiguration,
    internal var onUrlClicked: ((String) -> Unit)? = null
) {

    private val manager by lazy { TextKitEditorManager(configuration) }

    internal var visualTransformation by mutableStateOf(VisualTransformation.None)

    var textFieldValue by mutableStateOf(TextFieldValue())
        private set

    internal var selection by mutableStateOf(TextRange.Zero)
        private set

    /**
     * Marks active at the collapsed caret, captured when the selection moves. Text typed next
     * inherits these ("stored marks"), so formatting continues across the caret without a selection.
     */
    var lastMarks by mutableStateOf<Set<Mark>>(emptySet())
        private set

    /**
     * List-item type active at the collapsed caret, captured alongside [lastMarks] when the
     * selection moves. Lets the formatting bar reflect whether the caret sits inside a numbered,
     * bulleted, or task list (or none) without re-querying the document.
     */
    var lastListItem by mutableStateOf<TextEditorDecoratorItem>(TextEditorListItem.None)
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

    /**
     * Whether the character under [position] (in the text field's local coordinates) sits on a
     * link. Used by the editor to swap the pointer to a hand cursor while hovering a link, since
     * the editable field renders links as plain spans rather than clickable [LinkAnnotation]s.
     * Returns false until the first [onTextLayout] has run.
     */
    internal fun isLinkAtPosition(position: Offset): Boolean {
        val layout = textLayoutResult ?: return false

        val line = layout.getLineForVerticalPosition(position.y)
        val outsideText = position.y > layout.getLineBottom(layout.lineCount - 1) ||
                position.x < layout.getLineLeft(line) ||
                position.x > layout.getLineRight(line)
        if (outsideText) return false

        val offset = layout.getOffsetForPosition(position).coerceIn(0, textFieldValue.text.length)
        val (href, _) = manager.getLink(offset, offset)
        return href != null
    }

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

    fun applyBold(selected: Boolean): Boolean = applyMark(BoldMark(), selected)

    fun applyItalic(selected: Boolean): Boolean = applyMark(ItalicMark(), selected)

    fun applyHighlight(selected: Boolean): Boolean = applyMark(HighlightMark(), selected)

    fun applyUnderline(selected: Boolean): Boolean = applyMark(UnderlineMark(), selected)

    fun applyStrikeThrough(selected: Boolean): Boolean = applyMark(StrikeMark(), selected)

    fun applyTextStyle(fontSize: Int, color: String?): Boolean =
        applyMark(
            TextStyleMark(TextStyleAttrs(fontSize = fontSize, color = color)),
            selected = true
        )

    /**
     * Toggles a single [mark] on/off while keeping the other marks active at the caret
     * ([lastMarks]) intact, then applies the resulting full set.
     *
     * Passing only the toggled mark used to reset the whole set — e.g. turning italic off while
     * bold was active cleared bold too — because [TextEditorMarkProcessor] diffs the previous set
     * against the current one by size to decide add vs remove. It needs the complete post-toggle
     * set, not just the button that changed, so we rebuild it here (matching marks by [Mark.type]
     * so attribute-carrying marks like text style/link replace rather than duplicate).
     */
    private fun applyMark(mark: Mark, selected: Boolean): Boolean {
        val withoutType = lastMarks.filterNotTo(mutableSetOf()) { it.type == mark.type }
        val marks = if (selected) withoutType + mark else withoutType
        return applyMarks(marks)
    }

    private fun applyMarks(marks: Set<Mark>): Boolean {
        // With no selection there is no text to reformat; store the marks so the next typed
        // characters inherit them (and the formatting bar reflects the toggle).
        if (selection.collapsed) {
            lastMarks = marks
            return true
        }
        return updateDocument(
            selection,
            TextEditorSelectedMark(
                marks = lastMarks, listItemSelectedValue = lastListItem
            ),
            currentMarks = TextEditorSelectedMark(
                marks = marks, listItemSelectedValue = lastListItem
            ),
            transactionType = TextEditorTransactionType.Format
        )
    }

    private fun updateDocument(
        selection: TextRange,
        previousMarks: TextEditorSelectedMark,
        currentMarks: TextEditorSelectedMark,
        transactionType: TextEditorTransactionType
    ): Boolean {
        val (updated, selection) = manager.updateDocument(
            selection,
            previousMarks,
            currentMarks,
            transactionType
        )

        if (updated) {
            this.selection = selection
            updateAnnotatedString(textFieldValue.copy(selection = selection))
            // Refresh the caret context so formatting-bar toggles reflect the change just applied.
            readSelectionContext()
        }
        return updated
    }

    internal fun getSelectedMarksWithType(): MarkSearchType {
        return manager.getSearchMarkType(selection)
    }

    /**
     * Refreshes the stored caret context ([lastMarks] + [lastListItem]) from a single document
     * query so both stay in sync with the current selection.
     */
    private fun readSelectionContext() {
        val searchType = getSelectedMarksWithType()
        lastMarks = searchType.marks
        lastListItem = searchType.listItem
    }

    private fun checkDecorator(start: Int, end: Int) {
        val (hasDecorator, range) = manager.checkDecorator(start, end)
        if (start == end && hasDecorator) {
            textFieldValue = textFieldValue.copy(selection = TextRange(range.end))
        }
    }

    fun onTextFieldChange(newTextFieldValue: TextFieldValue = prevTextFieldValue) {
        prevTextFieldValue = newTextFieldValue

        if (prevTextFieldValue.text.length > textFieldValue.text.length) {
            handleAddingText()
        } else if (prevTextFieldValue.text.length < textFieldValue.text.length) {
            handleRemovingText()
        } else {
            if (prevTextFieldValue.text == textFieldValue.text &&
                prevTextFieldValue.selection != textFieldValue.selection
            ) {
                // Update selection
                textFieldValue = prevTextFieldValue
                selection = textFieldValue.selection
                readSelectionContext()
                checkDecorator(textFieldValue.selection.start, textFieldValue.selection.end)
            } else {
                // Replacing the same length of characters using the clipboard
                updateAnnotatedString(prevTextFieldValue)
            }
        }
        prevTextFieldValue = TextFieldValue()
    }

    private fun handleAddingText(marks: Set<Mark> = lastMarks) {
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
        annotatedString = defaultAnnotatedStringFormatting()
        textFieldValue = newTextFieldValue.copy(text = annotatedString.text)
        visualTransformation =
            VisualTransformation { _ -> TransformedText(annotatedString, OffsetMapping.Identity) }
    }

    private fun defaultAnnotatedStringFormatting(): AnnotatedString {
        return buildAnnotatedString {
            // A single ParagraphStyle wraps the whole document. Wrapping each paragraph in its own
            // ParagraphStyle turned every trailing '\n' into a full-height empty line (visible as
            // large gaps between paragraphs); keeping one paragraph block makes the '\n' plain line
            // breaks (single spacing) while every character — and thus the offset mapping — is kept.
            withStyle(DefaultParagraphStyle) {
                paragraphs.forEach { paragraph ->
                    paragraph.children.forEach { child ->
                        if (child.text.endsWith(lineBreak)) {
                            val text = child.text.dropLast(1)
                            withStyle(child.createStyle(manager.configuration)) {
                                append(text)
                            }
                            append(lineBreak)
                        } else {
                            withStyle(child.createStyle(manager.configuration)) {
                                append(child.text)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun createViewerAnnotatedString(): Pair<AnnotatedString, Map<String, InlineTextContent>> {
        val inlineContent = mutableMapOf<String, InlineTextContent>()
        val annotatedString = buildAnnotatedString {
            paragraphs.forEach { paragraph ->
                withStyle(DefaultParagraphStyle) {
                    paragraph.children.forEach { child ->
                        val id = "${child.start}-${child.end}"
                        val text = if (child.text.endsWith(lineBreak)) {
                            child.text
                        } else child.text
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

        val Saver = Saver<TextKitState, Any>(
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

                val state = TextKitState(json, isViewer, config)
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
            lineHeight = 1.5.em,
            // Trim.None keeps full line height on every line — including the empty line created
            // right after pressing Enter — so the caret lands at the correct vertical position
            // immediately instead of appearing on the previous paragraph's line and then jumping
            // down once the layout re-measures. Center keeps the glyph centered in the line box.
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.None,
            ),
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
