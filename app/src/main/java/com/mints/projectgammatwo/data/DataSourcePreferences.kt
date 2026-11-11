package com.mints.projectgammatwo.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class DataSourcePreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("data_source_preferences", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_SELECTED_SOURCES = "selected_sources"
        val DEFAULT_SOURCES = setOf("NYC")

        /**
         * The sources in [sources] that the app knows ([known]); null when none are, so callers
         * keep what they have instead of saving an empty selection, which loaded nothing at all.
         */
        fun sanitizeSources(sources: Collection<String?>?, known: Set<String>): Set<String>? =
            sources.orEmpty().filterNotNull().filter { it in known }.toSet().ifEmpty { null }
    }

    /** Never empty: an empty or unrecognised stored selection reads as the default. */
    fun getSelectedSources(): Set<String> =
        sanitizeSources(prefs.getStringSet(KEY_SELECTED_SOURCES, null), ApiClient.DATA_SOURCE_URLS.keys)
            ?: DEFAULT_SOURCES

    fun setSelectedSources(sources: Set<String>) {
        prefs.edit { putStringSet(KEY_SELECTED_SOURCES, HashSet(sources)) }
    }
}
