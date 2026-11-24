package com.mints.projectgammatwo.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.mints.projectgammatwo.helpers.parseLatLng

/**
 * Where the player last battled a grunt and last visited a quest: the reference points for the
 * invasion list's distance sort and the quest list's nearest-first order.
 *
 * The app's lists and the overlay each have their own view models, so this is read from the
 * shared prefs every time rather than cached per view model: a cached copy never saw the
 * overlay's ▶, and the app kept sorting from the last stop reached in the app itself.
 * SharedPreferences keeps its values in memory once loaded, so the reads are cheap.
 *
 * The player can also set either point by hand or clear it (the Rockets and Quests menus); a
 * cleared point makes the list start with whatever the server listed first.
 */
class LastLocations(context: Context) {

    /** Which of the two points: each list has its own. */
    enum class Kind { INVASION, QUEST }

    private val invasionPrefs = context.getSharedPreferences(INVASION_PREFS, Context.MODE_PRIVATE)
    private val questPrefs = context.getSharedPreferences(QUEST_PREFS, Context.MODE_PRIVATE)

    private fun prefs(kind: Kind) = if (kind == Kind.INVASION) invasionPrefs else questPrefs
    private fun key(kind: Kind) = if (kind == Kind.INVASION) KEY_INVASION else KEY_QUEST

    fun saveInvasion(lat: Double, lng: Double) = set(Kind.INVASION, lat, lng)

    /** The last battled grunt's stop; null if none was recorded. */
    fun invasion(): Pair<Double, Double>? = get(Kind.INVASION)

    fun saveQuest(lat: Double, lng: Double) = set(Kind.QUEST, lat, lng)

    /** The last visited quest's stop; null if none was recorded. */
    fun quest(): Pair<Double, Double>? = get(Kind.QUEST)

    fun get(kind: Kind): Pair<Double, Double>? {
        val stored = parseLatLng(prefs(kind).getString(key(kind), null))
        return if (kind == Kind.QUEST) stored ?: legacyQuest() else stored
    }

    /** Records a point; [manual] when the player typed it rather than reaching a stop. */
    fun set(kind: Kind, lat: Double, lng: Double, manual: Boolean = false) {
        prefs(kind).edit {
            putString(key(kind), "$lat,$lng")
            putBoolean(KEY_MANUAL, manual)
            if (kind == Kind.QUEST) remove(LEGACY_QUEST_LAT).remove(LEGACY_QUEST_LNG)
        }
    }

    /** Whether the current point was set by hand rather than by a battle or visit. */
    fun isManual(kind: Kind): Boolean = get(kind) != null && prefs(kind).getBoolean(KEY_MANUAL, false)

    /** Forgets the point (the legacy quest keys too, or they would be read back). */
    fun clear(kind: Kind) {
        prefs(kind).edit {
            remove(key(kind))
            remove(KEY_MANUAL)
            if (kind == Kind.QUEST) remove(LEGACY_QUEST_LAT).remove(LEGACY_QUEST_LNG)
        }
    }

    /**
     * Calls [onChange] on the main thread whenever the point changes, from anywhere in the
     * process (the overlay's ▶ included), until the returned function is called.
     */
    fun observe(kind: Kind, onChange: () -> Unit): () -> Unit {
        // SharedPreferences holds its listeners weakly; the returned closure keeps this one alive.
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> onChange() }
        val prefs = prefs(kind)
        prefs.registerOnSharedPreferenceChangeListener(listener)
        return { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    /** Versions before the string key stored the quest's coordinates as two floats. */
    private fun legacyQuest(): Pair<Double, Double>? {
        val lat = questPrefs.getFloat(LEGACY_QUEST_LAT, Float.NaN)
        val lng = questPrefs.getFloat(LEGACY_QUEST_LNG, Float.NaN)
        return if (lat.isNaN() || lng.isNaN()) null else lat.toDouble() to lng.toDouble()
    }

    private companion object {
        const val INVASION_PREFS = "last_invasion_pref"
        const val KEY_INVASION = "lastInvasion"
        const val QUEST_PREFS = "last_visited_pref"
        const val KEY_QUEST = "lastVisited"
        const val KEY_MANUAL = "setManually"
        const val LEGACY_QUEST_LAT = "lastVisitedLat"
        const val LEGACY_QUEST_LNG = "lastVisitedLng"
    }
}
