package com.mints.projectgammatwo.data

import com.mints.projectgammatwo.testutil.PrefsContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Saved filters are snapshots of the live selection, stored beside it in the same prefs file. */
class FilterPreferencesTest {

    private val context = PrefsContext()
    private val prefs = FilterPreferences(context)

    private val candy = setOf("4,0,483")
    private val candyConditions = setOf("4|483|3|Win a raid|3 Dialga Candy")
    private val dust = setOf("3,200,0")
    private val dustConditions = setOf("3|0|200|Catch 5 Pokémon|200 Stardust")

    @Test
    fun `a quest filter named filters is refused and the saved names survive`() {
        prefs.saveQuestSelection(candy, candyConditions)
        prefs.saveCurrentQuestFilter("mine")

        // "quest_filters" is where the saved names live; this snapshot used to overwrite them.
        prefs.saveCurrentQuestFilter("filters")

        assertEquals(setOf("mine"), prefs.listQuestFilterNames())
        assertEquals(mapOf("mine" to candy), prefs.getSavedQuestFilters())
    }

    @Test
    fun `loading a saved quest filter restores its rewards and its variant ticks`() {
        prefs.saveQuestSelection(candy, candyConditions)
        prefs.saveCurrentQuestFilter("raids")
        prefs.saveQuestSelection(dust, dustConditions)

        prefs.loadFilter("raids", "Quest")

        assertEquals(candy, prefs.getEnabledQuestFilters())
        assertEquals(candyConditions, prefs.getEnabledEncounterConditions())
        assertEquals("raids", prefs.getActiveQuestFilter())
        // The view models read the same file through QuestFilterPreferences.
        val questPrefs = QuestFilterPreferences(context)
        assertEquals(candy, questPrefs.getEnabledFilters())
        assertEquals(candyConditions, questPrefs.getEnabledEncounterConditions())
    }

    @Test
    fun `loading a quest filter drops legacy Spinda form entries`() {
        prefs.saveQuestSelection(candy, candyConditions + "spinda_form_3")
        prefs.saveCurrentQuestFilter("old")

        prefs.loadFilter("old", "Quest")

        assertEquals(candyConditions, prefs.getEnabledEncounterConditions())
    }

    @Test
    fun `deleting the active quest filter clears the live selection`() {
        prefs.saveQuestSelection(candy, candyConditions)
        prefs.saveCurrentQuestFilter("raids")
        prefs.loadFilter("raids", "Quest")

        prefs.deleteFilter("raids", "Quest")

        assertTrue(prefs.listQuestFilterNames().isEmpty())
        assertTrue(prefs.getEnabledQuestFilters().isEmpty())
        assertTrue(prefs.getEnabledEncounterConditions().isEmpty())
        assertEquals("", prefs.getActiveQuestFilter())
    }

    @Test
    fun `loading a saved rocket filter restores its characters`() {
        prefs.saveEnabledCharacters(setOf(4, 5))
        prefs.saveCurrentAsFilter("grunts")
        prefs.saveEnabledCharacters(setOf(41))

        prefs.loadFilter("grunts", "Rocket")

        assertEquals(setOf(4, 5), prefs.getEnabledCharacters())
        assertEquals("grunts", prefs.getActiveRocketFilter())
    }
}
