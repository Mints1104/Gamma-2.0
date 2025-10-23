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
import com.mints.projectgammatwo.data.Quests
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import okio.`-DeprecatedOkio`.source
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
                            }catch(e: IOException) {
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
                val enabledCharacters = filterPreferences.getEnabledCharacters()
                _currentFilterSize.value = enabledCharacters.size

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
                _currentInvasionCount.value = filteredAndSorted.size

                CurrentInvasionData.currentInvasions = filteredAndSorted.toMutableList()
                _deletedCount.value = deletedRepo.getDeletionCountLast24Hours()

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
        deletedRepo.addDeletedInvasion(invasion)
        _invasions.value = _invasions.value?.toMutableList()?.apply { remove(invasion) }
        _deletedCount.value = deletedRepo.getDeletionCountLast24Hours()
    }
}
