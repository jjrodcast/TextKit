@file:OptIn(ExperimentalSerializationApi::class)

package com.jjrodcast.textkit.editor.core.parser

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable(with = ParagraphSerializer::class)
internal sealed class BaseParagraph {
    @JsonNames(ParagraphKeyJsonName)
    abstract val type: String
}

@Serializable
@SerialName(ParagraphTypes.Paragraph)
internal data class Paragraph(val content: List<BaseText> = emptyList()) : BaseParagraph() {
    override val type: String = ParagraphTypes.Paragraph
}

@Serializable
@SerialName(ParagraphTypes.None)
internal data object ParagraphNone : BaseParagraph() {
    override val type: String = ParagraphTypes.None
}

@Serializable
@SerialName(ParagraphTypes.Heading)
internal data class Heading(
    val attrs: HeadingAttrs = HeadingAttrs(),
    val content: List<BaseText>
) : BaseParagraph() {
    override val type: String = ParagraphTypes.Heading
}

internal val EmptyParagraph = Paragraph()

internal object ParagraphTypes {
    const val Paragraph = ParagraphType
    const val Heading = HeadingType
    const val None = ParagraphNoneType
}

/**
 * h1 { font-size: 24px;}
 * h2 { font-size: 22px;}
 * h3 { font-size: 18px;}
 * h4 { font-size: 16px;}
 * h5 { font-size: 12px;}
 * h6 { font-size: 10px;}
 */
internal object HeadingLevels {
    const val H1 = 1
    const val H2 = 2
    const val H3 = 3
    const val H4 = 4
    const val H5 = 5
    const val H6 = 6
}

internal object HeadingLevelsValues {
    const val H1 = 24
    const val H2 = 22
    const val H3 = 18
    const val H4 = 16
    const val H5 = 12
    const val H6 = 10
}

internal const val ParagraphType = "paragraph"
internal const val HeadingType = "heading"
internal const val ParagraphNoneType = "none"
internal const val ParagraphKeyJsonName = "type"
internal const val ParagraphDiscriminator = "type"

internal object ParagraphSerializer :
    JsonContentPolymorphicSerializer<BaseParagraph>(BaseParagraph::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<BaseParagraph> {
        return when (element.jsonObject[ParagraphDiscriminator]?.jsonPrimitive?.contentOrNull) {
            ParagraphTypes.Paragraph -> Paragraph.serializer()
            ParagraphTypes.Heading -> Heading.serializer()
            ListTypes.BulletList -> BulletedList.serializer()
            ListTypes.OrderedList -> OrderedList.serializer()
            ListTypes.TaskList -> TaskList.serializer()
            BlockquoteType.Blockquote -> Blockquote.serializer()
            else -> ParagraphNone.serializer()
        }
    }
}
