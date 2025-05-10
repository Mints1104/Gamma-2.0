// File: src/main/java/com/mints/projectgammatwo/services/OverlayService.kt
package com.mints.projectgammatwo.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.*
import android.view.WindowManager.LayoutParams.*
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mints.projectgammatwo.R
import com.mints.projectgammatwo.data.CurrentInvasionData
import com.mints.projectgammatwo.data.Invasion
import com.mints.projectgammatwo.data.CurrentQuestData
import com.mints.projectgammatwo.data.FavoritesManager
import com.mints.projectgammatwo.data.HomeCoordinatesManager
import com.mints.projectgammatwo.helpers.DragTouchListener
import com.mints.projectgammatwo.recyclerviews.FavoritesAdapter
import com.mints.projectgammatwo.recyclerviews.OverlayFavoritesAdapter
import com.mints.projectgammatwo.viewmodels.HomeViewModel
import com.mints.projectgammatwo.viewmodels.QuestsViewModel

class OverlayService : AccessibilityService() {
    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var currentIndex = 0
    private val TAG = "OverlayService"
    private var viewModel: HomeViewModel? = null
    private var invasionsObserver: Observer<List<Invasion>>? = null
    private var errorObserver: Observer<String>? = null
    private lateinit var homeCoordinatesManager: HomeCoordinatesManager
    private var favoritesOverlayView: View? = null
    private var isFavoritesVisible = false
    private lateinit var favoritesAdapter: OverlayFavoritesAdapter

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "overlay_service_channel",
                "Overlay Service",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "Running the Pokemon GO invasion overlay"
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
        homeCoordinatesManager = HomeCoordinatesManager.getInstance(this)

