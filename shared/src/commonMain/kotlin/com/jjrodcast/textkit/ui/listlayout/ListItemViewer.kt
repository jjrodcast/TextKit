package com.jjrodcast.textkit.ui.listlayout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jjrodcast.textkit.editor.core.piecetable.models.TextDecoratorModel
import com.jjrodcast.textkit.editor.core.transactions.models.TextEditorItem
import com.jjrodcast.textkit.editor.core.transactions.models.TextEditorParagraph
import com.jjrodcast.textkit.editor.core.parser.TextAlign as TextKitTextAlign
import com.jjrodcast.textkit.theme.TextKitTheme

internal sealed interface ViewerBlock {
    data class ListItem(
        val gutter: TextDecoratorModel,
        val content: AnnotatedString,
        val inlineContent: Map<String, InlineTextContent>,
        val textAlign: TextKitTextAlign,
    ) : ViewerBlock

    data class Paragraph(
        val text: AnnotatedString,
        val inlineContent: Map<String, InlineTextContent>,
        /** Whether the paragraph carries the blockquote attribute (#126) — renders the quote bar. */
        val quoted: Boolean = false,
    ) : ViewerBlock
}

internal fun buildViewerBlocks(
    paragraphs: List<TextEditorParagraph>,
    buildParagraph: (
        paragraph: TextEditorParagraph,
        includeDecorator: Boolean,
    ) -> Pair<AnnotatedString, Map<String, InlineTextContent>>,
): List<ViewerBlock> = paragraphs.map { paragraph ->
    if (paragraph.usesSplitListLayout()) {
        val gutter = paragraph.listDecoratorChild()?.decorator
            ?: error("split list layout requires a decorator")
        val (content, inlineContent) = buildParagraph(paragraph, false)
        ViewerBlock.ListItem(
            gutter = gutter,
            content = content,
            inlineContent = inlineContent,
            textAlign = paragraph.textAlign,
        )
    } else {
        val (text, inlineContent) = buildParagraph(paragraph, true)
        ViewerBlock.Paragraph(text = text, inlineContent = inlineContent, quoted = paragraph.isQuoted())
    }
}

@Composable
internal fun TextKitViewerBlocks(
    blocks: List<ViewerBlock>,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val lineStyle = remember(textStyle) { textStyle.withListLineMetrics() }
    val listGutters = remember(blocks) {
        blocks.filterIsInstance<ViewerBlock.ListItem>().map { it.gutter }
    }
    val markerColumnWidth = remember(listGutters, lineStyle, density) {
        viewerMarkerColumnWidth(listGutters, textMeasurer, lineStyle, density)
    }

    Column(modifier = modifier) {
        blocks.forEach { block ->
            when (block) {
                is ViewerBlock.ListItem -> ListItemRow(
                    gutter = block.gutter,
                    content = block.content,
                    inlineContent = block.inlineContent,
                    textAlign = block.textAlign,
                    lineStyle = lineStyle,
                    markerColumnWidth = markerColumnWidth,
                )

                is ViewerBlock.Paragraph -> if (block.quoted) {
                    // Quoted paragraph: accent bar + indent, the blockquote's visual treatment.
                    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .fillMaxHeight()
                                .background(
                                    color = TextKitTheme.colors.onSurfaceVariant,
                                    shape = RoundedCornerShape(2.dp)
                                )
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        BasicText(
                            text = block.text,
                            inlineContent = block.inlineContent,
                            style = lineStyle,
                        )
                    }
                } else BasicText(
                    text = block.text,
                    inlineContent = block.inlineContent,
                    style = lineStyle,
                )
            }
        }
    }
}

@Composable
private fun ListItemRow(
    gutter: TextDecoratorModel,
    content: AnnotatedString,
    inlineContent: Map<String, InlineTextContent>,
    textAlign: TextKitTextAlign,
    lineStyle: TextStyle,
    markerColumnWidth: Dp,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val indentLabel = gutter.viewerIndentLabel()
    val indentWidth = remember(indentLabel, lineStyle, density) {
        viewerMeasuredWidth(indentLabel, textMeasurer, lineStyle, density)
    }
    val firstLineHeight = remember(lineStyle, density) {
        viewerFirstLineHeight(textMeasurer, lineStyle, density)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = indentWidth),
        verticalAlignment = Alignment.Top,
    ) {
        ListItemGutter(
            gutter = gutter,
            textStyle = lineStyle,
            markerColumnWidth = markerColumnWidth,
            firstLineHeight = firstLineHeight,
        )
        BasicText(
            text = content,
            inlineContent = inlineContent,
            style = lineStyle.copy(textAlign = textAlign.toComposeTextAlign()),
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp),
        )
    }
}

