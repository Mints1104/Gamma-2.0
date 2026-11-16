package com.mints.projectgammatwo.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Whole ▶/◀ sequences, the way OverlayService drives [OverlayWalk]. The helpers it uses were
 * tested on their own, but ◀ once stopped walking the list when there was no earlier stop, and
 * nothing tested them together.
 */
class OverlayWalkTest {

    private val list = listOf("A", "B", "C", "D", "E")

    /** ▶: the next eligible item, recorded as teleported to (the teleport always launches here). */
    private fun OverlayWalk<String>.forward(
        items: List<String> = list,
        isEligible: (String) -> Boolean = { true },
    ): String? = next(items, 1, isEligible)?.let { items[it] }?.also { recordTeleport(it) }

    /** ◀: back to the previous stop, or with none, one step back along the list unrecorded. */
    private fun OverlayWalk<String>.back(
        items: List<String> = list,
        isUsable: (String) -> Boolean = { true },
    ): String? {
        val stop = previousStop(isUsable)
        if (stop != null) {
            returnTo(stop, items)
            return stop.value
        }
        return next(items, -1) { true }?.let { items[it] }
    }

    @Test
    fun `back before any forward goes to the last item without recording it`() {
        val walk = OverlayWalk<String>()

        assertEquals("E", walk.back())
        assertTrue(walk.stops.isEmpty())
        // ▶ carries on from there, wrapping to the top.
        assertEquals("A", walk.forward())
    }

    @Test
    fun `back returns through earlier stops, then walks the list once they are used up`() {
        val walk = OverlayWalk<String>()
        repeat(3) { walk.forward() }

        assertEquals("B", walk.back())
        assertEquals("A", walk.back())
        // No earlier stop than A: one step back along the list from A.
        assertEquals("E", walk.back())
        assertEquals(listOf("A"), walk.stops)
    }

    @Test
    fun `forward after back carries on from there, skipping stops already visited`() {
        val walk = OverlayWalk<String>()
        repeat(3) { walk.forward() } // A, B, C

        assertEquals("B", walk.back())
        assertEquals("D", walk.forward())
        // C was left behind, as in a browser.
        assertEquals(listOf("A", "B", "D"), walk.stops)
        assertEquals("B", walk.back())
    }

    @Test
    fun `forward never revisits a stop`() {
        val walk = OverlayWalk<String>()
        val items = listOf("A", "B")

        assertEquals("A", walk.forward(items))
        assertEquals("B", walk.forward(items))
        assertNull(walk.forward(items))
    }

    @Test
    fun `forward skips items the caller rejects`() {
        val walk = OverlayWalk<String>()

        assertEquals("A", walk.forward())
        assertEquals("D", walk.forward { it != "B" && it != "C" })
    }

    @Test
    fun `back passes over stops that are no longer usable`() {
        val walk = OverlayWalk<String>()
        repeat(3) { walk.forward() } // A, B, C

        // B's invasion has ended.
        assertEquals("A", walk.back { it != "B" })
    }

    @Test
    fun `forward follows the last stop when the list is reordered`() {
        val walk = OverlayWalk<String>()
        walk.forward()
        walk.forward() // A, B

        assertEquals("Y", walk.forward(listOf("X", "B", "Y", "A")))
    }

    @Test
    fun `forward takes the item now in the last stop's place when it has left the list`() {
        val walk = OverlayWalk<String>()
        walk.forward()
        walk.forward() // A, B at index 1

        assertEquals("C", walk.forward(listOf("A", "C", "D")))
    }

    @Test
    fun `a new list starts from the top but keeps what was visited and the history`() {
        val walk = OverlayWalk<String>()
        walk.forward()
        walk.forward() // A, B

        walk.resetAnchor()
        assertEquals("C", walk.forward())
        assertEquals("B", walk.back())
    }

    @Test
    fun `the history keeps only the latest stops`() {
        val walk = OverlayWalk<String>(maxHistory = 3)
        repeat(5) { walk.forward() }

        assertEquals(listOf("C", "D", "E"), walk.stops)
    }

    @Test
    fun `items with the same handled key count as visited together`() {
        // Quests are handled by visit id, which two quests at one stop share.
        val walk = OverlayWalk<Pair<String, Int>>(handledKey = { it.first })
        val quests = listOf("stop1" to 1, "stop1" to 2, "stop2" to 1)

        val first = walk.next(quests, 1) { true }!!
        walk.recordTeleport(quests[first])

        assertEquals(2, walk.next(quests, 1) { true })
    }
}
