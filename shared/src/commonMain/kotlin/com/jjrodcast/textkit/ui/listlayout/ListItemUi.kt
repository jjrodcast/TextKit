package com.jjrodcast.textkit.ui.listlayout

import com.jjrodcast.textkit.editor.core.piecetable.models.TextDecoratorModel
import com.jjrodcast.textkit.editor.core.piecetable.models.TextDecoratorModel.Companion.createDecoratorString
import com.jjrodcast.textkit.editor.core.transactions.models.TextEditorItem
import com.jjrodcast.textkit.editor.core.transactions.models.TextEditorParagraph
import com.jjrodcast.textkit.editor.utils.BULLET_DECORATOR_LEVEL_ONE
import com.jjrodcast.textkit.editor.utils.BULLET_DECORATOR_LEVEL_TWO
import com.jjrodcast.textkit.editor.utils.BULLET_DECORATOR_LEVEL_THREE
import com.jjrodcast.textkit.editor.utils.DOT
import com.jjrodcast.textkit.editor.utils.SPACE
import com.jjrodcast.textkit.editor.utils.TASK_DECORATOR_INTERACTIVE
import com.jjrodcast.textkit.editor.utils.TASK_DECORATOR_UNCHECKED_INTERACTIVE
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em

/** Matches [com.jjrodcast.textkit.ui.state.TextKitState.DefaultParagraphStyle] line metrics. */
internal val ListItemLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

internal fun TextStyle.withListLineMetrics(): TextStyle = copy(
    lineHeight = 1.5.em,
    lineHeightStyle = ListItemLineHeightStyle,
)

/**
 * Layout metadata for one engine paragraph when the editor display omits the list gutter from the
 * aligned text run (field text unchanged; UI-only split).
 */
internal data class EditorParagraphSegment(
    val fieldStart: Int,
    val fieldEnd: Int,
    val displayStart: Int,
    val displayEnd: Int,
    val gutterLength: Int,
    val gutter: TextDecoratorModel? = null,
    /** Whether this paragraph carries the blockquote attribute (#126) — drives the quote bar. */
    val quoted: Boolean = false,
)

/** Whether this paragraph carries the blockquote attribute (#126). */
internal fun TextEditorParagraph.isQuoted(): Boolean =
    children.any { it.decorator is TextDecoratorModel.BlockquoteDecorator }

/** True when this paragraph should render gutter and content in separate UI regions. */
internal fun TextEditorParagraph.usesSplitListLayout(): Boolean {
    val decorator = listDecoratorChild()?.decorator ?: return false
    return decorator !is TextDecoratorModel.BlockquoteDecorator
}

internal fun TextEditorParagraph.listDecoratorChild(): TextEditorItem? =
    children.firstOrNull { it.decorator != null }

internal fun TextEditorParagraph.decoratorFieldLength(): Int =
    if (usesSplitListLayout()) listDecoratorChild()?.text?.length ?: 0 else 0

internal fun buildEditorSegments(
    paragraphs: List<TextEditorParagraph>,
    fieldLength: Int,
    displayTextOf: (TextEditorItem, Int) -> String,
): List<EditorParagraphSegment> {
    val segments = mutableListOf<EditorParagraphSegment>()
    var displayCursor = 0

    paragraphs.forEach { paragraph ->
        val first = paragraph.children.firstOrNull() ?: return@forEach
        val last = paragraph.children.lastOrNull() ?: return@forEach
        val fieldStart = first.start
        val fieldEnd = last.end
        val fieldParagraphLength =
            paragraph.children.sumOf { displayTextOf(it, fieldLength).length }
        val gutterLength = paragraph.decoratorFieldLength()
        val displayLength = fieldParagraphLength - gutterLength

        segments += EditorParagraphSegment(
            fieldStart = fieldStart,
            fieldEnd = fieldEnd,
            displayStart = displayCursor,
            displayEnd = displayCursor + displayLength,
            gutterLength = gutterLength,
            gutter = if (gutterLength > 0) paragraph.listDecoratorChild()?.decorator else null,
            quoted = paragraph.isQuoted(),
        )
        displayCursor += displayLength
    }
    return segments
}

