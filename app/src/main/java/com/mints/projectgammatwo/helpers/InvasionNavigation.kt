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

/** How many earlier stops the overlay remembers per mode for ◀. */
const val MAX_OVERLAY_HISTORY = 50

/**
 * The overlay's ▶/◀ state for one mode (invasions or quests): the item last teleported to, the
 * items it has handled, and the history ◀ returns through. The service owns the teleports and
 * the stores; this decides where each tap goes.
 *
 * - ▶: [next] with step 1, then [recordTeleport] once the teleport has launched.
 * - ◀: [previousStop]; with an earlier stop, [returnTo] it once the teleport has launched.
 *   With none (◀ before any ▶, or once the history is used up), [next] with step -1 walks the
 *   list backwards, recording nothing, as ◀ always did.
 *
 * @param handledKey what identifies an item as handled: the invasion itself, a quest's visit id.
 */
class OverlayWalk<T>(
    private val maxHistory: Int = MAX_OVERLAY_HISTORY,
    private val handledKey: (T) -> Any? = { it },
) {
    /** The item last teleported to; null before the first teleport or after [resetAnchor]. */
    var anchor: T? = null
        private set
    private var anchorIndex = -1
    private val handled = HashSet<Any?>()
    private val history = ArrayDeque<T>()

    /** The stops ▶ has teleported to, oldest first, ending with the current one. */
    val stops: List<T> get() = history.toList()

    fun isHandled(item: T): Boolean = handledKey(item) in handled

    /**
     * Index in [items] of the next item [step]'s way from the anchor ([nextOverlayIndex]) that
     * [isEligible] accepts and that hasn't been handled; null if there is none. The item found
     * becomes the anchor.
     */
    fun next(items: List<T>, step: Int, isEligible: (T) -> Boolean): Int? {
        val index = nextOverlayIndex(items, anchor, anchorIndex, step) { !isHandled(it) && isEligible(it) }
            ?: return null
        anchor = items[index]
        anchorIndex = index
        return index
    }

    /** ▶ teleported to [item]: it's handled, and the latest stop in the history. */
    fun recordTeleport(item: T) {
        handled += handledKey(item)
        history.addLast(item)
        while (history.size > maxHistory) history.removeFirst()
    }

    /** The stop ◀ returns to ([previousStopIndex]), with its index in the history; null if none. */
    fun previousStop(isUsable: (T) -> Boolean): IndexedValue<T>? =
        previousStopIndex(history, isUsable)?.let { IndexedValue(it, history[it]) }

    /**
     * ◀ teleported back to [stop]: it becomes the current stop, the ones after it are left behind
     * (as in a browser), and ▶ carries on from it in [items], skipping those already handled.
     */
    fun returnTo(stop: IndexedValue<T>, items: List<T>) {
        while (history.size > stop.index + 1) history.removeLast()
        anchorIndex = items.indexOf(stop.value).takeIf { it >= 0 } ?: anchorIndex
        anchor = stop.value
    }

    /** A new list: ▶ starts from its top. The handled items and the history are kept. */
    fun resetAnchor() {
        anchor = null
        anchorIndex = -1
    }
}