@Composable
private fun ListItemGutter(
    gutter: TextDecoratorModel,
    textStyle: TextStyle,
    markerColumnWidth: Dp,
    firstLineHeight: Dp,
) {
    Box(
        modifier = Modifier
            .width(markerColumnWidth)
            .height(firstLineHeight),
        contentAlignment = Alignment.CenterEnd,
    ) {
        when (gutter) {
            is TextDecoratorModel.TaskDecoratorModel -> {
                CompositionLocalProvider(LocalRippleConfiguration provides null) {
                    Checkbox(
                        checked = gutter.checked,
                        colors = CheckboxDefaults.colors(
                            checkedColor = TextKitTheme.colors.primary,
                            uncheckedColor = TextKitTheme.colors.outline,
                            checkmarkColor = TextKitTheme.colors.onPrimary,
                            disabledCheckedColor = TextKitTheme.colors.onSurface.copy(alpha = 0.38f),
                            disabledUncheckedColor = TextKitTheme.colors.onSurface.copy(alpha = 0.38f),
                            disabledIndeterminateColor = TextKitTheme.colors.onSurface.copy(alpha = 0.38f),
                        ),
                        onCheckedChange = {},
                        modifier = Modifier.size(TASK_CHECKBOX_SIZE),
                    )
                }
            }

            else -> {
                BasicText(
                    text = gutter.viewerMarkerLabel(),
                    style = textStyle.copy(textAlign = TextAlign.End),
                    maxLines = 1,
                )
            }
        }
    }
}

private fun viewerFirstLineHeight(
    textMeasurer: TextMeasurer,
    textStyle: TextStyle,
    density: Density,
): Dp = with(density) {
    textMeasurer.measure("Ag", textStyle).size.height.toDp()
}

private fun viewerMarkerColumnWidth(
    gutters: List<TextDecoratorModel>,
    textMeasurer: TextMeasurer,
    textStyle: TextStyle,
    density: Density,
): Dp {
    if (gutters.isEmpty()) return 0.dp
    var maxPx = 0
    gutters.forEach { gutter ->
        when (gutter) {
            is TextDecoratorModel.TaskDecoratorModel -> {
                maxPx = maxOf(maxPx, with(density) { TASK_CHECKBOX_COLUMN_WIDTH.roundToPx() })
            }

            else -> {
                val label = gutter.viewerMarkerLabel()
                if (label.isNotEmpty()) {
                    maxPx = maxOf(maxPx, textMeasurer.measure(label, textStyle).size.width)
                }
            }
        }
    }
    return with(density) { maxPx.toDp() }
}

private fun viewerMeasuredWidth(
    label: String,
    textMeasurer: TextMeasurer,
    textStyle: TextStyle,
    density: Density,
): Dp {
    if (label.isEmpty()) return 0.dp
    return with(density) {
        textMeasurer.measure(label, textStyle).size.width.toDp()
    }
}

private val TASK_CHECKBOX_COLUMN_WIDTH = 24.dp
private val TASK_CHECKBOX_SIZE = 20.dp

private fun TextKitTextAlign.toComposeTextAlign(): TextAlign = when (this) {
    TextKitTextAlign.Left -> TextAlign.Left
    TextKitTextAlign.Center -> TextAlign.Center
    TextKitTextAlign.Right -> TextAlign.Right
    TextKitTextAlign.Justify -> TextAlign.Justify
}

/** Builds viewer [AnnotatedString] content for one paragraph (optionally skipping the decorator child). */
internal fun buildViewerParagraphContent(
    paragraph: TextEditorParagraph,
    defaultStyle: ParagraphStyle,
    toComposeAlign: (TextKitTextAlign) -> TextAlign,
    includeDecorator: Boolean,
    appendChild: AnnotatedString.Builder.(TextEditorItem) -> Unit,
): AnnotatedString = buildAnnotatedString {
    val composeAlign = if (!includeDecorator && paragraph.usesSplitListLayout()) {
        TextAlign.Left
    } else {
        toComposeAlign(paragraph.textAlign)
    }
    withStyle(defaultStyle.copy(textAlign = composeAlign)) {
        paragraph.children.forEach { child ->
            if (!includeDecorator && child.decorator != null) return@forEach
            appendChild(child)
        }
    }
}
