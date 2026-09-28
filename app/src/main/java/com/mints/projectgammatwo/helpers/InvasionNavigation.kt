package com.mints.projectgammatwo.helpers

import com.mints.projectgammatwo.data.Invasion

/**
 * Invasions with less than this long left are skipped: by the time the teleport lands and the
 * player reaches the stop, they would be gone.
 */
const val MIN_REMAINING_SECONDS = 60L

/**
 * Index of the next invasion after [from] ([step] = 1) or before it ([step] = -1) that still has
 * at least [MIN_REMAINING_SECONDS] left, wrapping around the list; null if none do.
 *
 * The overlay only refetches on demand, so its list goes stale while in use. Grunts last about
 * 30 minutes, and ▶/◀ used to keep teleporting to stops whose invasion had already ended — each
 * one also counted against the daily limit.
 */
fun nextLiveInvasionIndex(invasions: List<Invasion>, from: Int, step: Int, nowSeconds: Long): Int? {
    if (invasions.isEmpty()) return null
    val size = invasions.size
    for (offset in 1..size) {
        val index = Math.floorMod(from + step * offset, size)
        if (invasions[index].invasion_end - nowSeconds >= MIN_REMAINING_SECONDS) return index
    }
    return null
}
