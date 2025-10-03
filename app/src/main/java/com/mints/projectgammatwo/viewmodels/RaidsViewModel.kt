package com.mints.projectgammatwo.viewmodels

import android.app.Application
import android.util.Log
import android.util.Log.e
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonSyntaxException
import com.mints.projectgammatwo.data.ApiClient
import com.mints.projectgammatwo.data.DataSourcePreferences
import com.mints.projectgammatwo.data.Quests
import com.mints.projectgammatwo.data.QuestsApiService
import com.mints.projectgammatwo.data.RaidApiService
import com.mints.projectgammatwo.data.Raids
import com.mints.projectgammatwo.data.Raids.RaidsResponse
import com.mints.projectgammatwo.data.Raids.Raid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okio.IOException
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class RaidsViewModel(application: Application) : AndroidViewModel(application) {
    private val _raidsLiveData = MutableLiveData<List<Raid>>()
    val raidsLiveData: LiveData<List<Raid>> = _raidsLiveData

    private val _raidsCountLiveData = MutableLiveData<Int>()
    val raidsCountLiveData: LiveData<Int> = _raidsCountLiveData

    private val _filterSizeLiveData = MutableLiveData<Int>()
    val filterSizeLiveData: LiveData<Int> = _filterSizeLiveData


    private val _error = MutableLiveData<String>()
    val error: LiveData<String> get() = _error

    val tag = "RaidsViewModel"

    fun fetchRaids() {
        Log.d(tag, "Starting fetchRaids...")
        val context = getApplication<Application>().applicationContext
        val dataSourcePreferences = DataSourcePreferences(context)

        val selectedSources = dataSourcePreferences.getSelectedSources()
        val interceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.NONE
        }
        val client = OkHttpClient.Builder().addInterceptor(interceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        viewModelScope.launch {
            try {
                Log.d(tag, "Selected data sources: $selectedSources")
                val deferredList = selectedSources.mapNotNull { source ->
                    ApiClient.DATA_SOURCE_URLS[source]?.let { baseUrl ->
                        async(Dispatchers.IO) {
                            try {
                                val retrofit = Retrofit.Builder()
                                    .baseUrl(baseUrl)
                                    .addConverterFactory(GsonConverterFactory.create())
                                    .client(client)
                                    .build()
                                val service = retrofit.create(RaidApiService::class.java)

                                val response =
                                    service.getRaids(System.currentTimeMillis()).execute()

                                // Check if the response is successful
                                if (response.isSuccessful) {
                                    Log.d(tag, "API call successful for source $source")
                                    Pair(source, Result.success(response))
                                } else {
                                    // Handle HTTP errors (4xx, 5xx)
                                    val errorMsg = "HTTP ${response.code()}: ${response.message()}"
                                    Log.w(tag, "API error for source $source: $errorMsg")
                                    Pair(
                                        source, Result.failure<retrofit2.Response<RaidsResponse>>(
                                            HttpException(response)
                                        )
                                    )
                                }
                            } catch (e: IOException) {
                                // Network connectivity issues, timeouts
                                Log.e(tag, "Network error for source $source", e)
                                Pair(
                                    source, Result.failure<retrofit2.Response<RaidsResponse>>(
                                        IOException("Network error for source $source", e)
                                    )
                                )

                            } catch (e: HttpException) {
                                // HTTP errors (if using suspend functions)
                                Log.e(tag, "HTTP error for source $source: ${e.code()}", e)
                                Pair(
                                    source, Result.failure<retrofit2.Response<RaidsResponse>>(
                                        e
                                    )
                                )
                            } catch (e: JsonSyntaxException) {
                                // JSON parsing errors
                                Log.e(tag, "JSON parsing error for source $source", e)
                                Pair(
                                    source, Result.failure<retrofit2.Response<RaidsResponse>>(
                                        IOException("JSON parsing error for source $source", e)
                                    )
                                )
                            } catch (e: Exception) {
                                // Any other unexpected errors
                                Log.e(tag, "Unexpected error for source $source", e)
                                Pair(
                                    source, Result.failure<retrofit2.Response<RaidsResponse>>(
                                        Exception("Unexpected error for source $source", e)
                                    )
                                )
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
                    Log.w(tag, "No successful API responses received")
                    _raidsLiveData.postValue(emptyList())
                    _raidsCountLiveData.postValue(0)
                    _filterSizeLiveData.postValue(0)
                    _error.postValue("Unable to fetch raids. Please check your connection.")
                    return@launch
                }

                // Process successful responses
                val combinedRaids = successfulResponses.flatMap { (source, response) ->
                    response.body()?.raids?.map { raid ->
                        raid.copy(source = source)
                    } ?: emptyList()
                }.toMutableList()

                // Filter raids (you might want to add filtering preferences similar to HomeViewModel)
                val filteredRaids = combinedRaids
                    .filter { raid ->
                        // Add any filtering logic here
                        // For now, we'll include all raids
                        true
                    }
                    .sortedBy { it.raid_start }
                    .reversed()

                // Update LiveData
                _raidsLiveData.postValue(filteredRaids)
                _raidsCountLiveData.postValue(filteredRaids.size)
                _filterSizeLiveData.postValue(filteredRaids.size)

                Log.d(tag, "Successfully fetched raids from ${successfulResponses.size} sources. Total raids: ${filteredRaids.size}")

            } catch (e: Exception) {
                e(tag, "Error in fetchRaids", e)
                _raidsLiveData.postValue(emptyList())
                _raidsCountLiveData.postValue(0)
                _filterSizeLiveData.postValue(0)
                _error.postValue("An unexpected error occurred")
            }
        }
    }

    fun getRaids(): List<Raid>? {
        return _raidsLiveData.value
    }

    // Method to handle raid deletion (if needed)
    fun deleteRaid(raid: Raid) {
        _raidsLiveData.value = _raidsLiveData.value?.toMutableList()?.apply { remove(raid) }
        _raidsCountLiveData.value = _raidsLiveData.value?.size ?: 0
    }
}
