package com.mints.projectgammatwo.helpers

/** Quest sub-variants (and the API's filter lists) are rediscovered at most this often. */
const val VARIANT_CACHE_MAX_AGE_MS = 24 * 60 * 60 * 1000L

/** A published quest list built from every source is reused for this long. */
const val QUESTS_FRESH_FOR_MS = 5 * 60_000L

/**
 * A list where some source failed is retried sooner, but not on every visit: while one map was
 * down, every return to the Quests tab refetched every source.
 */
const val PARTIAL_QUESTS_FRESH_FOR_MS = 60_000L

/**
 * Whether a quest fetch should also rediscover the reward sub-variants: one unfiltered request
 * plus one per 80 filters, per source. They used to be rediscovered on every fetch, although
 * the result is cached for a day and only changes with the daily quest rotation.
 *
 * @param forced the user asked for fresh filter data (the filter screen's refresh).
 * @param cachedAtMs when the cached sub-variants were written; 0 if never.
 */
fun shouldRediscoverVariants(
    forced: Boolean,
    hasCachedFilters: Boolean,
    hasCachedVariants: Boolean,
    cachedAtMs: Long,
    nowMs: Long,
): Boolean {
    if (forced || !hasCachedFilters || !hasCachedVariants) return true
    val age = nowMs - cachedAtMs
    // A negative age means the clock moved back; don't trust the cache then either.
    return age < 0 || age >= VARIANT_CACHE_MAX_AGE_MS
}

/**
 * Whether the published quest list can be reused instead of refetched.
 *
 * @param sameInputs it was built from the sources, filters and visited quests now in effect.
 * @param complete every source succeeded when it was built.
 */
fun isPublishedQuestListFresh(sameInputs: Boolean, ageMs: Long, complete: Boolean): Boolean =
    sameInputs && ageMs in 0 until if (complete) QUESTS_FRESH_FOR_MS else PARTIAL_QUESTS_FRESH_FOR_MS
