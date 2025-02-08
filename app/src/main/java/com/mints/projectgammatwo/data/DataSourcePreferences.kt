package com.mints.projectgammatwo.data

import android.content.Context
import android.content.SharedPreferences

class DataSourcePreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("data_source_preferences", Context.MODE_PRIVATE)
    private val KEY_SELECTED_SOURCES = "selected_sources"
    private val DEFAULT_SOURCES = setOf("NYC")

    fun getSelectedSources(): Set<String> =
        prefs.getStringSet(KEY_SELECTED_SOURCES, DEFAULT_SOURCES) ?: DEFAULT_SOURCES

    fun setSelectedSources(sources: Set<String>) {
        prefs.edit().putStringSet(KEY_SELECTED_SOURCES, sources).apply()
    }
}
