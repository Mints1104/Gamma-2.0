package com.mints.projectgammatwo.data

import android.content.Context

class QuestFilterPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("quest_filters", Context.MODE_PRIVATE)

    // Returns a set of filters (each filter is a string like "2,0,708")
    fun getEnabledFilters(): Set<String> {
        return prefs.getStringSet("enabled_quest_filters", emptySet()) ?: emptySet()
    }

    fun saveEnabledFilters(filters: Set<String>) {
        prefs.edit().putStringSet("enabled_quest_filters", filters).apply()
    }
}
