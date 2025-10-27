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
import com.mints.projectgammatwo.data.DataMappings
import com.mints.projectgammatwo.data.DeletedEntry
import com.mints.projectgammatwo.data.DeletedInvasionsRepository
import com.mints.projectgammatwo.recyclerviews.DeletedInvasionsAdapter
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.appcompat.app.AlertDialog
import android.widget.Button

class DeletedInvasionsFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: DeletedInvasionsAdapter
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var emptyText: TextView
    private lateinit var countText: TextView
    private lateinit var scrollToTopFab: FloatingActionButton

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_deleted_invasions, container, false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView = view.findViewById(R.id.deletedRecyclerView)
        swipeRefresh = view.findViewById(R.id.swipeRefresh)
        emptyText = view.findViewById(R.id.emptyText)
        countText = view.findViewById(R.id.deletedCountText)
        scrollToTopFab = view.findViewById(R.id.scrollToTopFab)

        adapter = DeletedInvasionsAdapter()
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

        title.text = getString(R.string.delete_all_invasions_title)
        message.text = getString(R.string.delete_all_invasions_message)
        cancelButton.text = getString(R.string.action_cancel)
        confirmButton.text = getString(R.string.action_delete)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        cancelButton.setOnClickListener { dialog.dismiss() }
        confirmButton.setOnClickListener {
            val repo = DeletedInvasionsRepository(requireContext())
            repo.resetDeletedInvasions()
            loadData()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun loadData() {
        swipeRefresh.isRefreshing = true
        viewLifecycleOwner.lifecycleScope.launch {
            // Capture string resources on main thread for safe use off-thread
            val unknownCharacter = getString(R.string.deleted_unknown_character)
            val unknownType = getString(R.string.deleted_unknown_type)
            val unknownStop = getString(R.string.deleted_unknown_stop)
            val unknownSource = getString(R.string.deleted_unknown_source)

            val mapped = withContext(Dispatchers.IO) {
                val repo = DeletedInvasionsRepository(requireContext())
                val entries: List<DeletedEntry> = repo.getDeletedEntries()
                    .sortedByDescending { it.timestamp }
                    .toList()
                entries.map { e: DeletedEntry ->
                    val characterName = e.character?.let { DataMappings.characterNamesMap[it] } ?: unknownCharacter
                    val typeDesc = e.type?.let { DataMappings.typeDescriptionsMap[it] } ?: unknownType
                    DeletedInvasionsAdapter.UIModel(
                        name = e.name ?: unknownStop,
                        source = e.source ?: unknownSource,
                        characterName = characterName,
                        typeDescription = typeDesc,
                        lat = e.lat,
                        lng = e.lng,
                        timestamp = e.timestamp
                    )
                }
            }

            adapter.submitList(mapped)
            swipeRefresh.isRefreshing = false

            countText.text = resources.getQuantityString(R.plurals.deleted_count, mapped.size, mapped.size)
            emptyText.visibility = if (mapped.isEmpty()) View.VISIBLE else View.GONE
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
