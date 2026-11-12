package com.mints.projectgammatwo.viewmodels

import android.app.Application
import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.util.Log.e
import androidx.core.content.edit
import androidx.lifecycle.*
import com.mints.projectgammatwo.data.ApiClient
import com.mints.projectgammatwo.data.CurrentInvasionData
import com.mints.projectgammatwo.data.DataSourcePreferences
import com.mints.projectgammatwo.data.FilterPreferences
import com.mints.projectgammatwo.data.Invasion
import com.mints.projectgammatwo.data.DeletedInvasionsRepository
import com.mints.projectgammatwo.R
import com.mints.projectgammatwo.helpers.DeletedInvasions
import com.mints.projectgammatwo.helpers.Event
import com.mints.projectgammatwo.helpers.carryOverFailedSources
import com.mints.projectgammatwo.helpers.filterInvasions
import com.mints.projectgammatwo.helpers.sortAndCapInvasions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    private val _scrollToTop = MutableLiveData<Event<Unit>>()

    /**
     * Sent just before a list the screen should show from the top (a new sort order, a
     * pull-to-refresh). Every other list update keeps the scroll position: the screen used to
     * jump to the top on each one, including deleting a card and the replay after rotation.
     */
    val scrollToTop: LiveData<Event<Unit>> get() = _scrollToTop

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

    /**
     * Fetches every selected source and publishes the filtered, sorted list.
     *
     * A source that fails keeps what the previous list showed from it ([carryOverFailedSources]),
     * and the failures are reported in one toast for the whole fetch; each failing source used to
     * queue its own toast and leave its part of the list empty.
     *
     * @param scrollToTop ask the screen to show the top of the new list: for a sort change or a
     * pull-to-refresh, not for the background refreshes that run as the screen opens.
     */
    fun fetchInvasions(scrollToTop: Boolean = false) {
        Log.d(TAG, "Starting to fetch invasions...")
        val inputs = currentInputs()

        // Cancel any ongoing fetch operation before starting a new one
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            try {
                val selectedSources = dataSourcePreferences.getSelectedSources()
                val deferredBySource = selectedSources.mapNotNull { source ->
                    ApiClient.DATA_SOURCE_URLS[source]?.let { baseUrl ->
                        source to async { fetchSourceInvasions(source, baseUrl) }
                    }
                }
                // Null for a source that failed.
                val results = deferredBySource.map { (source, deferred) -> source to deferred.await() }
                val failedSources = results.filter { it.second == null }.map { it.first }.toSet()
                val combinedInvasions = carryOverFailedSources(
                    fresh = results.flatMap { it.second.orEmpty() },
                    previous = _invasions.value.orEmpty(),
                    failedSources = failedSources,
                ) { it.source }

                // Load preferences and deleted entries off the main thread
                val enabledCharacters = withContext(Dispatchers.IO) {
                    filterPreferences.getEnabledCharacters()
                }
                _currentFilterSize.value = enabledCharacters.size

                // Retrieve deleted entries once and use a fast lookup set
                val deletedEntries = withContext(Dispatchers.IO) {
                    deletedRepo.getDeletedEntries()
                }
                val deleted = DeletedInvasions(deletedEntries)

                // Perform CPU-bound mapping/filtering AND sorting off the main thread
                val finalList = withContext(Dispatchers.Default) {
                    val baseList = filterInvasions(
                        combinedInvasions,
                        enabledCharacters,
                        deleted,
                        nowSeconds = System.currentTimeMillis() / 1000
                    )
                    applySorting(baseList)
                }

                // Before the list, so the screen knows to scroll once the list is shown.
                if (scrollToTop) _scrollToTop.value = Event(Unit)
                _invasions.value = finalList
                _currentInvasionCount.value = finalList.size

                CurrentInvasionData.currentInvasions = finalList.toMutableList()

                _deletedCount.value = deletedEntries.size

                // Only a fetch where every source answered is recorded as the published state;
                // otherwise the list is partly carried over and the next visit retries.
                if (failedSources.isEmpty()) {
                    publishedInputs = inputs
                    publishedAtMs = SystemClock.elapsedRealtime()
                } else {
                    _error.value = getApplication<Application>().getString(
                        R.string.invasions_error_sources,
                        failedSources.sorted().joinToString(", ")
                    )
                }

                Log.d(
                    TAG,
                    "Fetched invasions from ${selectedSources.size} sources (${failedSources.size} failed). Total items: ${finalList.size}"
                )
            } catch (e: CancellationException) {
                // Coroutine was cancelled - this is normal behavior, don't treat it as an error
                Log.d(TAG, "Fetch invasions was cancelled")
                throw e // Re-throw to properly propagate cancellation
            } catch (e: Exception) {
                // The current list is left as it is.
                e(TAG, "Error fetching invasions: ${e.message}", e)
                _error.value = getApplication<Application>().getString(R.string.invasions_error_unexpected)
            }
        }
    }

    /** One source's invasions, tagged with [source]; null if the source failed. */
    private suspend fun fetchSourceInvasions(source: String, baseUrl: String): List<Invasion>? =
        try {
            ApiClient.getApiForBaseUrl(baseUrl)
                .getInvasions().invasions.orEmpty()
                .filterNotNull()
                .map { invasion -> invasion.copy(source = source) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            e(TAG, "Source $source: HTTP ${e.code()}", e)
            null
        } catch (e: Exception) {
            // A superseded fetch cancels its OkHttp calls, which surface here as
            // IOException("Canceled"), not as CancellationException; that's not a source failure.
            currentCoroutineContext().ensureActive()
            e(TAG, "Source $source failed: ${e.message}", e)
            null
        }

    /**
     * Sorts by time, or by distance from the last battled invasion (falling back to the first in
     * the list), then caps the list; see [sortAndCapInvasions].
     */
    private fun applySorting(list: List<Invasion>): List<Invasion> {
        if (list.isEmpty()) return list

        val reference = if (sortByDistanceInternal) {
            val (lat, lng) = loadLastInvasionCoordinates()
            if (lat != null && lng != null) lat to lng else list.first().let { it.lat to it.lng }
        } else {
            null
        }
        val sorted = sortAndCapInvasions(list, reference)
        Log.d(TAG, "Sorted ${list.size} invasions by ${if (reference == null) "time" else "distance from $reference"}, listing ${sorted.size}")
        return sorted
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
        fetchInvasions(scrollToTop = true)
    }

    // When an invasion is deleted/handled, record its coords as last invasion and remove from list.
    fun deleteInvasion(invasion: Invasion) {
        val current = _invasions.value.orEmpty()
        // A second tap on the same card lands before the list re-renders (the adapter diffs
        // asynchronously), so it arrives for an invasion that's already been removed. Recording
        // it again logged one battle twice and inflated the daily-limit count.
        if (invasion !in current) return

        // Remove it from the list right away, on the main thread, before anything suspends.
        // This used to happen only after the IO work, reading _invasions.value at that point:
        // two quick deletes both read the original list, so the second re-published the first
        // invasion — and postValue could merge the two updates, dropping one outright.
        val remaining = current - invasion
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
