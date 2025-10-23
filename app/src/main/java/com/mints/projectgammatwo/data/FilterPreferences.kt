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
    private var currentRocketFilterName: String = "Default"
    private val QUEST_SPINDA_PREFIX = "spinda_"
    private val KEY_ENABLED_SPINDA = "enabled_spinda_forms"



    fun saveEnabledCharacters(characters: Set<Int>) {
        val stringSet = characters.map { it.toString() }.toSet()

        prefs.edit {
            putStringSet(KEY_CHARACTERS, stringSet)
        }
        val activeFilter = getActiveRocketFilter()
        if (activeFilter.isNotEmpty()) {
            updateFilter(activeFilter, characters)
        }
    }

    fun saveCurrentAsFilter(name: String) {
        val current = getEnabledCharacters()
        Log.d("FilterPreferences", "Saving filter $name with characters: $current")
        val stringSet = current.map { it.toString() }.toSet()
        Log.d("FilterPreferences", "Converted to string set: $stringSet")
        prefs.edit()
            .putStringSet("$KEY_FILTER_PREFIX$name", stringSet)
            .apply()
        val all = prefs.getStringSet(KEY_ALL_FILTERS, emptySet())!!.toMutableSet()
        all.add(name)
        Log.d("FilterPreferences", "All filters after adding: $all")
        prefs.edit {
            putStringSet(KEY_ALL_FILTERS, all)
        }
    }

    fun getEnabledQuestFilters(): Set<String> {
        return questPrefs.getStringSet("enabled_quest_filters", emptySet()) ?: emptySet()
    }

    fun saveEnabledQuestFilters(filters: Set<String>) {
        questPrefs.edit { putStringSet("enabled_quest_filters", filters) }
        val activeFilter = getActiveQuestFilter()
        if (activeFilter.isNotEmpty()) {
            updateQuestFilter(activeFilter, filters)
        }
    }

    fun saveCurrentQuestFilter(name: String) {
        // 1) Persist the set of enabled quest-IDs
        val currentQuests = getEnabledQuestFilters()
        Log.d("FilterPreferences", "Saving filter $name with quests: $currentQuests")
        questPrefs.edit {
            putStringSet("$QUEST_FILTER_PREFIX$name", currentQuests)
        }

        // 2) Persist the set of enabled Spinda forms under "spinda_$name"
        val currentForms = getEnabledSpindaForms()
        Log.d("FilterPreferences", "Saving Spinda forms for filter $name: $currentForms")
        questPrefs.edit {
            putStringSet("$QUEST_SPINDA_PREFIX$name", currentForms)
        }

        // 3) Add this name into the master list
        val all = questPrefs.getStringSet(KEY_QUEST_FILTERS, emptySet())!!.toMutableSet()
        all.add(name)
        questPrefs.edit {
            putStringSet(KEY_QUEST_FILTERS, all)
        }
    }

    fun getSavedQuestFilters(): Map<String, Set<String>> {
        val all = questPrefs.getStringSet(KEY_QUEST_FILTERS, emptySet())!!
        val filters = mutableMapOf<String, Set<String>>()
        for (name in all) {
            Log.d("FilterPreferences", "Loading filter $name")
            val saved = questPrefs.getStringSet("$QUEST_FILTER_PREFIX$name", emptySet())!!
            filters[name] = saved
            Log.d("FilterPreferences", "Loaded filter $name with quests: $saved")
        }
        return filters
    }

    fun listQuestFilterNames(): Set<String> =
        questPrefs.getStringSet(KEY_QUEST_FILTERS, emptySet())!!

    fun getAllSavedFilters(): Map<String, Set<Int>> {
        val all = prefs.getStringSet(KEY_ALL_FILTERS, emptySet())!!
        val filters = mutableMapOf<String, Set<Int>>()
        for (name in all) {
            Log.d("FilterPreferences", "Loading filter $name")
            val saved = prefs.getStringSet("$KEY_FILTER_PREFIX$name", emptySet())!!
            val ints = saved.mapNotNull { it.toIntOrNull() }.toSet()
            filters[name] = ints
            Log.d("FilterPreferences", "Loaded filter $name with characters: $ints")
        }
        return filters
    }

    fun getFilter(name: String): Set<Int> {
        val saved = prefs.getStringSet("$KEY_FILTER_PREFIX$name", emptySet())!!
        val ints = saved.mapNotNull { it.toIntOrNull() }.toSet()
        Log.d("FilterPreferences", "Loaded filter $name with characters: $ints")
        return ints
    }

    fun getQuestFilter(name: String): Set<String> {
        val saved = questPrefs.getStringSet("$QUEST_FILTER_PREFIX$name", emptySet())!!
        Log.d("FilterPreferences", "Loaded quest filter $name with values: $saved")
        return saved
    }

    fun loadFilter(name: String, type: String) {
        if (type == "Quest") {
            Log.d("FilterPreferences", "Loading QUEST filter $name")

            // 1) Read saved quest-IDs and activate them
            val savedQuests = questPrefs.getStringSet("$QUEST_FILTER_PREFIX$name", emptySet())!!
            setActiveQuestFilter(name)
            saveEnabledQuestFilters(savedQuests)

            // 2) Read saved Spinda-forms for this filter
            val savedForms = questPrefs.getStringSet("$QUEST_SPINDA_PREFIX$name", emptySet())!!
            Log.d("FilterPreferences", "Restoring Spinda forms for $name: $savedForms")
            // copy them into the “working” enabled_spinda_forms slot
            questPrefs.edit {
                putStringSet(KEY_ENABLED_SPINDA, savedForms)
            }
            return
        }

        Log.d("FilterPreferences", "Loading ROCKET filter $name (Prior Active Rocket: ${getActiveRocketFilter()})")
        val savedFilterDataSet = prefs.getStringSet("$KEY_FILTER_PREFIX$name", emptySet())!! // Renamed for clarity
        val charactersToLoad = savedFilterDataSet.mapNotNull { it.toIntOrNull() }.toSet() // Renamed for clarity
        Log.d("FilterPreferences", "Data for $name: $charactersToLoad")
        setActiveRocketFilter(name)
        saveEnabledCharacters(charactersToLoad)
        Log.d("FilterPreferences", "Loaded and applied rocket filter $name. Active rocket is now: ${getActiveRocketFilter()}")
    }

    fun listFilterNames(): Set<String> =
        prefs.getStringSet(KEY_ALL_FILTERS, emptySet())!!

    fun deleteFilter(name: String, filterType: String) {
        if(filterType == "Quest") {
            Log.d("FilterPreferences", "Deleting QUEST filter $name")
            questPrefs.edit {
                remove("$QUEST_FILTER_PREFIX$name")
                remove("$QUEST_SPINDA_PREFIX$name")
            }
            val all = questPrefs.getStringSet(KEY_QUEST_FILTERS, emptySet())!!.toMutableSet()
            all.remove(name)
            questPrefs.edit { putStringSet(KEY_QUEST_FILTERS, all) }

            if (name == getActiveQuestFilter()) {
                clearActiveQuestFilter()
                saveEnabledSpindaForms(emptySet())
            }
            return
        }

        prefs.edit { remove("$KEY_FILTER_PREFIX$name") }
        val all = prefs.getStringSet(KEY_ALL_FILTERS, emptySet())!!.toMutableSet()
        all.remove(name)
        prefs.edit { putStringSet(KEY_ALL_FILTERS, all) }

        if (name == getActiveRocketFilter()) {
            clearActiveRocketFilter()
        }
    }

    fun getCurrentRocketFilterName(): String {
        val all = prefs.getStringSet(KEY_ALL_FILTERS, emptySet())!!
        val current = getEnabledCharacters()
        for (name in all) {
            val saved = prefs.getStringSet("$KEY_FILTER_PREFIX$name", emptySet())!!
            val ints = saved.mapNotNull { it.toIntOrNull() }.toSet()
            if (ints == current) {
                return name
            }
        }
        return ""
    }

    fun getCurrentQuestFilterName(): String {
        val all = questPrefs.getStringSet(KEY_QUEST_FILTERS, emptySet())!!
        val current = getEnabledCharacters()
        for (name in all) {
            val saved = questPrefs.getStringSet("$QUEST_FILTER_PREFIX$name", emptySet())!!
            val ints = saved.mapNotNull { it.toIntOrNull() }.toSet()
            if (ints == current) {
                return name
            }
        }
        return ""
    }


    fun setActiveRocketFilter(name: String) {
        prefs.edit { putString(KEY_ACTIVE_ROCKET_FILTER, name) }
    }

    fun getActiveRocketFilter(): String {
        return prefs.getString(KEY_ACTIVE_ROCKET_FILTER, "") ?: ""
    }


    fun clearActiveRocketFilter() {
        prefs.edit { remove(KEY_ACTIVE_ROCKET_FILTER) }
    }

    fun setActiveQuestFilter(name: String) {
        questPrefs.edit { putString(KEY_ACTIVE_QUEST_FILTER, name) }
    }

    fun getActiveQuestFilter(): String {
        return questPrefs.getString(KEY_ACTIVE_QUEST_FILTER, "") ?: ""
    }

    private fun clearActiveQuestFilter() {
        questPrefs.edit { remove(KEY_ACTIVE_QUEST_FILTER) }
    }

    private fun updateFilter(name: String, characters: Set<Int>) {
        val stringSet = characters.map { it.toString() }.toSet()
        prefs.edit {
            putStringSet("$KEY_FILTER_PREFIX$name", stringSet)
        }
        Log.d("FilterPreferences", "Updated filter $name with characters: $characters")
    }


    private fun updateQuestFilter(name: String, filters: Set<String>) {
        questPrefs.edit {
            putStringSet("$QUEST_FILTER_PREFIX$name", filters)
        }
        Log.d("FilterPreferences", "Updated filter $name with quests: $filters")
    }


    fun getEnabledCharacters(): Set<Int> {
        return prefs.getStringSet(KEY_CHARACTERS, null)
            ?.mapNotNull { it.toIntOrNull() }
            ?.toSet()
            ?: DataMappings.characterNamesMap.keys
    }

    fun clearEnabledSpindaForms() {
        questPrefs.edit { remove("enabled_spinda_forms") }
    }

    fun resetToDefault() {
        prefs.edit { remove(KEY_CHARACTERS) }
        clearActiveRocketFilter()
    }


    fun wipeFilters() {
        prefs.edit { putStringSet(KEY_CHARACTERS, emptySet()) }
        clearActiveRocketFilter()
    }

    fun getEnabledSpindaForms(filterName: String? = null): Set<String> {
        val key = filterName?.let { "$QUEST_SPINDA_PREFIX$it" }
            ?: "enabled_spinda_forms"
        return questPrefs.getStringSet(key, emptySet()) ?: emptySet()
    }

    fun saveEnabledSpindaForms(forms: Set<String>, filterName: String? = null) {
        val key = filterName?.let { "$QUEST_SPINDA_PREFIX$it" }
            ?: "enabled_spinda_forms"
        questPrefs.edit { putStringSet(key, forms) }
    }

    companion object {
        private const val PREF_NAME = "invasion_filters"
        private const val QUEST_PREF_NAME = "quest_filters"
        private const val KEY_CHARACTERS = "enabled_characters"
    }
}