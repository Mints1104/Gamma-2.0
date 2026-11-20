package com.mints.projectgammatwo.data

import android.content.Context
import androidx.core.content.edit

/**
 * Where the player last battled a grunt and last visited a quest: the reference points for the
 * invasion list's distance sort and the quest list's nearest-first order.
 *
 * The app's lists and the overlay each have their own view models, so this is read from the
 * shared prefs every time rather than cached per view model: a cached copy never saw the
 * overlay's ▶, and the app kept sorting from the last stop reached in the app itself.
 * SharedPreferences keeps its values in memory once loaded, so the reads are cheap.
 */
class LastLocations(context: Context) {
    private val invasionPrefs = context.getSharedPreferences(INVASION_PREFS, Context.MODE_PRIVATE)
    private val questPrefs = context.getSharedPreferences(QUEST_PREFS, Context.MODE_PRIVATE)

    fun saveInvasion(lat: Double, lng: Double) {
        invasionPrefs.edit { putString(KEY_INVASION, "$lat,$lng") }
    }

    /** The last battled grunt's stop; null if none was recorded. */
    fun invasion(): Pair<Double, Double>? = parseLatLng(invasionPrefs.getString(KEY_INVASION, null))

    fun saveQuest(lat: Double, lng: Double) {
        questPrefs.edit { putString(KEY_QUEST, "$lat,$lng") }
    }

    /** The last visited quest's stop; null if none was recorded. */
    fun quest(): Pair<Double, Double>? =
        parseLatLng(questPrefs.getString(KEY_QUEST, null)) ?: legacyQuest()

    /** Versions before the string key stored the quest's coordinates as two floats. */
    private fun legacyQuest(): Pair<Double, Double>? {
        val lat = questPrefs.getFloat(LEGACY_QUEST_LAT, Float.NaN)
        val lng = questPrefs.getFloat(LEGACY_QUEST_LNG, Float.NaN)
        return if (lat.isNaN() || lng.isNaN()) null else lat.toDouble() to lng.toDouble()
    }

    private fun parseLatLng(value: String?): Pair<Double, Double>? {
        val parts = value?.split(",") ?: return null
        if (parts.size != 2) return null
        val lat = parts[0].trim().toDoubleOrNull() ?: return null
        val lng = parts[1].trim().toDoubleOrNull() ?: return null
        return lat to lng
    }

    private companion object {
        const val INVASION_PREFS = "last_invasion_pref"
        const val KEY_INVASION = "lastInvasion"
        const val QUEST_PREFS = "last_visited_pref"
        const val KEY_QUEST = "lastVisited"
        const val LEGACY_QUEST_LAT = "lastVisitedLat"
        const val LEGACY_QUEST_LNG = "lastVisitedLng"
    }
}
