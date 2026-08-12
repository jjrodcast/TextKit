package com.jjrodcast.textkit

import com.jjrodcast.textkit.editor.core.parser.embedNameOf
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

    @Test
    fun reads_the_name_attribute_with_the_same_leniency() {
        assertEquals(
            "Reporte Q3.pdf",
            embedNameOf("""{"type":"document","attrs":{"name":"Reporte Q3.pdf","url":"https://example.dev/q3.pdf"}}"""),
        )
        assertNull(embedNameOf("""{"type":"document","attrs":{"url":"https://example.dev/q3.pdf"}}"""))
        assertNull(embedNameOf("""{"type":"document","attrs":{"name":""}}"""))
        assertNull(embedNameOf("not json"))
    }
}
