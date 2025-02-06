package com.mints.projectgammatwo.data

import android.content.Context
import android.content.SharedPreferences


class FilterPreferences(context: Context) {

    // Initialize SharedPreferences with a specific file name ("invasion_filters") and private access mode.
    // The context is used to access the preferences file.
    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

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

        // Begin editing SharedPreferences, store the converted set, and apply the changes.
        prefs.edit()
            .putStringSet(KEY_CHARACTERS, stringSet)
            .apply() // Asynchronously saves the changes without blocking the main thread.
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

    companion object {
        // Constant for the SharedPreferences file name.
        private const val PREF_NAME = "invasion_filters"
        // Constant key for saving and retrieving the set of enabled characters.
        private const val KEY_CHARACTERS = "enabled_characters"
    }
}
