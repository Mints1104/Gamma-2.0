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
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.mints.projectgammatwo.R
import com.mints.projectgammatwo.data.Quests
import com.mints.projectgammatwo.helpers.OverlayServiceManager
import com.mints.projectgammatwo.recyclerviews.QuestsAdapter
import com.mints.projectgammatwo.viewmodels.QuestsViewModel
import com.mints.projectgammatwo.services.OverlayService


class QuestsFragment : Fragment() {

    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var questsCountText: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var questsAdapter: QuestsAdapter
    private lateinit var questsViewModel: QuestsViewModel
    private lateinit var serviceManager: OverlayServiceManager

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate layout containing a header TextView, SwipeRefreshLayout, and RecyclerView
        return inflater.inflate(R.layout.fragment_quests, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        serviceManager = OverlayServiceManager(requireContext())
        swipeRefresh = view.findViewById(R.id.swipeRefresh)
        questsCountText = view.findViewById(R.id.questsCountText)
        recyclerView = view.findViewById(R.id.questsRecyclerView)

        // Initialize adapter with onDelete callback preserving your exact deletion logic.
        questsAdapter = QuestsAdapter { quest: Quests.Quest ->
            questsViewModel.saveLastVisitedCoordinates(quest)

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

        val startServiceButton = view.findViewById<Button>(R.id.startServiceButton)
        startServiceButton.setOnClickListener {
            handleStartServiceClick()
        }

        // Load quests initially.
        swipeRefresh.isRefreshing = true
        questsViewModel.fetchQuests()
    }

    // In src/main/java/com/mints/projectgammatwo/ui/QuestsFragment.kt
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

    private fun updateQuestsCount(count: Int) {
        questsCountText.text = "Total Quests: $count"
    }
}
