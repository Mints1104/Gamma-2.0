package com.mints.projectgammatwo.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson

@kotlinx.serialization.Serializable
data class DeletedEntry(
    val lat: Double,
    val lng: Double,
    val timestamp: Long,
    val name: String? = null,
    val source: String? = null,
    val character: Int? = null,
    val type: Int? = null,
)

/**
 * Repository for persisting and querying deleted invasions.
 *
 * Storage details:
 * - Backed by SharedPreferences using a string set.
 * - Entries are now encoded as JSON (safer for arbitrary strings like names).
 * - Backward-compatible: legacy entries encoded as "lat,lng,timestamp" are
 *   still parsed and represented as DeletedEntry with optional fields null.
 * - Using a Set prevents duplicate entries for the same encoded value.
 *
 * Limitations:
 * - Deletion identity is based on exact lat/lng equality when checking with
 *   isInvasionDeleted; small coordinate precision changes may prevent a match.
 */
class DeletedInvasionsRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("deleted_invasions", Context.MODE_PRIVATE)
    private val key = "deleted_invasions_set"

    // Gson instance for JSON (de)serialization
    private val gson = Gson()

    private fun cutoff24h(): Long = System.currentTimeMillis() - 24 * 60 * 60 * 1000

    /**
     * Record a deletion for the given invasion.
     *
     * Behavior:
     * - Skips certain transient/derived invasion types (7, 8, 9) to avoid
     *   persisting non-standard entries.
     * - Persists as JSON for robust storage of string fields.
     */
    fun addDeletedInvasion(invasion: Invasion) {
        // Ignore specific types deemed non-persistent
        if (invasion.type == 8 || invasion.type == 9 || invasion.type == 7) return

        val entry = DeletedEntry(
            lat = invasion.lat,
            lng = invasion.lng,
            timestamp = System.currentTimeMillis(),
            name = invasion.name,
            source = invasion.source,
            character = invasion.character,
            type = invasion.type
        )
        val encoded = gson.toJson(entry)

        val set = prefs.getStringSet(key, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        set.add(encoded) // Set semantics deduplicate identical entries
        prefs.edit { putStringSet(key, set) }
    }

    /**
     * Retrieve all deleted entries from storage, decoding them to DeletedEntry objects.
     *
     * Supports both JSON (current) and legacy CSV ("lat,lng,timestamp") formats.
     * Malformed entries are skipped defensively.
     */
    fun getDeletedEntries(): Set<DeletedEntry> {
        val set = prefs.getStringSet(key, emptySet()) ?: emptySet()
        val parsed = set.mapNotNull { raw ->
            val entry = raw.trim()
            try {
                if (entry.startsWith("{")) {
                    // Current JSON format
                    gson.fromJson(entry, DeletedEntry::class.java)
                } else {
                    // Legacy CSV: lat,lng,timestamp
                    val parts = entry.split(",")
                    if (parts.size >= 3) {
                        val lat = parts[0].toDouble()
                        val lng = parts[1].toDouble()
                        val timestamp = parts[2].toLong()
                        DeletedEntry(lat, lng, timestamp)
                    } else null
                }
            } catch (_: Exception) {
                // Skip malformed or legacy entries we cannot parse
                null
            }
        }
        val cutoff = cutoff24h()
        val pruned = parsed.filter { it.timestamp >= cutoff }.toSet()
        // Persist pruned set back to storage
        val stringSet = pruned.map { gson.toJson(it) }.toSet()
        prefs.edit { putStringSet(key, stringSet) }
        return pruned
    }

    /**
     * Check if an invasion was previously deleted by matching exact lat/lng.
     * Note: relies on exact coordinate equality; small precision differences
     * in source data may prevent a match.
     */
    fun isInvasionDeleted(invasion: Invasion): Boolean {
        return getDeletedEntries().any { it.lat == invasion.lat && it.lng == invasion.lng }
    }

    /**
     * Count entries deleted within the last 24 hours (rolling window).
     */
    fun getDeletionCountLast24Hours(): Int {
        val twentyFourHoursAgo = cutoff24h()
        return getDeletedEntries().count { it.timestamp >= twentyFourHoursAgo }
    }

    /**
     * Remove all deletion records.
     */
    fun resetDeletedInvasions() {
        prefs.edit { remove(key) }
    }

    /**
     * Overwrite storage with the provided set of entries.
     * Entries are encoded to JSON for storage.
     */
    fun setDeletedEntries(entries: Set<DeletedEntry>) {
        // When writing externally provided entries, also enforce 24h retention
        val cutoff = cutoff24h()
        val filtered = entries.filter { it.timestamp >= cutoff }.toSet()
        val stringSet = filtered.map { gson.toJson(it) }.toSet()
        prefs.edit { putStringSet(key, stringSet) }
    }
}
