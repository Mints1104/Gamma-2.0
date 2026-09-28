package com.mints.projectgammatwo.helpers

import com.mints.projectgammatwo.data.Invasion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InvasionNavigationTest {

    private val now = 1_000L

    /** An invasion with [secondsLeft] remaining at [now]. */
    private fun invasion(secondsLeft: Long) = Invasion(
        lat = secondsLeft.toDouble(), lng = 0.0,
        invasion_start = 0, invasion_end = now + secondsLeft,
        character = 4, type = 1, source = "NYC",
    )

    private val live = invasion(600)

    @Test
    fun `next from before the first item lands on the first`() {
        // The overlay starts at -1 after a refresh so ▶ doesn't skip item 0.
        assertEquals(0, nextLiveInvasionIndex(listOf(live, live), -1, 1, now))
    }

    @Test
    fun `skips invasions with under a minute left`() {
        val list = listOf(live, invasion(30), invasion(59), invasion(60))
        assertEquals(3, nextLiveInvasionIndex(list, 0, 1, now))
    }

    @Test
    fun `wraps around in both directions`() {
        val list = listOf(live, live, live)
        assertEquals(0, nextLiveInvasionIndex(list, 2, 1, now))
        assertEquals(2, nextLiveInvasionIndex(list, 0, -1, now))
    }

    @Test
    fun `returns to the current item when it is the only live one`() {
        val list = listOf(invasion(10), live, invasion(10))
        assertEquals(1, nextLiveInvasionIndex(list, 1, 1, now))
    }

    @Test
    fun `null when nothing is live or the list is empty`() {
        assertNull(nextLiveInvasionIndex(listOf(invasion(10), invasion(-5)), 0, 1, now))
        assertNull(nextLiveInvasionIndex(emptyList(), -1, 1, now))
    }

    @Test
    fun `an index past the end of a shrunk list still wraps`() {
        assertEquals(1, nextLiveInvasionIndex(listOf(live, live), 4, 1, now))
    }
}
