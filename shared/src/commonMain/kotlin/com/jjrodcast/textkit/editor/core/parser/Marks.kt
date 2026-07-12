package com.jjrodcast.textkit.editor.core.parser

import com.jjrodcast.textkit.editor.components.TextEditorStyleItem.Strikethrough
import com.jjrodcast.textkit.editor.components.TextEditorStyleItem.TextStyle
import com.jjrodcast.textkit.editor.core.parser.MarkTypes.Bold
import com.jjrodcast.textkit.editor.core.parser.MarkTypes.Highlight
import com.jjrodcast.textkit.editor.core.parser.MarkTypes.Italic
import com.jjrodcast.textkit.editor.core.parser.MarkTypes.Link
import com.jjrodcast.textkit.editor.core.parser.MarkTypes.Strike
import com.jjrodcast.textkit.editor.core.parser.MarkTypes.TextStyle
import com.jjrodcast.textkit.editor.core.parser.MarkTypes.Underline
import com.jjrodcast.textkit.editor.utils.SEPARATOR
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable(with = MarkSerializer::class)
sealed class Mark {
    abstract val type: String

    abstract fun createKey(): String

    fun toTextEditorStyleItem() = when (this) {
        is BoldMark -> Bold
        is ItalicMark -> Italic
        is HighlightMark -> Highlight
        is UnderlineMark -> Underline
        is StrikeMark -> Strikethrough
        is TextStyleMark -> TextStyle(
            fontSize = attrs.fontSize.toFloat(),
            color = attrs.color.orEmpty()
        )

        else -> null
    }

    internal companion object {

        fun areTheSame(leftMarks: Set<Mark>, rightMarks: Set<Mark>): Boolean {
            return leftMarks == rightMarks && leftMarks.size == rightMarks.size
        }
    }
}

@Serializable
@SerialName(Bold)
data class BoldMark(override val type: String = Bold) : Mark() {
    override fun createKey() = type
}

@Serializable
@SerialName(Italic)
data class ItalicMark(override val type: String = Italic) : Mark() {
    override fun createKey() = type
}

@Serializable
@SerialName(Highlight)
data class HighlightMark(override val type: String = Highlight) : Mark() {
    override fun createKey() = type
}

@Serializable
@SerialName(Underline)
data class UnderlineMark(override val type: String = Underline) : Mark() {
    override fun createKey() = type
}

@Serializable
@SerialName(Strike)
data class StrikeMark(override val type: String = Strike) : Mark() {
    override fun createKey() = type
}

@Serializable
@SerialName(Link)
data class LinkMark(val attrs: LinkAttrs) : Mark() {
    override val type: String = Link

    override fun createKey() = type
}

@Serializable
@SerialName(MarkTypes.None)
data object None : Mark() {
    override val type = MarkTypes.None

    override fun createKey() = type
}

@Serializable
@SerialName(TextStyle)
data class TextStyleMark(val attrs: TextStyleAttrs) : Mark() {
    override val type: String = TextStyle

    // TODO()
    fun isDefaultColor() = attrs.color == ""  //TextEditorColors.Charcoal900.hexValue

    override fun createKey() = "$type$SEPARATOR${attrs.color}$SEPARATOR${attrs.fontSize}"

    fun colorHasChanged(color: String?) = attrs.color != color

    fun fontSizeHasChanged(fontSize: Int?) = attrs.fontSize != fontSize

    companion object {

        //TODO()
        fun isDefault(mark: TextStyleMark): Boolean {
            val fontSize = mark.attrs.fontSize
            val color = mark.attrs.color
            return color == "" && fontSize == TextStyleAttrs.getDefaultFontSize()
        }

        //TODO()
        val Default
            get() = TextStyleMark(
                TextStyleAttrs(
                    color = "",
                    fontSize = TextStyleAttrs.getDefaultFontSize()
                )
            )
    }
}

internal object MarkTypes {
    const val Bold = "bold"
    const val Italic = "italic"
    const val Highlight = "highlight"
    const val Underline = "underline"
    const val Strike = "strike"
    const val Link = "link"
    const val TextStyle = "textStyle"
    const val None = "none"
}

internal const val MarkDiscriminator = "type"
internal const val MarkKeyJsonName = "type"

internal object MarkSerializer : JsonContentPolymorphicSerializer<Mark>(Mark::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<Mark> {
        return when (element.jsonObject[MarkDiscriminator]?.jsonPrimitive?.content) {
            Bold -> BoldMark.serializer()
            Italic -> ItalicMark.serializer()
            Highlight -> HighlightMark.serializer()
            Underline -> UnderlineMark.serializer()
            Strike -> StrikeMark.serializer()
            Link -> LinkMark.serializer()
            TextStyle -> TextStyleMark.serializer()
            else -> None.serializer()
        }
    }
}
