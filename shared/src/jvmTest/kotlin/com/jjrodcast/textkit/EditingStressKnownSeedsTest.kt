package com.jjrodcast.textkit

import kotlin.test.Test

/**
 * Seeds outside the sweep's range that reproduced #120's corruption — a delete over an embed
 * placeholder adjacent to list items leaving a duplicated or fragmented marker. Each replays the
 * exact churn that manufactured the corrupting piece layout, so a regression in the delete path's
 * reorder handling fails here with the seed and step named. (Seed 197, the variant inside the
 * sweep's range, is covered by `EditingStressSweepTest`.)
 */
class EditingStressKnownSeedsTest {

    @Test
    fun seeds_that_reproduced_issue_120_stay_clean() {
        intArrayOf(12994, 23942, 24674, 28998, 34398).forEach { seed ->
            EditingStress.run(seed..seed, 30)
        }
    }
}
