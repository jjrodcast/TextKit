package com.jjrodcast.textkit

import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.components.TextEditorListItem
import com.jjrodcast.textkit.editor.components.TextEditorStyleItem
import com.jjrodcast.textkit.editor.core.TextKitEditorManager
import com.jjrodcast.textkit.editor.core.parser.LinkAttrs
import com.jjrodcast.textkit.editor.core.parser.LinkMark
import com.jjrodcast.textkit.editor.core.parser.Mark
import com.jjrodcast.textkit.editor.core.transactions.models.TextEditorAction
import com.jjrodcast.textkit.editor.core.transactions.models.TextEditorSelectedMark
import com.jjrodcast.textkit.editor.core.transactions.models.TextEditorTransactionType
import com.jjrodcast.textkit.editor.core.transactions.text.TextTransaction

/**
 * Test helpers that drive the editor engine through its *real* entry points:
 *
 * - Text add / delete / replace go through [TextTransaction.onTextUpdated] (the same path the
 *   Compose `RichTextState` uses when a `TextField` reports a change).
 * - Marks / lists / colors / links go through [TextKitEditorManager.updateDocument].
 *
 * They intentionally assert on observable output (`text`, `getParagraphs()`, `toJson()`,
 * `getSearchMarkType()`, `checkDecorator()`), never on private state, so the suite keeps working
 * if the internals are refactored while behavior stays the same.
 */

internal fun editorFrom(json: String, isViewer: Boolean = false): TextKitEditorManager =
    TextKitEditorManager().apply { load(json, isViewer) }

/** Insert [textToAdd] at [offset], as if the user typed it. Returns the resulting cursor range. */
internal fun TextKitEditorManager.typeText(offset: Int, textToAdd: String): TextRange {
    val action = TextEditorAction.TextAdded(
        text = textToAdd,
        offset = offset,
        selection = TextRange(offset + textToAdd.length)
    )
    return TextTransaction.onTextUpdated(action, this).second
}

/** Delete [length] characters starting at [offset]. */
internal fun TextKitEditorManager.deleteText(offset: Int, length: Int): TextRange {
    val action = TextEditorAction.TextRemoved(
        offset = offset,
        length = length,
        selection = TextRange(offset)
    )
    return TextTransaction.onTextUpdated(action, this).second
}

/** Replace [removeLength] characters at [offset] with [textToAdd]. */
internal fun TextKitEditorManager.replaceText(
    offset: Int,
    removeLength: Int,
    textToAdd: String
): TextRange {
    val action = TextEditorAction.TextUpdated(
        removeLength = removeLength,
        text = textToAdd,
        offset = offset,
        selection = TextRange(offset + textToAdd.length)
    )
    return TextTransaction.onTextUpdated(action, this).second
}

/** Turn the given styles ON over [range] (toolbar-style toggle from "nothing selected"). */
internal fun TextKitEditorManager.applyStyle(range: TextRange, vararg styles: TextEditorStyleItem): Boolean =
    updateDocument(
        selection = range,
        prevSelectedMark = TextEditorSelectedMark(marks = emptySet()),
        currSelectedMark = TextEditorSelectedMark(marks = styles.map { it.toMark() }.toSet()),
        transactionType = TextEditorTransactionType.Format
    ).first

/** Turn the given styles OFF over [range], starting from the [existing] marks on the selection. */
internal fun TextKitEditorManager.removeStyle(
    range: TextRange,
    existing: Set<Mark>,
    vararg styles: TextEditorStyleItem
): Boolean {
    val toRemove = styles.map { it.toMark() }.toSet()
    return updateDocument(
        selection = range,
        prevSelectedMark = TextEditorSelectedMark(marks = existing),
        currSelectedMark = TextEditorSelectedMark(marks = existing - toRemove),
        transactionType = TextEditorTransactionType.Format
    ).first
}

/** Convert the paragraph(s) covered by [range] from [from] to [to] list type ([None] = plain paragraph). */
internal fun TextKitEditorManager.toListItem(
    range: TextRange,
    from: TextEditorListItem,
    to: TextEditorListItem
): Boolean = updateDocument(
    selection = range,
    prevSelectedMark = TextEditorSelectedMark(listItemSelectedValue = from),
    currSelectedMark = TextEditorSelectedMark(listItemSelectedValue = to),
).first

