package com.mints.projectgammatwo.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.mints.projectgammatwo.R
import com.mints.projectgammatwo.helpers.OverlayServiceManager
import com.mints.projectgammatwo.recyclerviews.InvasionsAdapter
import com.mints.projectgammatwo.viewmodels.HomeViewModel

class HomeFragment : Fragment() {

    private val viewModel: HomeViewModel by viewModels()
    private lateinit var adapter: InvasionsAdapter
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var serviceManager: OverlayServiceManager
    private lateinit var scrollToTopFab: FloatingActionButton
    private lateinit var recyclerView: RecyclerView

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

        recyclerView = view.findViewById<RecyclerView>(R.id.invasionsRecyclerView)
        adapter = InvasionsAdapter { invasion ->
            viewModel.deleteInvasion(invasion)
        }
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(context)
        scrollToTopFab = view.findViewById(R.id.scrollToTopFab)
        setupScrollToTop()

        swipeRefresh = view.findViewById(R.id.swipeRefresh)
        swipeRefresh.setOnRefreshListener {
            viewModel.fetchInvasions()
        }

        // Observe invasions LiveData
        viewModel.invasions.observe(viewLifecycleOwner) { invasions ->
            adapter.submitList(invasions) {
                // This callback runs after submitList has completed
                swipeRefresh.isRefreshing = false

                // Force check FAB visibility after the list has been updated
                recyclerView.post {
                    checkAndUpdateFabVisibility()
                }
            }
        }

        // Observe error LiveData
        viewModel.error.observe(viewLifecycleOwner) { errorMessage ->
            Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
            swipeRefresh.isRefreshing = false
        }

        val deletedCountTextView = view.findViewById<TextView>(R.id.deletedCountText)
        viewModel.deletedCount.observe(viewLifecycleOwner) { count ->
            deletedCountTextView.text = "Battled in last 24h: $count"
        }

        val startServiceButton = view.findViewById<Button>(R.id.startServiceButton)
        startServiceButton.setOnClickListener {
            handleStartServiceClick()
        }

        viewModel.fetchInvasions()
        updateServiceButtonState(startServiceButton)
    }

    override fun onResume() {
        super.onResume()
        view?.findViewById<Button>(R.id.startServiceButton)?.let {
            updateServiceButtonState(it)
        }
        // Check FAB visibility when resuming (handles backgrounded app case)
        checkAndUpdateFabVisibility()
    }

    private fun setupScrollToTop() {
        // Show/hide FAB based on scroll position
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                checkAndUpdateFabVisibility()
            }
        })

        // Also monitor adapter data changes
        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                super.onItemRangeInserted(positionStart, itemCount)
                // Post to ensure RecyclerView has updated
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
        })

        // Handle FAB click
        scrollToTopFab.setOnClickListener {
            // Smooth scroll to top
            recyclerView.scrollToPosition(0)

        }
    }

    private fun checkAndUpdateFabVisibility() {
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return

        // Make sure adapter has items before checking positions
        if (adapter.itemCount == 0) {
            Log.d("FAB_DEBUG", "No items, hiding FAB")
            scrollToTopFab.hide()
            return
        }

        val firstVisibleItem = layoutManager.findFirstVisibleItemPosition()
        Log.d(
            "FAB_DEBUG",
            "First visible item: $firstVisibleItem, Total items: ${adapter.itemCount}"
        )

        // Only show FAB if we have valid position data
        if (firstVisibleItem != RecyclerView.NO_POSITION && firstVisibleItem > 2) {
            Log.d("FAB_DEBUG", "Showing FAB")
            scrollToTopFab.show()
        } else {
            Log.d("FAB_DEBUG", "Hiding FAB")
            scrollToTopFab.hide()
        }
    }

    private fun handleStartServiceClick() {
        // Check if we have overlay permission
        if (Settings.canDrawOverlays(requireContext())) {
            // We have permission, start service directly
            serviceManager.startOverlayService("quests")
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

        // Set up button click listeners
        notNowButton.setOnClickListener {
            dialog.dismiss()
        }

        openSettingsButton.setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${requireContext().packageName}")
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
            button.text = "Enable Overlay"
        } else {
            button.text = "Start Service"
        }
    }
}