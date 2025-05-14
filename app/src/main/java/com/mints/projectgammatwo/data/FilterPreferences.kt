package com.mints.projectgammatwo.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

class FilterPreferences(context: Context) {


    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val questPrefs: SharedPreferences = context.getSharedPreferences(QUEST_PREF_NAME, Context.MODE_PRIVATE)

    private val KEY_ALL_FILTERS = "all_filters"
    private val KEY_FILTER_PREFIX = "filter_"
    private val QUEST_FILTER_PREFIX = "quest_"
    private val KEY_QUEST_FILTERS = "quest_filters"


    /**
     * Saves the set of enabled character IDs into SharedPreferences.
     *
     * @param characters A set of integers representing the enabled character IDs.
     *
     * How it works:
     * 1. Converts each integer in the set to its string representation.
     * 2. Collects these strings into a new set.
     * 3. Saves the set of strings into SharedPreferences using the key defined by KEY_CHARACTERS.
     * 4. The .apply() method commits the changes asynchronously.
     */
    fun saveEnabledCharacters(characters: Set<Int>) {
        // Convert the Set<Int> to a Set<String> because SharedPreferences supports string sets.
        val stringSet = characters.map { it.toString() }.toSet()

        prefs.edit()
            .putStringSet(KEY_CHARACTERS, stringSet)
            .apply()
    }

    fun saveCurrentAsFilter(name: String) {
        // 1. Snapshot the current IDs
        val current = getEnabledCharacters()
        Log.d("FilterPreferences", "Saving filter $name with characters: $current")
        // 2. Convert to Set<String>
        val stringSet = current.map { it.toString() }.toSet()
        Log.d("FilterPreferences", "Converted to string set: $stringSet")
        // 3. Save under "filter_<name>"
        prefs.edit()
            .putStringSet("$KEY_FILTER_PREFIX$name", stringSet)
            .apply()
        // 4. Register this filter name
        val all = prefs.getStringSet(KEY_ALL_FILTERS, emptySet())!!.toMutableSet()
        all.add(name)
        Log.d("FilterPreferences", "All filters after adding: $all")
        prefs.edit()
            .putStringSet(KEY_ALL_FILTERS, all)
            .apply()
    }


    // Returns a set of filters (each filter is a string like "2,0,708")
    fun getEnabledQuestFilters(): Set<String> {
        return questPrefs.getStringSet("enabled_quest_filters", emptySet()) ?: emptySet()
    }

    fun saveEnabledQuestFilters(filters: Set<String>) {
        questPrefs.edit().putStringSet("enabled_quest_filters", filters).apply()
    }

    fun saveCurrentQuestFilter(name: String) {
        // 1. Snapshot the current IDs
        val current = getEnabledQuestFilters()
        Log.d("FilterPreferences", "Saving filter $name with quests: $current")
        // 2. Convert to Set<String>
        val stringSet = current.map { it }.toSet()
        Log.d("FilterPreferences", "Converted to string set: $stringSet")
        // 3. Save under "filter_<name>"
        questPrefs.edit()
            .putStringSet("$QUEST_FILTER_PREFIX$name", stringSet)
            .apply()
        // 4. Register this filter name
        val all = questPrefs.getStringSet(KEY_QUEST_FILTERS, emptySet())!!.toMutableSet()
        all.add(name)
        Log.d("FilterPreferences", "All filters after adding: $all")
        questPrefs.edit()
            .putStringSet(KEY_QUEST_FILTERS, all)
            .apply()
    }

