package com.jjrodcast.textkit.editor.core.parser

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Inline atomic node representing a mention. Serializes as
 * `{"type":"mention","attrs":{"id":"…","label":"…"},"marks":[…]}`.
 *
 * A mention can carry inline [marks] (bold, italic, color, …) just like a [Text] node; they apply to
 * the whole atomic chip. Polymorphism is handled by the default sealed-class machinery of
 * [BaseText] (discriminator `"type"`), same as [Text]/[HardBreak].
 */
@Serializable
@SerialName(MentionType.Mention)
internal data class Mention(
    val attrs: MentionAttrs,
    val marks: Set<Mark> = emptySet(),
) : BaseText() {
    @Transient
    override val key: String = MentionType.Mention
}

internal object MentionType {
    const val Mention = "mention"

    /** Fallback trigger char used to render a mention's visible label when none is configured. */
    const val DEFAULT_MENTION_CHAR = '@'
}
