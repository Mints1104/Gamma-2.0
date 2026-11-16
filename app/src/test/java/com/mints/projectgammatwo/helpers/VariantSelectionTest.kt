package com.mints.projectgammatwo.helpers

import com.mints.projectgammatwo.viewmodels.QuestsViewModel.SubVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The filter screen reconciles the live quest selection with the reward variants each time it
 * builds the quest filters. Dropping a reward ticked before its variants loaded undid Toggle All.
 */
class VariantSelectionTest {

    private fun variant(type: String, id: String, amount: String, condition: String, reward: String) =
        SubVariant(
            filterString = "$type,$amount,$id",
            label = "× $amount — $condition",
            condition = condition,
            type = type,
            id = id,
            amount = amount,
            reward = reward,
        )

    // Two candy variants of Pokémon 483 and two 200 Stardust variants.
    private val candySpin = variant("4", "483", "1", "Spin 14 stops", "Dialga Candy")
    private val candyRaid = variant("4", "483", "3", "Win a raid", "3 Dialga Candy")
    private val dustCatch = variant("3", "0", "200", "Catch 5 Pokémon", "200 Stardust")
    private val dustThrow = variant("3", "0", "200", "Make 3 Nice Throws", "200 Stardust")
    private val encounter = variant("7", "25", "1", "Spin 1 stop", "Pikachu")

    private val variants = mapOf(
        "4,0,483" to listOf(candySpin, candyRaid),
        "3,200,0" to listOf(dustCatch, dustThrow),
        "7,0,25" to listOf(encounter),
    )

    private fun key(v: SubVariant) = questConditionKey(v.type, v.id, v.amount, v.condition, v.reward)

    @Test
    fun `a reward ticked before its variants loaded gets all of them ticked`() {
        val filters = mutableSetOf("4,0,483", "3,200,0")
        val conditions = mutableSetOf<String>()

        assertTrue(reconcileVariantSelections(variants, filters, conditions))

        assertEquals(setOf("4,0,483", "3,200,0"), filters)
        assertEquals(setOf(key(candySpin), key(candyRaid), key(dustCatch), key(dustThrow)), conditions)
    }

    @Test
    fun `a reward with some variants ticked is left alone`() {
        val filters = mutableSetOf("4,0,483")
        val conditions = mutableSetOf(key(candyRaid))

        assertFalse(reconcileVariantSelections(variants, filters, conditions))

        assertEquals(setOf("4,0,483"), filters)
        assertEquals(setOf(key(candyRaid)), conditions)
    }

    @Test
    fun `a ticked variant gets its base filter back`() {
        val filters = mutableSetOf<String>()
        val conditions = mutableSetOf(key(dustThrow))

        assertTrue(reconcileVariantSelections(variants, filters, conditions))

        assertEquals(setOf("3,200,0"), filters)
        assertEquals(setOf(key(dustThrow)), conditions)
    }

    @Test
    fun `legacy per-variant filter strings become that variant's condition key`() {
        val filters = mutableSetOf(candyRaid.filterString)
        val conditions = mutableSetOf<String>()

        assertTrue(reconcileVariantSelections(variants, filters, conditions))

        assertEquals(setOf("4,0,483"), filters)
        assertEquals(setOf(key(candyRaid)), conditions)
    }

    @Test
    fun `nothing ticked stays unticked`() {
        val filters = mutableSetOf<String>()
        val conditions = mutableSetOf<String>()

        assertFalse(reconcileVariantSelections(variants, filters, conditions))

        assertTrue(filters.isEmpty())
        assertTrue(conditions.isEmpty())
    }

    @Test
    fun `an encounter keeps its base filter without ticking its variants`() {
        val filters = mutableSetOf("7,0,25")
        val conditions = mutableSetOf<String>()

        assertFalse(reconcileVariantSelections(variants, filters, conditions))

        assertEquals(setOf("7,0,25"), filters)
        assertTrue(conditions.isEmpty())
    }

    @Test
    fun `a ticked encounter variant gets its base filter back`() {
        val filters = mutableSetOf<String>()
        val conditions = mutableSetOf(key(encounter))

        assertTrue(reconcileVariantSelections(variants, filters, conditions))

        assertEquals(setOf("7,0,25"), filters)
    }

    @Test
    fun `filters for rewards without known variants are kept`() {
        val filters = mutableSetOf("2,0,1", "4,0,483")
        val conditions = mutableSetOf(key(candySpin), "2|1|5|Catch 3 Pokémon|5 Poké Balls")

        assertFalse(reconcileVariantSelections(variants, filters, conditions))

        assertEquals(setOf("2,0,1", "4,0,483"), filters)
        assertEquals(2, conditions.size)
    }

    @Test
    fun `no known variants changes nothing`() {
        val filters = mutableSetOf("4,0,483")
        val conditions = mutableSetOf<String>()

        assertFalse(reconcileVariantSelections(emptyMap(), filters, conditions))

        assertEquals(setOf("4,0,483"), filters)
    }
}
