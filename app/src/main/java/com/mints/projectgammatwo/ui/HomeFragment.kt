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
import com.mints.projectgammatwo.R
import com.mints.projectgammatwo.helpers.OverlayServiceManager
import com.mints.projectgammatwo.services.OverlayService
import com.mints.projectgammatwo.viewmodels.HomeViewModel

class HomeFragment : Fragment() {

    private val viewModel: HomeViewModel by viewModels()
    private lateinit var adapter: InvasionsAdapter
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var serviceManager: OverlayServiceManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize service manager
        serviceManager = OverlayServiceManager(requireContext())

        // Initialize RecyclerView with the adapter that handles deletion
        val recyclerView = view.findViewById<RecyclerView>(R.id.invasionsRecyclerView)
        adapter = InvasionsAdapter { invasion ->
            viewModel.deleteInvasion(invasion)
        }
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(context)

        // Initialize SwipeRefreshLayout
        swipeRefresh = view.findViewById(R.id.swipeRefresh)
        swipeRefresh.setOnRefreshListener {
            viewModel.fetchInvasions()
        }

        // Observe invasions LiveData
        viewModel.invasions.observe(viewLifecycleOwner) { invasions ->
            adapter.submitList(invasions)
            swipeRefresh.isRefreshing = false
        }

        // Observe error LiveData
        viewModel.error.observe(viewLifecycleOwner) { errorMessage ->
            Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
            swipeRefresh.isRefreshing = false
        }

        // Observe deletion counter LiveData
        val deletedCountTextView = view.findViewById<TextView>(R.id.deletedCountText)
        viewModel.deletedCount.observe(viewLifecycleOwner) { count ->
            deletedCountTextView.text = "Battled in last 24h: $count"
        }

        // Set up service button
        val startServiceButton = view.findViewById<Button>(R.id.startServiceButton)
        startServiceButton.setOnClickListener {
            handleStartServiceClick()
        }

        // Initial data fetch
        viewModel.fetchInvasions()

        // Update the service button text based on current status
        updateServiceButtonState(startServiceButton)
    }

    override fun onResume() {
        super.onResume()
        // Update button state when returning to the fragment
        view?.findViewById<Button>(R.id.startServiceButton)?.let {
            updateServiceButtonState(it)
        }
    }


    private fun handleStartServiceClick() {
        // Check if we already have all permissions
        if (Settings.canDrawOverlays(requireContext()) &&
            serviceManager.isOverlayServiceEnabled()
        ) {
            // We have all permissions, start service directly
            serviceManager.startOverlayService()
            return
        }

        // We need to request permissions
        // Check overlay permission first
        if (!Settings.canDrawOverlays(requireContext())) {
            // Show overlay permission dialog and request
            AlertDialog.Builder(requireContext())
                .setTitle("Overlay Permission Required")
                .setMessage("This app needs the 'Display over other apps' permission to show overlays with Pokémon GO. Please enable this in the settings.")
                .setPositiveButton("Open Settings") { _, _ ->
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${requireContext().packageName}")
                    )
                    startActivity(intent)
                }
                .setNegativeButton("Not Now") { dialog, _ ->
                    dialog.dismiss()
                }
                .setCancelable(false)
                .show()
            return
        }

        // Then check accessibility service
        if (!serviceManager.isOverlayServiceEnabled()) {
            AlertDialog.Builder(requireContext())
                .setTitle("Accessibility Permission Required")
                .setMessage(
                    "This app needs Accessibility Service permission to function correctly. Please enable '${
                        getString(
                            R.string.app_name
                        )
                    } Overlay Service' in the Accessibility settings."
                )
                .setPositiveButton("Open Settings") { _, _ ->
                    openAccessibilitySettings()
                }
                .setNegativeButton("Not Now") { dialog, _ ->
                    dialog.dismiss()
                }
                .setCancelable(false)
                .show()
            return
        }
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
        Toast.makeText(
            requireContext(),
            "Please enable '${getString(R.string.app_name)} Overlay Service' in the list",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun updateServiceButtonState(button: Button) {
        val overlayPermission = Settings.canDrawOverlays(requireContext())
        val accessibilityEnabled = serviceManager.isOverlayServiceEnabled()

        // Add logging to debug permission issues
        Log.d("PermissionStatus", "Overlay permission: $overlayPermission")
        Log.d("PermissionStatus", "Accessibility enabled: $accessibilityEnabled")

        when {
            !overlayPermission && !accessibilityEnabled -> {
                button.text = "Enable Permissions"
            }

            !overlayPermission -> {
                button.text = "Enable Overlay"
            }

            !accessibilityEnabled -> {
                button.text = "Enable Accessibility"
            }

            else -> {
                button.text = "Start Service"
            }
        }
    }
}