package com.mints.projectgammatwo.helpers

import com.mints.projectgammatwo.data.Quests.Quest
import com.mints.projectgammatwo.data.FilterPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestMatchingTest {

    private fun quest(
        name: String,
        types: String,
        ids: String,
        amounts: String,
        condition: String,
        reward: String,
    ) = Quest(
        name = name,
        lat = 0.0,
        lng = 0.0,
        rewardsString = reward,
        conditionsString = condition,
        image = "",
        rewardsTypes = types,
        rewardsAmounts = amounts,
        rewardsIds = ids,
    )

    // Two quests rewarding the same Pikachu encounter (type 7, id 25) with different tasks.
    private val spinOne = quest("A", "7", "25", "1", "Spin 1 stop", "Pikachu")
    private val spinFourteen = quest("B", "7", "25", "1", "Spin 14 stops", "Pikachu")
    private val catchFive = quest("C", "7", "25", "1", "Catch 5 Pokémon", "Pikachu")
    private val rareCandy = quest("D", "2", "1301", "3", "Win a raid", "3 Rare Candy")

    // Real API shape: a stardust reward's id is always "0"; the amount is what tells them apart.
    private val dust200Spin = quest("F", "3", "0", "200", "Spin 3 PokéStops", "200 Stardust")
    private val dust200Catch = quest("G", "3", "0", "200", "Catch 5 Pokémon", "200 Stardust")
    private val dust500 = quest("H", "3", "0", "500", "Hatch an egg", "500 Stardust")

    private fun key(q: Quest) =
        questConditionKey(q.rewardsTypes, q.rewardsIds, q.rewardsAmounts, q.conditionsString, q.rewardsString)

    private val all = listOf(spinOne, spinFourteen, catchFive, rareCandy)

    @Test
    fun `nothing is narrowed without variant selections`() {
        assertEquals(all, filterQuestsByConditions(all, listOf("7,0,25"), emptySet()))
        assertEquals(all, filterQuestsByConditions(all, emptyList(), setOf(key(spinOne))))
    }

    @Test
    fun `a ticked variant keeps only that variant of its base filter`() {
        val result = filterQuestsByConditions(all, listOf("7,0,25"), setOf(key(spinFourteen)))
        assertEquals(listOf(spinFourteen), result)
    }

    @Test
    fun `every ticked variant is kept, not just one`() {
        // Regression: unticking one variant used to stop the other ticked ones from matching.
        val result = filterQuestsByConditions(all, listOf("7,0,25"), setOf(key(spinOne), key(catchFive)))
        assertEquals(listOf(spinOne, catchFive), result)
    }

    @Test
    fun `a base filter without variant selections keeps all of its quests`() {
        // Variants are chosen for the Pikachu encounter only; the candy filter is untouched.
        val result = filterQuestsByConditions(all, listOf("7,0,25", "2,0,1301"), setOf(key(spinOne)))
        assertEquals(listOf(spinOne, rareCandy), result)
    }

    @Test
    fun `quests matching no enabled base filter are dropped`() {
        val result = filterQuestsByConditions(all, listOf("2,0,1301"), setOf(key(spinOne)))
        assertEquals(listOf(rareCandy), result)
    }

    @Test
    fun `keys saved before the reward label was added still match`() {
        val legacy = legacyQuestConditionKey("7", "25", "1", "Spin 14 stops")
        assertEquals(listOf(spinFourteen), filterQuestsByConditions(all, listOf("7,0,25"), setOf(legacy)))
    }

    @Test
    fun `a quest with several rewards is kept when any of them matches`() {
        val double = quest("E", "3,7", "0,25", "500,1", "Hatch an egg", "500 Stardust, Pikachu")
        val stardustOnly = filterQuestsByConditions(listOf(double), listOf("3,500,0"), setOf(key(spinOne)))
        assertEquals(listOf(double), stardustOnly)
    }

    @Test
    fun `stardust quests are kept while variants are ticked for other rewards`() {
        // Regression: stardust was matched by its id ("0"), so any variant selection (Toggle
        // All ticks them all) dropped every stardust quest.
        val quests = listOf(spinOne, dust200Spin, dust500)
        val result = filterQuestsByConditions(quests, listOf("7,0,25", "3,200,0"), setOf(key(spinOne)))
        assertEquals(listOf(spinOne, dust200Spin), result)
    }

    @Test
    fun `stardust variants narrow by task like other rewards`() {
        val quests = listOf(dust200Spin, dust200Catch, dust500)
        val result = filterQuestsByConditions(quests, listOf("3,200,0", "3,500,0"), setOf(key(dust200Catch)))
        // 200 has a variant ticked, so only that task; 500 has none, so all of its quests.
        assertEquals(listOf(dust200Catch, dust500), result)
    }

    @Test
    fun `base filters match the filter screen's for every reward type`() {
        assertEquals("3,200,0", questBaseFilter("3", "0", "200"))
        assertEquals("8,50,0", questBaseFilter("8", "0", "50"))
        assertEquals("7,0,25", questBaseFilter("7", "25", "1"))
        assertEquals("4,0,483", questBaseFilter("4", "483", "3"))
        assertEquals("2,0,1301", questBaseFilter("2", "1301", "3"))
        // Each base filter selects the same (type, discriminator) a quest reward produces.
        assertEquals("3" to "200", questFilterPrefix(questBaseFilter("3", "0", "200")))
    }

    @Test
    fun `stardust and pokecoins are keyed by amount, everything else by id`() {
        assertEquals("3" to "200", questFilterPrefix("3,200,0"))
        assertEquals("8" to "50", questFilterPrefix("8,50,0"))
        assertEquals("4" to "483", questFilterPrefix("4,0,483"))
        assertEquals(null, questFilterPrefix("4,0"))
    }

    @Test
    fun `condition keys use the format the filter screen stores`() {
        assertEquals("7|25|1|Spin 1 stop|Pikachu", key(spinOne))
    }

    @Test
    fun `a ticked variant that no longer exists doesn't hide its reward`() {
        // The daily rotation replaced "Spin 1 stop" with the other two tasks. The reward used to
        // vanish from the list until the selection was redone.
        val today = listOf(spinFourteen, catchFive, rareCandy)
        val result = filterQuestsByConditions(today, listOf("7,0,25"), setOf(key(spinOne)))
        assertEquals(listOf(spinFourteen, catchFive), result)
    }

    @Test
    fun `a gone variant doesn't widen a reward that still has a ticked one`() {
        val today = listOf(spinFourteen, catchFive)
        val result = filterQuestsByConditions(today, listOf("7,0,25"), setOf(key(spinOne), key(catchFive)))
        assertEquals(listOf(catchFive), result)
    }

    @Test
    fun `a ticked variant whose quests were all visited still narrows its reward`() {
        // The visited quests are gone from the list but not from the map: availableQuests says so.
        val unvisited = listOf(spinFourteen, catchFive)
        val result = filterQuestsByConditions(
            unvisited, listOf("7,0,25"), setOf(key(spinOne)), availableQuests = all
        )
        assertEquals(emptyList<Quest>(), result)
    }

    @Test
    fun `selections for variants no longer offered are pruned`() {
        val today = listOf(key(spinFourteen), key(catchFive), key(dust200Spin))
        val enabled = setOf(
            key(spinOne),                                            // gone: pruned
            key(catchFive),                                          // still offered
            legacyQuestConditionKey("7", "25", "1", "Spin 14 stops"),  // legacy form of one offered
            key(dust200Catch),                                       // 200 dust known, task gone
            key(dust500),                                            // 500 dust not offered today
            key(rareCandy),                                          // reward not offered today
            "spinda_form_3",                                         // not a condition key
        )
        assertEquals(
            setOf(
                key(catchFive),
                legacyQuestConditionKey("7", "25", "1", "Spin 14 stops"),
                key(dust500),
                key(rareCandy),
                "spinda_form_3",
            ),
            pruneStaleConditionKeys(enabled, today),
        )
    }

    @Test
    fun `nothing is pruned when the variants are unknown`() {
        val enabled = setOf(key(spinOne), key(rareCandy))
        assertEquals(enabled, pruneStaleConditionKeys(enabled, emptyList()))
    }

    @Test
    fun `a visit id round-trips, even for a stop whose name contains a bar`() {
        val plain = quest("Fountain", "7", "25", "1", "Spin", "Pikachu").copy(lat = 40.5, lng = -73.25)
        assertEquals(Triple("Fountain", 40.5, -73.25), parseQuestVisitId(questVisitId(plain)))

        val barred = plain.copy(name = "Cafe | Bar")
        assertEquals(Triple("Cafe | Bar", 40.5, -73.25), parseQuestVisitId(questVisitId(barred)))
    }

    @Test
    fun `malformed visit ids are rejected`() {
        assertEquals(null, parseQuestVisitId("no bars here"))
        assertEquals(null, parseQuestVisitId("name|40.5"))
        assertEquals(null, parseQuestVisitId("name|north|west"))
    }

    @Test
    fun `only the quest filter name that collides with the name list is reserved`() {
        assertTrue(FilterPreferences.isReservedQuestFilterName("filters"))
        assertFalse(FilterPreferences.isReservedQuestFilterName("Filters"))
        assertFalse(FilterPreferences.isReservedQuestFilterName("my filters"))
    }
}
