package com.mints.projectgammatwo.viewmodels

import android.app.Application
import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.map
import com.mints.projectgammatwo.helpers.Event
import com.mints.projectgammatwo.helpers.filterQuestsByConditions
import com.mints.projectgammatwo.helpers.questBaseFilter
import com.mints.projectgammatwo.helpers.questConditionKey
import com.mints.projectgammatwo.helpers.VARIANT_CACHE_MAX_AGE_MS
import com.mints.projectgammatwo.helpers.isPublishedQuestListFresh
import com.mints.projectgammatwo.helpers.pruneStaleConditionKeys
import com.mints.projectgammatwo.helpers.shouldRediscoverVariants
import com.google.gson.Gson
import com.mints.projectgammatwo.data.ApiClient
import com.mints.projectgammatwo.data.DataSourcePreferences
import com.mints.projectgammatwo.data.QuestFilterPreferences
import com.mints.projectgammatwo.data.VisitedQuestsPreferences
import com.mints.projectgammatwo.data.Quests
import com.mints.projectgammatwo.data.Quests.Quest
import com.mints.projectgammatwo.data.QuestCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import com.mints.projectgammatwo.data.CurrentQuestData
import androidx.core.content.edit
import kotlinx.coroutines.withContext
import com.mints.projectgammatwo.R
import com.mints.projectgammatwo.helpers.carryOverFailedSources
import com.mints.projectgammatwo.helpers.questVisitId

class QuestsViewModel(application: Application) : AndroidViewModel(application) {

    private val _questsLiveData = MutableLiveData<List<Quest>>()
    val questsLiveData: LiveData<List<Quest>> = _questsLiveData

    private val _questsCountLiveData = MutableLiveData<Int>()
    val questsCountLiveData: LiveData<Int> = _questsCountLiveData

    private val _filterSizeLiveData = MutableLiveData<Int>()
    val filterSizeLiveData: LiveData<Int> = _filterSizeLiveData



    // Map of "type,0,id" -> list of SubVariant(filterString, label, condition, type, id, amount, reward)
    // e.g. "9,0,483" -> [SubVariant("9,1,483","× 1 — Spin 14 stops", ...), SubVariant("9,3,483","× 3 — Win a raid", ...)]
    data class SubVariant(
        val filterString: String,
        val label: String,
        val condition: String,
        val type: String,
        val id: String,
        val amount: String,
        val reward: String
    ) {
        /** False for a cached entry missing a field: Gson leaves those null despite the types. */
        @Suppress("SENSELESS_COMPARISON")
        fun isComplete(): Boolean = filterString != null && label != null && condition != null &&
            type != null && id != null && amount != null && reward != null
    }

    private val _rewardSubVariantsLiveData = MutableLiveData<Map<String, List<SubVariant>>>()
    val rewardSubVariantsLiveData: LiveData<Map<String, List<SubVariant>>> = _rewardSubVariantsLiveData

    private val _variantsLoadingLiveData = MutableLiveData(false)
    val variantsLoadingLiveData: LiveData<Boolean> = _variantsLoadingLiveData

    companion object {
        private const val TAG = "QuestsViewModel"
    }

    private var fetchJob: Job? = null

    /** What the running [fetchJob] was started for; only meaningful while it's active. */
    private var inFlightInputs: Inputs? = null

    /** Everything that decides what the quest list contains. */
    private data class Inputs(
        val sources: Set<String>,
        val filters: Set<String>,
        val conditions: Set<String>,
        val visitedFingerprint: Int,
    )

    /** What the published list was built from, and when; null until a fetch succeeds. */
    private var publishedInputs: Inputs? = null
    private var publishedAtMs = 0L
    /** Whether every source succeeded for the published list; a partial one is retried sooner. */
    private var publishedComplete = false
    /**
     * The quest filters the list on screen was fetched with. A failed source's earlier quests are
     * carried over only while these are unchanged: the API did that filtering, so quests fetched
     * for other rewards would otherwise show up.
     */
    private var listFetchedWithFilters: Set<String>? = null

    private fun currentInputs(): Inputs {
        val context = getApplication<Application>().applicationContext
        val filterPreferences = QuestFilterPreferences(context)
        return Inputs(
            sources = DataSourcePreferences(context).getSelectedSources(),
            filters = filterPreferences.getEnabledFilters(),
            conditions = filterPreferences.getEnabledEncounterConditions(),
            visitedFingerprint = VisitedQuestsPreferences(context).fingerprint(),
        )
    }

