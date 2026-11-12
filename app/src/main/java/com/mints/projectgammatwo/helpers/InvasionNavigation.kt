package com.mints.projectgammatwo.helpers

import com.mints.projectgammatwo.data.Invasion

/**
 * Invasions with less than this long left are skipped: by the time the teleport lands and the
 * player reaches the stop, they would be gone.
 */
const val MIN_REMAINING_SECONDS = 60L

/**
 * Whether [invasion] still has at least [MIN_REMAINING_SECONDS] left.
 *
 * The overlay only refetches on demand, so its list goes stale while in use. Grunts last about
 * 30 minutes, and ▶/◀ used to keep teleporting to stops whose invasion had already ended — each
 * one also counted against the daily limit.
 */
fun isLiveInvasion(invasion: Invasion, nowSeconds: Long): Boolean =
    invasion.invasion_end - nowSeconds >= MIN_REMAINING_SECONDS

/**
 * Index of the item the overlay's ▶ ([step] = 1) should teleport to next: the first one in
 * that direction, wrapping around, that [isEligible] accepts; null if none do. [step] = -1 walks
 * the other way (◀ itself goes back through [previousStopIndex] instead).
 *
 * The walk continues from [anchor], the item last teleported to, found by value rather than by
 * position: the list is shared with the app, whose refreshes and deletions replace it while the
 * overlay is in use, and a remembered index then pointed at some other item, so ▶ skipped or
 * repeated stops. If [anchor] is no longer listed, its old position [anchorIndex] stands in for
 * it: the item now in that place is next going forward. With no [anchor] (a fresh list), ▶ starts
 * at the first item and ◀ at the last.
 */
fun <T> nextOverlayIndex(
    items: List<T>,
    anchor: T?,
    anchorIndex: Int,
    step: Int,
    isEligible: (T) -> Boolean,
): Int? {
    if (items.isEmpty()) return null
    val found = if (anchor == null) -1 else items.indexOf(anchor)
    val start = when {
        anchor == null -> if (step > 0) -1 else 0
        found >= 0 -> found
        step > 0 -> anchorIndex - 1
        else -> anchorIndex
    }
    val size = items.size
    for (offset in 1..size) {
        val index = Math.floorMod(start + step * offset, size)
        if (isEligible(items[index])) return index
    }
    return null
}

/**
 * Index in [history] of the stop the overlay's ◀ returns to; null if there is none.
 *
 * [history] lists the overlay's teleports, oldest first, ending with the stop the player is at.
 * ◀ goes back to the latest earlier one that [isUsable] accepts (an invasion that hasn't ended),
 * so the player can return after an accidental ▶ or a bad landing. Going by the history rather
 * than the list keeps working when the app has refreshed or reordered the list meanwhile.
 */
fun <T> previousStopIndex(history: List<T>, isUsable: (T) -> Boolean): Int? {
    for (index in history.size - 2 downTo 0) {
        if (isUsable(history[index])) return index
    }
    return null
}
