package com.mints.projectgammatwo.viewmodels

import android.app.Application
import android.content.Context
import android.util.Log
import android.util.Log.e
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

    // Save the coordinates of the last visited quest to shared preferences.
    fun saveLastVisitedCoordinates(quest: Quest) {
        val context = getApplication<Application>().applicationContext
        val prefs = context.getSharedPreferences("last_visited_pref", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putFloat("lastVisitedLat", quest.lat.toFloat())
            putFloat("lastVisitedLng", quest.lng.toFloat())
            apply()
        }
    }

    fun fetchQuests() {
        Log.d("QuestsViewModel", "Starting fetchQuests()")
        val context = getApplication<Application>().applicationContext
        val interceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.NONE
        }
        val client = OkHttpClient.Builder().addInterceptor(interceptor)
            .connectTimeout(30,TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()


        val filterPreferences = QuestFilterPreferences(context)
        val visitedPreferences = VisitedQuestsPreferences(context)
        val dataSourcePreferences = DataSourcePreferences(context)

        val enabledFilters = filterPreferences.getEnabledFilters()
        val filtersToUse = if (enabledFilters.isEmpty()) emptyList() else enabledFilters.toList()
        Log.d("QuestsViewModel","Filters enabled: $filtersToUse")
        _filterSizeLiveData.postValue(filtersToUse.size)
        val selectedSources = dataSourcePreferences.getSelectedSources()

        viewModelScope.launch {
            try {
                Log.d("QuestsViewModel", "Selected data sources: $selectedSources")
                val deferredList = selectedSources.mapNotNull { source ->
                    ApiClient.DATA_SOURCE_URLS[source]?.let { baseUrl ->
                        async(Dispatchers.IO) {
                            try {
                                val retrofit = Retrofit.Builder()
                                    .baseUrl(baseUrl)
                                    .addConverterFactory(GsonConverterFactory.create())
                                    .client(client)
                                    .build()
                                val service = retrofit.create(QuestsApiService::class.java)

                                val response = service.getQuests(filtersToUse, System.currentTimeMillis()).execute()

                                // Check if the response is successful
                                if (response.isSuccessful) {
                                    Log.d("QuestsViewModel", "API call successful for source $source")
                                    Pair(source, Result.success(response))
                                } else {
                                    // Handle HTTP errors (4xx, 5xx)
                                    val errorMsg = "HTTP ${response.code()}: ${response.message()}"
                                    Log.w("QuestsViewModel", "API error for source $source: $errorMsg")
                                    Pair(source, Result.failure<retrofit2.Response<Quests.QuestsResponse>>(
                                        HttpException(response)
                                    ))
                                }
                            } catch (e: IOException) {
                                // Network connectivity issues, timeouts
                                Log.e("QuestsViewModel", "Network error for source $source", e)
                                Pair(source, Result.failure<retrofit2.Response<Quests.QuestsResponse>>(
                                    IOException("Network error for source $source", e)
                                ))

                            } catch (e: HttpException) {
                                // HTTP errors (if using suspend functions)
                                Log.e("QuestsViewModel", "HTTP error for source $source: ${e.code()}", e)
                                Pair(source, Result.failure<retrofit2.Response<Quests.QuestsResponse>>(
                                    e
                                ))
                            } catch (e: JsonSyntaxException) {
                                // JSON parsing errors
                                Log.e("QuestsViewModel", "JSON parsing error for source $source", e)
                                Pair(source, Result.failure<retrofit2.Response<Quests.QuestsResponse>>(
                                    IOException("JSON parsing error for source $source", e)
                                ))
                            } catch (e: Exception) {
                                // Any other unexpected errors
                                Log.e("QuestsViewModel", "Unexpected error for source $source", e)
                                Pair(source, Result.failure<retrofit2.Response<Quests.QuestsResponse>>(
                                    Exception("Unexpected error for source $source", e)
                                ))
                            }
                        }
                    }
                }

                val responses = deferredList.map { it.await() }

                // Filter successful responses
                val successfulResponses = responses.mapNotNull { (source, result) ->
                    result.getOrNull()?.let { response ->
                        Pair(source, response)
                    }
                }

                // If no successful responses, handle the error state
                if (successfulResponses.isEmpty()) {
                    Log.w("QuestsViewModel", "No successful API responses received")
                    _questsLiveData.postValue(emptyList())
                     _error.postValue("Unable to fetch quests. Please check your connection.")
                    return@launch
                }
                Log.d("QuestsViewModel", "Successfully fetched quests from ${successfulResponses.size} sources")

                // Process successful responses
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
                val enabledFormNumbers: List<String> =
                    filterPreferences
                        .getEnabledSpindaForms()
                        .map { it.substringAfterLast("_") }

                Log.d("QuestsViewModel", "Enabled Spinda Form Numbers: $enabledFormNumbers")
                val formRegex = Regex("Spinda \\((\\d{2})\\)")

                if(spindaForms.isNotEmpty()) {
                    filteredQuests = filterSpindaForms(filteredQuests, formRegex, enabledFormNumbers)
                }
                Log.d("QuestsViewModel", "Filtered quests after Spinda form filtering: ${filteredQuests.size}")





                filteredQuests = filteredQuests.take(500)



                // Load last visited coordinates from shared preferences
                val prefs = context.getSharedPreferences("last_visited_pref", Context.MODE_PRIVATE)
                val lastVisitedLat = prefs.getFloat("lastVisitedLat", Float.NaN)
                val lastVisitedLng = prefs.getFloat("lastVisitedLng", Float.NaN)
                val startLat = if (!lastVisitedLat.isNaN()) lastVisitedLat.toDouble() else null
                val startLng = if (!lastVisitedLng.isNaN()) lastVisitedLng.toDouble() else null

                // Sort the filtered quests using the last visited coordinates
                val sortedQuests = sortQuestsByNearestNeighbor(filteredQuests, startLat, startLng)

                _questsLiveData.postValue(sortedQuests)
                CurrentQuestData.currentQuests = sortedQuests.toMutableList()

            } catch (e: Exception) {
                e("QuestsViewModel", "Error in fetchQuests", e)
                _questsLiveData.postValue(emptyList())
                 _error.postValue("An unexpected error occurred")
            }
        }
    }

    private fun filterSpindaForms(
        quests: List<Quest>,
        formRegex: Regex,
        enabledFormNumbers: List<String>
    ): List<Quest> {
        return quests.filter { quest ->
            if (quest.rewardsIds != "327") {
                true
            } else {
                val matchResult = formRegex.find(quest.rewardsString)
                val formNumber = matchResult?.groupValues?.getOrNull(1)
                // We only keep the Spinda quest if we found its form number AND it's in the enabled list.
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
                // Switch to IO dispatcher for the blocking call
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
