package com.jjrodcast.textkit.editor.core.parser

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A block-level node the editor cannot render inline (a `table`, `image`, `document`, …). The whole
 * original JSON subtree is kept verbatim in [raw] and re-emitted unchanged on serialization, so **any**
 * block type round-trips without a dedicated model — this is the generic, opaque embed described in
 * `docs/EMBEDDED_BLOCKS.md`.
 *
 * In the editor it is shown as a single-line placeholder (see the `is EmbedBlock` branch in
 * `TextEditorConverter`); [raw] rides on the placeholder piece's `RichToken.payload`.
 */
@Serializable(with = EmbedBlockSerializer::class)
internal data class EmbedBlock(
    val embedType: String,
    val id: String,
    val raw: JsonElement,
) : BaseParagraph() {
    override val type: String get() = embedType
}

/** The block `"type"`s the editor treats as opaque embeds. Add a type here to make it embeddable. */
internal object EmbedTypes {
    const val Table = "table"
    const val Image = "image"
    const val Document = "document"

    val ALL: Set<String> = setOf(Table, Image, Document)

    fun isEmbed(type: String?): Boolean = type != null && type in ALL
}

/** The `RichToken.type` used for the placeholder piece of any embedded block. */
internal const val EmbedTokenType = "embed"

/** Builds an [EmbedBlock] from the raw JSON string stored on a placeholder piece. */
internal fun embedBlockFromPayload(id: String, payload: String): EmbedBlock {
    val element = TEXT_EDITOR_JSON.parseToJsonElement(payload)
    val embedType = element.embedType()
    return EmbedBlock(embedType = embedType, id = id, raw = element)
}

/** Reads the `"type"` field of an embed JSON node, defaulting to [EmbedTokenType] when absent. */
internal fun JsonElement.embedType(): String =
    jsonObject["type"]?.jsonPrimitive?.contentOrNull ?: EmbedTokenType

/**
 * Reads `attrs.url` from an embed's raw JSON, or null when the payload has no attrs, no url, or is
 * not valid JSON at all. Producers put the content's location here (an `image` node's source, a
 * `document` node's file); the editor never interprets it beyond display.
 */
internal fun embedUrlOf(payload: String): String? = embedAttrOf(payload, "url")

/** Reads `attrs.name` from an embed's raw JSON — a `document` node's display name. */
internal fun embedNameOf(payload: String): String? = embedAttrOf(payload, "name")

/**
 * Reads one string attribute from an embed's raw JSON, or null when the payload has no attrs, no
 * such key, a blank value, or is not valid JSON at all — embeds are an opaque passthrough, so a
 * malformed payload degrades to "attribute absent", never to an exception.
 */
private fun embedAttrOf(payload: String, key: String): String? = runCatching {
    TEXT_EDITOR_JSON.parseToJsonElement(payload)
        .jsonObject["attrs"]?.jsonObject?.get(key)?.jsonPrimitive?.contentOrNull
}.getOrNull()?.takeIf { it.isNotBlank() }

/**
 * Human-readable, display-only label for a placeholder (e.g. `📊 Tabla 1`). [indexByType] numbers
 * embeds of the same type in document order. This is what the user sees in the editor; the real
 * content lives in the payload.
 */
internal object EmbedLabels {
    fun format(embedType: String, indexByType: Int): String = when (embedType) {
        EmbedTypes.Table -> "📊 Tabla $indexByType"
        EmbedTypes.Image -> "🖼 Imagen $indexByType"
        EmbedTypes.Document -> "📄 Documento $indexByType"
        else -> "⧉ $embedType $indexByType"
    }
}

/**
 * Serializes an [EmbedBlock] by writing its [EmbedBlock.raw] JSON verbatim, and deserializes by
 * capturing the whole JSON object into [EmbedBlock.raw]. This is the "passthrough" that makes embeds
 * lossless and type-agnostic. Requires the JSON format (the editor always uses `TEXT_EDITOR_JSON`).
 */
internal object EmbedBlockSerializer : KSerializer<EmbedBlock> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("EmbedBlock")

    override fun serialize(encoder: Encoder, value: EmbedBlock) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: error("EmbedBlock can only be serialized to JSON")
        jsonEncoder.encodeJsonElement(value.raw)
    }

    override fun deserialize(decoder: Decoder): EmbedBlock {
        val jsonDecoder = decoder as? JsonDecoder
            ?: error("EmbedBlock can only be deserialized from JSON")
        val element = jsonDecoder.decodeJsonElement()
        val embedType = element.embedType()
        val id = element.jsonObject["attrs"]?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull
            ?: embedType
        return EmbedBlock(embedType = embedType, id = id, raw = element)
    }
}
