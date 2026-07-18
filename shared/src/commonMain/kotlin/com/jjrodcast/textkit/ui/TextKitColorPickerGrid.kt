package com.jjrodcast.textkit.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jjrodcast.textkit.ui.utils.TextKitPickerPallete

@Composable
fun TextKitColorPickerGrid(
    colors: List<Color>,
    selected: Color?,
    onColorSelected: (Color?) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 8,
) {
    val defaultModifier = Modifier.aspectRatio(1f).clip(CircleShape)

    LazyVerticalGrid(
        modifier = modifier,
        columns = GridCells.Fixed(columns),
        contentPadding = PaddingValues(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (colors.isNotEmpty()) {
            // "No color" swatch: a white circle with a red diagonal line. Picking it emits null, which
            // resets the text color. It is highlighted when the selection currently has no color.
            item {
                val isSelected = selected == null
                Box(
                    modifier = defaultModifier
                        .background(Color.White)
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) Color.Black else Color.LightGray,
                            shape = CircleShape
                        )
                        .clickable { onColorSelected(null) }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawLine(
                            color = Color.Red,
                            start = Offset(0f, size.height),
                            end = Offset(size.width, 0f),
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }
            }
        }
        items(colors) { color ->
            val isSelected = color == selected
            Box(
                modifier = defaultModifier
                    .background(color)
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) Color.Black else Color.LightGray,
                        shape = CircleShape
                    )
                    .clickable {
                        onColorSelected(color)
                    }
            )
        }
    }
}

@Preview(
    showBackground = true,
    backgroundColor = 0xFFEFEFEF,
    widthDp = 340,
    heightDp = 280
)
@Composable
private fun ColorPickerGridPreview() {
    var selected by remember { mutableStateOf<Color?>(Color.Red) }

    TextKitColorPickerGrid(
        modifier = Modifier.padding(8.dp),
        colors = TextKitPickerPallete.DefaultPallete,
        selected = selected,
        onColorSelected = {
            selected = it
        }
    )
}