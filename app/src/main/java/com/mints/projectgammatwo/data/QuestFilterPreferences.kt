package com.mints.projectgammatwo.data

import android.content.Context
import androidx.core.content.edit

class QuestFilterPreferences(context: Context) {
    private val prefs = context.getSharedPreferences(
        FilterPreferences.QUEST_PREF_NAME, Context.MODE_PRIVATE
    )

    fun getEnabledFilters(): Set<String> =
        prefs.getStringSet("enabled_quest_filters", emptySet())?.toSet() ?: emptySet()

    fun saveEnabledFilters(filters: Set<String>) {
        prefs.edit(commit = true) { putStringSet("enabled_quest_filters", HashSet(filters)) }
    }

    fun getEnabledEncounterConditions(): Set<String> =
        prefs.getStringSet(FilterPreferences.KEY_ENABLED_ENCOUNTER_CONDITIONS, emptySet())?.toSet() ?: emptySet()

    fun saveEnabledEncounterConditions(conditions: Set<String>) {
        prefs.edit(commit = true) { putStringSet(FilterPreferences.KEY_ENABLED_ENCOUNTER_CONDITIONS, HashSet(conditions)) }
    }
}
