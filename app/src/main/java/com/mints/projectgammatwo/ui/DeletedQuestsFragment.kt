package com.mints.projectgammatwo.ui

import android.os.Bundle
import android.view.*
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.mints.projectgammatwo.R
import com.mints.projectgammatwo.data.VisitedQuestsPreferences
import com.mints.projectgammatwo.recyclerviews.DeletedQuestsAdapter
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DeletedQuestsFragment : Fragment() {

    private companion object {
        const val CLEAR_HISTORY_REQUEST_KEY = "clear_visited_quests"
        const val CLEAR_HISTORY_DIALOG_TAG = "clear_visited_quests_dialog"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Registered here, not with the view, so a confirmation restored after rotation still
        // reaches this fragment.
        childFragmentManager.setFragmentResultListener(CLEAR_HISTORY_REQUEST_KEY, this) { _, _ ->
            clearHistory()
        }
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: DeletedQuestsAdapter
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var emptyText: TextView
    private lateinit var countText: TextView
    private lateinit var scrollToTopFab: FloatingActionButton

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_deleted_quests, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.visitedRecyclerView)
        swipeRefresh = view.findViewById(R.id.swipeRefresh)
        emptyText = view.findViewById(R.id.emptyText)
        countText = view.findViewById(R.id.visitedCountText)
        scrollToTopFab = view.findViewById(R.id.scrollToTopFab)

        adapter = DeletedQuestsAdapter()
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                checkFab()
            }
        })

        scrollToTopFab.setOnClickListener { recyclerView.smoothScrollToPosition(0) }
        swipeRefresh.setOnRefreshListener { loadData() }

        loadData()

        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_deleted_history, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean = when (menuItem.itemId) {
                R.id.action_clear_history -> { confirmClearHistory(); true }
                else -> false
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun confirmClearHistory() {
        ConfirmDialogFragment.newInstance(
            CLEAR_HISTORY_REQUEST_KEY,
            title = R.string.delete_all_visited_quests_title,
            message = R.string.delete_all_visited_quests_message,
            confirm = R.string.action_delete,
            cancel = R.string.action_cancel,
        ).show(childFragmentManager, CLEAR_HISTORY_DIALOG_TAG)
    }

    private fun clearHistory() {
        VisitedQuestsPreferences(requireContext()).resetVisited()
        loadData()
    }

    private fun loadData() {
        swipeRefresh.isRefreshing = true
        val context = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            // Off the main thread, like the invasion history: getVisitedRecords parses every
            // stored record (and may prune and rewrite the set), which made the screen stutter
            // as it opened.
            val models = withContext(Dispatchers.IO) {
                VisitedQuestsPreferences(context).getVisitedRecords()
                    .sortedByDescending { it.timestamp }
                    .mapNotNull { r ->
                        val parts = r.id.split("|")
                        val name = parts.getOrNull(0) ?: return@mapNotNull null
                        val lat = parts.getOrNull(1)?.toDoubleOrNull() ?: return@mapNotNull null
                        val lng = parts.getOrNull(2)?.toDoubleOrNull() ?: return@mapNotNull null
                        DeletedQuestsAdapter.UIModel(
                            name = name,
                            lat = lat,
                            lng = lng,
                            timestamp = r.timestamp,
                            source = r.source.orEmpty(),
                            rewards = r.rewards.orEmpty(),
                            conditions = r.conditions.orEmpty()
                        )
                    }
            }
            adapter.submitList(models)
            swipeRefresh.isRefreshing = false

            countText.text = resources.getQuantityString(R.plurals.visited_count, models.size, models.size)
            emptyText.visibility = if (models.isEmpty()) View.VISIBLE else View.GONE
            checkFab()
        }
    }

    private fun checkFab() {
        if (adapter.itemCount == 0 || !recyclerView.canScrollVertically(-1)) {
            scrollToTopFab.hide(); return
        }
        scrollToTopFab.show()
    }
}
