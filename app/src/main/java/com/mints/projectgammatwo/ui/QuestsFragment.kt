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
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.mints.projectgammatwo.R
import com.mints.projectgammatwo.data.Quests
import com.mints.projectgammatwo.data.VisitedQuestsPreferences
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
    private lateinit var scrollToTopFab: FloatingActionButton

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_quests, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        serviceManager = OverlayServiceManager(requireContext())
        swipeRefresh = view.findViewById(R.id.swipeRefresh)
        questsCountText = view.findViewById(R.id.questsCountText)
        recyclerView = view.findViewById(R.id.questsRecyclerView)
        questsAdapter = QuestsAdapter { quest: Quests.Quest ->
            questsViewModel.saveLastVisitedCoordinates(quest)

            val questId = "${quest.name}|${quest.lat}|${quest.lng}"
            val visitedPreferences = VisitedQuestsPreferences(requireContext())
            visitedPreferences.addVisitedQuest(questId)
            val currentList = questsAdapter.currentList.toMutableList()
            currentList.remove(quest)
            questsAdapter.submitList(currentList)
            updateQuestsCount(currentList.size)
        }

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = questsAdapter
        scrollToTopFab = view.findViewById(R.id.scrollToTopFab)
        setupScrollToTop()
        questsViewModel = ViewModelProvider(this)[QuestsViewModel::class.java]
        questsViewModel.questsLiveData.observe(viewLifecycleOwner) { quests: List<Quests.Quest> ->
            questsAdapter.submitList(quests)
            updateQuestsCount(quests.size)
            swipeRefresh.isRefreshing = false
            recyclerView.post {
                checkAndUpdateFabVisibility()
            }

        }

        questsViewModel.error.observe(viewLifecycleOwner) { errorMessage ->
            Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
            swipeRefresh.isRefreshing = false
        }


        swipeRefresh.setOnRefreshListener {
            questsViewModel.fetchQuests()
        }

        val startServiceButton = view.findViewById<Button>(R.id.startServiceButton)
        startServiceButton.setOnClickListener {
            handleStartServiceClick()
        }
        updateServiceButtonState(startServiceButton)

        swipeRefresh.isRefreshing = true
        questsViewModel.fetchQuests()
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
        questsAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
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
        if (questsAdapter.itemCount == 0) {
            scrollToTopFab.hide()
            return
        }

        val firstVisibleItem = layoutManager.findFirstVisibleItemPosition()


        if (firstVisibleItem != RecyclerView.NO_POSITION && firstVisibleItem > 2) {
            scrollToTopFab.show()
        } else {
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

    private fun updateQuestsCount(count: Int) {
        questsCountText.text = "Total Quests: $count"
    }

    override fun onResume() {
        super.onResume()
        view?.findViewById<Button>(R.id.startServiceButton)?.let {
            updateServiceButtonState(it)
        }
        checkAndUpdateFabVisibility()

    }
}
