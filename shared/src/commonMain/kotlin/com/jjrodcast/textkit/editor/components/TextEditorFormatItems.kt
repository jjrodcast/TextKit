package com.jjrodcast.textkit.editor.components

import com.jjrodcast.textkit.editor.components.TextEditorListItem.BulletedList
import com.jjrodcast.textkit.editor.components.TextEditorListItem.CheckList
import com.jjrodcast.textkit.editor.components.TextEditorListItem.NumberedList
import com.jjrodcast.textkit.editor.core.parser.BoldMark
import com.jjrodcast.textkit.editor.core.parser.HighlightMark
import com.jjrodcast.textkit.editor.core.parser.ItalicMark
import com.jjrodcast.textkit.editor.core.parser.None
import com.jjrodcast.textkit.editor.core.parser.StrikeMark
import com.jjrodcast.textkit.editor.core.parser.TextStyleAttrs
import com.jjrodcast.textkit.editor.core.parser.TextStyleMark
import com.jjrodcast.textkit.editor.core.parser.UnderlineMark
import com.jjrodcast.textkit.editor.core.piecetable.models.TextDecoratorModel.BulletDecoratorModel
import com.jjrodcast.textkit.editor.core.piecetable.models.TextDecoratorModel.NumberDecoratorModel
import com.jjrodcast.textkit.editor.core.piecetable.models.TextDecoratorModel.TaskDecoratorModel
import kotlinx.serialization.Serializable

interface TextEditorFormatItem

interface TextEditorDecoratorItem : TextEditorFormatItem {

    fun toTextDecoratorModel(count: Int = 1, level: Int = 1) = when (this) {
        BulletedList -> BulletDecoratorModel(level = level)
        NumberedList -> NumberDecoratorModel(count = count, level = level)
        CheckList -> TaskDecoratorModel(level = level)
        else -> null
    }

}

@Serializable
sealed class TextEditorListItem : TextEditorDecoratorItem {

    @Serializable
    data object NumberedList : TextEditorListItem()

    @Serializable
    data object BulletedList : TextEditorListItem()

    @Serializable
    data object CheckList : TextEditorListItem()

    @Serializable
    data object None : TextEditorListItem()
}

@Serializable
sealed class TextEditorDecorator : TextEditorDecoratorItem {

    @Serializable
    data object Blockquote : TextEditorDecorator()
}

@Serializable
sealed class TextEditorStyleItem : TextEditorFormatItem {

    @Serializable
    data object Bold : TextEditorStyleItem()

    @Serializable
    data object Italic : TextEditorStyleItem()

    @Serializable
    data object Underline : TextEditorStyleItem()

    @Serializable
    data object Strikethrough : TextEditorStyleItem()

    @Serializable
    data object Highlight : TextEditorStyleItem()

    @Serializable
    data class TextStyle(val color: String, val fontSize: Float? = null) : TextEditorStyleItem()

    fun toMark() = when (this) {
        Bold -> BoldMark()
        Italic -> ItalicMark()
        Underline -> UnderlineMark()
        Strikethrough -> StrikeMark()
        Highlight -> HighlightMark()
        else -> None
    }
}

// Charcoal900
fun Pair<TextEditorColorModel, Int>.toMark(color: String?) =
    if (first.isValidModel) TextStyleMark(TextStyleAttrs(color = first.value, fontSize = second))
    else TextStyleMark(TextStyleAttrs(color = color, fontSize = second))

fun TextEditorDecoratorItem.toFinalListItemType(
    prevItemType: TextEditorDecoratorItem,
    currentLevel: Int = 1
): TextEditorDecoratorItem = if (currentLevel > 1) {
    if (this is TextEditorListItem.None) prevItemType
    else this
} else this
