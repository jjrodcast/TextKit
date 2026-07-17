package com.jjrodcast.textkit.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.jjrodcast.textkit.editor.core.TextKitEditorManager
import com.jjrodcast.textkit.ui.state.TextKitState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import textkit.shared.generated.resources.Res
import textkit.shared.generated.resources.close_text
import textkit.shared.generated.resources.remove_label
import textkit.shared.generated.resources.text_kit_banner
import kotlin.math.roundToInt

/**
 * Popup anchored to the embedded-block placeholder whose popup is open ([TextKitState.activeEmbed]).
 * Renders nothing when none is active.
 *
 * For a `table` it renders the actual table (parsed from the stored JSON) so the user can *see* it
 * without the editor having to render it inline; other embed types fall back to showing their raw
 * JSON. A **Eliminar** action removes the placeholder (and its JSON node) from the document.
 *
 * Place it in the same `Box` as the editor so it shares the coordinate space:
 * ```
 * Box {
 *     TextKitEditor(state = state)
 *     TextKitEmbedPopup(state = state)
 * }
 * ```
 */
@Composable
fun TextKitEmbedPopup(
    state: TextKitState,
    modifier: Modifier = Modifier,
    onClose: () -> Unit = { state.dismissEmbedPopup() },
    onRemove: () -> Unit = { state.removeActiveEmbed() },
) {
    val embed = state.activeEmbed ?: return
    val anchor = state.activeEmbedBoundingBox() ?: return

    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val gap = with(density) { 6.dp.roundToPx() }
        val maxW = constraints.maxWidth
        val maxH = constraints.maxHeight
        var cardSize by remember { mutableStateOf(IntSize.Zero) }

        // Clamp X within the container; place below the placeholder, flipping above if it overflows.
        val x = anchor.left.roundToInt().coerceIn(0, (maxW - cardSize.width).coerceAtLeast(0))
        val below = anchor.bottom.roundToInt() + gap
        val above = anchor.top.roundToInt() - gap - cardSize.height
        val y = if (below + cardSize.height <= maxH || above < 0) below else above

        EmbedPopupContent(
            embed = embed,
            onClose = onClose,
            onRemove = onRemove,
            modifier = Modifier
                .offset { IntOffset(x, y) }
                .onSizeChanged { cardSize = it },
        )
    }
}

@Composable
private fun EmbedPopupContent(
    embed: TextKitEditorManager.EmbedInfo,
    onClose: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.widthIn(max = 360.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = embed.embedType.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(Res.string.close_text))
                }
            }
            HorizontalDivider()

            when (embed.embedType) {
                "image" -> Image(
                    painter = painterResource(Res.drawable.text_kit_banner),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
                )

                "table" -> {
                    val rows = remember(embed.rawJson) { parseTableRows(embed.rawJson) }
                    if (rows.isNotEmpty()) TableView(rows)
                    else Text(text = embed.rawJson, style = MaterialTheme.typography.bodySmall)
                }

                // Any other embed: show the stored JSON so it is at least inspectable.
                else -> Text(text = embed.rawJson, style = MaterialTheme.typography.bodySmall)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onRemove) {
                    Text(stringResource(Res.string.remove_label))
                }
            }
        }
    }
}

/** A parsed table row: its cell texts and whether it is a header row. */
private data class DemoTableRow(val cells: List<String>, val isHeader: Boolean)

@Composable
private fun TableView(rows: List<DemoTableRow>) {
    Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
        rows.forEach { row ->
            Row {
                row.cells.forEach { cell ->
                    Box(
                        modifier = Modifier
                            .width(120.dp)
                            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = cell,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (row.isHeader) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

private val lenientJson = Json { ignoreUnknownKeys = true }

/**
 * Parses a ProseMirror `table` JSON into rows of cell texts. Returns an empty list for non-tables or
 * malformed input (the popup then falls back to showing the raw JSON).
 */
private fun parseTableRows(rawJson: String): List<DemoTableRow> = runCatching {
    val table = lenientJson.parseToJsonElement(rawJson).jsonObject
    if (table["type"]?.jsonPrimitive?.content != "table") return emptyList()
    val rows = table["content"]?.jsonArray ?: return emptyList()
    rows.map { rowElement ->
        val cells = rowElement.jsonObject["content"]?.jsonArray ?: JsonArray(emptyList())
        var isHeader = false
        val texts = cells.map { cellElement ->
            val cell = cellElement.jsonObject
            if (cell["type"]?.jsonPrimitive?.content == "tableHeader") isHeader = true
            cellText(cell["content"]?.jsonArray)
        }
        DemoTableRow(cells = texts, isHeader = isHeader)
    }
}.getOrElse { emptyList() }

/** Concatenates all `text` leaves inside a cell's paragraph content. */
private fun cellText(paragraphs: JsonArray?): String {
    if (paragraphs == null) return ""
    val builder = StringBuilder()
    paragraphs.forEach { paragraph ->
        paragraph.jsonObject["content"]?.jsonArray?.forEach { inline ->
            inline.jsonObject["text"]?.jsonPrimitive?.content?.let { builder.append(it) }
        }
    }
    return builder.toString()
}
