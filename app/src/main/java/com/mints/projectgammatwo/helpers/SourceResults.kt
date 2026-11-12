package com.mints.projectgammatwo.helpers

/**
 * The results to list after a fetch in which [failedSources] didn't answer: everything [fresh],
 * plus whatever the previous list showed from each failed source.
 *
 * A refresh used to replace the list outright, so one map timing out emptied its part of the
 * list (or, if every map failed, the whole list) until the next successful fetch. The caller
 * filters the result again, so carried-over items that have since expired, been deleted or been
 * filtered out still drop away.
 */
fun <T> carryOverFailedSources(
    fresh: List<T>,
    previous: List<T>,
    failedSources: Set<String>,
    sourceOf: (T) -> String,
): List<T> {
    if (failedSources.isEmpty() || previous.isEmpty()) return fresh
    return fresh + previous.filter { sourceOf(it) in failedSources }
}
