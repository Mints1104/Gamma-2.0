package com.mints.projectgammatwo.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.mints.projectgammatwo.R
import com.mints.projectgammatwo.helpers.OverlayServiceManager
import com.mints.projectgammatwo.recyclerviews.InvasionsAdapter
import com.mints.projectgammatwo.viewmodels.HomeViewModel
import androidx.core.net.toUri
import com.mints.projectgammatwo.data.DataMappings
import androidx.lifecycle.Lifecycle

class HomeFragment : Fragment() {

    private val viewModel: HomeViewModel by viewModels()
    private lateinit var adapter: InvasionsAdapter
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var serviceManager: OverlayServiceManager
    private lateinit var scrollToTopFab: FloatingActionButton
    private lateinit var recyclerView: RecyclerView
    private lateinit var errorHandlerText: TextView
    // Track listeners to properly unregister on view teardown
    private var scrollListener: RecyclerView.OnScrollListener? = null
    private var dataObserver: RecyclerView.AdapterDataObserver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ensure this fragment contributes to the options menu
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        serviceManager = OverlayServiceManager(requireContext())

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
            adapter.submitList(invasions)
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

            recyclerView.post {
                checkAndUpdateFabVisibility()
                // Scroll to top on list updates to prevent jumping to bottom
                recyclerView.scrollToPosition(0)
            }
        }

        viewModel.error.observe(viewLifecycleOwner) { errorMessage ->
            Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
            swipeRefresh.isRefreshing = false
        }

        val deletedCountTextView = view.findViewById<TextView>(R.id.deletedCountText)
        viewModel.deletedCount.observe(viewLifecycleOwner) { count ->
            deletedCountTextView.text = getString(R.string.battles_last_24h, count)
        }

        val startServiceButton = view.findViewById<Button>(R.id.startServiceButton)
        startServiceButton.setOnClickListener {
            handleStartServiceClick()
        }

        viewModel.fetchInvasions()
        updateServiceButtonState(startServiceButton)

        // Observe sort mode changes to update menu
        viewModel.sortByDistance.observe(viewLifecycleOwner) {
            requireActivity().invalidateOptionsMenu()
        }
    }

    private fun testGetDeletedInvasions() {
        val deletedInvasions = viewModel.getDeletedInvasions()
        deletedInvasions.forEach { deletedEntry ->
            val characterName = deletedEntry.character?.let {
                DataMappings.characterNamesMap[it]
            } ?: "Unknown Character"

            val typeDescription = deletedEntry.type.let {
                DataMappings.typeDescriptionsMap[it]
            } ?: "Unknown Type"

            Log.d("Test","DeletedInvasion: ${deletedEntry.name} from source ${deletedEntry.source}, Name: $characterName, Type: $typeDescription")
        }
    }

    override fun onResume() {
        super.onResume()
        view?.findViewById<Button>(R.id.startServiceButton)?.let {
            updateServiceButtonState(it)
        }
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
            recyclerView.scrollToPosition(0)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        // Menu is already inflated in MainActivity
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
           R.id.action_sort_by_distance -> {
               viewModel.sortInvasions(true)
               true
           }
            R.id.action_sort_by_time -> {
                viewModel.sortInvasions(false)

                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        val sortByDistance = viewModel.sortByDistance.value ?: false
        val sortMenuItem = menu.findItem(R.id.action_sort_by_distance)
        val timeMenuItem = menu.findItem(R.id.action_sort_by_time)
        if (sortByDistance) {
            sortMenuItem?.title = "✓ Sorted by Distance"
            timeMenuItem?.title = "Sort by Time"
        } else {
            sortMenuItem?.title = "Sort by Distance"
            timeMenuItem?.title = "✓ Sorted by Time"
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

    private fun handleStartServiceClick() {
        if (Settings.canDrawOverlays(requireContext())) {
            serviceManager.startOverlayService("invasions")
            return
        }

        val builder = AlertDialog.Builder(requireContext())
        val inflater = requireActivity().layoutInflater
        val dialogView = inflater.inflate(R.layout.dialog_overlay_permission, null)

        val notNowButton = dialogView.findViewById<Button>(R.id.notNowButton)
        val openSettingsButton = dialogView.findViewById<Button>(R.id.openSettingsButton)

        builder.setView(dialogView)
        builder.setCancelable(false)
        val dialog = builder.create()

        notNowButton.setOnClickListener {
            dialog.dismiss()
        }

        openSettingsButton.setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:${requireContext().packageName}".toUri()
            )
            startActivity(intent)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun updateServiceButtonState(button: Button) {
        val overlayPermission = Settings.canDrawOverlays(requireContext())
        Log.d("PermissionStatus", "Overlay permission: $overlayPermission")

        if (!overlayPermission) {
            button.setText(R.string.enable_overlay_permissions)
        } else {
            button.setText(R.string.enable_overlay)
        }
    }
}