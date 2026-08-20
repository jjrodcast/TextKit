package com.jjrodcast.textkit.ui.listlayout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.jjrodcast.textkit.editor.core.parser.TextAlign as TextKitTextAlign
import com.jjrodcast.textkit.editor.core.piecetable.models.TextDecoratorModel
import com.jjrodcast.textkit.editor.core.piecetable.models.TextDecoratorModel.Companion.createDecoratorString
import com.jjrodcast.textkit.editor.core.transactions.models.TextEditorItem
import com.jjrodcast.textkit.editor.core.transactions.models.TextEditorParagraph
import kotlin.math.roundToInt

internal object ListItemEditorTransform {

    fun buildDisplayAnnotatedString(
        paragraphs: List<TextEditorParagraph>,
        fieldLength: Int,
        defaultStyle: ParagraphStyle,
        toComposeAlign: (TextKitTextAlign) -> TextAlign,
        displayTextOf: (TextEditorItem, Int) -> String,
        spanStyleOf: (TextEditorItem) -> SpanStyle,
    ): AnnotatedString = buildAnnotatedString {
        paragraphs.forEach { paragraph ->
            val splitLayout = paragraph.usesSplitListLayout()
            withStyle(paragraphStyle(paragraph, defaultStyle, toComposeAlign, splitLayout)) {
                paragraph.children.forEach { child ->
                    if (splitLayout && child.decorator != null) return@forEach
                    withStyle(spanStyleOf(child)) {
                        append(displayTextOf(child, fieldLength))
                    }
                }
            }
        }
    }

    fun offsetMapping(
        segments: List<EditorParagraphSegment>,
        totalDisplayLength: Int,
    ): OffsetMapping = if (editorSegmentsNeedOffsetMapping(segments)) {
        ListItemOffsetMapping(segments, totalDisplayLength)
    } else {
        OffsetMapping.Identity
    }

    private fun paragraphStyle(
        paragraph: TextEditorParagraph,
        defaultStyle: ParagraphStyle,
        toComposeAlign: (TextKitTextAlign) -> TextAlign,
        splitLayout: Boolean,
    ): ParagraphStyle {
        val base = defaultStyle.copy(textAlign = toComposeAlign(paragraph.textAlign))
        if (!splitLayout) {
            // Quoted paragraphs indent so the quote bar overlay has room at the container start.
            return if (paragraph.isQuoted()) base.copy(textIndent = TextIndent(firstLine = QUOTE_INDENT, restLine = QUOTE_INDENT)) else base
        }
        val gutter = paragraph.listDecoratorChild()?.decorator ?: return base
        val indent = gutterIndent(gutter)
        return base.copy(textIndent = TextIndent(firstLine = indent, restLine = indent))
    }

    private fun gutterIndent(decorator: TextDecoratorModel): TextUnit =
        (decorator.createDecoratorString().length * GUTTER_EM_FACTOR).em

    private const val GUTTER_EM_FACTOR = 0.55f
}

private class ListItemOffsetMapping(
    private val segments: List<EditorParagraphSegment>,
    private val totalDisplayLength: Int,
) : OffsetMapping {

    override fun originalToTransformed(offset: Int): Int {
        var skippedGutters = 0
        for (segment in segments) {
            if (offset <= segment.fieldStart) break
            if (segment.gutterLength == 0) continue
            when {
                offset >= segment.fieldEnd -> skippedGutters += segment.gutterLength
                offset < segment.fieldStart + segment.gutterLength -> return segment.displayStart
                else -> return segment.displayStart + (offset - segment.fieldStart - segment.gutterLength)
            }
        }
        return (offset - skippedGutters).coerceIn(0, totalDisplayLength)
    }

    override fun transformedToOriginal(offset: Int): Int {
        segments.forEachIndexed { index, segment ->
            if (offset < segment.displayStart) return@forEachIndexed
            val isLast = index == segments.lastIndex
            // Display ranges are consecutive: [displayStart, displayEnd) for interior segments.
            // Offset displayEnd is shared with the next segment's displayStart (caret after the
            // paragraph-separator space); it must map to the following paragraph, not the previous
            // one — otherwise consecutive empty list items collapse to the same field offset.
            val inSegment = offset < segment.displayEnd || (isLast && offset <= segment.displayEnd)
            if (!inSegment) return@forEachIndexed
            val local = offset - segment.displayStart
            return if (segment.gutterLength == 0) {
                segment.fieldStart + local
            } else {
                segment.fieldStart + segment.gutterLength + local
            }
        }
        return segments.lastOrNull()?.fieldEnd ?: offset
    }
}

