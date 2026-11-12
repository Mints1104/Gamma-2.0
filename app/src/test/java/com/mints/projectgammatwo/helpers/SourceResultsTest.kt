package com.mints.projectgammatwo.helpers

import org.junit.Assert.assertEquals
import org.junit.Test

class SourceResultsTest {

    private data class Item(val name: String, val source: String)

    private fun carry(fresh: List<Item>, previous: List<Item>, failed: Set<String>) =
        carryOverFailedSources(fresh, previous, failed) { it.source }

    private val nycOld = Item("nyc-old", "NYC")
    private val londonOld = Item("london-old", "LONDON")
    private val nycNew = Item("nyc-new", "NYC")

    @Test
    fun `a failed source keeps what the previous list showed from it`() {
        assertEquals(
            listOf(nycNew, londonOld),
            carry(fresh = listOf(nycNew), previous = listOf(nycOld, londonOld), failed = setOf("LONDON")),
        )
    }

    @Test
    fun `every source failing keeps the whole previous list`() {
        assertEquals(
            listOf(nycOld, londonOld),
            carry(fresh = emptyList(), previous = listOf(nycOld, londonOld), failed = setOf("NYC", "LONDON")),
        )
    }

    @Test
    fun `sources that answered are replaced, not merged`() {
        assertEquals(
            listOf(nycNew),
            carry(fresh = listOf(nycNew), previous = listOf(nycOld, londonOld), failed = emptySet()),
        )
    }

    @Test
    fun `a failed source with nothing listed before adds nothing`() {
        assertEquals(
            listOf(nycNew),
            carry(fresh = listOf(nycNew), previous = listOf(nycOld), failed = setOf("LONDON")),
        )
    }
}
