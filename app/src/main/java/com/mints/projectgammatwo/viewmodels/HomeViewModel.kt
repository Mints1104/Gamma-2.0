package com.mints.projectgammatwo.viewmodels

import android.app.Application
import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.util.Log.e
import androidx.core.content.edit
import androidx.lifecycle.*
import com.google.gson.JsonSyntaxException
import com.mints.projectgammatwo.data.ApiClient
import com.mints.projectgammatwo.data.CurrentInvasionData
import com.mints.projectgammatwo.data.DataSourcePreferences
import com.mints.projectgammatwo.data.DeletedEntry
import com.mints.projectgammatwo.data.FilterPreferences
import com.mints.projectgammatwo.data.Invasion
import com.mints.projectgammatwo.data.DeletedInvasionsRepository
import com.mints.projectgammatwo.helpers.Event
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
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

    /**
     * Each error is delivered as a one-shot [Event]: LiveData replays its latest value to every
     * new observer, so exposing the string directly re-showed the last error toast after every
     * rotation and every return to the tab.
     */
    val error: LiveData<Event<String>> = _error.map { Event(it) }

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

    // Cache for last invasion coordinates to avoid repeated SharedPreferences reads
    private var cachedLastInvasionCoords: Pair<Double?, Double?>? = null

    /** Everything that decides what the invasion list contains. */
    private data class Inputs(
        val sources: Set<String>,
        val characters: Set<Int>,
        val deletedFingerprint: Int,
    )

    /** What the published list was built from, and when; null until a fetch fully succeeds. */
    private var publishedInputs: Inputs? = null
    private var publishedAtMs = 0L

    private val appPrefs = application.getSharedPreferences(APP_PREFS_NAME, Context.MODE_PRIVATE)
    private var sortModeRestored = false

    companion object {
        private const val TAG = "HomeViewModel"

        /** A published list younger than this, built from the current inputs, is reused. */
        private const val STALE_AFTER_MS = 60_000L

        private const val APP_PREFS_NAME = "app_preferences"
        private const val KEY_SORT_BY_DISTANCE = "invasions_sort_by_distance"
    }

    private fun currentInputs() = Inputs(
        sources = dataSourcePreferences.getSelectedSources(),
        characters = filterPreferences.getEnabledCharacters(),
        deletedFingerprint = deletedRepo.fingerprint(),
    )

    /**
     * Fetches only if the published list is out of date: older than [STALE_AFTER_MS], or built
     * from different sources, filters or deletions than are now in effect — including changes
     * made elsewhere, such as the overlay recording a deletion or the history screen's clear.
     *
     * This view model is activity-scoped so it survives tab switches; the screen used to
     * refetch every source on every visit. Pull-to-refresh still calls [fetchInvasions].
     *
     * @return whether a fetch was started.
     */
    fun refreshIfStale(): Boolean {
        val age = SystemClock.elapsedRealtime() - publishedAtMs
        if (currentInputs() == publishedInputs && age < STALE_AFTER_MS) return false
        fetchInvasions()
        return true
    }

    fun fetchInvasions() {
        Log.d(TAG, "Starting to fetch invasions...")
        val inputs = currentInputs()

        // Cancel any ongoing fetch operation before starting a new one
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            // Sources that failed. Only a fetch where every source succeeded is recorded as the
            // published state; otherwise the list is incomplete and the next visit retries.
            // Only touched on the main thread: the async blocks below inherit it.
            var failedSources = 0
            try {
                val selectedSources = dataSourcePreferences.getSelectedSources()
                val deferredList = selectedSources.mapNotNull { source ->
                    ApiClient.DATA_SOURCE_URLS[source]?.let { baseUrl ->
                        async {
                            try {
                                ApiClient.getApiForBaseUrl(baseUrl)
                                    .getInvasions().invasions
                                    .map { invasion -> invasion.copy(source = source) }
                            } catch (e: CancellationException) {
                                throw e
                            } catch(e: IOException) {
                                // A superseded fetch cancels its OkHttp calls, which surface
                                // here as IOException("Canceled") — not as CancellationException.
                                // Without this the user sees a spurious error toast every time
                                // they pull-to-refresh twice or change the sort mode.
                                ensureActive()
                                e(TAG, "Network error: ${e.message}", e)
                                _error.value = "Network error: ${e.message}"
                                failedSources++
                                emptyList()
                            } catch (e: HttpException) {
                                e(TAG, "HTTP error: ${e.code()} - ${e.message}", e)
                                _error.value = "HTTP error: ${e.code()} - ${e.message}"
                                failedSources++
                                emptyList()
                            } catch (e: JsonSyntaxException) {
                                e(TAG, "JSON parsing error: ${e.message}", e)
                                _error.value = "JSON parsing error: ${e.message}"
                                failedSources++
                                emptyList()
                            } catch (e: Exception) {
                                ensureActive()
                                e(TAG, "Source “$source” failed: ${e.message}", e)
                                failedSources++
                                emptyList()
                            }
                        }
                    }
                }

                val combinedInvasions = deferredList
                    .mapNotNull {
                        try {
                            it.await()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            e(TAG, "Failed to await result: ${e.message}", e)
                            failedSources++
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

                // Perform CPU-bound mapping/filtering AND sorting off the main thread
                val finalList = withContext(Dispatchers.Default) {
                    val currentTimeSeconds = System.currentTimeMillis() / 1000
                    val baseList = combinedInvasions
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

                    // Apply sorting on background thread
                    applySorting(baseList)
                }

                _invasions.value = finalList
                _currentInvasionCount.value = finalList.size

                CurrentInvasionData.currentInvasions = finalList.toMutableList()

                _deletedCount.value = deletedEntries.size

                if (failedSources == 0) {
                    publishedInputs = inputs
                    publishedAtMs = SystemClock.elapsedRealtime()
                }

                Log.d(
                    TAG,
                    "Fetched invasions from ${selectedSources.size} sources. Total items: ${finalList.size}"
                )
            } catch (e: CancellationException) {
                // Coroutine was cancelled - this is normal behavior, don't treat it as an error
                Log.d(TAG, "Fetch invasions was cancelled")
                throw e // Re-throw to properly propagate cancellation
            } catch (e: Exception) {
                e(TAG, "Error fetching invasions: ${e.message}", e)
                _error.value = "Failed to fetch invasions: ${e.message}"
            }
        }
    }

    // Apply time or distance sort based on current sortByDistance flag
    private fun applySorting(list: List<Invasion>): List<Invasion> {
        if (list.isEmpty()) return list

        // Limit to 500 items before sorting to reduce processing time
        val limitedList = list.take(500)
        Log.d(TAG, "Limited invasions from ${list.size} to ${limitedList.size} before sorting")

        return if (sortByDistanceInternal) {
            val (lat, lng) = loadLastInvasionCoordinates()
            if (lat != null && lng != null) {
                Log.d(TAG, "Sorting by distance using stored last invasion coordinates: ($lat, $lng)")
                limitedList.sortedBy { haversineDistanceFromPoint(lat, lng, it) }
            } else {
                // Fallback to first invasion's coordinates
                val first = limitedList.first()
                Log.d(TAG, "Sorting by distance using first invasion coordinates (no stored coords): (${first.lat}, ${first.lng})")
                limitedList.sortedBy { haversineDistanceFromPoint(first.lat, first.lng, it) }
            }
        } else {
            Log.d(TAG, "Sorting by time (reversed)")
            limitedList.sortedBy { it.invasion_start }.asReversed()
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

    // Load last invasion coordinates from cache or SharedPreferences (NO quest fallback)
    private fun loadLastInvasionCoordinates(): Pair<Double?, Double?> {
        // Return cached value if available
        cachedLastInvasionCoords?.let { return it }

        // Otherwise load from SharedPreferences
        val context = getApplication<Application>().applicationContext
        val prefs = context.getSharedPreferences("last_invasion_pref", Context.MODE_PRIVATE)
        val combined = prefs.getString("lastInvasion", null)
        val result = if (!combined.isNullOrEmpty() && "," in combined) {
            val parts = combined.split(",")
            val lat = parts.getOrNull(0)?.toDoubleOrNull()
            val lng = parts.getOrNull(1)?.toDoubleOrNull()
            Log.d(TAG, "Loaded last invasion coordinates from SharedPreferences: ($lat, $lng)")
            lat to lng
        } else {
            Log.d(TAG, "No stored last invasion coordinates found")
            null to null
        }

        // Cache the result
        cachedLastInvasionCoords = result
        return result
    }

    // Save last invasion coordinates to SharedPreferences on background thread
    private suspend fun saveLastInvasionCoordinates(invasion: Invasion) {
        withContext(Dispatchers.IO) {
            val context = getApplication<Application>().applicationContext
            val prefs = context.getSharedPreferences("last_invasion_pref", Context.MODE_PRIVATE)
            prefs.edit().apply {
                putString("lastInvasion", "${invasion.lat},${invasion.lng}")
                putFloat("lastInvasionLat", invasion.lat.toFloat())
                putFloat("lastInvasionLng", invasion.lng.toFloat())
                apply()
            }
            Log.d(TAG, "Saved last invasion coordinates: (${invasion.lat}, ${invasion.lng})")
        }
        // Update cache immediately
        cachedLastInvasionCoords = invasion.lat to invasion.lng
    }

    /**
     * Applies the sort the user last chose on the Home screen. Called by that screen before its
     * first fetch; the overlay's own instance never calls it, so the overlay keeps its existing
     * time ordering rather than silently switching to distance order around an unrelated point.
     */
    fun restoreSavedSortMode() {
        if (sortModeRestored) return
        sortModeRestored = true
        val saved = appPrefs.getBoolean(KEY_SORT_BY_DISTANCE, false)
        sortByDistanceInternal = saved
        _sortByDistance.value = saved
    }

    fun sortInvasions(sortType: Boolean) {
        when (sortType) {
            true -> Log.d(TAG, "Switching to sort by distance")
            else -> Log.d(TAG, "Switching to sort by time")
        }

        // Update the sort mode, and remember it across app restarts
        sortByDistanceInternal = sortType
        _sortByDistance.value = sortType
        sortModeRestored = true
        appPrefs.edit { putBoolean(KEY_SORT_BY_DISTANCE, sortType) }

        // Auto-refresh invasions to get fresh data with the new sort mode
        Log.d(TAG, "Auto-refreshing invasions after sort mode change")
        fetchInvasions()
    }

    fun getInvasions(): List<Invasion>? {
        return _invasions.value
    }

    fun getDeletedInvasions(): Set<DeletedEntry> {
        return deletedRepo.getDeletedEntries()
    }

    // When an invasion is deleted/handled, record its coords as last invasion and remove from list.
    fun deleteInvasion(invasion: Invasion) {
        // Remove it from the list right away, on the main thread, before anything suspends.
        // This used to happen only after the IO work, reading _invasions.value at that point:
        // two quick deletes both read the original list, so the second re-published the first
        // invasion — and postValue could merge the two updates, dropping one outright.
        val remaining = _invasions.value.orEmpty() - invasion
        _invasions.value = remaining

        viewModelScope.launch {
            val deletedFingerprint = withContext(Dispatchers.IO) {
                deletedRepo.addDeletedInvasion(invasion)
                deletedRepo.fingerprint()
            }
            // The published list already reflects this deletion, so record that; otherwise the
            // changed deleted set would make the next visit to the tab refetch for nothing.
            publishedInputs = publishedInputs?.copy(deletedFingerprint = deletedFingerprint)

            // Save last invasion coordinates for distance-based ordering
            saveLastInvasionCoordinates(invasion)

            // Re-sort (distance order is relative to the invasion just deleted), but only if
            // nothing newer — another delete, or a fetch — has replaced the list meanwhile.
            val sorted = withContext(Dispatchers.Default) { applySorting(remaining) }
            if (_invasions.value === remaining) {
                _invasions.value = sorted
            }

            val count = withContext(Dispatchers.IO) { deletedRepo.getDeletionCountLast24Hours() }
            _deletedCount.value = count
        }
    }
}
