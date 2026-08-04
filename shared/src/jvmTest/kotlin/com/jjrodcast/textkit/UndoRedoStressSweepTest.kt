package com.jjrodcast.textkit

import kotlin.test.Test

/**
 * The full undo/redo property sweep. JVM-only for the same reason as `EditingStressSweepTest`: a
 * run this long starves the browser test runner's event loop and its watchdog kills the tab. The
 * cross-platform smoke run lives in `UndoRedoStressTest`.
 */
class UndoRedoStressSweepTest {

    @Test
    fun full_sweep() {
        UndoRedoStress.run(0 until 400, 250)
    }
}
