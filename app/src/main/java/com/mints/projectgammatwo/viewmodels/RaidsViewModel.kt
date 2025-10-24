package com.mints.projectgammatwo.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonSyntaxException
import com.mints.projectgammatwo.R
import com.mints.projectgammatwo.data.ApiClient
import com.mints.projectgammatwo.data.DataSourcePreferences
import com.mints.projectgammatwo.data.RaidApiService
import com.mints.projectgammatwo.data.Raids
import com.mints.projectgammatwo.data.Raids.Raid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
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

    private val tag = "RaidsViewModel"

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

    // Cache RaidApiService per base URL
    private val serviceCache = mutableMapOf<String, RaidApiService>()

    private fun getServiceForBase(baseUrl: String): RaidApiService {
        return serviceCache.getOrPut(baseUrl) {
            Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .client(httpClient)
                .build()
                .create(RaidApiService::class.java)
        }
    }

    fun fetchRaids() {
        val context = getApplication<Application>().applicationContext
        val dataSourcePreferences = DataSourcePreferences(context)
        val selectedSources = dataSourcePreferences.getSelectedSources()
        Log.d(tag, "Selected data sources: $selectedSources")
        _filterSizeLiveData.postValue(selectedSources.size)

        viewModelScope.launch {
            try {
                val deferredList = selectedSources.mapNotNull { source ->
                    ApiClient.DATA_SOURCE_URLS[source]?.let { baseUrl ->
                        async(Dispatchers.IO) {
                            try {
                                val service = getServiceForBase(baseUrl)
                                val response = service.getRaids(System.currentTimeMillis()).execute()
                                if (response.isSuccessful) {
                                    Log.d(tag, "API call successful for source $source")
                                    Pair(source, Result.success(response))
                                } else {
                                    Log.w(tag, "API error for source $source: HTTP ${response.code()} ${response.message()}")
                                    Pair(source, Result.failure<retrofit2.Response<Raids.RaidsResponse>>(HttpException(response)))
                                }
                            } catch (e: IOException) {
                                Log.e(tag, "Network error for source $source", e)
                                Pair(source, Result.failure<retrofit2.Response<Raids.RaidsResponse>>(e))
                            } catch (e: HttpException) {
                                Log.e(tag, "HTTP error for source $source: ${e.code()}", e)
                                Pair(source, Result.failure<retrofit2.Response<Raids.RaidsResponse>>(e))
                            } catch (e: JsonSyntaxException) {
                                Log.e(tag, "JSON parsing error for source $source", e)
                                Pair(source, Result.failure<retrofit2.Response<Raids.RaidsResponse>>(e))
                            } catch (e: Exception) {
                                Log.e(tag, "Unexpected error for source $source", e)
                                Pair(source, Result.failure<retrofit2.Response<Raids.RaidsResponse>>(e))
                            }
                        }
                    }
                }

                val responses = deferredList.map { it.await() }
                val successfulResponses = responses.mapNotNull { (source, result) ->
                    result.getOrNull()?.let { response -> source to response }
                }

                if (successfulResponses.isEmpty()) {
                    Log.w(tag, "No successful API responses received")
                    _raidsLiveData.postValue(emptyList())
                    _raidsCountLiveData.postValue(0)
                    _error.postValue(context.getString(R.string.quests_error_unable_fetch))
                    return@launch
                }

                val allRaids = successfulResponses.flatMap { (source, response) ->
                    response.body()?.raids?.map { raid -> raid.copy(source = source) } ?: emptyList()
                }
                Log.d(tag, "Total raids fetched: ${allRaids.size}")

                val sorted = allRaids.sortedBy { it.raid_start }.reversed()
                _raidsLiveData.postValue(sorted)
                _raidsCountLiveData.postValue(sorted.size)

            } catch (e: Exception) {
                Log.e(tag, "Error in fetchRaids", e)
                _raidsLiveData.postValue(emptyList())
                _raidsCountLiveData.postValue(0)
                _error.postValue(context.getString(R.string.quests_error_unexpected))
            }
        }
    }

    fun getRaids(): List<Raid>? = _raidsLiveData.value

    fun deleteRaid(raid: Raid) {
        _raidsLiveData.value = _raidsLiveData.value?.toMutableList()?.apply { remove(raid) }
        _raidsCountLiveData.value = _raidsLiveData.value?.size ?: 0
    }
}
