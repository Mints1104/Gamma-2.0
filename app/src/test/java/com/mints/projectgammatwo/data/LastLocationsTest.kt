package com.mints.projectgammatwo.data

import com.mints.projectgammatwo.testutil.PrefsContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The lists sort from the last grunt battled and the last quest visited. The app and the overlay
 * each have their own view models; each used to cache its own copy, so the app never saw where
 * the overlay's ▶ had taken the player.
 */
class LastLocationsTest {

    @Test
    fun `a location saved by one instance is read by another`() {
        val context = PrefsContext()
        // The app's view model reads once, then the overlay records a battle and a visit.
        val app = LastLocations(context)
        assertNull(app.invasion())

        LastLocations(context).saveInvasion(40.7, -73.9)
        LastLocations(context).saveQuest(51.5, -0.12)

        assertEquals(40.7 to -73.9, app.invasion())
        assertEquals(51.5 to -0.12, app.quest())
    }

    @Test
    fun `the latest save wins`() {
        val locations = LastLocations(PrefsContext())
        locations.saveInvasion(1.0, 2.0)
        locations.saveInvasion(3.0, 4.0)

        assertEquals(3.0 to 4.0, locations.invasion())
    }

    @Test
    fun `tiny coordinates survive the round trip`() {
        // Double.toString writes these in scientific notation.
        val locations = LastLocations(PrefsContext())
        locations.saveQuest(0.00001, -0.0005)

        assertEquals(0.00001 to -0.0005, locations.quest())
    }

    @Test
    fun `older versions' float quest coordinates are still read`() {
        val context = PrefsContext()
        context.getSharedPreferences("last_visited_pref", 0).edit()
            .putFloat("lastVisitedLat", 1.5f)
            .putFloat("lastVisitedLng", -2.5f)
            .apply()

        assertEquals(1.5 to -2.5, LastLocations(context).quest())
    }

    @Test
    fun `unreadable values read as none`() {
        val context = PrefsContext()
        context.getSharedPreferences("last_invasion_pref", 0).edit()
            .putString("lastInvasion", "nowhere")
            .apply()

        assertNull(LastLocations(context).invasion())
        assertNull(LastLocations(context).quest())
    }

    @Test
    fun `a point set by hand is marked manual until a stop replaces it`() {
        val locations = LastLocations(PrefsContext())
        locations.set(LastLocations.Kind.QUEST, 35.6, 139.7, manual = true)
        assertEquals(35.6 to 139.7, locations.quest())
        assertTrue(locations.isManual(LastLocations.Kind.QUEST))

        locations.saveQuest(35.7, 139.8)
        assertFalse(locations.isManual(LastLocations.Kind.QUEST))
    }

    @Test
    fun `clearing one point leaves the other`() {
        val locations = LastLocations(PrefsContext())
        locations.saveInvasion(1.0, 2.0)
        locations.saveQuest(3.0, 4.0)

        locations.clear(LastLocations.Kind.QUEST)

        assertNull(locations.quest())
        assertFalse(locations.isManual(LastLocations.Kind.QUEST))
        assertEquals(1.0 to 2.0, locations.invasion())
    }

    @Test
    fun `clearing the quest point also forgets the legacy float keys`() {
        // Otherwise the old floats would be read back and the reset would do nothing.
        val context = PrefsContext()
        context.getSharedPreferences("last_visited_pref", 0).edit()
            .putFloat("lastVisitedLat", 1.5f)
            .putFloat("lastVisitedLng", -2.5f)
            .apply()

        LastLocations(context).clear(LastLocations.Kind.QUEST)

        assertNull(LastLocations(context).quest())
    }
}
