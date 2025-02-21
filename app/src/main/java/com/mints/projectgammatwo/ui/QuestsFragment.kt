package com.mints.projectgammatwo.ui

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.mints.projectgammatwo.R
import com.mints.projectgammatwo.data.QuestFilterPreferences
import com.mints.projectgammatwo.data.VisitedQuestsPreferences
import com.mints.projectgammatwo.recyclerviews.QuestsAdapter
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
    val rewardsIds: String
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

        // Initialize adapter with onDelete callback that saves visited quests
        questsAdapter = QuestsAdapter { quest ->
            // Create a unique identifier for the quest
            val questId = "${quest.name}|${quest.lat}|${quest.lng}"
            // Save as visited
            visitedPreferences.addVisitedQuest(questId)
            // Remove from the adapter list
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

        // Load quests initially
        fetchQuests()
    }

    private fun fetchQuests() {
        swipeRefresh.isRefreshing = true

        val interceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = OkHttpClient.Builder().addInterceptor(interceptor).build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://nycpokemap.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()

        val service = retrofit.create(QuestsApiService::class.java)

        // Get enabled quest filters from SharedPreferences.
        // If none are set, default to one value ("7,0,1").
        val enabledFilters = filterPreferences.getEnabledFilters()
            val filtersToUse = if (enabledFilters.isEmpty()) listOf("7,0,1") else enabledFilters.toList()

        val call = service.getQuests(filtersToUse, System.currentTimeMillis())
        call.enqueue(object : retrofit2.Callback<QuestsResponse> {
            override fun onResponse(call: Call<QuestsResponse>, response: retrofit2.Response<QuestsResponse>) {
                swipeRefresh.isRefreshing = false
                if (response.isSuccessful) {
                    response.body()?.let { questsResponse ->
                        // Save API filters for quest filtering UI
                        val filtersJson = Gson().toJson(questsResponse.filters)
                        requireContext().getSharedPreferences("quest_filters", Context.MODE_PRIVATE)
                            .edit().putString("quest_api_filters", filtersJson).apply()

                        // Filter out quests that have already been visited.
                        val visited = visitedPreferences.getVisitedQuests()
                        val filteredQuests = questsResponse.quests.filter { quest ->
                            val id = "${quest.name}|${quest.lat}|${quest.lng}"
                            !visited.contains(id)
                        }
                        questsAdapter.submitList(filteredQuests)
                        updateQuestsCount(filteredQuests.size)
                    } ?: Log.e("QuestsFragment", "No quests found")
                } else {
                    Log.e("QuestsFragment", "API error: ${response.errorBody()?.string()}")
                }
            }

            override fun onFailure(call: Call<QuestsResponse>, t: Throwable) {
                swipeRefresh.isRefreshing = false
                Log.e("QuestsFragment", "Failed to fetch quests", t)
            }
        })
    }

    private fun updateQuestsCount(count: Int) {
        questsCountText.text = "Total Quests: $count"
    }
}
