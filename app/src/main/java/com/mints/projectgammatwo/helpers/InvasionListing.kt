package com.mints.projectgammatwo.helpers

import com.mints.projectgammatwo.data.Invasion
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** How many invasions the list shows, after sorting. */
const val MAX_LISTED_INVASIONS = 500

/**
 * The invasions worth listing from a raw fetch: those whose character is enabled, that haven't
 * been battled ([deleted] holds their lat/lng) and that haven't ended.
 *
 * The API reports the two event grunts by type with a placeholder character, so types 8 and 9
 * are mapped to the characters the filter screen offers for them first.
 */
fun filterInvasions(
    invasions: List<Invasion>,
    enabledCharacters: Set<Int>,
    deleted: Set<Pair<Double, Double>>,
    nowSeconds: Long,
): List<Invasion> = invasions
    .asSequence()
    .map { invasion ->
        when (invasion.type) {
            8 -> invasion.copy(character = 1)
            9 -> invasion.copy(character = 0)
            else -> invasion
        }
    }
    .filter { invasion ->
        invasion.character in enabledCharacters &&
            invasion.lat to invasion.lng !in deleted &&
            invasion.invasion_end > nowSeconds
    }
    .toList()

/**
 * Orders [invasions] nearest-first from [distanceFrom] (lat to lng), or newest-first when it's
 * null, then keeps the first [max].
 *
 * The cap has to come after the sort. It used to be applied first, so the sort only reordered
 * whichever 500 the API happened to return first: with NYC's ~5,400 active invasions, distance
 * order showed its nearest invasion kilometres away and time order included none of the newest.
 */
fun sortAndCapInvasions(
    invasions: List<Invasion>,
    distanceFrom: Pair<Double, Double>?,
    max: Int = MAX_LISTED_INVASIONS,
): List<Invasion> {
    if (distanceFrom == null) {
        // sortedByDescending is stable, so invasions that started at the same moment keep their
        // relative order on every re-sort; sortedBy + asReversed flipped them each time, so cards
        // swapped places after every delete.
        return invasions.sortedByDescending { it.invasion_start }.take(max)
    }
    val (lat, lng) = distanceFrom
    // Each distance computed once, rather than twice per comparison inside sortedBy.
    return invasions.map { it to distanceMeters(lat, lng, it.lat, it.lng) }
        .sortedBy { it.second }
        .take(max)
        .map { it.first }
}

/** Great-circle distance in metres (haversine). */
fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val earthRadius = 6371e3
    val phi1 = Math.toRadians(lat1)
    val phi2 = Math.toRadians(lat2)
    val dPhi = Math.toRadians(lat2 - lat1)
    val dLambda = Math.toRadians(lng2 - lng1)
    val a = sin(dPhi / 2) * sin(dPhi / 2) + cos(phi1) * cos(phi2) * sin(dLambda / 2) * sin(dLambda / 2)
    return earthRadius * 2 * atan2(sqrt(a), sqrt(1 - a))
}
