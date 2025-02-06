package com.mints.projectgammatwo.data

import android.content.Context
import android.content.SharedPreferences

// This class is responsible for saving and loading user preferences related to enabled characters.
class FilterPreferences(context: Context) {
    // SharedPreferences is used to save data locally on the device
    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    // Save enabled characters to SharedPreferences
    fun saveEnabledCharacters(characters: Set<Int>) {
        prefs.edit().putStringSet(KEY_CHARACTERS, characters.map { it.toString() }.toSet()).apply()
    }

    // Get the enabled characters from SharedPreferences, or use the default values if no preferences are saved
    fun getEnabledCharacters(): Set<Int> {
        return prefs.getStringSet(KEY_CHARACTERS, null)
            ?.mapNotNull { it.toIntOrNull() }
            ?.toSet()
            ?: DataMappings.characterNamesMap.keys // Default to all characters enabled
    }

    companion object {
        // Constants for SharedPreferences keys
        private const val PREF_NAME = "invasion_filters"
        private const val KEY_CHARACTERS = "enabled_characters"
    }
}
