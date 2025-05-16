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
    private val KEY_ACTIVE_ROCKET_FILTER = "active_rocket_filter"
    private val KEY_ACTIVE_QUEST_FILTER = "active_quest_filter"
    private var currentRocketFilterName: String = "Default" // Or some initial value, or load last active



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

        // If there's an active filter, update it with the new values
        val activeFilter = getActiveRocketFilter()
        if (activeFilter.isNotEmpty()) {
            updateFilter(activeFilter, characters)
        }
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

        // Don't automatically set this as the active filter
        // If we want to set this as active after saving:
        // setActiveRocketFilter(name)
    }

    // Returns a set of filters (each filter is a string like "2,0,708")
    fun getEnabledQuestFilters(): Set<String> {
        return questPrefs.getStringSet("enabled_quest_filters", emptySet()) ?: emptySet()
    }

    fun saveEnabledQuestFilters(filters: Set<String>) {
        questPrefs.edit().putStringSet("enabled_quest_filters", filters).apply()

        // If there's an active filter, update it with the new values
        val activeFilter = getActiveQuestFilter()
        if (activeFilter.isNotEmpty()) {
            updateQuestFilter(activeFilter, filters)
        }
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

        // Don't automatically set this as the active filter
        // If we want to set this as active after saving:
        // setActiveQuestFilter(name)
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

    fun getQuestFilter(name: String): Set<String> {
        val saved = questPrefs.getStringSet("$QUEST_FILTER_PREFIX$name", emptySet())!!
        Log.d("FilterPreferences", "Loaded quest filter $name with values: $saved")
        return saved
    }

    fun loadFilter(name: String, type: String) {
        if (type == "Quest") {
            Log.d("FilterPreferences", "Loading QUEST filter $name (Prior Active Quest: ${getActiveQuestFilter()})")
            val savedFilterData = questPrefs.getStringSet("$QUEST_FILTER_PREFIX$name", emptySet())!! // Renamed for clarity
            Log.d("FilterPreferences", "Data for $name: $savedFilterData")


            // --- SOLUTION FOR QUEST FILTERS ---
            // 1. Set this filter name as the ACTIVE quest filter FIRST.
            setActiveQuestFilter(name)
            // Log.d("FilterPreferences", "Active quest filter set to: $name")

            // 2. Now call saveEnabledQuestFilters.
            // This will:
            //    a. Update "enabled_quest_filters" (the current working set for quests) with `savedFilterData`.
            //    b. Inside saveEnabledQuestFilters, getActiveQuestFilter() will correctly return `name`.
            //    c. It will then call updateQuestFilter(`name`, `savedFilterData`), effectively re-saving
            //       the loaded filter under its own name. This is okay and prevents corrupting other filters.
            saveEnabledQuestFilters(savedFilterData)
            Log.d("FilterPreferences", "Loaded and applied quest filter $name. Active quest is now: ${getActiveQuestFilter()}")
            return
        }

        // ROCKET filter logic:
        Log.d("FilterPreferences", "Loading ROCKET filter $name (Prior Active Rocket: ${getActiveRocketFilter()})")
        val savedFilterDataSet = prefs.getStringSet("$KEY_FILTER_PREFIX$name", emptySet())!! // Renamed for clarity
        val charactersToLoad = savedFilterDataSet.mapNotNull { it.toIntOrNull() }.toSet() // Renamed for clarity
        Log.d("FilterPreferences", "Data for $name: $charactersToLoad")

        // --- SOLUTION FOR ROCKET FILTERS ---
        // 1. Set this filter name as the ACTIVE rocket filter FIRST.
        setActiveRocketFilter(name)
        // Log.d("FilterPreferences", "Active rocket filter set to: $name")

        // 2. Now call saveEnabledCharacters.
        // This will:
        //    a. Update KEY_CHARACTERS (the current working set for rocket filters) with `charactersToLoad`.
        //    b. Inside saveEnabledCharacters, getActiveRocketFilter() will correctly return `name`.
        //    c. It will then call updateFilter(`name`, `charactersToLoad`), effectively re-saving
        //       the loaded filter under its own name. This is okay and prevents corrupting other filters.
        saveEnabledCharacters(charactersToLoad)
        Log.d("FilterPreferences", "Loaded and applied rocket filter $name. Active rocket is now: ${getActiveRocketFilter()}")
    }

    fun listFilterNames(): Set<String> =
        prefs.getStringSet(KEY_ALL_FILTERS, emptySet())!!

    /** Delete a saved filter (does not touch current enabled set). */
    fun deleteFilter(name: String, filterType: String) {
        if(filterType == "Quest") {
            Log.d("FilterPreferences", "Deleting QUEST filter $name")
            questPrefs.edit().remove("$QUEST_FILTER_PREFIX$name").apply()
            val all = questPrefs.getStringSet(KEY_QUEST_FILTERS, emptySet())!!.toMutableSet()
            all.remove(name)
            questPrefs.edit().putStringSet(KEY_QUEST_FILTERS, all).apply()

            // If this was the active filter, clear it
            if (name == getActiveQuestFilter()) {
                clearActiveQuestFilter()
            }
            return
        }

        prefs.edit().remove("$KEY_FILTER_PREFIX$name").apply()
        val all = prefs.getStringSet(KEY_ALL_FILTERS, emptySet())!!.toMutableSet()
        all.remove(name)
        prefs.edit().putStringSet(KEY_ALL_FILTERS, all).apply()

        // If this was the active filter, clear it
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


    // Set the active rocket filter name
    fun setActiveRocketFilter(name: String) {
        prefs.edit().putString(KEY_ACTIVE_ROCKET_FILTER, name).apply()
    }

    // Get the active rocket filter name
    fun getActiveRocketFilter(): String {
        return prefs.getString(KEY_ACTIVE_ROCKET_FILTER, "") ?: ""
    }

    // Clear the active rocket filter
    fun clearActiveRocketFilter() {
        prefs.edit().remove(KEY_ACTIVE_ROCKET_FILTER).apply()
    }

    // Set the active quest filter name
    fun setActiveQuestFilter(name: String) {
        questPrefs.edit().putString(KEY_ACTIVE_QUEST_FILTER, name).apply()
    }

    // Get the active quest filter name
    fun getActiveQuestFilter(): String {
        return questPrefs.getString(KEY_ACTIVE_QUEST_FILTER, "") ?: ""
    }

    // Clear the active quest filter
    fun clearActiveQuestFilter() {
        questPrefs.edit().remove(KEY_ACTIVE_QUEST_FILTER).apply()
    }

    // Update a specific rocket filter with new values
    fun updateFilter(name: String, characters: Set<Int>) {
        val stringSet = characters.map { it.toString() }.toSet()
        prefs.edit()
            .putStringSet("$KEY_FILTER_PREFIX$name", stringSet)
            .apply()
        Log.d("FilterPreferences", "Updated filter $name with characters: $characters")
    }

    // Update a specific quest filter with new values
    fun updateQuestFilter(name: String, filters: Set<String>) {
        questPrefs.edit()
            .putStringSet("$QUEST_FILTER_PREFIX$name", filters)
            .apply()
        Log.d("FilterPreferences", "Updated filter $name with quests: $filters")
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
        // Clear any active filter
        clearActiveRocketFilter()
    }

    /**
     * Wipes the invasion filter completely by clearing all stored filter values.
     * This does not affect the data source settings.
     */
    fun wipeFilters() {
        prefs.edit().putStringSet(KEY_CHARACTERS, emptySet()).apply()
        // Clear any active filter
        clearActiveRocketFilter()
    }

    companion object {
        // Constant for the SharedPreferences file name.
        private const val PREF_NAME = "invasion_filters"
        private const val QUEST_PREF_NAME = "quest_filters"
        // Constant key for saving and retrieving the set of enabled characters.
        private const val KEY_CHARACTERS = "enabled_characters"
    }
}