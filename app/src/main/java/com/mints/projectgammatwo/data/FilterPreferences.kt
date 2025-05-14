package com.mints.projectgammatwo.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

class FilterPreferences(context: Context) {


    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val KEY_ALL_FILTERS = "all_filters"
    private val KEY_FILTER_PREFIX = "filter_"

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


    fun loadFilter(name: String) {
        val saved = prefs.getStringSet("$KEY_FILTER_PREFIX$name", emptySet())!!
        val ints = saved.mapNotNull { it.toIntOrNull() }.toSet()
        saveEnabledCharacters(ints)
    }
    fun listFilterNames(): Set<String> =
        prefs.getStringSet(KEY_ALL_FILTERS, emptySet())!!

    /** Delete a saved filter (does not touch current enabled set). */
    fun deleteFilter(name: String) {
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
        // Constant key for saving and retrieving the set of enabled characters.
        private const val KEY_CHARACTERS = "enabled_characters"
    }
}
