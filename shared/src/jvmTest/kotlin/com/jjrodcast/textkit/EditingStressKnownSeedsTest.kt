package com.jjrodcast.textkit

import kotlin.test.Test

/**
 * Seeds outside the sweep's range that reproduced real corruption — pinned so a regression in the
 * delete path or the list toggles fails here with the seed and step named.
 *
 * - #120: a delete over an embed placeholder adjacent to list items duplicated or fragmented a
 *   marker (reorder transactions overlapping the delete window). Seed 197, the in-range variant,
 *   is covered by `EditingStressSweepTest`.
 * - #122: a delete starting inside a marker sheared the atomic decorator piece, and a list toggle
 *   whose selection ended inside an embed placeholder replaced the placeholder's characters with
 *   the marker (Insert/Update membership keyed on re-indexed positions).
 */
class EditingStressKnownSeedsTest {

    @Test
    fun seeds_that_reproduced_issue_120_stay_clean() {
        intArrayOf(12994, 23942, 24674, 28998, 34398).forEach { seed ->
            EditingStress.run(seed..seed, 30)
        }
    }

    @Test
    fun seeds_that_reproduced_issue_122_stay_clean() {
        intArrayOf(44852, 45717, 47417, 57326, 66142, 69496).forEach { seed ->
            EditingStress.run(seed..seed, 60)
        }
    }
}
