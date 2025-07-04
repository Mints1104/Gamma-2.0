package com.mints.projectgammatwo.data

import android.content.Context
import androidx.core.content.edit

class QuestFilterPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("quest_filters", Context.MODE_PRIVATE)

    fun getEnabledFilters(): Set<String> {
        return prefs.getStringSet("enabled_quest_filters", emptySet()) ?: emptySet()
    }

    fun saveEnabledFilters(filters: Set<String>) {
        prefs.edit { putStringSet("enabled_quest_filters", filters) }
    }

    fun getEnabledSpindaForms(): Set<String> {
        return prefs.getStringSet("enabled_spinda_forms", emptySet()) ?: emptySet()

    }

    fun clearEnabledSpindaForms() {
        prefs.edit { remove("enabled_spinda_forms") }
    }



    fun saveEnabledSpindaForms(forms: Set<String>) {
        prefs.edit { putStringSet("enabled_spinda_forms", forms) }

    }
}
