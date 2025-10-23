package com.mints.projectgammatwo.viewmodels

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.mints.projectgammatwo.data.ApiClient
import com.mints.projectgammatwo.data.DataSourcePreferences
import com.mints.projectgammatwo.data.QuestFilterPreferences
import com.mints.projectgammatwo.data.VisitedQuestsPreferences
import com.mints.projectgammatwo.data.Quests
import com.mints.projectgammatwo.data.Quests.Quest
import com.mints.projectgammatwo.data.QuestsApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import com.mints.projectgammatwo.data.CurrentQuestData
import okio.IOException
import retrofit2.HttpException
import java.util.concurrent.TimeUnit
import androidx.core.content.edit
import kotlinx.coroutines.withContext
import com.mints.projectgammatwo.R

class QuestsViewModel(application: Application) : AndroidViewModel(application) {

    private val _questsLiveData = MutableLiveData<List<Quest>>()
    val questsLiveData: LiveData<List<Quest>> = _questsLiveData

    private val _questsCountLiveData = MutableLiveData<Int>()
    val questsCountLiveData: LiveData<Int> = _questsCountLiveData

    private val _filterSizeLiveData = MutableLiveData<Int>()
    val filterSizeLiveData: LiveData<Int> = _filterSizeLiveData


    // Inside QuestsViewModel:
    private val _spindaFormsLiveData = MutableLiveData<Map<String, Int>>()
    val spindaFormsLiveData: LiveData<Map<String, Int>> = _spindaFormsLiveData


    private val _error = MutableLiveData<String>()
    val error: LiveData<String> get() = _error

    // Single OkHttpClient reused for all requests
    private val httpClient: OkHttpClient by lazy {
        val interceptor = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.NONE }
        OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    // Cache QuestsApiService per base URL
    private val serviceCache = mutableMapOf<String, QuestsApiService>()

    private fun getServiceForBase(baseUrl: String): QuestsApiService {
        return serviceCache.getOrPut(baseUrl) {
            Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .client(httpClient)
                .build()
                .create(QuestsApiService::class.java)
        }
    }

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

