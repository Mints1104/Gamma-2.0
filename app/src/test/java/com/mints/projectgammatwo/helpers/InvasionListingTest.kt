package com.mints.projectgammatwo.helpers

import com.mints.projectgammatwo.data.DeletedEntry
import com.mints.projectgammatwo.data.Invasion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InvasionListingTest {

    private fun invasion(
        lat: Double,
        lng: Double = 0.0,
        start: Long = 0,
        end: Long = 10_000,
        character: Int = 4,
        type: Int = 1,
    ) = Invasion(
        name = "stop $lat,$lng",
        lat = lat,
        lng = lng,
        invasion_start = start,
        invasion_end = end,
        character = character,
        type = type,
        source = "NYC",
    )

    @Test
    fun `keeps only enabled, unbattled, unexpired invasions`() {
        val kept = invasion(1.0, character = 4)
        val disabled = invasion(2.0, character = 5)
        val battled = invasion(3.0, character = 4)
        val ended = invasion(4.0, character = 4, end = 100)

        val result = filterInvasions(
            listOf(kept, disabled, battled, ended),
            enabledCharacters = setOf(4),
            deleted = DeletedInvasions(listOf(DeletedEntry(3.0, 0.0, timestamp = 0, invasionEnd = 10_000))),
            nowSeconds = 100,
        )

        assertEquals(listOf(kept), result)
    }

    @Test
    fun `event grunt types are mapped to their filter characters before filtering`() {
        val type8 = invasion(1.0, character = 99, type = 8)
        val type9 = invasion(2.0, character = 99, type = 9)

        val onlyOne = filterInvasions(listOf(type8, type9), setOf(1), DeletedInvasions.NONE, nowSeconds = 0)
        val onlyZero = filterInvasions(listOf(type8, type9), setOf(0), DeletedInvasions.NONE, nowSeconds = 0)

        assertEquals(listOf(1.0), onlyOne.map { it.lat })
        assertEquals(1, onlyOne.single().character)
        assertEquals(listOf(2.0), onlyZero.map { it.lat })
    }

    @Test
    fun `time order is newest first and stable for equal start times`() {
        val a = invasion(1.0, start = 10)
        val b = invasion(2.0, start = 30)
        val c = invasion(3.0, start = 30)
        val d = invasion(4.0, start = 20)

        val sorted = sortAndCapInvasions(listOf(a, b, c, d), distanceFrom = null)

        // b and c started together: they must keep their input order, every time.
        assertEquals(listOf(b, c, d, a), sorted)
        assertEquals(sorted, sortAndCapInvasions(sorted, distanceFrom = null))
    }

    @Test
    fun `distance order is nearest first`() {
        val far = invasion(0.5)
        val near = invasion(0.001)
        val middle = invasion(0.1)

        val sorted = sortAndCapInvasions(listOf(far, near, middle), distanceFrom = 0.0 to 0.0)

        assertEquals(listOf(near, middle, far), sorted)
    }

    @Test
    fun `the cap applies after sorting, so the nearest invasions are never cut`() {
        // 600 invasions, with the ten nearest at the end of the API's order.
        val distant = (1..590).map { invasion(1.0 + it * 0.001) }
        val nearest = (1..10).map { invasion(it * 0.0001) }

        val sorted = sortAndCapInvasions(distant + nearest, distanceFrom = 0.0 to 0.0, max = 500)

        assertEquals(500, sorted.size)
        assertEquals(nearest, sorted.take(10))
    }

    @Test
    fun `the cap applies after sorting, so the newest invasions are never cut`() {
        val older = (1..590).map { invasion(it.toDouble(), start = it.toLong()) }
        val newest = (1..10).map { invasion(-it.toDouble(), start = 10_000L - it) }

        val sorted = sortAndCapInvasions(older + newest, distanceFrom = null, max = 500)

        assertEquals(500, sorted.size)
        assertEquals(newest, sorted.take(10))
    }

    @Test
    fun `distanceMeters matches a known distance`() {
        // One degree of latitude is about 111.2 km.
        val meters = distanceMeters(0.0, 0.0, 1.0, 0.0)
        assertTrue("was $meters", meters in 111_100.0..111_300.0)
        assertEquals(0.0, distanceMeters(40.7, -73.9, 40.7, -73.9), 1e-9)
    }

    @Test
    fun `a battled invasion hides only itself, not a later one at the same stop`() {
        val battled = DeletedInvasions(listOf(DeletedEntry(1.0, 0.0, timestamp = 0, invasionEnd = 2_000)))
        assertTrue(invasion(1.0, end = 2_000) in battled)
        // Another source reporting the same invasion a little differently.
        assertTrue(invasion(1.0, end = 2_090) in battled)
        // The next invasion at that stop, ending half an hour later.
        assertFalse(invasion(1.0, end = 3_800) in battled)
        assertFalse(invasion(2.0, end = 2_000) in battled)
    }

    @Test
    fun `entries without an end time still hide every invasion at their stop`() {
        val legacy = DeletedInvasions(listOf(DeletedEntry(1.0, 0.0, timestamp = 0)))
        assertTrue(invasion(1.0, end = 2_000) in legacy)
        assertTrue(invasion(1.0, end = 9_000) in legacy)
    }
}
