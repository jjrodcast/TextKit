package com.jjrodcast.textkit.ui.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.jjrodcast.textkit.editor.components.TextEditorDecoratorItem
import com.jjrodcast.textkit.editor.components.TextEditorListItem
import com.jjrodcast.textkit.editor.core.parser.BoldMark
import com.jjrodcast.textkit.editor.core.parser.HighlightMark
import com.jjrodcast.textkit.editor.core.parser.ItalicMark
import com.jjrodcast.textkit.editor.core.parser.LinkMark
import com.jjrodcast.textkit.editor.core.parser.Mark
import com.jjrodcast.textkit.editor.core.parser.StrikeMark
import com.jjrodcast.textkit.editor.core.parser.TextStyleMark
import com.jjrodcast.textkit.editor.core.parser.UnderlineMark
import com.jjrodcast.textkit.ui.utils.TextKitPickerPallete
import com.jjrodcast.textkit.ui.utils.restore
import com.jjrodcast.textkit.ui.utils.save

/**
 * Remembers a [TextKitFormattingBarState] across recomposition and configuration changes.
 *
 * The state reflects which formatting toggles (bold, italic, list type, …) are active at the
 * caret, so the [com.jjrodcast.textkit.ui.TextKitFormattingBar] can highlight them. Keep it in
 * sync with the editor via [TextKitFormattingBarState.syncFrom].
 *
 * @param colors The palette shown in the text-color picker popup. Re-applied on every composition,
 * so it is caller-owned configuration (not persisted through the [TextKitFormattingBarState.Saver]).
 */
@Composable
fun rememberTextKitFormattingBarState(colors: List<Color> = emptyList()): TextKitFormattingBarState {
    return rememberSaveable(saver = TextKitFormattingBarState.Saver) {
        TextKitFormattingBarState()
    }.also { it.colors = colors }
}

/**
 * Holds the selected/active state of the formatting bar toggles.
 *
 * @param isBold Whether the bold toggle is active at the caret.
 * @param isItalic Whether the italic toggle is active at the caret.
 * @param isUnderline Whether the underline toggle is active at the caret.
 * @param isStrikethrough Whether the strikethrough toggle is active at the caret.
 * @param isHighlight Whether the highlight toggle is active at the caret.
 * @param isLink Whether the caret sits on a link.
 * @param listItem The list-item type active at the caret (numbered, bulleted, task, or none).
 */
@Stable
class TextKitFormattingBarState(
    isBold: Boolean = false,
    isItalic: Boolean = false,
    isUnderline: Boolean = false,
    isStrikethrough: Boolean = false,
    isHighlight: Boolean = false,
    isTextStyle: Boolean = false,
    isLink: Boolean = false,
    listItem: TextEditorDecoratorItem = TextEditorListItem.None,
) {

    var isBold by mutableStateOf(isBold)
        private set

    var isItalic by mutableStateOf(isItalic)
        private set

    var isUnderline by mutableStateOf(isUnderline)
        private set

    var isStrikethrough by mutableStateOf(isStrikethrough)
        private set

    var isHighlight by mutableStateOf(isHighlight)
        private set

    var isLink by mutableStateOf(isLink)
        private set

    var listItem by mutableStateOf(listItem)
        private set

    var isTextStyle by mutableStateOf(isTextStyle)
        private set

    /**
     * Palette shown in the text-color picker popup. Set from [rememberTextKitFormattingBarState];
     * read by [com.jjrodcast.textkit.ui.TextKitFormattingBar].
     */
    var colors by mutableStateOf<List<Color>>(emptyList())
        internal set

    val isNumberedList get() = listItem == TextEditorListItem.NumberedList

    val isBulletedList get() = listItem == TextEditorListItem.BulletedList

    val isCheckList get() = listItem == TextEditorListItem.CheckList

    /**
     * Recomputes every toggle from the marks and list-item type active at the current caret,
     * so the bar mirrors the editor selection. Wire this to [TextKitState.lastMarks] and
     * [TextKitState.lastListItem] whenever the selection moves.
     */
    fun syncFrom(marks: Set<Mark>, listItem: TextEditorDecoratorItem) {
        isBold = marks.any { it is BoldMark }
        isItalic = marks.any { it is ItalicMark }
        isUnderline = marks.any { it is UnderlineMark }
        isStrikethrough = marks.any { it is StrikeMark }
        isHighlight = marks.any { it is HighlightMark }
        isLink = marks.any { it is LinkMark }
        isTextStyle = marks.any { it is TextStyleMark }
        this.listItem = listItem
    }

    companion object {

        val Saver = Saver<TextKitFormattingBarState, Any>(
            save = {
                arrayListOf(
                    save(it.isBold),
                    save(it.isItalic),
                    save(it.isUnderline),
                    save(it.isStrikethrough),
                    save(it.isHighlight),
                    save(it.isTextStyle),
                    save(it.isLink),
                    save(it.listItem.toTag()),
                )
            },
            restore = {
                @Suppress("UNCHECKED_CAST")
                val list = it as List<Any>
                TextKitFormattingBarState(
                    isBold = restore(list[0])!!,
                    isItalic = restore(list[1])!!,
                    isUnderline = restore(list[2])!!,
                    isStrikethrough = restore(list[3])!!,
                    isHighlight = restore(list[4])!!,
                    isTextStyle = restore(list[5])!!,
                    isLink = restore(list[6])!!,
                    listItem = restore<String>(list[7])!!.toListItem(),
                )
            }
        )

        private const val TAG_NUMBERED = "numbered"
        private const val TAG_BULLETED = "bulleted"
        private const val TAG_CHECK = "check"
        private const val TAG_NONE = "none"

        private fun TextEditorDecoratorItem.toTag(): String = when (this) {
            TextEditorListItem.NumberedList -> TAG_NUMBERED
            TextEditorListItem.BulletedList -> TAG_BULLETED
            TextEditorListItem.CheckList -> TAG_CHECK
            else -> TAG_NONE
        }

        private fun String.toListItem(): TextEditorDecoratorItem = when (this) {
            TAG_NUMBERED -> TextEditorListItem.NumberedList
            TAG_BULLETED -> TextEditorListItem.BulletedList
            TAG_CHECK -> TextEditorListItem.CheckList
            else -> TextEditorListItem.None
        }
    }
}