    fun fetchQuests() {
        Log.d("QuestsViewModel", "Starting fetchQuests()")
        val context = getApplication<Application>().applicationContext

        val filterPreferences = QuestFilterPreferences(context)
        val visitedPreferences = VisitedQuestsPreferences(context)
        val dataSourcePreferences = DataSourcePreferences(context)

        val enabledFilters = filterPreferences.getEnabledFilters()
        val filtersToUse = if (enabledFilters.isEmpty()) emptyList() else enabledFilters.toList()
        Log.d("QuestsViewModel","Filters enabled: $filtersToUse")
        _filterSizeLiveData.postValue(filtersToUse.size)
        val selectedSources = dataSourcePreferences.getSelectedSources()

        // Load last visited coordinates from shared preferences (support new and old format)
        val prefs = context.getSharedPreferences("last_visited_pref", Context.MODE_PRIVATE)
        val lastCombined = prefs.getString("lastVisited", null)
        val (startLat, startLng) = if (!lastCombined.isNullOrEmpty() && "," in lastCombined) {
            val parts = lastCombined.split(",")
            parts.getOrNull(0)?.toDoubleOrNull() to parts.getOrNull(1)?.toDoubleOrNull()
        } else {
            val latF = prefs.getFloat("lastVisitedLat", Float.NaN)
            val lngF = prefs.getFloat("lastVisitedLng", Float.NaN)
            val lat = if (!latF.isNaN()) latF.toDouble() else null
            val lng = if (!lngF.isNaN()) lngF.toDouble() else null
            lat to lng
        }

        viewModelScope.launch {
            try {
                Log.d("QuestsViewModel", "Selected data sources: $selectedSources")
                val deferredList = selectedSources.mapNotNull { source ->
                    ApiClient.DATA_SOURCE_URLS[source]?.let { baseUrl ->
                        async(Dispatchers.IO) {
                            try {
                                val service = getServiceForBase(baseUrl)
                                val response = service.getQuests(filtersToUse, System.currentTimeMillis()).execute()

                                if (response.isSuccessful) {
                                    Log.d("QuestsViewModel", "API call successful for source $source")
                                    Pair(source, Result.success(response))
                                } else {
                                    val errorMsg = "HTTP ${response.code()}: ${response.message()}"
                                    Log.w("QuestsViewModel", "API error for source $source: $errorMsg")
                                    Pair(source, Result.failure<retrofit2.Response<Quests.QuestsResponse>>(
                                        HttpException(response)
                                    ))
                                }
                            } catch (e: IOException) {
                                Log.e("QuestsViewModel", "Network error for source $source", e)
                                Pair(source, Result.failure<retrofit2.Response<Quests.QuestsResponse>>(
                                    IOException("Network error for source $source", e)
                                ))
                            } catch (e: HttpException) {
                                Log.e("QuestsViewModel", "HTTP error for source $source: ${e.code()}", e)
                                Pair(source, Result.failure<retrofit2.Response<Quests.QuestsResponse>>(
                                    e
                                ))
                            } catch (e: JsonSyntaxException) {
                                Log.e("QuestsViewModel", "JSON parsing error for source $source", e)
                                Pair(source, Result.failure<retrofit2.Response<Quests.QuestsResponse>>(
                                    IOException("JSON parsing error for source $source", e)
                                ))
                            } catch (e: Exception) {
                                Log.e("QuestsViewModel", "Unexpected error for source $source", e)
                                Pair(source, Result.failure<retrofit2.Response<Quests.QuestsResponse>>(
                                    Exception("Unexpected error for source $source", e)
                                ))
                            }
                        }
                    }
                }

                val responses = deferredList.map { it.await() }

                val successfulResponses = responses.mapNotNull { (source, result) ->
                    result.getOrNull()?.let { response ->
                        Pair(source, response)
                    }
                }

                if (successfulResponses.isEmpty()) {
                    Log.w("QuestsViewModel", "No successful API responses received")
                    _questsLiveData.postValue(emptyList())
                    _error.postValue(getApplication<Application>().getString(R.string.quests_error_unable_fetch))
                    return@launch
                }
                Log.d("QuestsViewModel", "Successfully fetched quests from ${successfulResponses.size} sources")

                successfulResponses.firstOrNull()?.second?.body()?.filters?.let { filters ->
                    val filtersJson = Gson().toJson(filters)
                    context.getSharedPreferences("quest_filters", Context.MODE_PRIVATE)
                        .edit { putString("quest_api_filters", filtersJson) }
                }

                val allQuests = successfulResponses.flatMap { (source, response) ->
                    response.body()?.quests?.map { quest ->
                        quest.copy(source = source)
                    } ?: emptyList()
                }
                Log.d("QuestsViewModel", "Total quests fetched: ${allQuests.size}")
                _questsCountLiveData.postValue(allQuests.size)

                val visited = visitedPreferences.getVisitedQuests()
                var filteredQuests = allQuests.filter { quest ->
                    val id = "${quest.name}|${quest.lat}|${quest.lng}"
                    !visited.contains(id)
                }
                Log.d("QuestsViewModel", "Filtered quests after removing visited: ${filteredQuests.size}")

                val spindaForms = filterPreferences.getEnabledSpindaForms()
                val enabledFormNumbers: List<String> = spindaForms.map { it.substringAfterLast("_") }
                Log.d("QuestsViewModel", "Enabled Spinda Form Numbers: $enabledFormNumbers")
                val formRegex = Regex("Spinda \\((\\d{2})\\)")

                if (spindaForms.isNotEmpty()) {
                    filteredQuests = filterSpindaForms(filteredQuests, formRegex, enabledFormNumbers)
                }
                Log.d("QuestsViewModel", "Filtered quests after Spinda form filtering: ${filteredQuests.size}")

                // Pre-limit by proximity to last visited when available to reduce sorting cost
                filteredQuests = if (startLat != null && startLng != null) {
                    nearestNByPoint(filteredQuests, startLat, startLng, 500)
                } else {
                    filteredQuests.take(500)
                }

                // Sort the filtered quests using the last visited coordinates
                val sortedQuests = sortQuestsByNearestNeighbor(filteredQuests, startLat, startLng)

                _questsLiveData.postValue(sortedQuests)
                CurrentQuestData.currentQuests = sortedQuests.toMutableList()

            } catch (e: Exception) {
                Log.e("QuestsViewModel", "Error in fetchQuests", e)
                _questsLiveData.postValue(emptyList())
                _error.postValue(getApplication<Application>().getString(R.string.quests_error_unexpected))
            }
        }
    }