    /**
     * Fetches only if the published list is out of date (see [isPublishedQuestListFresh]): too
     * old, or built from different sources, filters or visited quests than are now in effect.
     * Does nothing while a fetch for the current inputs is already running.
     *
     * This view model is activity-scoped — shared by the quest list and the filter screen, and
     * surviving tab switches. The list used to refetch everything on every visit. Pull-to-refresh
     * and the filter screen's refresh still call [fetchQuests].
     *
     * @return whether a fetch is now running, either started here or already in flight.
     */
    fun refreshIfStale(): Boolean {
        val inputs = currentInputs()
        if (fetchJob?.isActive == true && inputs == inFlightInputs) return true
        val age = SystemClock.elapsedRealtime() - publishedAtMs
        if (isPublishedQuestListFresh(inputs == publishedInputs, age, publishedComplete)) return false
        fetchQuests()
        return true
    }

    /**
     * Records [quest] as visited and drops it from the published list.
     *
     * The removal has to happen here, not just in the adapter, now that this view model outlives
     * the screen: removing it from the adapter alone meant returning to the tab replayed the
     * view model's list, visited quest included.
     */
    fun markVisited(quest: Quest) {
        val current = _questsLiveData.value.orEmpty()
        // A double tap delivers the same quest twice before the list re-renders; recording it
        // again would log one visit twice.
        if (quest !in current) return

        val remaining = current - quest
        _questsLiveData.value = remaining
        _questsCountLiveData.value = remaining.size
        saveLastVisitedCoordinates(quest)

        val context = getApplication<Application>().applicationContext
        viewModelScope.launch {
            val visitedFingerprint = withContext(Dispatchers.IO) {
                val visited = VisitedQuestsPreferences(context)
                visited.addVisitedQuest(
                    questId = questVisitId(quest),
                    rewards = quest.rewardsString,
                    conditions = quest.conditionsString,
                    source = quest.source
                )
                visited.fingerprint()
            }
            // The published list already excludes this quest, so record that; otherwise the
            // changed visited set would make the next visit to the tab refetch for nothing.
            publishedInputs = publishedInputs?.copy(visitedFingerprint = visitedFingerprint)
        }
    }

    init {
        val context = getApplication<Application>().applicationContext
        val questPrefs = context.getSharedPreferences("quest_filters", Context.MODE_PRIVATE)

        // Run the legacy Spinda key migration on a background thread — questPrefs.all
        // reads every key+value from disk and must not block the main thread.
        viewModelScope.launch(Dispatchers.IO) {
            val legacySpindaKeys = questPrefs.all.keys.filter { key ->
                key == "enabled_spinda_forms" || key.startsWith("spinda_")
            }
            if (legacySpindaKeys.isNotEmpty()) {
                questPrefs.edit {
                    legacySpindaKeys.forEach { remove(it) }
                }
                Log.d("QuestsViewModel", "Migrated: removed ${legacySpindaKeys.size} legacy Spinda keys")
            }

            // Also scrub any spinda_form_* values that leaked into enabled_encounter_conditions.
            val encounterConditions = questPrefs.getStringSet("enabled_encounter_conditions", emptySet())!!
            val cleanedConditions = encounterConditions.filterNot { it.startsWith("spinda_form_") }.toSet()
            if (cleanedConditions.size != encounterConditions.size) {
                questPrefs.edit { putStringSet("enabled_encounter_conditions", cleanedConditions) }
                Log.d("QuestsViewModel", "Migrated: removed ${encounterConditions.size - cleanedConditions.size} spinda_form_ values from encounter conditions")
            }
        }

        // Restore cached sub-variants synchronously on the main thread so the filter UI
        // is populated immediately on app restart. SharedPreferences caches all values
        // in memory after the first file load, so individual key reads are fast.
        val cacheTimestamp = QuestCache.prefs(context).getLong(QuestCache.KEY_SUB_VARIANTS_TIMESTAMP, 0L)
        val cacheAge = System.currentTimeMillis() - cacheTimestamp
        val cached = if (cacheAge in 0 until VARIANT_CACHE_MAX_AGE_MS) {
            QuestCache.prefs(context).getString(QuestCache.KEY_SUB_VARIANTS, null)
        } else {
            Log.d("QuestsViewModel", "Sub-variant cache is stale (${cacheAge / 3600000}h old), discarding")
            null
        }
        if (!cached.isNullOrEmpty()) {
            try {
                val type = object : com.google.gson.reflect.TypeToken<Map<String, List<SubVariant>>>() {}.type
                val map: Map<String, List<SubVariant?>?>? = Gson().fromJson(cached, type)
                // Regroup under each variant's own base filter. Caches written before the
                // stardust fix hold every stardust variant under "3,0,0", which matches none of
                // the filter screen's stardust rows; regrouping gives them their variants straight
                // away instead of after the next fetch. Correctly keyed groups come out unchanged.
                // Entries missing a field (Gson leaves them null) are dropped rather than shown.
                val regrouped = map.orEmpty().values
                    .flatMap { it.orEmpty() }
                    .filterNotNull()
                    .filter { it.isComplete() }
                    .groupBy { questBaseFilter(it.type, it.id, it.amount) }
                if (regrouped.isNotEmpty()) _rewardSubVariantsLiveData.value = regrouped
            } catch (e: Exception) {
                Log.w("QuestsViewModel", "Failed to restore cached sub-variants", e)
            }
        }
    }

