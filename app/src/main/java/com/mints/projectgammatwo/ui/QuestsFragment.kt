package com.mints.projectgammatwo.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.mints.projectgammatwo.R
import com.mints.projectgammatwo.data.Quests
import com.mints.projectgammatwo.helpers.OverlayButtonController
import com.mints.projectgammatwo.recyclerviews.QuestsAdapter
import com.mints.projectgammatwo.viewmodels.QuestsViewModel


class QuestsFragment : Fragment() {

    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var questsCountText: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var questsAdapter: QuestsAdapter
    private lateinit var questsViewModel: QuestsViewModel
    private val overlayButton = OverlayButtonController(this, "quests")
    private lateinit var scrollToTopFab: FloatingActionButton
    private lateinit var questErrorHandler: TextView

    // Track listeners to properly unregister on view teardown
    private var scrollListener: RecyclerView.OnScrollListener? = null
    private var dataObserver: RecyclerView.AdapterDataObserver? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_quests, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Removed fragment menu provider; MainActivity owns the app bar menu

        swipeRefresh = view.findViewById(R.id.swipeRefresh)
        questsCountText = view.findViewById(R.id.questsCountText)
        recyclerView = view.findViewById(R.id.questsRecyclerView)
        questErrorHandler = view.findViewById(R.id.errorHandlerText)
        questsAdapter = QuestsAdapter { quest: Quests.Quest ->
            // The view model removes it from the list; the observer below updates the adapter.
            questsViewModel.markVisited(quest)
        }

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = questsAdapter
        scrollToTopFab = view.findViewById(R.id.scrollToTopFab)
        setupScrollToTop()
        // Activity-scoped: shared with the filter screen (which already used the activity's
        // instance), and surviving tab switches instead of being refetched each visit.
        questsViewModel = ViewModelProvider(requireActivity())[QuestsViewModel::class.java]
        questsViewModel.questsLiveData.observe(viewLifecycleOwner) { quests: List<Quests.Quest> ->
            questsAdapter.submitList(quests)
            updateQuestsCount(quests.size)
            swipeRefresh.isRefreshing = false

            if(questsViewModel.filterSizeLiveData.value == 0) {
                questErrorHandler.visibility = View.VISIBLE
                questErrorHandler.setText(R.string.no_quest_filters_message)

            } else if(quests.isEmpty()) {
                questErrorHandler.visibility = View.VISIBLE
                questErrorHandler.setText(R.string.no_quests_available_message)
            } else {
                questErrorHandler.visibility = View.GONE
            }

            recyclerView.post {
                checkAndUpdateFabVisibility()
            }

        }

        questsViewModel.error.observe(viewLifecycleOwner) { event ->
            event.consume()?.let { errorMessage ->
                Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                swipeRefresh.isRefreshing = false
            }
        }

        swipeRefresh.setOnRefreshListener {
            questsViewModel.fetchQuests()
        }

        overlayButton.bind(view.findViewById(R.id.startServiceButton))

        if (questsViewModel.refreshIfStale()) {
            swipeRefresh.isRefreshing = true
        }
    }

    override fun onDestroyView() {
        // Unregister listeners to avoid leaks and duplicate callbacks when the view is recreated
        scrollListener?.let { recyclerView.removeOnScrollListener(it) }
        scrollListener = null
        dataObserver?.let { questsAdapter.unregisterAdapterDataObserver(it) }
        dataObserver = null
        super.onDestroyView()
    }

    private fun setupScrollToTop() {
        // Remove existing listener if any (defensive in case of multiple calls)
        scrollListener?.let { recyclerView.removeOnScrollListener(it) }
        val newScrollListener = object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                checkAndUpdateFabVisibility()
            }
        }
        recyclerView.addOnScrollListener(newScrollListener)
        scrollListener = newScrollListener

        // Unregister previous observer if any, then add a new one tied to this view lifecycle
        dataObserver?.let { questsAdapter.unregisterAdapterDataObserver(it) }
        val newObserver = object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                super.onItemRangeInserted(positionStart, itemCount)
                recyclerView.post { checkAndUpdateFabVisibility() }
            }

            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
                super.onItemRangeRemoved(positionStart, itemCount)
                recyclerView.post { checkAndUpdateFabVisibility() }
            }

            override fun onChanged() {
                super.onChanged()
                recyclerView.post { checkAndUpdateFabVisibility() }
            }
        }
        questsAdapter.registerAdapterDataObserver(newObserver)
        dataObserver = newObserver

        // Handle FAB click
        scrollToTopFab.setOnClickListener {
            recyclerView.smoothScrollToPosition(0)
        }
    }

    private fun checkAndUpdateFabVisibility() {
        // Hide when there's nothing to scroll or we are at the top
        if (questsAdapter.itemCount == 0 || !recyclerView.canScrollVertically(-1)) {
            scrollToTopFab.hide()
            return
        }
        scrollToTopFab.show()
    }

    private fun updateQuestsCount(count: Int) {
        questsCountText.text = getString(R.string.total_quests, count)
    }

    override fun onResume() {
        super.onResume()
        overlayButton.updateLabel()
        checkAndUpdateFabVisibility()

    }
}
