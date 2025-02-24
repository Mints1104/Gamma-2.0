package com.mints.projectgammatwo.viewmodels

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
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

class QuestsViewModel(application: Application) : AndroidViewModel(application) {

    private val _questsLiveData = MutableLiveData<List<Quest>>()
    val questsLiveData: LiveData<List<Quest>> = _questsLiveData

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
                _questsLiveData.postValue(filteredQuests)
            } catch (e: Exception) {
                Log.e("QuestsViewModel", "Error fetching quests", e)
            }
        }
    }
}
