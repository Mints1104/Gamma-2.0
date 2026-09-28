package com.mints.projectgammatwo.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.mints.projectgammatwo.R
import com.mints.projectgammatwo.helpers.OverlayButtonController
import com.mints.projectgammatwo.recyclerviews.InvasionsAdapter
import com.mints.projectgammatwo.viewmodels.HomeViewModel
import androidx.lifecycle.Lifecycle

class HomeFragment : Fragment() {

    // Activity-scoped so the list survives tab switches instead of being refetched each visit.
    private val viewModel: HomeViewModel by activityViewModels()
    private lateinit var adapter: InvasionsAdapter
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private val overlayButton = OverlayButtonController(this, "invasions")
    private lateinit var scrollToTopFab: FloatingActionButton
    private lateinit var recyclerView: RecyclerView
    private lateinit var errorHandlerText: TextView
    // Track listeners to properly unregister on view teardown
    private var scrollListener: RecyclerView.OnScrollListener? = null
    private var dataObserver: RecyclerView.AdapterDataObserver? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView = view.findViewById(R.id.invasionsRecyclerView)
        swipeRefresh = view.findViewById(R.id.swipeRefresh)
        scrollToTopFab = view.findViewById(R.id.scrollToTopFab)
        errorHandlerText = view.findViewById(R.id.errorHandlerText)

        adapter = InvasionsAdapter { invasion ->
            viewModel.deleteInvasion(invasion)
        }
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(context)

        setupScrollToTop()

        swipeRefresh.setOnRefreshListener {
            viewModel.fetchInvasions()
        }

        viewModel.invasions.observe(viewLifecycleOwner) { invasions ->
            // Scroll to top once the new list is actually applied. ListAdapter diffs in the
            // background, so scrolling straight after submitList ran against the old list and
            // RecyclerView then kept the previously visible card on screen: switching sort mode
            // left the view somewhere in the middle, making the new order look wrong.
            adapter.submitList(invasions) {
                recyclerView.scrollToPosition(0)
                recyclerView.post { checkAndUpdateFabVisibility() }
            }
            swipeRefresh.isRefreshing = false

            val filterSize = viewModel.currentFilterSize.value
            if (filterSize == 0) {
                errorHandlerText.visibility = View.VISIBLE
                errorHandlerText.setText(R.string.no_rocket_filters_message)
            } else if (invasions.isEmpty()) {
                errorHandlerText.visibility = View.VISIBLE
                errorHandlerText.setText(R.string.no_invasions_available_message)
            } else {
                errorHandlerText.visibility = View.GONE
            }
        }

        viewModel.error.observe(viewLifecycleOwner) { event ->
            event.consume()?.let { errorMessage ->
                Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                swipeRefresh.isRefreshing = false
            }
        }

        val deletedCountTextView = view.findViewById<TextView>(R.id.deletedCountText)
        viewModel.deletedCount.observe(viewLifecycleOwner) { count ->
            deletedCountTextView.text = getString(R.string.battles_last_24h, count)
        }

        overlayButton.bind(view.findViewById(R.id.startServiceButton))

        viewModel.restoreSavedSortMode()
        viewModel.refreshIfStale()

        requireActivity().addMenuProvider(sortMenuProvider, viewLifecycleOwner, Lifecycle.State.RESUMED)

        // Observe sort mode changes to update menu
        viewModel.sortByDistance.observe(viewLifecycleOwner) {
            requireActivity().invalidateOptionsMenu()
        }
    }

    override fun onResume() {
        super.onResume()
        overlayButton.updateLabel()
        checkAndUpdateFabVisibility()
    }

    override fun onDestroyView() {
        // Unregister listeners to avoid leaks and duplicate callbacks when the view is recreated
        scrollListener?.let { recyclerView.removeOnScrollListener(it) }
        scrollListener = null
        dataObserver?.let { adapter.unregisterAdapterDataObserver(it) }
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
        dataObserver?.let { adapter.unregisterAdapterDataObserver(it) }
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
        adapter.registerAdapterDataObserver(newObserver)
        dataObserver = newObserver

        scrollToTopFab.setOnClickListener {
            recyclerView.smoothScrollToPosition(0)
        }
    }

    /** Handles the sort items; MainActivity inflates the Rockets menu they belong to. */
    private val sortMenuProvider = object : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) = Unit

        override fun onPrepareMenu(menu: Menu) {
            val sortByDistance = viewModel.sortByDistance.value ?: false
            menu.findItem(R.id.action_sort_by_distance)?.setTitle(
                if (sortByDistance) R.string.sorted_by_distance else R.string.sort_by_distance
            )
            menu.findItem(R.id.action_sort_by_time)?.setTitle(
                if (sortByDistance) R.string.sort_by_time else R.string.sorted_by_time
            )
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean = when (menuItem.itemId) {
            R.id.action_sort_by_distance -> { viewModel.sortInvasions(true); true }
            R.id.action_sort_by_time -> { viewModel.sortInvasions(false); true }
            else -> false
        }
    }

    private fun checkAndUpdateFabVisibility() {
        // Hide when there's nothing to scroll or we are at the top
        if (adapter.itemCount == 0 || !recyclerView.canScrollVertically(-1)) {
            scrollToTopFab.hide()
            return
        }
        scrollToTopFab.show()
    }
}