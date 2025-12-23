package com.mints.projectgammatwo.viewmodels

import android.app.Application
import android.content.Context
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
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

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

    // LiveData for current sort mode
    private val _sortByDistance = MutableLiveData<Boolean>()
    val sortByDistance: LiveData<Boolean> get() = _sortByDistance

    // Track current sort mode. Default is time-based sorting.
    private var sortByDistanceInternal: Boolean = false

    // Job to cancel ongoing fetch if new one is started
    private var fetchJob: kotlinx.coroutines.Job? = null

    companion object {
        private const val TAG = "HomeViewModel"
    }

    fun fetchInvasions() {
        Log.d(TAG, "Starting to fetch invasions...")

        // Cancel any ongoing fetch operation before starting a new one
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
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
                val baseList = withContext(Dispatchers.Default) {
                    val currentTimeSeconds = System.currentTimeMillis() / 1000
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
                                    invasion.lat to invasion.lng !in deletedKeySet &&
                                    invasion.invasion_end > currentTimeSeconds
                        }
                        .toList()
                }

                // Apply sorting according to current mode
                val finalList = applySorting(baseList)

                _invasions.value = finalList
                _currentInvasionCount.value = finalList.size

                CurrentInvasionData.currentInvasions = finalList.toMutableList()

                _deletedCount.value = deletedEntries.size

                Log.d(
                    TAG,
                    "Fetched invasions from ${selectedSources.size} sources. Total items: ${finalList.size}"
                )
            } catch (e: Exception) {
                e(TAG, "Error fetching invasions: ${e.message}", e)
                _error.value = "Failed to fetch invasions: ${e.message}"
            }
        }
    }

    // Apply time or distance sort based on current sortByDistance flag
    private fun applySorting(list: List<Invasion>): List<Invasion> {
        if (list.isEmpty()) return list
        return if (sortByDistanceInternal) {
            val (lat, lng) = loadLastInvasionCoordinates()
            if (lat != null && lng != null) {
                Log.d(TAG, "Sorting by distance using stored coordinates: ($lat, $lng)")
                list.sortedBy { haversineDistanceFromPoint(lat, lng, it) }
            } else if (list.isNotEmpty()) {
                // Use the first invasion's coordinates as fallback
                val first = list.first()
                Log.d(TAG, "Sorting by distance using first invasion coordinates as fallback: (${first.lat}, ${first.lng})")
                list.sortedBy { haversineDistanceFromPoint(first.lat, first.lng, it) }
            } else {
                Log.d(TAG, "No coordinates available for distance sorting, returning unsorted list")
                list
            }
        } else {
            Log.d(TAG, "Sorting by time (reversed)")
            list.sortedBy { it.invasion_start }.asReversed()
        }
    }

    // Distance helpers from a point
    private fun haversineDistanceFromPoint(lat: Double, lng: Double, invasion: Invasion): Double {
        val R = 6371e3 // meters
        val lat1 = Math.toRadians(lat)
        val lat2 = Math.toRadians(invasion.lat)
        val dLat = Math.toRadians(invasion.lat - lat)
        val dLon = Math.toRadians(invasion.lng - lng)
        val aVal = sin(dLat / 2) * sin(dLat / 2) +
                cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(aVal), sqrt(1 - aVal))
        return R * c
    }

    // Load last invasion coordinates (fallback to quests key if present)
    private fun loadLastInvasionCoordinates(): Pair<Double?, Double?> {
        val context = getApplication<Application>().applicationContext
        val prefs = context.getSharedPreferences("last_invasion_pref", Context.MODE_PRIVATE)
        val combined = prefs.getString("lastInvasion", null)
        if (!combined.isNullOrEmpty() && "," in combined) {
            val parts = combined.split(",")
            val lat = parts.getOrNull(0)?.toDoubleOrNull()
            val lng = parts.getOrNull(1)?.toDoubleOrNull()
            return lat to lng
        }
        // Fallback: use quests last visited if available
        val qp = context.getSharedPreferences("last_visited_pref", Context.MODE_PRIVATE)
        val qCombined = qp.getString("lastVisited", null)
        return if (!qCombined.isNullOrEmpty() && "," in qCombined) {
            val parts = qCombined.split(",")
            parts.getOrNull(0)?.toDoubleOrNull() to parts.getOrNull(1)?.toDoubleOrNull()
        } else {
            val latF = qp.getFloat("lastVisitedLat", Float.NaN)
            val lngF = qp.getFloat("lastVisitedLng", Float.NaN)
            val lat = if (!latF.isNaN()) latF.toDouble() else null
            val lng = if (!lngF.isNaN()) lngF.toDouble() else null
            lat to lng
        }
    }

    // Persist last invasion coordinate when user handles one (e.g., teleport/delete)
    private fun saveLastInvasionCoordinates(invasion: Invasion) {
        val context = getApplication<Application>().applicationContext
        val prefs = context.getSharedPreferences("last_invasion_pref", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("lastInvasion", "${invasion.lat},${invasion.lng}")
            putFloat("lastInvasionLat", invasion.lat.toFloat())
            putFloat("lastInvasionLng", invasion.lng.toFloat())
            apply()
        }
    }

    fun sortInvasions(sortType: Boolean) {
        when (sortType) {
            true -> Log.d(TAG, "Filter by distance")
            else -> Log.d(TAG, "Filter by time")
        }

        // Update mode and resort current list asynchronously, filtering out expired invasions
        sortByDistanceInternal = sortType
        _sortByDistance.value = sortType
        viewModelScope.launch(Dispatchers.Default) {
            val current = _invasions.value ?: emptyList()
            val currentTimeSeconds = System.currentTimeMillis() / 1000
            Log.d(TAG, "Current time seconds: $currentTimeSeconds")
            if (current.isNotEmpty()) {
                Log.d(TAG, "Sample invasion_end: ${current.first().invasion_end}")
            }
            val filtered = current.filter { it.invasion_end > currentTimeSeconds }
            Log.d(TAG, "Filtered from ${current.size} to ${filtered.size} invasions")
            val sorted = applySorting(filtered)
            _invasions.postValue(sorted)
            _currentInvasionCount.postValue(sorted.size)
        }
    }

    fun getInvasions(): List<Invasion>? {
        return _invasions.value
    }

    fun getDeletedInvasions(): Set<DeletedEntry> {
        return deletedRepo.getDeletedEntries()
    }

    // When an invasion is deleted/handled, record its coords as last invasion and remove from list.
    fun deleteInvasion(invasion: Invasion) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                deletedRepo.addDeletedInvasion(invasion)
            }
            // Save last invasion coordinates for distance-based ordering
            saveLastInvasionCoordinates(invasion)

            // Resort asynchronously
            val updatedList = _invasions.value?.toMutableList()?.apply { remove(invasion) }
            if (updatedList != null) {
                val sorted = withContext(Dispatchers.Default) { applySorting(updatedList) }
                _invasions.postValue(sorted)
            }
            // Refresh the count efficiently using one repo read on IO
            val count = withContext(Dispatchers.IO) { deletedRepo.getDeletionCountLast24Hours() }
            _deletedCount.postValue(count)
        }
    }
}
