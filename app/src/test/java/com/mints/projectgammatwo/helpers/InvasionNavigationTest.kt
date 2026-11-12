package com.mints.projectgammatwo.helpers

import com.mints.projectgammatwo.data.Invasion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InvasionNavigationTest {

    private val now = 1_000L

    /** An invasion with [secondsLeft] remaining at [now]; [id] keeps otherwise equal ones apart. */
    private fun invasion(secondsLeft: Long, id: Double = secondsLeft.toDouble()) = Invasion(
        lat = id, lng = 0.0,
        invasion_start = 0, invasion_end = now + secondsLeft,
        character = 4, type = 1, source = "NYC",
    )

    private val everything: (Any?) -> Boolean = { true }

    private fun live(id: Int) = invasion(600, id.toDouble())

    @Test
    fun `an invasion needs a minute left to be live`() {
        assertTrue(isLiveInvasion(invasion(60), now))
        assertFalse(isLiveInvasion(invasion(59), now))
        assertFalse(isLiveInvasion(invasion(-5), now))
    }

    @Test
    fun `with no anchor, next starts at the first item and previous at the last`() {
        val list = listOf(live(1), live(2), live(3))
        assertEquals(0, nextOverlayIndex(list, null, -1, 1, everything))
        assertEquals(2, nextOverlayIndex(list, null, -1, -1, everything))
    }

    @Test
    fun `continues from the anchor wherever it has moved to`() {
        val a = live(1); val b = live(2); val c = live(3)
        // b was at index 1 when teleported to; a refresh put two new items in front of it.
        val refreshed = listOf(live(10), live(11), a, b, c)
        assertEquals(4, nextOverlayIndex(refreshed, b, 1, 1, everything))
        assertEquals(2, nextOverlayIndex(refreshed, b, 1, -1, everything))
    }

    @Test
    fun `an anchor that left the list is replaced by its old position`() {
        val a = live(1); val c = live(3); val d = live(4)
        // b (index 1) was handled and dropped: c now sits in its place and is next.
        val list = listOf(a, c, d)
        assertEquals(1, nextOverlayIndex(list, live(2), 1, 1, everything))
        assertEquals(0, nextOverlayIndex(list, live(2), 1, -1, everything))
    }

    @Test
    fun `skips ineligible items in both directions`() {
        val list = listOf(live(1), live(2), live(3), live(4))
        val handled = setOf(list[1], list[2])
        val eligible = { inv: Invasion -> inv !in handled }
        assertEquals(3, nextOverlayIndex(list, list[0], 0, 1, eligible))
        assertEquals(0, nextOverlayIndex(list, list[3], 3, -1, eligible))
    }

    @Test
    fun `skips invasions with under a minute left`() {
        val list = listOf(live(1), invasion(30), invasion(59), invasion(60))
        assertEquals(3, nextOverlayIndex(list, list[0], 0, 1) { isLiveInvasion(it, now) })
    }

    @Test
    fun `wraps around in both directions`() {
        val list = listOf(live(1), live(2), live(3))
        assertEquals(0, nextOverlayIndex(list, list[2], 2, 1, everything))
        assertEquals(2, nextOverlayIndex(list, list[0], 0, -1, everything))
    }

    @Test
    fun `returns to the anchor when it is the only eligible item`() {
        val list = listOf(invasion(10, 1.0), live(2), invasion(10, 3.0))
        assertEquals(1, nextOverlayIndex(list, list[1], 1, 1) { isLiveInvasion(it, now) })
    }

    @Test
    fun `null when nothing is eligible or the list is empty`() {
        assertNull(nextOverlayIndex(listOf(invasion(10), invasion(-5)), null, -1, 1) { isLiveInvasion(it, now) })
        assertNull(nextOverlayIndex(emptyList<Invasion>(), null, -1, 1, everything))
    }

    @Test
    fun `back returns to the stop before the current one`() {
        val history = listOf(live(1), live(2), live(3))
        assertEquals(1, previousStopIndex(history, everything))
    }

    @Test
    fun `back passes over earlier stops whose invasion has ended`() {
        val history = listOf(live(1), invasion(10, 2.0), invasion(-30, 3.0), live(4))
        assertEquals(0, previousStopIndex(history) { isLiveInvasion(it, now) })
    }

    @Test
    fun `back has nowhere to go without an earlier usable stop`() {
        assertNull(previousStopIndex(emptyList<Invasion>(), everything))
        assertNull(previousStopIndex(listOf(live(1)), everything))
        assertNull(previousStopIndex(listOf(invasion(10, 1.0), live(2))) { isLiveInvasion(it, now) })
    }

    @Test
    fun `an old position past the end of a shrunk list still wraps`() {
        val list = listOf(live(1), live(2))
        // Position 4 of a two-item list wraps to 0, the item "in its place".
        assertEquals(0, nextOverlayIndex(list, live(9), 4, 1, everything))
        assertEquals(1, nextOverlayIndex(list, live(9), 4, -1, everything))
    }
}
