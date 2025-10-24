package com.mints.projectgammatwo.ui

import android.content.Intent
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
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
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


class QuestsFragment : Fragment() {

    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var questsCountText: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var questsAdapter: QuestsAdapter
    private lateinit var questsViewModel: QuestsViewModel
    private lateinit var serviceManager: OverlayServiceManager
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

        serviceManager = OverlayServiceManager(requireContext())
        swipeRefresh = view.findViewById(R.id.swipeRefresh)
        questsCountText = view.findViewById(R.id.questsCountText)
        recyclerView = view.findViewById(R.id.questsRecyclerView)
        questErrorHandler = view.findViewById(R.id.errorHandlerText)
        questsAdapter = QuestsAdapter { quest: Quests.Quest ->
            questsViewModel.saveLastVisitedCoordinates(quest)

            val questId = "${quest.name}|${quest.lat}|${quest.lng}"
            val visitedPreferences = VisitedQuestsPreferences(requireContext())
            // Persist richer details for the deleted/visited UI
            visitedPreferences.addVisitedQuest(
                questId = questId,
                rewards = quest.rewardsString,
                conditions = quest.conditionsString,
                source = quest.source
            )
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

    private fun updateQuestsCount(count: Int) {
        questsCountText.text = getString(R.string.total_quests, count)
    }

    override fun onResume() {
        super.onResume()
        view?.findViewById<Button>(R.id.startServiceButton)?.let {
            updateServiceButtonState(it)
        }
        checkAndUpdateFabVisibility()

    }
}