/** Set (or clear, with `null`) the text color over [range]. */
internal fun TextKitEditorManager.setColor(range: TextRange, color: String?): Boolean =
    updateDocument(
        selection = range,
        prevSelectedMark = TextEditorSelectedMark.NONE,
        currSelectedMark = TextEditorSelectedMark.NONE,
        transactionType = TextEditorTransactionType.Color(color)
    ).first

/** Add ([href] non-empty) or remove ([href] empty) a link over [range]. */
internal fun TextKitEditorManager.setLink(range: TextRange, href: String): Boolean {
    val marks = if (href.isEmpty()) emptySet() else setOf(LinkMark(LinkAttrs(href)))
    return updateDocument(
        selection = range,
        prevSelectedMark = TextEditorSelectedMark.NONE,
        currSelectedMark = TextEditorSelectedMark(marks = marks),
        transactionType = TextEditorTransactionType.Link(href)
    ).first
}

internal fun TextKitEditorManager.marksAt(range: TextRange): Set<Mark> =
    getSearchMarkType(range).marks

internal inline fun <reified T : Mark> Set<Mark>.has(): Boolean = any { it is T }

/** Offset of the first occurrence of [needle] in the current text stream, or -1. */
internal fun TextKitEditorManager.offsetOf(needle: String): Int = text.indexOf(needle)

/** Range spanning the first occurrence of [needle] in the current text stream. */
internal fun TextKitEditorManager.rangeOf(needle: String): TextRange {
    val start = offsetOf(needle)
    require(start >= 0) { "'$needle' not found in text: \"$text\"" }
    return TextRange(start, start + needle.length)
}

object SampleDocuments {

    const val SINGLE_PARAGRAPH = """
        {"type":"doc","content":[
          {"type":"paragraph","content":[{"type":"text","text":"Hello world"}]}
        ]}
    """

    const val TWO_PARAGRAPHS = """
        {"type":"doc","content":[
          {"type":"paragraph","content":[{"type":"text","text":"First paragraph"}]},
          {"type":"paragraph","content":[{"type":"text","text":"Second paragraph"}]}
        ]}
    """

    const val THREE_PARAGRAPHS = """
        {"type":"doc","content":[
          {"type":"paragraph","content":[{"type":"text","text":"Alpha"}]},
          {"type":"paragraph","content":[{"type":"text","text":"Beta"}]},
          {"type":"paragraph","content":[{"type":"text","text":"Gamma"}]}
        ]}
    """

    const val PARAGRAPH_WITH_BOLD = """
        {"type":"doc","content":[
          {"type":"paragraph","content":[
            {"type":"text","text":"normal "},
            {"type":"text","marks":[{"type":"bold"}],"text":"bolded"},
            {"type":"text","text":" tail"}
          ]}
        ]}
    """

    const val PARAGRAPH_WITH_LINK = """
        {"type":"doc","content":[
          {"type":"paragraph","content":[
            {"type":"text","text":"visit "},
            {"type":"text","marks":[{"type":"link","attrs":{"href":"https://autodesk.com","target":"_blank"}}],"text":"autodesk"},
            {"type":"text","text":" now"}
          ]}
        ]}
    """

    const val ORDERED_LIST = """
        {"type":"doc","content":[
          {"type":"orderedList","attrs":{"start":1},"content":[
            {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"one"}]}]},
            {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"two"}]}]}
          ]}
        ]}
    """

    const val TASK_LIST = """
        {"type":"doc","content":[
          {"type":"taskList","content":[
            {"type":"taskItem","attrs":{"checked":false},"content":[{"type":"paragraph","content":[{"type":"text","text":"buy milk"}]}]},
            {"type":"taskItem","attrs":{"checked":true},"content":[{"type":"paragraph","content":[{"type":"text","text":"walk dog"}]}]}
          ]}
        ]}
    """

    const val BLOCKQUOTE = """
        {"type":"doc","content":[
          {"type":"paragraph","content":[{"type":"text","text":"before"}]},
          {"type":"blockquote","content":[{"type":"paragraph","content":[{"type":"text","text":"quoted text"}]}]},
          {"type":"paragraph","content":[{"type":"text","text":"after"}]}
        ]}
    """
}
