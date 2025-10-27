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
import androidx.appcompat.app.AlertDialog
import android.widget.Button

class DeletedQuestsFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: DeletedQuestsAdapter
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var emptyText: TextView
    private lateinit var countText: TextView
    private lateinit var scrollToTopFab: FloatingActionButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

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
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_deleted_history, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_clear_history -> {
                confirmClearHistory()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun confirmClearHistory() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_confirm_import_hotspots, null)

        val title = dialogView.findViewById<TextView>(R.id.confirmTitle)
        val message = dialogView.findViewById<TextView>(R.id.confirmMessage)
        val cancelButton = dialogView.findViewById<Button>(R.id.cancelConfirmButton)
        val confirmButton = dialogView.findViewById<Button>(R.id.confirmImportButton)

        title.text = getString(R.string.delete_all_visited_quests_title)
        message.text = getString(R.string.delete_all_visited_quests_message)
        cancelButton.text = getString(R.string.action_cancel)
        confirmButton.text = getString(R.string.action_delete)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        cancelButton.setOnClickListener { dialog.dismiss() }
        confirmButton.setOnClickListener {
            val prefs = VisitedQuestsPreferences(requireContext())
            prefs.resetVisited()
            loadData()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun loadData() {
        val prefs = VisitedQuestsPreferences(requireContext())
        val records = prefs.getVisitedRecords()
            .sortedByDescending { it.timestamp }
        val models = records.mapNotNull { r ->
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
        adapter.submitList(models)
        swipeRefresh.isRefreshing = false

        countText.text = resources.getQuantityString(R.plurals.visited_count, models.size, models.size)
        emptyText.visibility = if (models.isEmpty()) View.VISIBLE else View.GONE
        checkFab()
    }

    private fun checkFab() {
        if (adapter.itemCount == 0 || !recyclerView.canScrollVertically(-1)) {
            scrollToTopFab.hide(); return
        }
        scrollToTopFab.show()
    }
}
