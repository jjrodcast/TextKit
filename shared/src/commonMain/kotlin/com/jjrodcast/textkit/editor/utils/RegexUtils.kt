package com.jjrodcast.textkit.editor.utils

internal object RegexUtils {

    private val rgbRegex = Regex("""rgb\(\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})\s*\)""")

    fun rgbTextToHex(color: String?): String? {
        if (color == null) return color

        val matchResult = rgbRegex.matchEntire(color)
        return if (matchResult != null) {
            val (r, g, b) = matchResult.destructured
            val red = r.toInt().coerceIn(0, 255).toString(16).padStart(2, '0')
            val green = g.toInt().coerceIn(0, 255).toString(16).padStart(2, '0')
            val blue = b.toInt().coerceIn(0, 255).toString(16).padStart(2, '0')
            "#$red$green$blue"
        } else {
            return color
        }
    }
}
