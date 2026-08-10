package com.jjrodcast.textkit

import com.jjrodcast.textkit.editor.core.parser.embedUrlOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * `embedUrlOf` reads `attrs.url` from an embed's raw JSON — the source the popup renders for an
 * `image` node (#127). It must be lenient: embeds are an opaque passthrough, so payloads with no
 * attrs, no url, or malformed JSON yield null (the popup then falls back to the banner) rather
 * than throwing.
 */
class EmbedUrlTest {

    @Test
    fun reads_the_url_attribute() {
        assertEquals(
            "https://example.dev/photo.png",
            embedUrlOf("""{"type":"image","attrs":{"url":"https://example.dev/photo.png"}}"""),
        )
    }

    @Test
    fun missing_attrs_or_url_yield_null() {
        assertNull(embedUrlOf("""{"type":"image"}"""))
        assertNull(embedUrlOf("""{"type":"image","attrs":{}}"""))
        assertNull(embedUrlOf("""{"type":"image","attrs":{"url":""}}"""))
        assertNull(embedUrlOf("""{"type":"image","attrs":{"url":null}}"""))
    }

    @Test
    fun malformed_payloads_yield_null_rather_than_throwing() {
        assertNull(embedUrlOf("not json"))
        assertNull(embedUrlOf("""["array","not","object"]"""))
        assertNull(embedUrlOf(""))
    }
}
