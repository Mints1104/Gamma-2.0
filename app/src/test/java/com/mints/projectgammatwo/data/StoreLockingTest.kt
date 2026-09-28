package com.mints.projectgammatwo.data

import com.mints.projectgammatwo.testutil.PrefsContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The deleted-invasion and visited-quest stores do read-modify-writes on one string set, from
 * several instances and threads. Without their process-wide lock, concurrent writers dropped each
 * other's entries: battled invasions reappeared and the daily count came out low.
 */
class StoreLockingTest {

    private val threads = 8
    private val perThread = 50

    /** Runs [work] on [threads] threads at once, each with its own index, and waits for all. */
    private fun concurrently(work: (thread: Int) -> Unit) {
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val futures = (0 until threads).map { t -> pool.submit { start.await(); work(t) } }
        start.countDown()
        futures.forEach { it.get(30, TimeUnit.SECONDS) }
        pool.shutdown()
    }

    private fun invasion(lat: Double, type: Int = 1) = Invasion(
        name = "stop", lat = lat, lng = 0.0, invasion_start = 0, invasion_end = 0,
        character = 4, type = type, source = "NYC",
    )

    @Test
    fun `concurrent deletions from separate instances are all kept`() {
        val context = PrefsContext()

        concurrently { t ->
            // A fresh instance per thread, as the view models and the overlay each have their own.
            val repo = DeletedInvasionsRepository(context)
            repeat(perThread) { i -> repo.addDeletedInvasion(invasion(t * 1000.0 + i)) }
        }

        assertEquals(threads * perThread, DeletedInvasionsRepository(context).getDeletionCountLast24Hours())
    }

    @Test
    fun `concurrent visits from separate instances are all kept`() {
        val context = PrefsContext()

        concurrently { t ->
            val prefs = VisitedQuestsPreferences(context)
            repeat(perThread) { i -> prefs.addVisitedQuest("quest|$t|$i") }
        }

        assertEquals(threads * perThread, VisitedQuestsPreferences(context).getVisitedQuests().size)
    }

    @Test
    fun `event invasion types are never recorded`() {
        val repo = DeletedInvasionsRepository(PrefsContext())
        listOf(7, 8, 9).forEach { repo.addDeletedInvasion(invasion(1.0, type = it)) }
        repo.addDeletedInvasion(invasion(2.0, type = 1))

        assertEquals(listOf(2.0), repo.getDeletedEntries().map { it.lat })
    }

    @Test
    fun `entries older than a day are pruned, and reading doesn't rewrite an unchanged set`() {
        val context = PrefsContext()
        val repo = DeletedInvasionsRepository(context)
        val now = System.currentTimeMillis()
        repo.setDeletedEntries(setOf(DeletedEntry(1.0, 1.0, now)))
        // Written directly: setDeletedEntries already drops old entries.
        val prefs = context.getSharedPreferences("deleted_invasions", 0)
        val stale = "2.0,2.0,${now - 25 * 60 * 60 * 1000}"
        prefs.edit().putStringSet("deleted_invasions_set", prefs.getStringSet("deleted_invasions_set", emptySet())!! + stale).apply()

        assertEquals(listOf(1.0), repo.getDeletedEntries().map { it.lat })
        val afterPrune = repo.fingerprint()
        repo.getDeletedEntries()
        assertEquals(afterPrune, repo.fingerprint())
    }

    @Test
    fun `legacy csv entries are still read`() {
        val context = PrefsContext()
        val now = System.currentTimeMillis()
        context.getSharedPreferences("deleted_invasions", 0).edit()
            .putStringSet("deleted_invasions_set", setOf("40.5,-73.5,$now")).apply()

        val repo = DeletedInvasionsRepository(context)

        assertTrue(repo.isInvasionDeleted(invasion(40.5).copy(lng = -73.5)))
        assertFalse(repo.isInvasionDeleted(invasion(40.6).copy(lng = -73.5)))
    }
}