    private fun filterSpindaForms(
        quests: List<Quest>,
        formRegex: Regex,
        enabledFormNumbers: List<String>
    ): List<Quest> {
        return quests.filter { quest ->
            val hasSpinda = quest.rewardsIds.split(",").any { it.trim() == "327" }
            if (!hasSpinda) {
                true
            } else {
                val matchResult = formRegex.find(quest.rewardsString)
                val formNumber = matchResult?.groupValues?.getOrNull(1)
                formNumber != null && enabledFormNumbers.contains(formNumber)
            }
        }
    }

    fun fetchSpindaFormsFromApi() {
        val context = getApplication<Application>().applicationContext
        Log.d("QuestsViewModel", "Starting fetchSpindaFormsFromApi()")

        val filtersToUse = listOf("7,0,327")

        val interceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.NONE
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        viewModelScope.launch {
            try {
                val dataSourcePreferences = DataSourcePreferences(context)
                val selectedSources = dataSourcePreferences.getSelectedSources()
                Log.d("QuestsViewModel", "Selected sources: $selectedSources")

                if (selectedSources.isEmpty()) {
                    Log.w("QuestsViewModel", "No data sources selected, posting empty spinda forms map")
                    _spindaFormsLiveData.postValue(emptyMap())
                    return@launch
                }

                val baseUrl = ApiClient.DATA_SOURCE_URLS[selectedSources.first()]
                Log.d("QuestsViewModel", "Using base URL: $baseUrl")
                if (baseUrl == null) {
                    Log.w("QuestsViewModel", "Base URL for selected source is null, posting empty spinda forms map")
                    _spindaFormsLiveData.postValue(emptyMap())
                    return@launch
                }

                val retrofit = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(client)
                    .build()
                val service = retrofit.create(QuestsApiService::class.java)

                Log.d("QuestsViewModel", "Making API call to fetch quests with filters: $filtersToUse")
                val response = withContext(Dispatchers.IO) {
                    service.getQuests(filtersToUse, System.currentTimeMillis()).execute()
                }

                if (!response.isSuccessful) {
                    Log.w("QuestsViewModel", "API call unsuccessful: ${response.code()} - ${response.message()}")
                    _spindaFormsLiveData.postValue(emptyMap())
                    return@launch
                }

                val body = response.body()
                if (body == null) {
                    Log.w("QuestsViewModel", "API response body is null")
                    _spindaFormsLiveData.postValue(emptyMap())
                    return@launch
                }

                Log.d("QuestsViewModel", "Received ${body.quests.size} quests from API")

                val spindaQuests = body.quests.filter { quest ->
                    val contains327 = quest.rewardsIds.split(",").any { it == "327" }
                    contains327
                }
                Log.d("QuestsViewModel", "Filtered down to ${spindaQuests.size} spinda quests with reward id 327")

                val formCounts = mutableMapOf<String, Int>()
                val formPattern = "\\((\\d{2})\\)".toRegex()
                for (quest in spindaQuests) {
                    val match = formPattern.find(quest.rewardsString)
                    val formNumber = match?.groupValues?.get(1)
                    if (formNumber == null) {
                        Log.w("QuestsViewModel", "No form number found in rewardsString: '${quest.rewardsString}'")
                        continue
                    }
                    val formKey = "spinda_form_$formNumber"
                    formCounts[formKey] = formCounts.getOrDefault(formKey, 0) + 1
                }

                Log.d("QuestsViewModel", "Posting spinda form counts: $formCounts")
                _spindaFormsLiveData.postValue(formCounts)
            } catch (t: Throwable) {
                Log.e("QuestsViewModel", "Exception in fetchSpindaFormsFromApi", t)
                _spindaFormsLiveData.postValue(emptyMap())
            }
        }
    }
}