    fun getSavedQuestFilters(): Map<String, Set<Int>> {
        val all = questPrefs.getStringSet(KEY_QUEST_FILTERS, emptySet())!!
        val filters = mutableMapOf<String, Set<Int>>()
        for (name in all) {
            Log.d("FilterPreferences", "Loading filter $name")
            val saved = questPrefs.getStringSet("$QUEST_FILTER_PREFIX$name", emptySet())!!
            val ints = saved.mapNotNull { it.toIntOrNull() }.toSet()
            filters[name] = ints
            Log.d("FilterPreferences", "Loaded filter $name with characters: $ints")
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


    fun loadFilter(name: String, type:String) {

        if(type == "Quest") {
            Log.d("FilterPreferences", "Loading QUEST filter $name")
            val saved = questPrefs.getStringSet("$QUEST_FILTER_PREFIX$name", emptySet())!!
            saveEnabledQuestFilters(saved)
            Log.d("FilterPreferences", "Loaded filter $name with quests: $saved")
            return
        }

        Log.d("FilterPreferences", "Loading ROCKET filter $name")
        val saved = prefs.getStringSet("$KEY_FILTER_PREFIX$name", emptySet())!!
        val ints = saved.mapNotNull { it.toIntOrNull() }.toSet()
        Log.d("FilterPreferences", "Loaded filter $name with characters: $ints")
        saveEnabledCharacters(ints)
    }
    fun listFilterNames(): Set<String> =
        prefs.getStringSet(KEY_ALL_FILTERS, emptySet())!!

    /** Delete a saved filter (does not touch current enabled set). */
    fun deleteFilter(name: String, filterType:String) {

        if(filterType == "Quest") {
            Log.d("FilterPreferences", "Deleting QUEST filter $name")
            questPrefs.edit().remove("$QUEST_FILTER_PREFIX$name").apply()
            val all = questPrefs.getStringSet(KEY_QUEST_FILTERS, emptySet())!!.toMutableSet()
            all.remove(name)
            questPrefs.edit().putStringSet(KEY_QUEST_FILTERS, all).apply()
            return
        }

        prefs.edit().remove("$KEY_FILTER_PREFIX$name").apply()
        val all = prefs.getStringSet(KEY_ALL_FILTERS, emptySet())!!.toMutableSet()
        all.remove(name)
        prefs.edit().putStringSet(KEY_ALL_FILTERS, all).apply()
    }




    /**
     * Retrieves the set of enabled character IDs from SharedPreferences.
     *
     * @return A set of integers representing the enabled characters.
     *
     * How it works:
     * 1. Attempts to retrieve a stored Set<String> using the KEY_CHARACTERS key.
     * 2. If a set is found, it maps each string back to an integer using toIntOrNull().
     *    - toIntOrNull() converts the string to an integer, returning null if the conversion fails.
     *    - mapNotNull() applies the conversion and filters out any null values.
     * 3. Converts the resulting list of integers back to a Set<Int>.
     * 4. If no set is found (i.e., the value is null), it returns a default set from DataMappings.characterNamesMap.keys.
     */
    fun getEnabledCharacters(): Set<Int> {
        // Attempt to get the saved set of strings; if not found, null is returned.
        return prefs.getStringSet(KEY_CHARACTERS, null)
            // If the retrieved value is not null, convert each string to an integer.
            ?.mapNotNull { it.toIntOrNull() }
            // Convert the list of integers into a set.
            ?.toSet()
        // If no set was saved (the result is null), use the default set of characters.
            ?: DataMappings.characterNamesMap.keys
    }

    /**
     * Resets the filters to their default values.
     *
     * How it works:
     * 1. Removes the stored set of enabled characters using the KEY_CHARACTERS key.
     * 2. The next call to getEnabledCharacters() will return the default values.
     */
    fun resetToDefault() {
        prefs.edit().remove(KEY_CHARACTERS).apply()
    }

    /**
     * Wipes the invasion filter completely by clearing all stored filter values.
     * This does not affect the data source settings.
     */
    fun wipeFilters() {
        prefs.edit().putStringSet(KEY_CHARACTERS, emptySet()).apply()
    }

    companion object {
        // Constant for the SharedPreferences file name.
        private const val PREF_NAME = "invasion_filters"
        private const val QUEST_PREF_NAME = "quest_filters"
        // Constant key for saving and retrieving the set of enabled characters.
        private const val KEY_CHARACTERS = "enabled_characters"
    }
}
