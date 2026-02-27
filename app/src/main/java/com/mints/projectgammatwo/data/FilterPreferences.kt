package com.mints.projectgammatwo.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit

class FilterPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val questPrefs: SharedPreferences = context.getSharedPreferences(QUEST_PREF_NAME, Context.MODE_PRIVATE)

    private val KEY_ALL_FILTERS = "all_filters"
    private val KEY_FILTER_PREFIX = "filter_"
    private val QUEST_FILTER_PREFIX = "quest_"
    private val KEY_QUEST_FILTERS = "quest_filters"
    private val KEY_ACTIVE_ROCKET_FILTER = "active_rocket_filter"
    private val KEY_ACTIVE_QUEST_FILTER = "active_quest_filter"
    private val QUEST_ENCOUNTER_PREFIX = "encounter_"

    fun saveEnabledCharacters(characters: Set<Int>) {
        val stringSet = characters.map { it.toString() }.toSet()
        prefs.edit { putStringSet(KEY_CHARACTERS, HashSet(stringSet)) }
    }

    fun saveCurrentAsFilter(name: String) {
        val current = getEnabledCharacters()
        Log.d("FilterPreferences", "Saving filter $name with characters: $current")
        val stringSet = current.map { it.toString() }.toSet()
        prefs.edit().putStringSet("$KEY_FILTER_PREFIX$name", HashSet(stringSet)).apply()
        val all = prefs.getStringSet(KEY_ALL_FILTERS, emptySet())!!.toMutableSet()
        all.add(name)
        Log.d("FilterPreferences", "All filters after adding: $all")
        prefs.edit { putStringSet(KEY_ALL_FILTERS, all) }
    }

    fun getEnabledQuestFilters(): Set<String> =
        questPrefs.getStringSet("enabled_quest_filters", emptySet())?.toSet() ?: emptySet()

    fun saveEnabledQuestFilters(filters: Set<String>) {
        questPrefs.edit(commit = true) { putStringSet("enabled_quest_filters", HashSet(filters)) }
    }

    fun getEnabledEncounterConditions(): Set<String> =
        questPrefs.getStringSet(KEY_ENABLED_ENCOUNTER_CONDITIONS, emptySet())?.toSet() ?: emptySet()

    fun saveEnabledEncounterConditions(conditions: Set<String>) {
        questPrefs.edit(commit = true) { putStringSet(KEY_ENABLED_ENCOUNTER_CONDITIONS, HashSet(conditions)) }
    }

    fun saveCurrentQuestFilter(name: String) {
        val currentQuests = getEnabledQuestFilters()
        Log.d("FilterPreferences", "Saving filter $name with quests: $currentQuests")
        questPrefs.edit(commit = true) { putStringSet("$QUEST_FILTER_PREFIX$name", HashSet(currentQuests)) }

        val currentEncounterConditions = getEnabledEncounterConditions()
        Log.d("FilterPreferences", "Saving encounter conditions for filter $name: $currentEncounterConditions")
        questPrefs.edit(commit = true) { putStringSet("$QUEST_ENCOUNTER_PREFIX$name", HashSet(currentEncounterConditions)) }

        val all = questPrefs.getStringSet(KEY_QUEST_FILTERS, emptySet())!!.toMutableSet()
        all.add(name)
        questPrefs.edit(commit = true) { putStringSet(KEY_QUEST_FILTERS, all) }
    }

    fun getSavedQuestFilters(): Map<String, Set<String>> {
        val all = questPrefs.getStringSet(KEY_QUEST_FILTERS, emptySet())!!
        return all.associateWith { name ->
            questPrefs.getStringSet("$QUEST_FILTER_PREFIX$name", emptySet())!!
                .toSet()
                .also { Log.d("FilterPreferences", "Loaded filter $name with quests: $it") }
        }
    }

    fun getSavedQuestEncounterConditions(): Map<String, Set<String>> {
        val all = questPrefs.getStringSet(KEY_QUEST_FILTERS, emptySet())!!
        return all.associateWith { name ->
            questPrefs.getStringSet("$QUEST_ENCOUNTER_PREFIX$name", emptySet())!!.toSet()
        }
    }

    fun listQuestFilterNames(): Set<String> =
        questPrefs.getStringSet(KEY_QUEST_FILTERS, emptySet())!!.toSet()

    fun getAllSavedFilters(): Map<String, Set<Int>> {
        val all = prefs.getStringSet(KEY_ALL_FILTERS, emptySet())!!
        val filters = mutableMapOf<String, Set<Int>>()
        for (name in all) {
            val saved = prefs.getStringSet("$KEY_FILTER_PREFIX$name", emptySet())!!
            val ints = saved.mapNotNull { it.toIntOrNull() }.toSet()
            filters[name] = ints
            Log.d("FilterPreferences", "Loaded filter $name with characters: $ints")
        }
        return filters
    }

    fun loadFilter(name: String, type: String) {
        if (type == "Quest") {
            Log.d("FilterPreferences", "Loading QUEST filter $name")
            // Clear active first so no auto-update hooks can misfire during writes.
            clearActiveQuestFilter()
            val savedQuests = questPrefs.getStringSet("$QUEST_FILTER_PREFIX$name", emptySet())!!.toSet()
            questPrefs.edit(commit = true) { putStringSet("enabled_quest_filters", HashSet(savedQuests)) }
            val savedEncounterConditions = questPrefs.getStringSet("$QUEST_ENCOUNTER_PREFIX$name", emptySet())!!
                .filterNot { it.startsWith("spinda_form_") }.toSet()
            Log.d("FilterPreferences", "Restoring encounter conditions for $name: $savedEncounterConditions")
            questPrefs.edit(commit = true) { putStringSet(KEY_ENABLED_ENCOUNTER_CONDITIONS, HashSet(savedEncounterConditions)) }
            // Set active after writes so hooks see the correct name.
            setActiveQuestFilter(name)
            return
        }

        Log.d("FilterPreferences", "Loading ROCKET filter $name (Prior Active: ${getActiveRocketFilter()})")
        val savedFilterDataSet = prefs.getStringSet("$KEY_FILTER_PREFIX$name", emptySet())!!
        val charactersToLoad = savedFilterDataSet.mapNotNull { it.toIntOrNull() }.toSet()
        Log.d("FilterPreferences", "Data for $name: $charactersToLoad")
        setActiveRocketFilter(name)
        saveEnabledCharacters(charactersToLoad)
        Log.d("FilterPreferences", "Loaded rocket filter $name. Active is now: ${getActiveRocketFilter()}")
    }

    fun listFilterNames(): Set<String> =
        prefs.getStringSet(KEY_ALL_FILTERS, emptySet())!!

    fun deleteFilter(name: String, filterType: String) {
        if (filterType == "Quest") {
            Log.d("FilterPreferences", "Deleting QUEST filter $name")
            questPrefs.edit {
                remove("$QUEST_FILTER_PREFIX$name")
                remove("$QUEST_ENCOUNTER_PREFIX$name")
            }
            val all = questPrefs.getStringSet(KEY_QUEST_FILTERS, emptySet())!!.toMutableSet()
            all.remove(name)
            questPrefs.edit { putStringSet(KEY_QUEST_FILTERS, all) }
            if (name == getActiveQuestFilter()) {
                clearActiveQuestFilter()
                questPrefs.edit {
                    putStringSet("enabled_quest_filters", emptySet())
                    putStringSet(KEY_ENABLED_ENCOUNTER_CONDITIONS, emptySet())
                }
            }
            return
        }

        prefs.edit { remove("$KEY_FILTER_PREFIX$name") }
        val all = prefs.getStringSet(KEY_ALL_FILTERS, emptySet())!!.toMutableSet()
        all.remove(name)
        prefs.edit { putStringSet(KEY_ALL_FILTERS, all) }
        if (name == getActiveRocketFilter()) clearActiveRocketFilter()
    }

    fun setActiveRocketFilter(name: String) { prefs.edit { putString(KEY_ACTIVE_ROCKET_FILTER, name) } }
    fun getActiveRocketFilter(): String = prefs.getString(KEY_ACTIVE_ROCKET_FILTER, "") ?: ""
    fun clearActiveRocketFilter() { prefs.edit { remove(KEY_ACTIVE_ROCKET_FILTER) } }

    fun setActiveQuestFilter(name: String) { questPrefs.edit { putString(KEY_ACTIVE_QUEST_FILTER, name) } }
    fun getActiveQuestFilter(): String = questPrefs.getString(KEY_ACTIVE_QUEST_FILTER, "") ?: ""
    fun clearActiveQuestFilter() { questPrefs.edit { remove(KEY_ACTIVE_QUEST_FILTER) } }

    fun getEnabledCharacters(): Set<Int> =
        prefs.getStringSet(KEY_CHARACTERS, null)
            ?.mapNotNull { it.toIntOrNull() }
            ?.toSet()
            ?: DataMappings.characterNamesMap.keys

    companion object {
        private const val PREF_NAME = "invasion_filters"
        // Exposed so QuestFilterPreferences can reference it directly rather than
        // duplicating the string — eliminates the silent key-name coupling risk.
        const val QUEST_PREF_NAME = "quest_filters"
        private const val KEY_CHARACTERS = "enabled_characters"
        const val KEY_ENABLED_ENCOUNTER_CONDITIONS = "enabled_encounter_conditions"
    }
}