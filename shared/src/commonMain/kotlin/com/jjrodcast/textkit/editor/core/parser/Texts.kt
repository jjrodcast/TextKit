package com.jjrodcast.textkit.editor.core.parser

import com.jjrodcast.textkit.editor.utils.LINE_BREAK
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonNames

@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal sealed class BaseText {
    @JsonNames(TextKeyJsonName)
    abstract val key: String
}

@Serializable
@SerialName(TextType)
internal data class Text(
    val text: String,
    val marks: Set<Mark> = emptySet(),
) : BaseText() {
    @Transient
    override val key: String = TextType
}

@Serializable
@SerialName(HardBreakType)
internal data class HardBreak(
    val text: String = LINE_BREAK.toString(),
    val marks: Set<Mark> = emptySet(),
) : BaseText() {
    @Transient
    override val key: String = HardBreakType
}

internal const val TextKeyJsonName = "type"
internal const val TextType = "text"
internal const val HardBreakType = "hardBreak"
