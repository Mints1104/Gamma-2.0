package com.mints.projectgammatwo.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

data class DeletedEntry(val lat: Double, val lng: Double, val timestamp: Long)

class DeletedInvasionsRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("deleted_invasions", Context.MODE_PRIVATE)
    private val key = "deleted_invasions_set"

    fun addDeletedInvasion(invasion: Invasion) {


        if(invasion.type == 8 || invasion.type == 9 || invasion.type == 7) return
        val currentTime = System.currentTimeMillis()
        val entry = "${invasion.lat},${invasion.lng},$currentTime"
        val set = prefs.getStringSet(key, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        set.add(entry)
        prefs.edit { putStringSet(key, set) }
    }

    fun getDeletedEntries(): Set<DeletedEntry> {
        val set = prefs.getStringSet(key, emptySet()) ?: emptySet()
        return set.mapNotNull { entry ->
            val parts = entry.split(",")
            if (parts.size >= 3) {
                try {
                    val lat = parts[0].toDouble()
                    val lng = parts[1].toDouble()
                    val timestamp = parts[2].toLong()
                    DeletedEntry(lat, lng, timestamp)
                } catch (e: Exception) {
                    null
                }
            } else null
        }.toSet()
    }

    fun isInvasionDeleted(invasion: Invasion): Boolean {
        return getDeletedEntries().any { it.lat == invasion.lat && it.lng == invasion.lng }
    }

    fun getDeletionCountLast24Hours(): Int {
        val twentyFourHoursAgo = System.currentTimeMillis() - 24 * 60 * 60 * 1000
        return getDeletedEntries().count { it.timestamp >= twentyFourHoursAgo }
    }

    fun resetDeletedInvasions() {
        prefs.edit().remove(key).apply()
    }

    fun setDeletedEntries(entries: Set<DeletedEntry>) {
        val stringSet = entries.map { "${it.lat},${it.lng},${it.timestamp}" }.toSet()
        prefs.edit().putStringSet(key, stringSet).apply()
    }
}
