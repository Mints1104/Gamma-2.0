package com.mints.projectgammatwo.viewmodels

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
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
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class QuestsViewModel(application: Application) : AndroidViewModel(application) {

    private val _questsLiveData = MutableLiveData<List<Quest>>()
    val questsLiveData: LiveData<List<Quest>> = _questsLiveData

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

    // Sort the list of quests by repeatedly choosing the nearest unvisited quest.
    private fun sortQuestsByNearestNeighbor(quests: List<Quest>): List<Quest> {
        if (quests.isEmpty()) return quests

        val sorted = mutableListOf<Quest>()
        val remaining = quests.toMutableList()

        // Use the first quest as the starting point.
        var current = remaining.removeAt(0)
        sorted.add(current)

        while (remaining.isNotEmpty()) {
            val next = remaining.minByOrNull { haversineDistance(current, it) }!!
            sorted.add(next)
            remaining.remove(next)
            current = next
        }

        return sorted
    }

    fun fetchQuests() {
        val context = getApplication<Application>().applicationContext
        val interceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = OkHttpClient.Builder().addInterceptor(interceptor).build()

        val filterPreferences = QuestFilterPreferences(context)
        val visitedPreferences = VisitedQuestsPreferences(context)
        val dataSourcePreferences = DataSourcePreferences(context)

        val enabledFilters = filterPreferences.getEnabledFilters()
        val filtersToUse = if (enabledFilters.isEmpty()) listOf("7,0,1") else enabledFilters.toList()
        val selectedSources = dataSourcePreferences.getSelectedSources()

        viewModelScope.launch {
            try {
                val deferredList = selectedSources.mapNotNull { source ->
                    ApiClient.DATA_SOURCE_URLS[source]?.let { baseUrl ->
                        async(Dispatchers.IO) {
                            val retrofit = Retrofit.Builder()
                                .baseUrl(baseUrl)
                                .addConverterFactory(GsonConverterFactory.create())
                                .client(client)
                                .build()
                            val service = retrofit.create(QuestsApiService::class.java)
                            val response = service.getQuests(filtersToUse, System.currentTimeMillis()).execute()
                            Pair(source, response)
                        }
                    }
                }
                val responses = deferredList.mapNotNull { it.await() }

                responses.firstOrNull()?.second?.body()?.filters?.let { filters ->
                    val filtersJson = Gson().toJson(filters)
                    context.getSharedPreferences("quest_filters", Context.MODE_PRIVATE)
                        .edit().putString("quest_api_filters", filtersJson).apply()
                }

                val allQuests = responses.flatMap { (source, response) ->
                    response.body()?.quests?.map { quest ->
                        quest.copy(source = source)
                    } ?: emptyList()
                }
                val visited = visitedPreferences.getVisitedQuests()
                val filteredQuests = allQuests.filter { quest ->
                    val id = "${quest.name}|${quest.lat}|${quest.lng}"
                    !visited.contains(id)
                }

                // Sort the filtered quests by nearest neighbor using the first quest as the basis.
                val sortedQuests = sortQuestsByNearestNeighbor(filteredQuests)
                _questsLiveData.postValue(sortedQuests)
            } catch (e: Exception) {
                Log.e("QuestsViewModel", "Error fetching quests", e)
            }
        }
    }
}
