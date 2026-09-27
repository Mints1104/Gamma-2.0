package com.mints.projectgammatwo.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson

/**
 * Concurrency: every read-modify-write of the stored set happens under a process-wide lock.
 * Several instances exist at once and are used from different threads — a quest fetch prunes
 * the set on a background thread while a visit adds to it — so without the lock one writer
 * could discard the other's change, and a visited quest would reappear.
 */
class VisitedQuestsPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("visited_quests", Context.MODE_PRIVATE)
    private val key = "visited"
    private val gson = Gson()

    companion object {
        /** Shared by all instances: the stored set is shared, so the lock must be too. */
        private val lock = Any()
    }

    data class Record(
        val id: String, // Format: "name|lat|lng"
        val timestamp: Long,
        // Newly added optional details (nullable for legacy/back-compat)
        val rewards: String? = null,
        val conditions: String? = null,
        val source: String? = null
    )

    private fun now() = System.currentTimeMillis()
    private fun cutoff24h(): Long = now() - 24 * 60 * 60 * 1000

    // Parse mixed storage: JSON records (current) or plain IDs (legacy). Legacy are treated as stale and dropped.
    private fun readAllRecordsRaw(): List<Record> {
        val set = prefs.getStringSet(key, emptySet()) ?: emptySet()
        val cutoff = cutoff24h()
        return set.mapNotNull { raw ->
            val s = raw.trim()
            try {
                if (s.startsWith("{")) {
                    gson.fromJson(s, Record::class.java)
                } else {
                    // Legacy entry without timestamp cannot meet 24h rule -> drop
                    null
                }
            } catch (_: Exception) {
                null
            }
        }.filter { it.timestamp >= cutoff }
    }

    /**
     * Writes [records] back, but only when that changes what's stored. The getters prune on
     * every call, and rewriting unconditionally made every read a write.
     */
    private fun writeAllRecords(records: Collection<Record>) {
        val strings = records.map { gson.toJson(it) }.toSet()
        if (strings != prefs.getStringSet(key, emptySet())) {
            prefs.edit { putStringSet(key, strings) }
        }
    }

    // Public API kept backward compatible: returns only IDs visited in the last 24 hours and prunes storage.
    fun getVisitedQuests(): Set<String> = synchronized(lock) {
        val records = readAllRecordsRaw()
        // Persist pruned set back (also wipes legacy entries)
        writeAllRecords(records)
        records.map { it.id }.toSet()
    }

    // For UI: return timestamped records (last 24h only), sorted not guaranteed.
    fun getVisitedRecords(): List<Record> = synchronized(lock) {
        val records = readAllRecordsRaw()
        writeAllRecords(records)
        records
    }

    /**
     * A cheap change-detector for the stored set: no parsing, so it's fine on the main thread.
     * Lets the quest list tell whether it was built from the current visited set.
     */
    fun fingerprint(): Int = synchronized(lock) {
        (prefs.getStringSet(key, emptySet()) ?: emptySet()).hashCode()
    }

    // Legacy method: keeps compatibility with older call sites.
    fun addVisitedQuest(questId: String) {
        addVisitedQuest(questId, rewards = "", conditions = "", source = "")
    }

    // New method: allows storing additional quest details for richer deleted item display.
    fun addVisitedQuest(questId: String, rewards: String, conditions: String, source: String) = synchronized(lock) {
        // Prune existing then add fresh record
        val current = readAllRecordsRaw().toMutableList()
        current.add(Record(id = questId, timestamp = now(), rewards = rewards, conditions = conditions, source = source))
        writeAllRecords(current)
    }

    // Clear all stored visited/"deleted" quests
    fun resetVisited() = synchronized(lock) {
        prefs.edit { remove(key) }
    }
}