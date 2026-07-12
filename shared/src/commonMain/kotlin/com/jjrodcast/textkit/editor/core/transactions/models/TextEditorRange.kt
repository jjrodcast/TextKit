package com.jjrodcast.textkit.editor.core.transactions.models

import com.jjrodcast.textkit.editor.utils.unpackInt1
import com.jjrodcast.textkit.editor.utils.unpackInt2
import kotlinx.serialization.Serializable

@Serializable
data class TextEditorRange(val start: Int, val end: Int) {

    internal constructor(offset: Int) : this(offset, offset)

    internal constructor(packedValue: Long) : this(unpackInt1(packedValue), unpackInt2(packedValue))

    /**
     * Represents the min of the start and end selection. You can use it instead of start property.
     */
    val min get() = minOf(start, end)

    /**
     * Represents the max of the start and end selection. You can use it instead of end property.
     */
    val max get() = maxOf(start, end)

    val length get() = max - min

    val collapsed get() = start == end

    val reversed get() = start > end

    fun coerceIn(minValue: Int, maxValue: Int): TextEditorRange {
        val newStart = start.coerceIn(minValue, maxValue)
        val newEnd = end.coerceIn(minValue, maxValue)
        return if (newStart == start && newEnd == end) this else TextEditorRange(newStart, newEnd)
    }

    fun intersects(other: TextEditorRange) = min < other.max && other.min < max

    operator fun contains(offset: Int) = offset in min until max

    override fun toString(): String {
        return "TextRange($start, $end)"
    }

    companion object {
        val Zero = TextEditorRange(0, 0)
    }
}
