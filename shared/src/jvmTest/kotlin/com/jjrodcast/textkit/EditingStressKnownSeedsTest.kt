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
 * - #124: a replace across list items — the reorder's update targeting a marker inside the
 *   replace's removal window, the overlap #121 fixed on the delete path.
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

    @Test
    fun seeds_that_reproduced_issue_124_stay_clean() {
        intArrayOf(83626, 103174, 107802, 125854, 127674).forEach { seed ->
            EditingStress.run(seed..seed, 100)
        }
    }

    @Test
    fun seed_of_the_stale_caret_paste_corruption_stays_clean() {
        // A break on an empty nested numbered item returned a caret computed for a marker
        // demotion while the transactions deleted the marker whole; a multiline paste chaining
        // segments off that caret then wrote past the document end (#126's phase-2 sweep find).
        EditingStress.run(46382..46382, 60)
    }
}
