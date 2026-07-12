package com.jjrodcast.textkit.editor.components

import com.jjrodcast.textkit.editor.components.TextEditorListItem.BulletedList
import com.jjrodcast.textkit.editor.components.TextEditorListItem.CheckList
import com.jjrodcast.textkit.editor.components.TextEditorListItem.NumberedList
import com.jjrodcast.textkit.editor.core.parser.BoldMark
import com.jjrodcast.textkit.editor.core.parser.HighlightMark
import com.jjrodcast.textkit.editor.core.parser.ItalicMark
import com.jjrodcast.textkit.editor.core.parser.StrikeMark
import com.jjrodcast.textkit.editor.core.parser.TextStyleAttrs
import com.jjrodcast.textkit.editor.core.parser.TextStyleMark
import com.jjrodcast.textkit.editor.core.parser.UnderlineMark
import com.jjrodcast.textkit.editor.core.piecetable.models.TextDecoratorModel.BulletDecoratorModel
import com.jjrodcast.textkit.editor.core.piecetable.models.TextDecoratorModel.NumberDecoratorModel
import com.jjrodcast.textkit.editor.core.piecetable.models.TextDecoratorModel.TaskDecoratorModel

interface TextEditorFormatItem

interface TextEditorDecoratorItem : TextEditorFormatItem {

    fun toTextDecoratorModel(count: Int = 1, level: Int = 1) = when (this) {
        BulletedList -> BulletDecoratorModel(level = level)
        NumberedList -> NumberDecoratorModel(count = count, level = level)
        CheckList -> TaskDecoratorModel(level = level)
        else -> null
    }

    fun toFinalListItemType(
        prevItemType: TextEditorDecoratorItem,
        currentLevel: Int = 1
    ): TextEditorDecoratorItem = if (currentLevel > 1) {
        if (this is TextEditorListItem.None) prevItemType
        else this
    } else this

}

sealed class TextEditorListItem : TextEditorDecoratorItem {

    data object NumberedList : TextEditorListItem()

    data object BulletedList : TextEditorListItem()

    data object CheckList : TextEditorListItem()

    data object None : TextEditorListItem()
}

sealed class TextEditorDecorator : TextEditorDecoratorItem {

    data object Blockquote : TextEditorDecorator()
}

sealed class TextEditorStyleItem : TextEditorFormatItem {

    data object Bold : TextEditorStyleItem()

    data object Italic : TextEditorStyleItem()

    data object Underline : TextEditorStyleItem()

    data object Strikethrough : TextEditorStyleItem()

    data object Highlight : TextEditorStyleItem()

    data class TextStyle(val color: String, val fontSize: Int) : TextEditorStyleItem()

    fun toMark() = when (this) {
        Bold -> BoldMark()
        Italic -> ItalicMark()
        Underline -> UnderlineMark()
        Strikethrough -> StrikeMark()
        Highlight -> HighlightMark()
        is TextStyle -> TextStyleMark(TextStyleAttrs(color = color, fontSize = fontSize))
    }
}
