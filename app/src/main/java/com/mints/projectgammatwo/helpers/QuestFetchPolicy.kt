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

/**
 * Whether a variant discovery heard from every source about every reward, so that a variant
 * missing from its result is really gone rather than on a source that failed. Only then may
 * selections for missing variants be pruned ([pruneStaleConditionKeys]); pruning after a partial
 * discovery would delete the user's selections for variants offered only on the failed source.
 *
 * @param selectedSources how many sources are enabled.
 * @param services how many of them have a known URL (one request set each).
 * @param unfilteredResults one per service; null where the request failed.
 * @param chunkResults one per service and filter chunk; null where the request failed. Empty when
 *   there was no filter list to ask about, in which case nothing was asked reward by reward.
 */
fun isVariantDiscoveryComplete(
    selectedSources: Int,
    services: Int,
    unfilteredResults: List<Any?>,
    chunkResults: List<Any?>,
): Boolean =
    services == selectedSources && chunkResults.isNotEmpty() &&
        unfilteredResults.all { it != null } && chunkResults.all { it != null }