/**
 * Draws list gutters at a fixed horizontal origin (container start), independent of content alignment.
 * Vertical position follows the laid-out display line for each split segment.
 *
 * The overlay should fill the text field parent ([Modifier.fillMaxSize]) so gutter Y tracks
 * layout without resizing its own box (avoids blink when textAlign changes).
 */
@Composable
internal fun ListItemEditorGutterOverlay(
    layoutResult: TextLayoutResult?,
    segments: List<EditorParagraphSegment>,
    displayLength: Int,
    textStyle: TextStyle,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    if (layoutResult == null) return

    val laidOutLength = layoutResult.layoutInput.text.length
    if (laidOutLength == 0) return

    // The layout lags the segments by a frame: right after a list item is removed the segments
    // already describe the shorter document while [layoutResult] still measures the longer one, so
    // every marker would be placed on whatever line the *old* text had at that offset — the whole
    // gutter jumps for one frame. Rather than mixing the two generations, keep painting the last
    // pair that agreed until the matching layout arrives (#112).
    val lastAgreed = remember { arrayOfNulls<List<GutterMarker>>(1) }
    val markers = if (laidOutLength == displayLength) {
        buildGutterMarkers(editorOverlaySegments(segments), layoutResult, laidOutLength)
            .also { lastAgreed[0] = it }
    } else {
        lastAgreed[0] ?: return
    }
    if (markers.isEmpty()) return

    Box(modifier = modifier) {
        val markerStyle = textStyle.copy(color = textColor)
        markers.forEach { marker ->
            Text(
                text = marker.label,
                style = markerStyle,
                modifier = Modifier.offset {
                    IntOffset(x = 0, y = marker.top.roundToInt())
                }
            )
        }
    }
}

/**
 * Draws the blockquote accent bar for every quoted paragraph (#126), positioned from the laid-out
 * display lines. Same generation guard as [ListItemEditorGutterOverlay]: while the layout lags the
 * segments by a frame, keep painting the last pair that agreed (#112).
 */
@Composable
internal fun BlockquoteEditorOverlay(
    layoutResult: TextLayoutResult?,
    segments: List<EditorParagraphSegment>,
    displayLength: Int,
    barColor: Color,
    modifier: Modifier = Modifier,
) {
    if (layoutResult == null) return
    val laidOutLength = layoutResult.layoutInput.text.length
    if (laidOutLength == 0) return

    val lastAgreed = remember { arrayOfNulls<List<QuoteBar>>(1) }
    val bars = if (laidOutLength == displayLength) {
        buildQuoteBars(segments, layoutResult, laidOutLength).also { lastAgreed[0] = it }
    } else {
        lastAgreed[0] ?: return
    }
    if (bars.isEmpty()) return

    Box(modifier = modifier) {
        val density = LocalDensity.current
        bars.forEach { bar ->
            Box(
                modifier = Modifier
                    .offset { IntOffset(x = 0, y = bar.top.roundToInt()) }
                    .size(width = 3.dp, height = with(density) { (bar.bottom - bar.top).toDp() })
                    .background(color = barColor, shape = RoundedCornerShape(2.dp))
            )
        }
    }
}

/** One quoted paragraph's accent bar, resolved to the vertical span of its laid-out lines. */
private data class QuoteBar(val top: Float, val bottom: Float)

/** Display indent for quoted paragraphs — the freed space hosts the quote bar. */
private val QUOTE_INDENT = 1.em

private fun buildQuoteBars(
    segments: List<EditorParagraphSegment>,
    layoutResult: TextLayoutResult,
    displayLength: Int,
): List<QuoteBar> = segments.mapNotNull { segment ->
    if (!segment.quoted) return@mapNotNull null
    val startOffset = segment.displayStart.coerceIn(0, displayLength - 1)
    val endOffset = (segment.displayEnd - 1).coerceIn(startOffset, displayLength - 1)
    val top = layoutResult.getLineTop(layoutResult.getLineForOffset(startOffset))
    val bottom = layoutResult.getLineBottom(layoutResult.getLineForOffset(endOffset))
    QuoteBar(top = top, bottom = bottom)
}

/** A list marker resolved to the vertical position of the line it belongs to. */
private data class GutterMarker(val label: String, val top: Float)

private fun buildGutterMarkers(
    overlaySegments: List<EditorParagraphSegment>,
    layoutResult: TextLayoutResult,
    displayLength: Int,
): List<GutterMarker> = overlaySegments.mapNotNull { segment ->
    val label = segment.gutter?.createDecoratorString().orEmpty()
    if (label.isEmpty()) return@mapNotNull null
    val displayOffset = segment.displayStart.coerceIn(0, displayLength - 1)
    val lineIndex = layoutResult.getLineForOffset(displayOffset)
    GutterMarker(label = label, top = layoutResult.getLineTop(lineIndex))
}
