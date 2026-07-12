package com.jjrodcast.textkit.editor.utils

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

internal fun <T> List<T>.insertOrDelete(index: T): List<T> {
    val list = this.toMutableList()
    if (list.contains(index)) list.remove(index) else list.add(index)
    return list
}

internal fun <T> List<T>.filterByIndices(indices: List<Int>): List<T> {
    return filterIndexed { index, _ -> index in indices }
}

internal fun <T> List<T>.filterByIndex(index: Int): T? {
    return filterIndexed { idx, _ -> idx == index }.firstOrNull()
}

internal fun <T> List<T>.indicesOf(elements: List<T>): List<Int> {
    return elements.map { indexOf(it) }
}

internal fun <T> List<T>.removeAll(items: List<T>): List<T> {
    return toMutableList().apply { removeAll(items) }
}

internal fun <T> List<T>.takeWhileIndexed(predicate: (index: Int, T) -> Boolean): List<T> {
    val list = ArrayList<T>()
    for ((index, item) in withIndex()) {
        if (!predicate(index, item))
            break
        list.add(item)
    }
    return list
}

internal inline fun <T> List<T>.indicesOf(predicate: (T) -> Boolean) =
    mapIndexedNotNull { index, value -> index.takeIf { predicate(value) } }

/**
 * Copied from [androidx](https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:text/text/src/main/java/androidx/compose/ui/text/android/TempListUtils.kt;l=33;drc=b2e3d878411b7fb1147455b1a204cddb7bee1a1b).
 */
@OptIn(ExperimentalContracts::class)
internal inline fun <T> List<T>.fastForEach(action: (T) -> Unit) {
    contract { callsInPlace(action) }
    for (index in indices) {
        val item = get(index)
        action(item)
    }
}

/**
 * Copied from [androidx](https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:text/text/src/main/java/androidx/compose/ui/text/android/TempListUtils.kt;l=50;drc=b2e3d878411b7fb1147455b1a204cddb7bee1a1b).
 */
@OptIn(ExperimentalContracts::class)
internal inline fun <T, R> List<T>.fastMap(
    transform: (T) -> R
): List<R> {
    contract { callsInPlace(transform) }
    val destination = ArrayList<R>(size)
    fastForEach { item ->
        destination.add(transform(item))
    }
    return destination
}

@OptIn(ExperimentalContracts::class)
internal inline fun <T, R> List<T>.fastMapIndexed(
    transform: (index: Int, T) -> R
): List<R> {
    contract { callsInPlace(transform) }
    val destination = ArrayList<R>(size)
    fastForEachIndexed { index, item ->
        destination.add(transform(index, item))
    }
    return destination
}

@OptIn(ExperimentalContracts::class)
internal inline fun <T> List<T>.fastForEachIndexed(action: (index: Int, T) -> Unit) {
    contract { callsInPlace(action) }
    for (index in indices) {
        val item = get(index)
        action(index, item)
    }
}

fun <T> List<T>.addOrRemove(item: T): List<T> {
    val list = toMutableList()
    if (list.contains(item)) list.remove(item) else list.add(item)
    return list
}