    private fun buildFullFilterList(filters: Quests.Filters): List<String> =
        filters.stardust.map { "3,$it,0" } +
            filters.pokecoins.map { "8,$it,0" } +
            filters.candy.map { "4,0,$it" } +
            filters.candyXl.map { "9,0,$it" } +
            filters.megaEnergy.map { "12,0,$it" } +
            filters.encounters.map { "7,0,$it" } +
            filters.items.map { "2,0,$it" }

    private val _error = MutableLiveData<String>()
    val error: LiveData<Event<String>> = _error.map { Event(it) } // One-shot, so a replay to a re-created view doesn't show the last error again.

    // Cache for last visited coordinates to avoid repeated SharedPreferences reads
    private var cachedLastVisitedCoords: Pair<Double?, Double?>? = null

    // Calculate the distance between two quests using the Haversine formula.
    private fun haversineDistance(a: Quest, b: Quest): Double {
        val R = 6371e3 // Earth's radius in meters
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val deltaLat = Math.toRadians(b.lat - a.lat)
        val deltaLon = Math.toRadians(b.lng - a.lng)

        val aVal = sin(deltaLat / 2).pow(2.0) +
                cos(lat1) * cos(lat2) *
                sin(deltaLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(aVal), sqrt(1 - aVal))
        return R * c
    }

    // Calculate the distance from a given point (lat, lng) to a quest.
    private fun haversineDistanceFromPoint(lat: Double, lng: Double, quest: Quest): Double {
        val R = 6371e3 // Earth's radius in meters
        val lat1 = Math.toRadians(lat)
        val lat2 = Math.toRadians(quest.lat)
        val deltaLat = Math.toRadians(quest.lat - lat)
        val deltaLon = Math.toRadians(quest.lng - lng)
        val aVal = sin(deltaLat / 2).pow(2.0) +
                cos(lat1) * cos(lat2) * sin(deltaLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(aVal), sqrt(1 - aVal))
        return R * c
    }

    // Sort the list of quests by repeatedly choosing the nearest unvisited quest.
    // If startLat and startLng are provided, use them as the reference point.
    private fun sortQuestsByNearestNeighbor(
        quests: List<Quest>,
        startLat: Double? = null,
        startLng: Double? = null
    ): List<Quest> {
        if (quests.isEmpty()) return quests

        val sorted = mutableListOf<Quest>()
        val remaining = quests.toMutableList()

        // Determine the starting quest using the provided coordinates if available.
        val startingQuest = if (startLat != null && startLng != null) {
            remaining.minByOrNull { quest ->
                haversineDistanceFromPoint(startLat, startLng, quest)
            } ?: remaining.first()
        } else {
            remaining.first()
        }

        sorted.add(startingQuest)
        remaining.remove(startingQuest)

        var current = startingQuest
        while (remaining.isNotEmpty()) {
            val next = remaining.minByOrNull { haversineDistance(current, it) }!!
            sorted.add(next)
            remaining.remove(next)
            current = next
        }

        return sorted
    }

    // Pick nearest N quests to a given point to reduce sort workload
    private fun nearestNByPoint(quests: List<Quest>, lat: Double, lng: Double, n: Int): List<Quest> {
        if (quests.size <= n) return quests
        return quests
            .asSequence()
            .map { it to haversineDistanceFromPoint(lat, lng, it) }
            .sortedBy { it.second }
            .take(n)
            .map { it.first }
            .toList()
    }

