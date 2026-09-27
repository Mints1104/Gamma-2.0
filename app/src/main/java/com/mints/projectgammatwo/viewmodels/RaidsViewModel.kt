package com.mints.projectgammatwo.viewmodels

import android.app.Application
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import com.mints.projectgammatwo.helpers.Event
import com.google.gson.JsonSyntaxException
import com.mints.projectgammatwo.R
import com.mints.projectgammatwo.data.ApiClient
import com.mints.projectgammatwo.data.DataSourcePreferences
import com.mints.projectgammatwo.data.Raids
import com.mints.projectgammatwo.data.Raids.Raid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

class RaidsViewModel(application: Application) : AndroidViewModel(application) {

    private val _raidsLiveData = MutableLiveData<List<Raid>>()
    val raidsLiveData: LiveData<List<Raid>> = _raidsLiveData

    private val _raidsCountLiveData = MutableLiveData<Int>()
    val raidsCountLiveData: LiveData<Int> = _raidsCountLiveData

    private val _filterSizeLiveData = MutableLiveData<Int>()
    val filterSizeLiveData: LiveData<Int> = _filterSizeLiveData

    private val _error = MutableLiveData<String>()

    /** One-shot, so a replay to a re-created view doesn't show the last error again. */
    val error: LiveData<Event<String>> = _error.map { Event(it) }

    private val tag = "RaidsViewModel"

    private var fetchJob: Job? = null

    /** The sources the published list was fetched from, and when; null until one succeeds. */
    private var publishedSources: Set<String>? = null
    private var publishedAtMs = 0L

    /**
     * Fetches only if the published list is older than [STALE_AFTER_MS] or came from different
     * data sources. This view model is activity-scoped so it survives tab switches; the screen
     * used to refetch every source on every visit. Pull-to-refresh still calls [fetchRaids].
     *
     * @return whether a fetch was started.
     */
    fun refreshIfStale(): Boolean {
        val sources = DataSourcePreferences(getApplication<Application>()).getSelectedSources()
        val age = SystemClock.elapsedRealtime() - publishedAtMs
        if (sources == publishedSources && age < STALE_AFTER_MS) return false
        fetchRaids()
        return true
    }

    private companion object {
        /** A published list younger than this, from the current sources, is reused. */
        const val STALE_AFTER_MS = 60_000L
    }

    fun fetchRaids() {
        val context = getApplication<Application>().applicationContext
        val dataSourcePreferences = DataSourcePreferences(context)
        val selectedSources = dataSourcePreferences.getSelectedSources()
        Log.d(tag, "Selected data sources: $selectedSources")
        _filterSizeLiveData.postValue(selectedSources.size)

        // Now that this view model is shared across visits, overlapping fetches are possible;
        // cancel the older one so a slow, superseded fetch can't finish last and win.
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            try {
                val deferredList = selectedSources.mapNotNull { source ->
                    ApiClient.DATA_SOURCE_URLS[source]?.let { baseUrl ->
                        async(Dispatchers.IO) {
                            try {
                                val service = ApiClient.raidsApi(baseUrl)
                                val response = service.getRaids(System.currentTimeMillis()).execute()
                                if (response.isSuccessful) {
                                    Log.d(tag, "API call successful for source $source")
                                    Pair(source, Result.success(response))
                                } else {
                                    Log.w(tag, "API error for source $source: HTTP ${response.code()} ${response.message()}")
                                    Pair(source, Result.failure<retrofit2.Response<Raids.RaidsResponse>>(HttpException(response)))
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: IOException) {
                                // Cancelling an OkHttp call surfaces as IOException("Canceled"),
                                // so bail out rather than reporting it as a fetch failure.
                                ensureActive()
                                Log.e(tag, "Network error for source $source", e)
                                Pair(source, Result.failure<retrofit2.Response<Raids.RaidsResponse>>(e))
                            } catch (e: HttpException) {
                                Log.e(tag, "HTTP error for source $source: ${e.code()}", e)
                                Pair(source, Result.failure<retrofit2.Response<Raids.RaidsResponse>>(e))
                            } catch (e: JsonSyntaxException) {
                                Log.e(tag, "JSON parsing error for source $source", e)
                                Pair(source, Result.failure<retrofit2.Response<Raids.RaidsResponse>>(e))
                            } catch (e: Exception) {
                                ensureActive()
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

                // Only a complete result counts as the published state; a partial one retries.
                if (successfulResponses.size == responses.size) {
                    publishedSources = selectedSources
                    publishedAtMs = SystemClock.elapsedRealtime()
                }

            } catch (e: CancellationException) {
                // Coroutine was cancelled - this is normal behavior, don't treat it as an error
                Log.d(tag, "Fetch raids was cancelled")
                throw e // Re-throw to properly propagate cancellation
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
