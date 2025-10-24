package com.mints.projectgammatwo.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson

class VisitedQuestsPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("visited_quests", Context.MODE_PRIVATE)
    private val key = "visited"
    private val gson = Gson()

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

    private fun writeAllRecords(records: Collection<Record>) {
        val strings = records.map { gson.toJson(it) }.toSet()
        prefs.edit { putStringSet(key, strings) }
    }

    // Public API kept backward compatible: returns only IDs visited in the last 24 hours and prunes storage.
    fun getVisitedQuests(): Set<String> {
        val records = readAllRecordsRaw()
        // Persist pruned set back (also wipes legacy entries)
        writeAllRecords(records)
        return records.map { it.id }.toSet()
    }

    // For UI: return timestamped records (last 24h only), sorted not guaranteed.
    fun getVisitedRecords(): List<Record> {
        val records = readAllRecordsRaw()
        writeAllRecords(records)
        return records
    }

    // Legacy method: keeps compatibility with older call sites.
    fun addVisitedQuest(questId: String) {
        addVisitedQuest(questId, rewards = "", conditions = "", source = "")
    }

    // New method: allows storing additional quest details for richer deleted item display.
    fun addVisitedQuest(questId: String, rewards: String, conditions: String, source: String) {
        // Prune existing then add fresh record
        val current = readAllRecordsRaw().toMutableList()
        current.add(Record(id = questId, timestamp = now(), rewards = rewards, conditions = conditions, source = source))
        writeAllRecords(current)
    }

    // Clear all stored visited/"deleted" quests
    fun resetVisited() {
        prefs.edit { remove(key) }
    }
}