    // Save the coordinates of the last visited quest. Store as string for precision; fallback compatible when reading.
    fun saveLastVisitedCoordinates(quest: Quest) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val context = getApplication<Application>().applicationContext
                val prefs = context.getSharedPreferences("last_visited_pref", Context.MODE_PRIVATE)
                prefs.edit().apply {
                    putString("lastVisited", "${quest.lat},${quest.lng}")
                    // Keep old keys for backward compatibility
                    putFloat("lastVisitedLat", quest.lat.toFloat())
                    putFloat("lastVisitedLng", quest.lng.toFloat())
                    apply()
                }
            }
            // Update cache immediately
            cachedLastVisitedCoords = quest.lat to quest.lng
        }
    }

    // Load last visited coordinates from cache or SharedPreferences
    private fun loadLastVisitedCoordinates(): Pair<Double?, Double?> {
        // Return cached value if available
        cachedLastVisitedCoords?.let { return it }

        // Otherwise load from SharedPreferences
        val context = getApplication<Application>().applicationContext
        val prefs = context.getSharedPreferences("last_visited_pref", Context.MODE_PRIVATE)
        val lastCombined = prefs.getString("lastVisited", null)
        val result = if (!lastCombined.isNullOrEmpty() && "," in lastCombined) {
            val parts = lastCombined.split(",")
            parts.getOrNull(0)?.toDoubleOrNull() to parts.getOrNull(1)?.toDoubleOrNull()
        } else {
            val latF = prefs.getFloat("lastVisitedLat", Float.NaN)
            val lngF = prefs.getFloat("lastVisitedLng", Float.NaN)
            val lat = if (!latF.isNaN()) latF.toDouble() else null
            val lng = if (!lngF.isNaN()) lngF.toDouble() else null
            lat to lng
        }

        // Cache the result
        cachedLastVisitedCoords = result
        return result
    }

    /**
     * Starts [fetchQuests] unless one is already running. For the filter screen, which asks for
     * data each time it rebuilds while the filter lists are missing — two or three times as it
     * opens — and used to start (and cancel) a full fetch each time.
     */
    fun ensureFilterData() {
        if (fetchJob?.isActive == true) return
        fetchQuests()
    }

    /**
     * Fetches the quest list from every selected source, cancelling any fetch still running.
     *
     * Reward sub-variants (what the filter screen lists) are rediscovered alongside only when
     * [refreshVariants] is set or the cached ones are missing or a day old; see
     * [shouldRediscoverVariants].
     */
    fun fetchQuests(refreshVariants: Boolean = false) {
        Log.d(TAG, "Starting fetchQuests(refreshVariants=$refreshVariants)")
        val context = getApplication<Application>().applicationContext
        val inputs = currentInputs()

        val filterPreferences = QuestFilterPreferences(context)
        val visitedPreferences = VisitedQuestsPreferences(context)

        val filtersToUse = inputs.filters.toList()
        Log.d(TAG, "Filters enabled: $filtersToUse")
        _filterSizeLiveData.value = filtersToUse.size

        // The API expects base filter keys ("4,0,483", "9,0,1006", "3,200,0", "7,0,483")
        // not variant-specific strings ("4,1,483"). Convert variant filter strings to their
        // canonical API form so the server returns results. The variant-specific strings are
        // only used for local post-fetch filtering inside this function.
        val apiFiltersToUse = filtersToUse.map { filter ->
            val parts = filter.split(",")
            if (parts.size < 3) return@map filter
            when (val type = parts[0]) {
                // Stardust/PokéCoins: the amount is the discriminator; encounters are already
                // in base form.
                "3", "8", "7" -> filter
                // Candy, XL Candy, Mega Energy, Item: "T,<amt>,<id>" → "T,0,<id>"
                "4", "9", "12", "2" -> "$type,0,${parts[2]}"
                else -> filter
            }
        }.distinct()

        val selectedSources = inputs.sources
        val (startLat, startLng) = loadLastVisitedCoordinates()

        // Shared by the quest list and the filter screen, so overlapping fetches are possible;
        // cancel the older one so a slow fetch with stale filters can't finish last and win.
        // With suspend calls this now also cancels its HTTP requests.
        fetchJob?.cancel()
        inFlightInputs = inputs
        fetchJob = viewModelScope.launch {
            try {
                Log.d(TAG, "Selected data sources: $selectedSources")
                val deferredBySource = selectedSources.mapNotNull { source ->
                    ApiClient.DATA_SOURCE_URLS[source]?.let { baseUrl ->
                        source to async(Dispatchers.IO) { fetchSourceQuests(source, baseUrl, apiFiltersToUse) }
                    }
                }
                // Null for a source that failed.
                val results = deferredBySource.map { (source, deferred) -> source to deferred.await() }
                val successful = results.mapNotNull { it.second }
                val failedSources = results.filter { it.second == null }.map { it.first }.toSet()

                if (successful.isEmpty()) {
                    Log.w(TAG, "No successful API responses received")
                    // Not a published state: the next visit retries rather than reusing this.
                    // The list on screen stays; it used to be emptied.
                    publishedInputs = null
                    showEmptyListIfNone()
                    _error.value = getApplication<Application>().getString(R.string.quests_error_unable_fetch)
                    return@launch
                }
                Log.d(TAG, "Fetched quests from ${successful.size} of ${deferredBySource.size} sources")

                // We have a live network response, so record the refresh time now; the filter
                // screen shows it as "last refreshed" whether or not variants are rediscovered.
                val fetchTimestamp = System.currentTimeMillis()
                val cache = withContext(Dispatchers.IO) {
                    QuestCache.prefs(context).also {
                        it.edit { putLong(QuestCache.KEY_FILTERS_LAST_REFRESHED, fetchTimestamp) }
                    }
                }

                val allQuests = successful.flatten()
                val rediscover = shouldRediscoverVariants(
                    forced = refreshVariants,
                    hasCachedFilters = cache.contains(QuestCache.KEY_API_FILTERS),
                    hasCachedVariants = !_rewardSubVariantsLiveData.value.isNullOrEmpty(),
                    cachedAtMs = cache.getLong(QuestCache.KEY_SUB_VARIANTS_TIMESTAMP, 0L),
                    nowMs = fetchTimestamp,
                )
                if (rediscover) {
                    discoverVariants(context, selectedSources, allQuests, fetchTimestamp)
                }

                // Read after discovery, which may have pruned selections for variants that no
                // longer exist; the published inputs record what the list was actually built from.
                val enabledConditions = filterPreferences.getEnabledEncounterConditions()
                val previousQuests = _questsLiveData.value.orEmpty()
                val previousFilters = listFetchedWithFilters

                // Perform CPU-intensive filtering and sorting on background thread
                val sortedQuests = withContext(Dispatchers.Default) {
                    Log.d(TAG, "Total quests fetched: ${allQuests.size}")

                    // A source that failed keeps the quests the list showed from it.
                    val listQuests = if (inputs.filters == previousFilters) {
                        carryOverFailedSources(allQuests, previousQuests, failedSources) { it.source }
                    } else {
                        allQuests
                    }

                    val visited = visitedPreferences.getVisitedQuests()
                    var filteredQuests = listQuests.filter { quest ->
                        questVisitId(quest) !in visited
                    }

                    // All of them, not the unvisited ones, decide which ticked variants still exist.
                    filteredQuests = filterQuestsByConditions(
                        filteredQuests, filtersToUse, enabledConditions, availableQuests = listQuests
                    )
                    Log.d(TAG, "Quests after visited and condition filtering: ${filteredQuests.size}")

                    // Pre-limit by proximity to last visited when available to reduce sorting cost
                    filteredQuests = if (startLat != null && startLng != null) {
                        nearestNByPoint(filteredQuests, startLat, startLng, 500)
                    } else {
                        filteredQuests.take(500)
                    }

                    sortQuestsByNearestNeighbor(filteredQuests, startLat, startLng)
                }

                _questsCountLiveData.value = sortedQuests.size
                _questsLiveData.value = sortedQuests
                listFetchedWithFilters = inputs.filters
                CurrentQuestData.currentQuests = sortedQuests.toMutableList()

                // A partial result is published too, but goes stale sooner so it's retried.
                publishedInputs = inputs.copy(conditions = enabledConditions)
                publishedAtMs = SystemClock.elapsedRealtime()
                publishedComplete = failedSources.isEmpty()
                if (failedSources.isNotEmpty()) {
                    // One message for the fetch, naming every source that failed.
                    _error.value = getApplication<Application>().getString(
                        R.string.quests_error_sources,
                        failedSources.sorted().joinToString(", ")
                    )
                }
            } catch (e: CancellationException) {
                // Coroutine was cancelled - this is normal behavior, don't treat it as an error
                Log.d(TAG, "Fetch quests was cancelled")
                throw e
            } catch (e: Exception) {
                ensureActive()
                Log.e(TAG, "Error in fetchQuests", e)
                publishedInputs = null
                showEmptyListIfNone()
                _error.value = getApplication<Application>().getString(R.string.quests_error_unexpected)
            }
        }
    }

    /**
     * Publishes an empty list if there is none yet, so a first fetch that fails still ends in the
     * screen's empty state; a list already on screen is kept.
     */
    private fun showEmptyListIfNone() {
        if (_questsLiveData.value != null) return
        _questsLiveData.value = emptyList()
        _questsCountLiveData.value = 0
    }

    /**
     * One source's quests, tagged with [source]; null if the source failed. Malformed quests are
     * dropped by [Quests.QuestsResponse.validQuests] rather than failing the source.
     */
    private suspend fun fetchSourceQuests(source: String, baseUrl: String, filters: List<String>): List<Quest>? =
        try {
            val service = ApiClient.questsApi(baseUrl)
            val now = System.currentTimeMillis()
            val response = if (filters.isEmpty()) service.getAllQuests(now) else service.getQuests(filters, now)
            if (response.isSuccessful) {
                response.body()?.validQuests(source).orEmpty()
            } else {
                Log.w(TAG, "API error for source $source: HTTP ${response.code()}")
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Cancelling an OkHttp call can surface as IOException("Canceled"); that's not a
            // source failure.
            currentCoroutineContext().ensureActive()
            Log.e(TAG, "Fetching quests from $source failed", e)
            null
        }

    /** One request's quests, or null on failure; for variant discovery, where failures only log. */
    private suspend fun fetchVariantQuests(
        service: com.mints.projectgammatwo.data.QuestsApiService,
        filters: List<String>?,
    ): Quests.QuestsResponse? = try {
        val now = System.currentTimeMillis()
        val response = if (filters == null) service.getAllQuests(now) else service.getQuests(filters, now)
        if (response.isSuccessful) response.body() else {
            Log.w(TAG, "Variant fetch failed: HTTP ${response.code()}")
            null
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        currentCoroutineContext().ensureActive()
        Log.w(TAG, "Variant fetch failed", e)
        null
    }

    /**
     * Refreshes the API's filter lists and the reward sub-variants from unfiltered data, so the
     * filter screen offers every reward rather than only the ones already selected.
     *
     * @param fallbackQuests used for the variants if no unfiltered data could be fetched.
     */
    private suspend fun discoverVariants(
        context: Context,
        selectedSources: Set<String>,
        fallbackQuests: List<Quest>,
        fetchTimestamp: Long,
    ) {
        _variantsLoadingLiveData.value = true
        try {
            val services = selectedSources.mapNotNull { source ->
                ApiClient.DATA_SOURCE_URLS[source]?.let { ApiClient.questsApi(it) }
            }
            val unfilteredResults = coroutineScope {
                services.map { service -> async(Dispatchers.IO) { fetchVariantQuests(service, null) } }
            }.awaitAll()
            val unfilteredBodies = unfilteredResults.filterNotNull()

            // Save the API's filter lists from the unfiltered response if there is one.
            val freshFilters = unfilteredBodies.firstNotNullOfOrNull { it.filters }
            val filtersForVariants = withContext(Dispatchers.IO) {
                val cache = QuestCache.prefs(context)
                if (freshFilters != null) {
                    cache.edit { putString(QuestCache.KEY_API_FILTERS, Gson().toJson(freshFilters)) }
                    freshFilters
                } else {
                    cache.getString(QuestCache.KEY_API_FILTERS, null)?.let { json ->
                        runCatching { Gson().fromJson(json, Quests.Filters::class.java) }.getOrNull()
                    }
                }
            }
            val fullFilterList = filtersForVariants?.let { buildFullFilterList(it) }.orEmpty()

            // Ask for every reward explicitly, in chunks, so variants don't depend on whatever
            // the unfiltered endpoint chose to return.
            val chunkResults = if (fullFilterList.isEmpty()) emptyList() else {
                val chunks = fullFilterList.chunked(80)
                coroutineScope {
                    services.map { service ->
                        async(Dispatchers.IO) { chunks.map { chunk -> fetchVariantQuests(service, chunk) } }
                    }
                }.awaitAll().flatten()
            }
            val chunkedQuests = chunkResults.flatMap { it?.validQuests().orEmpty() }
            // Every source asked for every reward, and answered: only then is a variant missing
            // from the result really gone, rather than on a source that failed.
            val complete = services.size == selectedSources.size && chunkResults.isNotEmpty() &&
                unfilteredResults.all { it != null } && chunkResults.all { it != null }

            val questsForVariants = when {
                chunkedQuests.isNotEmpty() -> chunkedQuests
                unfilteredBodies.isNotEmpty() -> unfilteredBodies.flatMap { it.validQuests() }
                else -> fallbackQuests
            }

            val subVariantMap = withContext(Dispatchers.Default) { buildSubVariants(questsForVariants) }
            if (subVariantMap.isNotEmpty()) {
                _rewardSubVariantsLiveData.value = subVariantMap
                // Persist so the filter UI is populated immediately on the next app launch.
                withContext(Dispatchers.IO) {
                    QuestCache.prefs(context).edit {
                        putString(QuestCache.KEY_SUB_VARIANTS, Gson().toJson(subVariantMap))
                        putLong(QuestCache.KEY_SUB_VARIANTS_TIMESTAMP, fetchTimestamp)
                    }
                }
                if (complete) pruneStaleSelections(context, subVariantMap)
            }
        } finally {
            _variantsLoadingLiveData.value = false
        }
    }

    /**
     * Drops the selections for variants that the daily quest rotation has removed, so they don't
     * pile up (the matching already ignores them; see [filterQuestsByConditions]). Runs on the
     * main thread in one read-modify-write, so it can't interleave with the filter screen's own.
     */
    private fun pruneStaleSelections(context: Context, subVariants: Map<String, List<SubVariant>>) {
        val variantKeys = subVariants.values.flatten().map {
            questConditionKey(it.type, it.id, it.amount, it.condition, it.reward)
        }
        val preferences = QuestFilterPreferences(context)
        val enabled = preferences.getEnabledEncounterConditions()
        val pruned = pruneStaleConditionKeys(enabled, variantKeys)
        if (pruned.size != enabled.size) {
            Log.d(TAG, "Pruned ${enabled.size - pruned.size} selections for variants no longer offered")
            preferences.saveEnabledEncounterConditions(pruned)
        }
    }

    /** Groups every reward of [quests] under its base filter, one [SubVariant] per distinct variant. */
    private fun buildSubVariants(quests: List<Quest>): Map<String, List<SubVariant>> {
        // Keep same-amount variants distinct by reward label + condition
        val subVariantMap = mutableMapOf<String, MutableList<SubVariant>>()
        val seen = mutableSetOf<String>()
        for (quest in quests) {
            val types = quest.rewardsTypes.split(",").map { it.trim() }
            val ids = quest.rewardsIds.split(",").map { it.trim() }
            val amts = quest.rewardsAmounts.split(",").map { it.trim() }
            val condition = quest.conditionsString.trim()
            val rewardLabel = quest.rewardsString.trim()
            types.indices.forEach { i ->
                val type = types[i]
                val id = ids.getOrNull(i) ?: return@forEach
                val amt = amts.getOrNull(i) ?: return@forEach
                if (id.isBlank() || amt.isBlank() || type.isBlank()) return@forEach

                // Grouped under the filter screen's own base filter so the two agree.
                // Stardust used to be grouped by its id, "3,0,0" for every amount: that
                // matched none of the stardust checkboxes, and the filter screen's
                // reconcile step then removed every stardust selection.
                val baseKey = questBaseFilter(type, id, amt)
                val filterStr = when (type) {
                    "3", "8", "7" -> baseKey
                    else -> "$type,$amt,$id"
                }
                if (seen.add(questConditionKey(type, id, amt, condition, rewardLabel))) {
                    subVariantMap.getOrPut(baseKey) { mutableListOf() }
                        .add(SubVariant(filterStr, "$rewardLabel — $condition", condition, type, id, amt, rewardLabel))
                }
            }
        }
        subVariantMap.forEach { (key, list) ->
            if (key.startsWith("7,")) {
                list.sortBy { it.label }
            } else {
                list.sortBy { it.filterString.split(",").getOrNull(1)?.toIntOrNull() ?: 0 }
            }
        }
        return subVariantMap
    }
}
