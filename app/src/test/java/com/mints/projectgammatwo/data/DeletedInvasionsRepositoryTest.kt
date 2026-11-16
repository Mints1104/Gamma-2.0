package com.mints.projectgammatwo.data

import com.mints.projectgammatwo.helpers.DeletedInvasions
import com.mints.projectgammatwo.testutil.PrefsContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A battled invasion is matched by stop and end time. The matching is tested in
 * InvasionListingTest; this checks the repository stores the end time it needs, without which a
 * later grunt at the same stop was hidden for the 24 hours an entry is kept.
 */
class DeletedInvasionsRepositoryTest {

    private val end = 1_700_000_000L

    private fun invasion(invasionEnd: Long, lat: Double = 40.7) = Invasion(
        name = "Fountain", lat = lat, lng = -73.9, invasion_start = invasionEnd - 1800,
        invasion_end = invasionEnd, character = 4, type = 1, source = "NYC",
    )

    @Test
    fun `a deleted invasion is stored with its end time`() {
        val repo = DeletedInvasionsRepository(PrefsContext())
        repo.addDeletedInvasion(invasion(end))

        val entry = repo.getDeletedEntries().single()
        assertEquals(end, entry.invasionEnd)
        assertEquals("NYC", entry.source)
    }

    @Test
    fun `a later invasion at the same stop is not hidden`() {
        val context = PrefsContext()
        DeletedInvasionsRepository(context).addDeletedInvasion(invasion(end))

        // Read back through another instance, as the list and the overlay each have their own.
        val deleted = DeletedInvasions(DeletedInvasionsRepository(context).getDeletedEntries())
        assertTrue(invasion(end) in deleted)
        assertFalse(invasion(end + 1800) in deleted)
        assertFalse(invasion(end, lat = 40.8) in deleted)
    }

    @Test
    fun `another source's report of the same invasion is hidden too`() {
        val repo = DeletedInvasionsRepository(PrefsContext())
        repo.addDeletedInvasion(invasion(end))

        // Two maps can report one invasion's end a few seconds apart.
        val deleted = DeletedInvasions(repo.getDeletedEntries())
        assertTrue(invasion(end + 30).copy(source = "LONDON") in deleted)
    }
}
