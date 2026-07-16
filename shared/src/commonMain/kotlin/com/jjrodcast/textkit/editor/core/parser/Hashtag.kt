package com.jjrodcast.textkit.editor.core.parser

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Inline atomic node representing a hashtag. Serializes as
 * `{"type":"hashtag","attrs":{"id":"…","label":"…"},"marks":[…]}`.
 *
 * Mirrors [Mention]: an atomic trigger token whose visible text is `#<label>` and whose identity
 * lives in [attrs]. The `#` is presentation only and is never persisted. Polymorphism is handled by
 * the default sealed-class machinery of [BaseText] (discriminator `"type"`).
 */
@Serializable
@SerialName(HashtagType.Hashtag)
internal data class Hashtag(
    override val attrs: TokenAttrs,
    override val marks: Set<Mark> = emptySet(),
) : BaseText(), InlineToken {
    @Transient
    override val key: String = HashtagType.Hashtag

    @Transient
    override val type: String = HashtagType.Hashtag
}

internal object HashtagType {
    const val Hashtag = "hashtag"

    /** Fallback trigger char used to render a hashtag's visible label when none is configured. */
    const val DEFAULT_HASHTAG_CHAR = '#'
}
