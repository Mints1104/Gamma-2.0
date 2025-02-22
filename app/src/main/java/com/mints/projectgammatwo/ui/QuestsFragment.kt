package com.mints.projectgammatwo.ui

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.mints.projectgammatwo.R
import com.mints.projectgammatwo.data.ApiClient
import com.mints.projectgammatwo.data.DataSourcePreferences
import com.mints.projectgammatwo.data.QuestFilterPreferences
import com.mints.projectgammatwo.data.VisitedQuestsPreferences
import com.mints.projectgammatwo.recyclerviews.QuestsAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

// Data model for a quest (matching the JSON exactly)
data class Quest(
    val name: String,
    val lat: Double,
    val lng: Double,
    @SerializedName("rewards_string")
    val rewardsString: String,
    @SerializedName("conditions_string")
    val conditionsString: String,
    val image: String,
    @SerializedName("rewards_types")
    val rewardsTypes: String,
    @SerializedName("rewards_amounts")
    val rewardsAmounts: String,
    @SerializedName("rewards_ids")
    val rewardsIds: String,
    val source: String = ""  // New property; default is empty
)


data class QuestsResponse(
    val quests: List<Quest>,
    val meta: Meta,
    val filters: Filters
)

data class Meta(
    val time: Long
)

// Data model for filters returned by the API.
data class Filters(
    val t3: List<String>,
    val t4: List<String>,
    val t2: List<String>,
    val t7: List<String>,
    val t12: List<String>
)

// Retrofit API Interface now accepts multiple quests[] parameters.
interface QuestsApiService {
    @GET("quests.php")
    fun getQuests(
        @Query("quests[]") quests: List<String>,
        @Query("time") timestamp: Long
    ): Call<QuestsResponse>
}

class QuestsFragment : Fragment() {

    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var questsCountText: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var questsAdapter: QuestsAdapter
    private lateinit var filterPreferences: QuestFilterPreferences
    private lateinit var visitedPreferences: VisitedQuestsPreferences
    private lateinit var dataSourcePreferences: DataSourcePreferences

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate layout containing a header TextView, SwipeRefreshLayout, and RecyclerView
        return inflater.inflate(R.layout.fragment_quests, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        swipeRefresh = view.findViewById(R.id.swipeRefresh)
        questsCountText = view.findViewById(R.id.questsCountText)
        recyclerView = view.findViewById(R.id.questsRecyclerView)

        filterPreferences = QuestFilterPreferences(requireContext())
        visitedPreferences = VisitedQuestsPreferences(requireContext())
        dataSourcePreferences = DataSourcePreferences(requireContext())

        // Initialize adapter with onDelete callback that saves visited quests
        questsAdapter = QuestsAdapter { quest ->
            // Create a unique identifier for the quest.
            val questId = "${quest.name}|${quest.lat}|${quest.lng}"
            // Save as visited.
            visitedPreferences.addVisitedQuest(questId)
            // Remove from the adapter list.
            val currentList = questsAdapter.currentList.toMutableList()
            currentList.remove(quest)
            questsAdapter.submitList(currentList)
            updateQuestsCount(currentList.size)
        }

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = questsAdapter

        swipeRefresh.setOnRefreshListener {
            fetchQuests()
        }

        // Load quests initially.
        fetchQuests()
    }

    private fun fetchQuests() {
        swipeRefresh.isRefreshing = true

        val interceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = OkHttpClient.Builder().addInterceptor(interceptor).build()

        val enabledFilters = filterPreferences.getEnabledFilters()
        val filtersToUse = if (enabledFilters.isEmpty()) listOf("7,0,1") else enabledFilters.toList()

        // Get selected data sources
        val selectedSources = dataSourcePreferences.getSelectedSources()

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // For each selected source, fetch quests on the IO dispatcher and tag them with the source.
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

                // Use the filters from the first successful response to update the quest filter UI.
                responses.firstOrNull()?.second?.body()?.filters?.let { filters ->
                    val filtersJson = Gson().toJson(filters)
                    requireContext().getSharedPreferences("quest_filters", Context.MODE_PRIVATE)
                        .edit().putString("quest_api_filters", filtersJson).apply()
                }

                // Combine quests from all responses and tag each with its source.
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
                questsAdapter.submitList(filteredQuests)
                updateQuestsCount(filteredQuests.size)
            } catch (e: Exception) {
                Log.e("QuestsFragment", "Error fetching quests", e)
            } finally {
                swipeRefresh.isRefreshing = false
            }
        }
    }



    private fun updateQuestsCount(count: Int) {
        questsCountText.text = "Total Quests: $count"
    }
}
