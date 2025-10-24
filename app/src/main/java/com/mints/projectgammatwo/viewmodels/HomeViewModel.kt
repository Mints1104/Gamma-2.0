package com.mints.projectgammatwo.viewmodels

import android.app.Application
import android.util.Log
import android.util.Log.e
import androidx.lifecycle.*
import com.google.gson.JsonSyntaxException
import com.mints.projectgammatwo.data.ApiClient
import com.mints.projectgammatwo.data.CurrentInvasionData
import com.mints.projectgammatwo.data.DataSourcePreferences
import com.mints.projectgammatwo.data.DeletedEntry
import com.mints.projectgammatwo.data.FilterPreferences
import com.mints.projectgammatwo.data.Invasion
import com.mints.projectgammatwo.data.DeletedInvasionsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.IOException
import retrofit2.HttpException

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val filterPreferences = FilterPreferences(application)
    private val deletedRepo = DeletedInvasionsRepository(application)
    private val dataSourcePreferences = DataSourcePreferences(application)

    private val _invasions = MutableLiveData<List<Invasion>>()
    val invasions: LiveData<List<Invasion>> get() = _invasions

    private val _deletedCount = MutableLiveData<Int>()
    val deletedCount: LiveData<Int> get() = _deletedCount

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> get() = _error

    private val _currentFilterSize = MutableLiveData<Int>()
    val currentFilterSize: LiveData<Int> get() = _currentFilterSize

    private val _currentInvasionCount = MutableLiveData<Int>()
    val currentInvasionCount: LiveData<Int> get() = _currentInvasionCount

    companion object {
        private const val TAG = "HomeViewModel"
    }

    fun fetchInvasions() {
        Log.d(TAG, "Starting to fetch invasions...")

        viewModelScope.launch {
            try {
                val selectedSources = dataSourcePreferences.getSelectedSources()
                val deferredList = selectedSources.mapNotNull { source ->
                    ApiClient.DATA_SOURCE_URLS[source]?.let { baseUrl ->
                        async {
                            try {
                                ApiClient.getApiForBaseUrl(baseUrl)
                                    .getInvasions().invasions
                                    .map { invasion -> invasion.copy(source = source) }
                            } catch(e: IOException) {
                                e(TAG, "Network error: ${e.message}", e)
                                _error.value = "Network error: ${e.message}"
                                emptyList()
                            } catch (e: HttpException) {
                                e(TAG, "HTTP error: ${e.code()} - ${e.message}", e)
                                _error.value = "HTTP error: ${e.code()} - ${e.message}"
                                emptyList()
                            } catch (e: JsonSyntaxException) {
                                e(TAG, "JSON parsing error: ${e.message}", e)
                                _error.value = "JSON parsing error: ${e.message}"
                                emptyList()
                            } catch (e: Exception) {
                                e(TAG, "Source “$source” failed: ${e.message}", e)
                                emptyList()
                            }
                        }
                    }
                }

                val combinedInvasions = deferredList
                    .mapNotNull {
                        try {
                            it.await()
                        } catch (e: Exception) {
                            e(TAG, "Failed to await result: ${e.message}", e)
                            null
                        }
                    }
                    .flatten()
                    .toMutableList()

                // Load preferences and deleted entries off the main thread
                val enabledCharacters = withContext(Dispatchers.IO) {
                    filterPreferences.getEnabledCharacters()
                }
                _currentFilterSize.value = enabledCharacters.size

                // Retrieve deleted entries once and use a fast lookup set
                val deletedEntries = withContext(Dispatchers.IO) {
                    deletedRepo.getDeletedEntries()
                }
                val deletedKeySet = deletedEntries.map { it.lat to it.lng }.toHashSet()

                // Perform CPU-bound mapping/filtering off the main thread
                val filteredAndSorted = withContext(Dispatchers.Default) {
                    combinedInvasions
                        .asSequence()
                        .map { invasion ->
                            when (invasion.type) {
                                8 -> invasion.copy(character = 1)
                                9 -> invasion.copy(character = 0)
                                else -> invasion
                            }
                        }
                        .filter { invasion ->
                            invasion.character in enabledCharacters &&
                                    !(invasion.lat to invasion.lng in deletedKeySet)
                        }
                        .sortedBy { it.invasion_start }
                        .toList()
                        .asReversed()
                }

                _invasions.value = filteredAndSorted
                _currentInvasionCount.value = filteredAndSorted.size

                CurrentInvasionData.currentInvasions = filteredAndSorted.toMutableList()

                // deletedEntries is already pruned to last 24h by repo
                _deletedCount.value = deletedEntries.size

                Log.d(
                    TAG,
                    "Fetched invasions from ${selectedSources.size} sources. Total items: ${filteredAndSorted.size}"
                )
            } catch (e: Exception) {
                e(TAG, "Error fetching invasions: ${e.message}", e)
                _error.value = "Failed to fetch invasions: ${e.message}"
            }
        }
    }

    fun getInvasions(): List<Invasion>? {
        return _invasions.value
    }

    fun getDeletedInvasions(): Set<DeletedEntry> {
        return deletedRepo.getDeletedEntries()
    }

    // When an invasion is deleted.
    fun deleteInvasion(invasion: Invasion) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                deletedRepo.addDeletedInvasion(invasion)
            }
            _invasions.value = _invasions.value?.toMutableList()?.apply { remove(invasion) }
            // Refresh the count efficiently using one repo read on IO
            val count = withContext(Dispatchers.IO) { deletedRepo.getDeletionCountLast24Hours() }
            _deletedCount.value = count
        }
    }
}
