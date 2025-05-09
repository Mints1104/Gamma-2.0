// HomeViewModel.kt
package com.mints.projectgammatwo.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.*
import com.mints.projectgammatwo.data.ApiClient
import com.mints.projectgammatwo.data.CurrentInvasionData
import com.mints.projectgammatwo.data.DataSourcePreferences
import com.mints.projectgammatwo.data.FilterPreferences
import com.mints.projectgammatwo.data.Invasion
import com.mints.projectgammatwo.data.DeletedInvasionsRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val filterPreferences = FilterPreferences(application)
    private val deletedRepo = DeletedInvasionsRepository(application)
    private val dataSourcePreferences = DataSourcePreferences(application)

    private val _invasions = MutableLiveData<List<Invasion>>()
    val invasions: LiveData<List<Invasion>> get() = _invasions

    // For the deletion counter (from previous code)
    private val _deletedCount = MutableLiveData<Int>()
    val deletedCount: LiveData<Int> get() = _deletedCount

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> get() = _error

    companion object {
        private const val TAG = "HomeViewModel"
    }

    fun fetchInvasions() {
        Log.d(TAG, "Starting to fetch invasions...")

        viewModelScope.launch {
            try {
                val selectedSources = dataSourcePreferences.getSelectedSources()

                // For each source, fetch invasions and tag them with the source string.
                val deferredList = selectedSources.mapNotNull { source ->
                    ApiClient.DATA_SOURCE_URLS[source]?.let { baseUrl ->
                        async {
                            // Fetch the invasions from the source
                            ApiClient.getApiForBaseUrl(baseUrl)
                                .getInvasions().invasions
                                .map { invasion ->
                                    // Add the source to each invasion
                                    invasion.copy(source = source)
                                }
                        }
                    }
                }

                val combinedInvasions = deferredList.flatMap { it.await() }.toMutableList()

                val enabledCharacters = filterPreferences.getEnabledCharacters()
                val filteredAndSorted = combinedInvasions
                    .map { invasion ->
                        when (invasion.type) {
                            8 -> invasion.copy(character = 1)
                            9 -> invasion.copy(character = 0)
                            else -> invasion
                        }
                    }
                    .filter { invasion ->
                        invasion.character in enabledCharacters &&
                                !deletedRepo.isInvasionDeleted(invasion)
                    }
                    .sortedBy { it.invasion_start }
                    .reversed()

                _invasions.value = filteredAndSorted
                CurrentInvasionData.currentInvasions = filteredAndSorted.toMutableList()
                _deletedCount.value = deletedRepo.getDeletionCountLast24Hours()

                Log.d(TAG, "Fetched invasions from ${selectedSources.size} sources. Total items: ${filteredAndSorted.size}")
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching invasions: ${e.message}", e)
                _error.value = "Failed to fetch invasions: ${e.message}"
            }
        }
    }

    fun getInvasions(): List<Invasion>? {
        return _invasions.value
    }


    // When an invasion is deleted.
    fun deleteInvasion(invasion: Invasion) {
        deletedRepo.addDeletedInvasion(invasion)
        _invasions.value = _invasions.value?.toMutableList()?.apply { remove(invasion) }
        _deletedCount.value = deletedRepo.getDeletionCountLast24Hours()
    }
}
