package com.mints.projectgammatwo.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.mints.projectgammatwo.R
import com.mints.projectgammatwo.data.Quests
import com.mints.projectgammatwo.recyclerviews.QuestsAdapter
import com.mints.projectgammatwo.viewmodels.QuestsViewModel

class QuestsFragment : Fragment() {

    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var questsCountText: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var questsAdapter: QuestsAdapter
    private lateinit var questsViewModel: QuestsViewModel

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

        // Initialize adapter with onDelete callback preserving your exact deletion logic.
        questsAdapter = QuestsAdapter { quest: Quests.Quest ->
            // Create a unique identifier for the quest.
            val questId = "${quest.name}|${quest.lat}|${quest.lng}"
            // Save as visited.
            val visitedPreferences = com.mints.projectgammatwo.data.VisitedQuestsPreferences(requireContext())
            visitedPreferences.addVisitedQuest(questId)
            // Remove from the adapter list.
            val currentList = questsAdapter.currentList.toMutableList()
            currentList.remove(quest)
            questsAdapter.submitList(currentList)
            updateQuestsCount(currentList.size)
        }

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = questsAdapter

        questsViewModel = ViewModelProvider(this)[QuestsViewModel::class.java]
        questsViewModel.questsLiveData.observe(viewLifecycleOwner) { quests: List<Quests.Quest> ->
            questsAdapter.submitList(quests)
            updateQuestsCount(quests.size)
            swipeRefresh.isRefreshing = false
        }


        swipeRefresh.setOnRefreshListener {
            questsViewModel.fetchQuests()
        }

        // Load quests initially.
        swipeRefresh.isRefreshing = true
        questsViewModel.fetchQuests()
    }

    private fun updateQuestsCount(count: Int) {
        questsCountText.text = "Total Quests: $count"
    }
}
