package com.jjrodcast.textkit.editor.core.parser

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LinkAttrs(val href: String, val target: String = "")

@Serializable
data class TaskListAttrs(val checked: Boolean = false)

@Serializable
data class ListAttrs(val start: Int = 1, val dir: TextDirection = TextDirection.Ltr)

/** Attributes for a `bulletList`: only the writing direction of the whole list. */
@Serializable
data class BulletListAttrs(val dir: TextDirection = TextDirection.Ltr)

/** Attributes for a `blockquote`: only the writing direction of the whole quote. */
@Serializable
data class BlockquoteAttrs(val dir: TextDirection = TextDirection.Ltr)

@Serializable
data class HeadingAttrs(
    val level: Int = HeadingLevels.H4,
    val textAlign: TextAlign = TextAlign.Left,
    val dir: TextDirection = TextDirection.Ltr
)

/**
 * Attributes for a plain [Paragraph]. Carries the ProseMirror/TipTap `textAlign` and `dir` attrs so
 * paragraph alignment and writing direction round-trip through the parser.
 */
@Serializable
data class ParagraphAttrs(
    val textAlign: TextAlign = TextAlign.Left,
    val dir: TextDirection = TextDirection.Ltr
)

/**
 * Writing direction of a block node (`paragraph`, `heading`, `blockquote`, `bulletList`,
 * `orderedList`), serialized as the `dir` attr string (the HTML `dir` attribute values). [Rtl] is for
 * right-to-left scripts such as Arabic or Hebrew. Any other value in a loaded document coerces to
 * [Ltr] thanks to [TEXT_EDITOR_JSON]'s `coerceInputValues`.
 */
@Serializable
enum class TextDirection {
    @SerialName(TextDirectionValues.Ltr)
    Ltr,

    @SerialName(TextDirectionValues.Rtl)
    Rtl;

    internal companion object {
        /**
         * The direction a block nested in a container ends up with: RTL wins when either the block
         * itself or its container is RTL. [Ltr] is the default, so it cannot override a container's
         * [Rtl] — an unset `dir` and an explicit `"ltr"` decode to the same value.
         */
        fun resolve(own: TextDirection, inherited: TextDirection): TextDirection =
            if (own == Rtl || inherited == Rtl) Rtl else Ltr
    }
}

internal object TextDirectionValues {
    const val Ltr = "ltr"
    const val Rtl = "rtl"
}

/**
 * Horizontal alignment for block nodes (`paragraph`, `heading`), serialized as the
 * ProseMirror/TipTap `textAlign` attr string. Only the four values below are supported; any other
 * value in a loaded document coerces to [Left] thanks to [TEXT_EDITOR_JSON]'s `coerceInputValues`.
 */
@Serializable
enum class TextAlign {
    @SerialName(TextAlignValues.Left)
    Left,

    @SerialName(TextAlignValues.Center)
    Center,

    @SerialName(TextAlignValues.Right)
    Right,

    @SerialName(TextAlignValues.Justify)
    Justify
}

internal object TextAlignValues {
    const val Left = "left"
    const val Center = "center"
    const val Right = "right"
    const val Justify = "justify"
}

@Serializable
data class TextStyleAttrs(val color: String? = "", val fontSize: Int = UNSET_FONT_SIZE) {

    companion object {
        /**
         * Sentinel meaning "no font size was provided in the document". A missing or `null`
         * `fontSize` decodes to this value (see [TEXT_EDITOR_JSON]'s `coerceInputValues`), and the
         * converter later resolves it to the configured default font size.
         */
        const val UNSET_FONT_SIZE = 0
    }
}

/**
 * Attributes shared by every atomic inline "trigger token" node (`mention`, `hashtag`, …).
 * Intentionally limited to `id` and `label` so a token round-trips as exactly
 * `{"type":"<node>","attrs":{"id":"…","label":"…"}}`. The trigger char (`@`, `#`, …) is a
 * presentation/config concern (see `TextKitTrigger`) and is never persisted here — adding a
 * defaulted field would leak into the output because [TEXT_EDITOR_JSON] encodes defaults.
 */
@Serializable
data class TokenAttrs(val id: String, val label: String? = "")