internal fun editorSegmentsNeedOffsetMapping(segments: List<EditorParagraphSegment>): Boolean =
    segments.any { it.gutterLength > 0 }

internal fun editorOverlaySegments(segments: List<EditorParagraphSegment>): List<EditorParagraphSegment> =
    segments.filter { it.gutter != null }

/**
 * Maps the caret to a field offset that targets the intended paragraph for block-level UI ops
 * (alignment, etc.) when list items use split gutter layout.
 *
 * When [displayOffset] is available, boundary `\n` characters between list items are resolved
 * using display space: visually at the next item's line → content start; still on the previous
 * item's line → left for trailing-break normalization.
 */
internal fun resolveParagraphCaretFieldOffset(
    rawOffset: Int,
    segments: List<EditorParagraphSegment>,
    textLength: Int,
    displayOffset: Int = rawOffset,
): Int {
    val offset = rawOffset.coerceIn(0, textLength)
    segments.forEachIndexed { index, segment ->
        if (segment.gutterLength == 0) return@forEachIndexed
        val contentStart = segment.fieldStart + segment.gutterLength
        when {
            offset in segment.fieldStart until contentStart -> return contentStart
            offset == segment.displayStart && offset < segment.fieldStart -> return contentStart
            offset == segment.fieldStart - 1 -> {
                val prev = segments.getOrNull(index - 1)
                when {
                    prev?.gutter == null && displayOffset >= segment.displayStart ->
                        return contentStart
                    prev?.gutter != null && displayOffset >= segment.displayStart ->
                        return contentStart
                }
            }
        }
    }
    return offset
}

/**
 * Full caret normalization for paragraph-level UI ops: list gutter/display fixes, then trailing
 * line-break adjustment so the engine targets one paragraph only.
 */
internal fun resolveParagraphOperationOffset(
    fieldOffset: Int,
    displayOffset: Int,
    segments: List<EditorParagraphSegment>,
    textLength: Int,
    isAtEndOfParagraph: (Int) -> Boolean,
    paragraphStart: (Int) -> Int,
): Int {
    val resolved = resolveParagraphCaretFieldOffset(
        rawOffset = fieldOffset,
        segments = segments,
        textLength = textLength,
        displayOffset = displayOffset,
    )
    return normalizeTrailingParagraphBreakCaret(
        offset = resolved,
        isEndOfParagraph = isAtEndOfParagraph(resolved),
        paragraphStart = paragraphStart(resolved),
    )
}

/**
 * When the caret rests on a paragraph's trailing line break, block ops should target that paragraph,
 * not the following one (which also intersects the same boundary offset).
 */
internal fun normalizeTrailingParagraphBreakCaret(
    offset: Int,
    isEndOfParagraph: Boolean,
    paragraphStart: Int,
): Int {
    if (!isEndOfParagraph) return offset
    return (offset - 1).coerceAtLeast(paragraphStart)
}

/** Marker glyph only (no list-indent tabs) for viewer gutter columns. */
internal fun TextDecoratorModel.viewerMarkerLabel(): String = when (this) {
    is TextDecoratorModel.NumberDecoratorModel -> "$count$DOT$SPACE"
    is TextDecoratorModel.BulletDecoratorModel -> when (level) {
        1 -> BULLET_DECORATOR_LEVEL_ONE
        2 -> BULLET_DECORATOR_LEVEL_TWO
        else -> BULLET_DECORATOR_LEVEL_THREE
    }
    is TextDecoratorModel.TaskDecoratorModel,
    is TextDecoratorModel.BlockquoteDecorator -> ""
}

/** Tab indentation prefix from [createDecoratorString], without the marker. */
internal fun TextDecoratorModel.viewerIndentLabel(): String {
    val full = createDecoratorString()
    val marker = when (this) {
        is TextDecoratorModel.TaskDecoratorModel ->
            if (checked) TASK_DECORATOR_INTERACTIVE else TASK_DECORATOR_UNCHECKED_INTERACTIVE
        else -> viewerMarkerLabel()
    }
    return if (marker.isNotEmpty() && full.endsWith(marker)) full.dropLast(marker.length) else ""
}
