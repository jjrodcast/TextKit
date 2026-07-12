package com.jjrodcast.textkit.editor.utils

internal fun contains(baseStart: Int, baseEnd: Int, targetStart: Int, targetEnd: Int) =
    (baseStart <= targetStart && targetEnd <= baseEnd) &&
        (baseEnd != targetEnd || (targetStart == targetEnd) == (baseStart == baseEnd))

internal fun intersect(lStart: Int, lEnd: Int, rStart: Int, rEnd: Int) =
    maxOf(lStart, rStart) < minOf(lEnd, rEnd) ||
        contains(lStart, lEnd, rStart, rEnd) || contains(rStart, rEnd, lStart, lEnd)

internal fun packInts(val1: Int, val2: Int) = (val1.toLong() shl 32) or (val2.toLong() and 0xFFFFFFFF)

internal fun unpackInt1(value: Long) = (value shr 32).toInt()

internal fun unpackInt2(value: Long) = (value and 0xFFFFFFFF).toInt()
