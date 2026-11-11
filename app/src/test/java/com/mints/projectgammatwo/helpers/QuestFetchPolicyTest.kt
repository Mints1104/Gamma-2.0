package com.mints.projectgammatwo.helpers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestFetchPolicyTest {

    private val now = 10 * VARIANT_CACHE_MAX_AGE_MS

    private fun rediscover(
        forced: Boolean = false,
        filters: Boolean = true,
        variants: Boolean = true,
        cachedAt: Long = now - 60_000L,
    ) = shouldRediscoverVariants(forced, filters, variants, cachedAt, now)

    @Test
    fun `a fresh cache is reused`() {
        assertFalse(rediscover())
    }

    @Test
    fun `a forced refresh or missing cache rediscovers`() {
        assertTrue(rediscover(forced = true))
        assertTrue(rediscover(filters = false))
        assertTrue(rediscover(variants = false))
        assertTrue(rediscover(cachedAt = 0L))
    }

    @Test
    fun `a day-old or future-dated cache rediscovers`() {
        assertTrue(rediscover(cachedAt = now - VARIANT_CACHE_MAX_AGE_MS))
        assertFalse(rediscover(cachedAt = now - VARIANT_CACHE_MAX_AGE_MS + 1))
        assertTrue(rediscover(cachedAt = now + 1))
    }

    @Test
    fun `a complete list stays fresh longer than a partial one`() {
        val age = PARTIAL_QUESTS_FRESH_FOR_MS + 1
        assertTrue(isPublishedQuestListFresh(sameInputs = true, ageMs = age, complete = true))
        assertFalse(isPublishedQuestListFresh(sameInputs = true, ageMs = age, complete = false))
        assertTrue(isPublishedQuestListFresh(sameInputs = true, ageMs = 1_000L, complete = false))
    }

    @Test
    fun `a list built from other inputs, or too old, is never fresh`() {
        assertFalse(isPublishedQuestListFresh(sameInputs = false, ageMs = 0L, complete = true))
        assertFalse(isPublishedQuestListFresh(sameInputs = true, ageMs = QUESTS_FRESH_FOR_MS, complete = true))
        assertFalse(isPublishedQuestListFresh(sameInputs = true, ageMs = -1L, complete = true))
    }
}
