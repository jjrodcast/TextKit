package com.jjrodcast.textkit.ui.utils

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope

internal fun <T : Saver<Original, Saveable>, Original, Saveable> save(
    value: Original?,
    saver: T,
    scope: SaverScope
): Any {
    return value?.let { with(saver) { scope.save(value) } } ?: false
}

internal fun <T> save(value: T?): T? {
    return value
}

internal inline fun <T : Saver<Original, Saveable>, Original, Saveable, reified Result> restore(
    value: Saveable?,
    saver: T
): Result? {
    if (value == false) return null
    return value?.let { with(saver) { restore(value) } as Result }
}

internal inline fun <reified Result> restore(value: Any?): Result? {
    return value?.let { it as Result }
}