        val notification = NotificationCompat.Builder(this, "overlay_service_channel")
            .setContentTitle("Invasion Overlay")
            .setContentText("Overlay service is running")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        startForeground(1001, notification)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val favorites = FavoritesManager.getFavorites(this)
        if (favorites.isNotEmpty()) {
            Log.d(TAG, "Favorites loaded: ${favorites.size} items")
        } else {
            Log.d(TAG, "No favorites found")
        }

    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "AccessibilityService connected")
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        serviceInfo = info
        addOverlay("invasions")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val mode = intent?.getStringExtra("overlay_mode") ?: "invasions"
        if (overlayView == null) {
            addOverlay(mode)
            Log.d(TAG, "Overlay re-added on onStartCommand with mode: $mode")
        } else {
            updateOverlayBasedOnMode(mode)
            Log.d(TAG, "Overlay updated in onStartCommand with mode: $mode")
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun addOverlay(mode: String) {
        if (overlayView != null) return
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_layout, null)
        val params = WindowManager.LayoutParams().apply {
            width = WRAP_CONTENT
            height = WRAP_CONTENT
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                TYPE_APPLICATION_OVERLAY
            else
                TYPE_SYSTEM_ALERT
            flags = FLAG_NOT_TOUCH_MODAL or FLAG_NOT_FOCUSABLE or FLAG_WATCH_OUTSIDE_TOUCH
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 100
        }
        try {
            windowManager.addView(overlayView, params)
            Log.d(TAG, "Overlay added successfully with mode: $mode")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding overlay: ${e.message}")
            return
        }
        setupButtons(mode, params)
        if (mode == "quests") {
            if (CurrentQuestData.currentQuests.isEmpty())
                fetchQuests()
        } else {
            val invasions = CurrentInvasionData.currentInvasions
            if (invasions.isEmpty())
                fetchInvasions()
        }
    }

    private fun setupButtons(mode: String, params: WindowManager.LayoutParams) {
        val dragHandle = overlayView?.findViewById<ImageButton>(R.id.drag_handle)
        val closeBtn = overlayView?.findViewById<ImageButton>(R.id.close_button)
        val rightBtn = overlayView?.findViewById<ImageButton>(R.id.right_button)
        val leftBtn = overlayView?.findViewById<ImageButton>(R.id.left_button)
        val homeBtn = overlayView?.findViewById<ImageButton>(R.id.home_button)
        val refreshBtn = overlayView?.findViewById<ImageButton>(R.id.refresh_button)
        val switchModesBtn = overlayView?.findViewById<ImageButton>(R.id.switch_modes)
        val favoritesButton = overlayView?.findViewById<ImageButton>(R.id.favorites_tab)
        switchModesBtn?.setImageResource(if (mode == "quests") R.drawable.binoculars else R.drawable.team_rocket_logo)
        if (dragHandle == null || closeBtn == null || rightBtn == null || leftBtn == null || homeBtn == null || refreshBtn == null || switchModesBtn == null || favoritesButton == null) {
            Log.e(TAG, "One or more buttons not found in layout")
            return
        }



        dragHandle.setOnTouchListener(DragTouchListener(params, windowManager, overlayView!!))
        closeBtn.setOnClickListener {
            Log.d(TAG, "Close button clicked")
            showOverlayToast("Closing overlay")
            try {
                if (overlayView != null) {
                    windowManager.removeView(overlayView)
                    overlayView = null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error removing view: ${e.message}")
            }
            stopSelf()
        }
        homeBtn.setOnClickListener {
            val coordinates = homeCoordinatesManager.getHomeCoordinates()
            if(coordinates != null) {
                val(latitude, longitude) = coordinates
                launchHome(latitude,longitude)
            } else {
                Log.d(TAG, "Home coordinates not set")
                showOverlayToast("Home coordinates not set")
            }
        }

        refreshBtn.setOnClickListener {
            if (mode == "quests") {
                Log.d(TAG, "Refresh button clicked in quests mode")
                showOverlayToast("Refreshing quests...")
                fetchQuests()
            } else {
                Log.d(TAG, "Refresh button clicked in invasions mode")
                showOverlayToast("Refreshing invasions...")
                fetchInvasions()
            }
        }

        switchModesBtn.setOnClickListener {
            if (mode == "quests") {
                Log.d(TAG, "Switching to invasions mode")

                cleanupObservers()
                if (overlayView != null) {
                    windowManager.removeView(overlayView)
                    overlayView = null
                }
                addOverlay("invasions")
            } else {
                Log.d(TAG, "Switching to quests mode")
                cleanupObservers()
                if (overlayView != null) {
                    windowManager.removeView(overlayView)
                    overlayView = null
                }
                addOverlay("quests")
            }
        }

        favoritesButton?.setOnClickListener {
            showFavoritesOverlay()
        }


        rightBtn.setOnClickListener {
            if (mode == "quests") {
                val currentQuests = CurrentQuestData.currentQuests
                if (currentQuests.isEmpty()) {
                    Log.d(TAG, "No quests available, attempting to fetch...")
                    showOverlayToast("No quests available, fetching...")
                    fetchQuests()
                    return@setOnClickListener
                }
                currentIndex = (currentIndex + 1) % currentQuests.size
                Log.d(TAG, "Navigating to quest at index $currentIndex: ${currentQuests[currentIndex].lat}, ${currentQuests[currentIndex].lng}")
                showOverlayToast( "Teleporting to ${currentQuests[currentIndex].rewardsString}")
                launchQuest(currentQuests[currentIndex])
            } else {
                val currentInvasions = CurrentInvasionData.currentInvasions
                if (currentInvasions.isEmpty()) {
                    Log.d(TAG, "No invasions available, attempting to fetch...")
                    fetchInvasions()
                    return@setOnClickListener
                }
                currentIndex = (currentIndex + 1) % currentInvasions.size
                Log.d(TAG, "Navigating to invasion at index $currentIndex: ${currentInvasions[currentIndex].lat}, ${currentInvasions[currentIndex].lng}")
                showOverlayToast("Teleporting to ${currentInvasions[currentIndex].characterName}")
                launchMap(currentInvasions[currentIndex])
            }
        }

        leftBtn.setOnClickListener {
            if (mode == "quests") {
                val currentQuests = CurrentQuestData.currentQuests
                if (currentQuests.isEmpty()) {
                    Log.d(TAG, "No quests available, attempting to fetch...")
                    showOverlayToast("No quests available, fetching...")
                    fetchQuests()
                    return@setOnClickListener
                }
                currentIndex = if (currentIndex - 1 < 0) currentQuests.size - 1 else currentIndex - 1
                showOverlayToast("Teleporting to ${currentQuests[currentIndex].name}")
                launchQuest(currentQuests[currentIndex])
            } else {
                val currentInvasions = CurrentInvasionData.currentInvasions
                if (currentInvasions.isEmpty()) {
                    Log.d(TAG, "No invasions available, attempting to fetch...")
                    showOverlayToast("No invasions available, fetching...")
                    fetchInvasions()
                    return@setOnClickListener
                }
                currentIndex = if (currentIndex - 1 < 0) currentInvasions.size - 1 else currentIndex - 1
                showOverlayToast("Teleporting to ${currentInvasions[currentIndex].characterName}")
                launchMap(currentInvasions[currentIndex])
            }
        }
    }

    private fun updateOverlayBasedOnMode(mode: String) {
        if (mode == "quests") {
            showOverlayToast("Updated: Quests mode")
        } else {
            showOverlayToast("Updated: Invasions mode")
        }
    }

    private fun showOverlayMessage(message: String) {
        val statusText = overlayView?.findViewById<TextView>(R.id.status_text)
        if (statusText != null) {
            statusText.text = message
            statusText.visibility = View.VISIBLE
            Handler(Looper.getMainLooper()).postDelayed({
                statusText.visibility = View.GONE
            }, 3000)
        } else {
            Log.e(TAG, "Status text view not found, falling back to toast")
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun fetchInvasions() {
        Log.d(TAG, "Fetching invasions...")
        showOverlayToast("Fetching invasions...")
        cleanupObservers()
        viewModel = HomeViewModel(application)
        invasionsObserver = Observer { invasions ->
            Log.d(TAG, "Received ${invasions.size} invasions")
            CurrentInvasionData.currentInvasions = invasions.toMutableList()
            if (invasions.isNotEmpty()) {
                showOverlayToast("Found ${invasions.size} invasions")
                currentIndex = 0
            } else {
                showOverlayToast("No invasions found")
            }
            updateOverlayBasedOnMode("invasions")
        }
        errorObserver = Observer { errorMsg ->
            Log.e(TAG, "Error fetching invasions: $errorMsg")
            showOverlayToast("Error: $errorMsg")
        }
        viewModel?.invasions?.observeForever(invasionsObserver!!)
        viewModel?.error?.observeForever(errorObserver!!)
        viewModel?.fetchInvasions()
    }


    private fun fetchQuests() {
        Log.d(TAG, "Fetching quests...")
        showOverlayToast("Fetching quests...")

        val questsViewModel = QuestsViewModel(application)
        questsViewModel.fetchQuests()

    }

    private fun launchHome(lat: Double, lng: Double) {
        Log.d(TAG, "Launching home with coords: $lat, $lng")
        val url = "https://ipogo.app/?coords=$lat,$lng"
        Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            .also(::startActivity)
    }

    private fun launchMap(inv: Invasion) {
        Log.d(TAG, "Launching map with coords: ${inv.lat}, ${inv.lng}")
        val url = "https://ipogo.app/?coords=${inv.lat},${inv.lng}"
        Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            .also(::startActivity)
    }

    private fun launchQuest(quest: com.mints.projectgammatwo.data.Quests.Quest) {
        Log.d(TAG, "Launching quest map with coords: ${quest.lat}, ${quest.lng}")
        val url = "https://ipogo.app/?coords=${quest.lat},${quest.lng}"
        Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            .also(::startActivity)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    }

    private fun cleanupObservers() {
        invasionsObserver?.let { observer ->
            viewModel?.invasions?.removeObserver(observer)
        }
        errorObserver?.let { observer ->
            viewModel?.error?.removeObserver(observer)
        }
        invasionsObserver = null
        errorObserver = null
        viewModel = null
    }

    override fun onInterrupt() {
        // No-op
    }

    private fun showOverlayToast(message: String) {
        val toastOverlayView = LayoutInflater.from(this).inflate(R.layout.overlay_toast, null)
        val toastText = toastOverlayView.findViewById<TextView>(R.id.toast_text)
        toastText.text = message

        val params = WindowManager.LayoutParams().apply {
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 100  // Distance from bottom
        }

        try {
            windowManager.addView(toastOverlayView, params)
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    windowManager.removeView(toastOverlayView)
                } catch (e: Exception) {
                    Log.e(TAG, "Error removing toast overlay: ${e.message}")
                }
            }, 3000)  // Show for 3 seconds
        } catch (e: Exception) {
            Log.e(TAG, "Error showing toast overlay: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        cleanupObservers()
        try {
            if (overlayView != null) {
                windowManager.removeView(overlayView)
                overlayView = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error on destroy: ${e.message}")
        }
    }



    // Add this function to your OverlayService
    private fun setupFavoritesOverlay() {
        // Inflate the favorites overlay layout
        val contextThemeWrapper = ContextThemeWrapper(this, R.style.Theme_ProjectGamma2)

        favoritesOverlayView = LayoutInflater.from(contextThemeWrapper)
            .inflate(R.layout.favorites_overlay_layout, null)
        // Set up RecyclerView
        val recyclerView = favoritesOverlayView?.findViewById<RecyclerView>(R.id.favorites_recycler_view)
        recyclerView?.layoutManager = LinearLayoutManager(this)

        // Initialize adapter
        favoritesAdapter = OverlayFavoritesAdapter(
            onTeleportFavorite = { favorite ->
                // Teleport to location
                hideFavoritesOverlay()
                val url = "https://ipogo.app/?coords=${favorite.lat},${favorite.lng}"
                showOverlayToast("Teleporting to ${favorite.name}")
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    .also(::startActivity)
            }
        )

        recyclerView?.adapter = favoritesAdapter

        // Set up close button
        favoritesOverlayView?.findViewById<ImageButton>(R.id.close_favorites_button)?.setOnClickListener {
            hideFavoritesOverlay()
        }



        // Load favorites
        val favorites = FavoritesManager.getFavorites(this)
        favoritesAdapter.submitList(favorites)
    }

    private fun showFavoritesOverlay() {
        if (favoritesOverlayView == null) {
            setupFavoritesOverlay()
        }

        // Hide main overlay first
        overlayView?.visibility = View.GONE

        // Add favorites overlay to window manager if not already added
        if (favoritesOverlayView?.parent == null) {
            val params = WindowManager.LayoutParams().apply {
                width = WindowManager.LayoutParams.WRAP_CONTENT
                height = WindowManager.LayoutParams.WRAP_CONTENT
                type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                format = PixelFormat.TRANSLUCENT
                gravity = Gravity.TOP
            }
            val dragHandle = favoritesOverlayView?.findViewById<ImageButton>(R.id.drag_handle)
            dragHandle?.setOnTouchListener(DragTouchListener(params, windowManager, favoritesOverlayView!!))

            try {
                windowManager.addView(favoritesOverlayView, params)
                isFavoritesVisible = true
            } catch (e: Exception) {
                Log.e(TAG, "Error showing favorites overlay: ${e.message}")
            }
        } else {
            // Just make it visible if already added
            favoritesOverlayView?.visibility = View.VISIBLE
            isFavoritesVisible = true
        }

        // Refresh the favorites list
        val favorites = FavoritesManager.getFavorites(this)
        favoritesAdapter.submitList(favorites)
    }

    private fun hideFavoritesOverlay() {
        favoritesOverlayView?.visibility = View.GONE
        isFavoritesVisible = false

        // Show main overlay again
        overlayView?.visibility = View.VISIBLE
    }

}