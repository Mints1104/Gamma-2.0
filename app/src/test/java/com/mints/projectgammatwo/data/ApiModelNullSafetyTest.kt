package com.mints.projectgammatwo.data

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Gson ignores Kotlin nullability; these responses used to put null into non-null fields. */
class ApiModelNullSafetyTest {

    private val gson = Gson()

    @Test
    fun `malformed quests are dropped and the rest kept`() {
        val json = """
            {"quests": [
              {"name": "Good", "lat": 1.5, "lng": 2.5, "rewards_types": "7", "rewards_ids": "25",
               "rewards_amounts": "1"},
              {"name": "No position", "rewards_types": "7", "rewards_ids": "25", "rewards_amounts": "1"},
              {"name": "No reward", "lat": 1.0, "lng": 1.0},
              null
            ]}
        """.trimIndent()

        val quests = gson.fromJson(json, Quests.QuestsResponse::class.java).validQuests("NYC")

        assertEquals(listOf("Good"), quests.map { it.name })
        val good = quests.single()
        assertEquals("NYC", good.source)
        // Display-only text that was missing comes back empty rather than null.
        assertEquals("", good.rewardsString)
        assertEquals("", good.conditionsString)
    }

    @Test
    fun `a response without quests or filters has none`() {
        val response = gson.fromJson("{}", Quests.QuestsResponse::class.java)
        assertTrue(response.validQuests().isEmpty())
        assertEquals(null, response.filters)
    }

    @Test
    fun `missing filter sections read as empty`() {
        // A cache or response with only some sections: reading t4 or t7 used to throw.
        val filters = gson.fromJson("""{"t3": ["200", null, ""]}""", Quests.Filters::class.java)

        assertEquals(listOf("200"), filters.stardust)
        assertTrue(filters.candy.isEmpty())
        assertTrue(filters.candyXl.isEmpty())
        assertTrue(filters.megaEnergy.isEmpty())
        assertTrue(filters.encounters.isEmpty())
        assertTrue(filters.items.isEmpty())
        assertTrue(filters.pokecoins.isEmpty())
    }

    @Test
    fun `an invasion response without a list has no invasions`() {
        assertTrue(gson.fromJson("{}", InvasionResponse::class.java).invasions.orEmpty().isEmpty())
        val withNull = gson.fromJson("""{"invasions": [null]}""", InvasionResponse::class.java)
        assertTrue(withNull.invasions.orEmpty().filterNotNull().isEmpty())
    }
}
