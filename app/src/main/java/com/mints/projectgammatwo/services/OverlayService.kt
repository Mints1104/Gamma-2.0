package com.mints.projectgammatwo.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
import android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
import android.view.WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
import android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
import android.view.WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
import android.view.WindowManager.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mints.projectgammatwo.R
import com.mints.projectgammatwo.data.CurrentInvasionData
import com.mints.projectgammatwo.data.CurrentQuestData
import com.mints.projectgammatwo.data.DeletedInvasionsRepository
import com.mints.projectgammatwo.data.FavoritesManager
import com.mints.projectgammatwo.data.FilterPreferences
import com.mints.projectgammatwo.data.HomeCoordinatesManager
import com.mints.projectgammatwo.data.Invasion
import com.mints.projectgammatwo.data.OverlayCustomizationManager
import com.mints.projectgammatwo.helpers.DragTouchListener
import com.mints.projectgammatwo.helpers.ItemTouchHelperAdapter
import com.mints.projectgammatwo.helpers.ItemTouchHelperCallback
import com.mints.projectgammatwo.recyclerviews.FiltersRecyclerView
import com.mints.projectgammatwo.recyclerviews.OverlayFavoritesAdapter
import com.mints.projectgammatwo.recyclerviews.OverlayCustomizationAdapter
import com.mints.projectgammatwo.recyclerviews.OverlayButtonItem
import com.mints.projectgammatwo.viewmodels.HomeViewModel
import com.mints.projectgammatwo.viewmodels.QuestsViewModel

enum class FilterSortOrder {
    DEFAULT,
    NAME
}

private const val PREF_FILTER_SORT_ORDER = "filter_sort_order"

class OverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var currentIndex = 0
    private val TAG = "OverlayService"
    private var viewModel: HomeViewModel? = null
    private var invasionsObserver: Observer<List<Invasion>>? = null
    private var errorObserver: Observer<String>? = null
    private lateinit var homeCoordinatesManager: HomeCoordinatesManager
    private var favoritesOverlayView: View? = null
    private var filterOverlayView: View? = null
    private var isFavoritesVisible = false
    private var isFilterVisible = false
    private lateinit var favoritesAdapter: OverlayFavoritesAdapter
    private lateinit var filtersAdapter: FiltersRecyclerView
    private lateinit var filterPreferences: FilterPreferences
    private lateinit var deletedInvasionsRepository: DeletedInvasionsRepository
    private var currentMode = "invasions" // Default mode
    private var currentSortOrder = FilterSortOrder.DEFAULT
    private var currentX = 0
    private var currentY = 100
    private lateinit var dragTouchListener: DragTouchListener
    private var currentFavoritesSortOrder: FilterSortOrder = FilterSortOrder.DEFAULT
    private val PREF_FAVORITES_SORT_ORDER = "favorites_sort_order"
    private lateinit var customizationManager: OverlayCustomizationManager
    private var customizationOverlayView: View? = null
    private var isCustomizationVisible = false
    private lateinit var customizationAdapter: OverlayCustomizationAdapter
    private var itemTouchHelper: ItemTouchHelper? = null

    companion object {
        private const val TAG = "OverlayService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "overlay_service_channel"
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // Not a bound service
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Overlay Service",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "Running the Pokemon GO invasion overlay"
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
        homeCoordinatesManager = HomeCoordinatesManager.getInstance(this)
        filterPreferences = FilterPreferences(this)
        deletedInvasionsRepository = DeletedInvasionsRepository(this)
        customizationManager = OverlayCustomizationManager(this)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Invasion Overlay")
            .setContentText("Overlay service is running")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        startForeground(NOTIFICATION_ID, notification)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val favorites = FavoritesManager.getFavorites(this)
        if (favorites.isNotEmpty()) {
            Log.d(TAG, "Favorites loaded: ${favorites.size} items")
        } else {
            Log.d(TAG, "No favorites found")
        }
    }

    // Android 15 compatibility: Handle foreground service timeout
    override fun onTimeout(startId: Int, fgsType: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            Log.w(TAG, "Foreground service timeout reached for startId: $startId, fgsType: $fgsType")
            // Clean up resources and stop the service
            try {
                cleanupOverlays()
                stopSelf()
            } catch (e: Exception) {
                Log.e(TAG, "Error during timeout cleanup: ${e.message}")
                stopSelf() // Ensure we stop even if cleanup fails
            }
        }
    }

    private fun cleanupOverlays() {
        try {
            overlayView?.let {
                if (it.windowVisibility == View.VISIBLE) {
                    windowManager.removeView(it)
                }
                overlayView = null
            }
            favoritesOverlayView?.let {
                if (it.windowVisibility == View.VISIBLE) {
                    windowManager.removeView(it)
                }
                favoritesOverlayView = null
            }
            filterOverlayView?.let {
                if (it.windowVisibility == View.VISIBLE) {
                    windowManager.removeView(it)
                }
                filterOverlayView = null
            }
            cleanupObservers()
        } catch (e: Exception) {
            Log.e(TAG, "Error during overlay cleanup: ${e.message}")
        }
    }

    // Android 15 compatibility: Check if overlay is visible before starting foreground service
    private fun isOverlayVisible(): Boolean {
        return overlayView?.let { view ->
            view.windowVisibility == View.VISIBLE && view.visibility == View.VISIBLE
        } ?: false
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val mode = intent?.getStringExtra("mode").toString()
        Log.d(TAG, "Service onStartCommand with mode: $mode")

        // Android 15 compatibility: Ensure overlay is visible for SYSTEM_ALERT_WINDOW compliance
        if (overlayView == null) {
            addOverlay(mode)
            Log.d(TAG, "Overlay re-added on onStartCommand with mode: $mode")
        } else {
            updateOverlayBasedOnMode(mode)
            Log.d(TAG, "Overlay updated in onStartCommand with mode: $mode")
        }
        return START_NOT_STICKY
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
            x = currentX
            y = currentY
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
        val filtersButton = overlayView?.findViewById<ImageButton>(R.id.filter_tab)

        // Create DragTouchListener without long-press callback (no longer needed)
        dragTouchListener = DragTouchListener(params, windowManager, overlayView!!)

        switchModesBtn?.setImageResource(if (mode == "quests") R.drawable.binoculars else R.drawable.team_rocket_logo)
        if (dragHandle == null || closeBtn == null || rightBtn == null || leftBtn == null
            || homeBtn == null || refreshBtn == null ||
            switchModesBtn == null || favoritesButton == null || filtersButton == null) {
            Log.e(TAG, "One or more buttons not found in layout")
            return
        }

        dragHandle.setOnTouchListener(dragTouchListener)

        // Apply saved customization settings
        applyCustomizationToOverlay()

        closeBtn.setOnClickListener {
            Log.d(TAG, "Close button clicked")
            showOverlayToast("Closing overlay")
            try {
                if (overlayView != null) {
                    windowManager.removeView(overlayView)
                    overlayView = null
                }

                stopForeground(STOP_FOREGROUND_REMOVE)
                val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(NOTIFICATION_ID)

                stopSelf()
            } catch (e: Exception) {
                Log.e(TAG, "Error removing view: ${e.message}")
            }
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
                showOverlayToast("Switching to invasions mode")
                currentX = dragTouchListener.getCurrentParamsX()
                currentY = dragTouchListener.getCurrentParamsY()
                currentMode = "invasions"

                cleanupObservers()
                if (overlayView != null) {
                    windowManager.removeView(overlayView)
                    overlayView = null
                }
                addOverlay("invasions")
            } else {
                showOverlayToast("Switching to quests mode")
                Log.d(TAG, "Switching to quests mode")
                currentX = dragTouchListener.getCurrentParamsX()
                currentY = dragTouchListener.getCurrentParamsY()
                currentMode = "quests"

                cleanupObservers()
                if (overlayView != null) {
                    windowManager.removeView(overlayView)
                    overlayView = null
                }
                addOverlay("quests")
            }
        }

        filtersButton.setOnClickListener {
            showFiltersOverlay()
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
                deletedInvasionsRepository.addDeletedInvasion(currentInvasions[currentIndex])
                showOverlayToast("Teleporting to ${currentInvasions[currentIndex].characterName} \n Daily Limit: ${deletedInvasionsRepository.getDeletionCountLast24Hours()}/900")
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

        val questsViewModel = QuestsViewModel(application)
        questsViewModel.fetchQuests()
    }

    private fun launchHome(lat: Double, lng: Double) {
        Log.d(TAG, "Launching home with coords: $lat, $lng")
        val url = "https://ipogo.app/?coords=$lat,$lng"
        Intent(Intent.ACTION_VIEW, url.toUri())
            .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            .also(::startActivity)
    }

    private fun launchMap(inv: Invasion) {
        Log.d(TAG, "Launching map with coords: ${inv.lat}, ${inv.lng}")
        val url = "https://ipogo.app/?coords=${inv.lat},${inv.lng}"
        Intent(Intent.ACTION_VIEW, url.toUri())
            .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            .also(::startActivity)
    }

    private fun launchQuest(quest: com.mints.projectgammatwo.data.Quests.Quest) {
        Log.d(TAG, "Launching quest map with coords: ${quest.lat}, ${quest.lng}")
        val url = "https://ipogo.app/?coords=${quest.lat},${quest.lng}"
        Intent(Intent.ACTION_VIEW, url.toUri())
            .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            .also(::startActivity)
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

    private fun showOverlayToast(message: String) {
        val toastOverlayView = LayoutInflater.from(this).inflate(R.layout.overlay_toast, null)
        val toastText = toastOverlayView.findViewById<TextView>(R.id.toast_text)
        toastText.text = message

        val params = WindowManager.LayoutParams().apply {
            width = WRAP_CONTENT
            height = WRAP_CONTENT
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                TYPE_APPLICATION_OVERLAY
            else
                TYPE_SYSTEM_ALERT
            flags = FLAG_NOT_FOCUSABLE or
                    FLAG_NOT_TOUCH_MODAL
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
            }, 1500)  // Show for 3 seconds
        } catch (e: Exception) {
            Log.e(TAG, "Error showing toast overlay: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service onDestroy started")

        // Properly stop foreground service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(true)
        }

        // Cancel notification explicitly
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)

        cleanupObservers()
        try {
            if (overlayView != null) {
                windowManager.removeView(overlayView)
                overlayView = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error on destroy: ${e.message}")
        }
        Log.d(TAG, "Service destroyed completely")
    }

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
                hideFilterOverlay()
                val url = "https://ipogo.app/?coords=${favorite.lat},${favorite.lng}"
                showOverlayToast("Teleporting to ${favorite.name}")
                Intent(Intent.ACTION_VIEW, url.toUri())
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

    private fun setupFiltersOverlay() {
        // Inflate the filter overlay layout
        val contextThemeWrapper = ContextThemeWrapper(this, R.style.Theme_ProjectGamma2)
        filterOverlayView = LayoutInflater.from(contextThemeWrapper)
            .inflate(R.layout.item_filters_overlay, null)

        // Set up RecyclerView
        val recyclerView = filterOverlayView?.findViewById<RecyclerView>(R.id.filters_recycler_view)
        recyclerView?.layoutManager = LinearLayoutManager(this)

        // Initialize the adapter with a filter selection callback
        filtersAdapter = FiltersRecyclerView { filterName ->
            Log.d(TAG, "Filter selected: $filterName")
            showOverlayToast("Applying filter: $filterName")
            applyFilter(filterName)
        }

        recyclerView?.adapter = filtersAdapter

        // Set up close button
        filterOverlayView?.findViewById<ImageButton>(R.id.close_filter_button)?.setOnClickListener {
            hideFiltersOverlay()
        }
    }

    private fun setupOverflowMenu() {
        val overflowButton = filterOverlayView?.findViewById<ImageButton>(R.id.overflow_menu_button)

        overflowButton?.setOnClickListener { view ->
            val popupMenu = PopupMenu(this, view)
            popupMenu.menuInflater.inflate(R.menu.menu_filters_sort, popupMenu.menu)

            // Check the currently active sort method
            when (currentSortOrder) {
                FilterSortOrder.DEFAULT -> popupMenu.menu.findItem(R.id.sort_default).isChecked = true
                FilterSortOrder.NAME -> popupMenu.menu.findItem(R.id.sort_by_name).isChecked = true
            }

            popupMenu.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.sort_default -> {
                        if (currentSortOrder != FilterSortOrder.DEFAULT) {
                            currentSortOrder = FilterSortOrder.DEFAULT
                            saveSortOrderPreference(FilterSortOrder.DEFAULT)
                            loadFiltersByMode() // Reload filters with new sort order
                        }
                        true
                    }
                    R.id.sort_by_name -> {
                        if (currentSortOrder != FilterSortOrder.NAME) {
                            currentSortOrder = FilterSortOrder.NAME
                            saveSortOrderPreference(FilterSortOrder.NAME)
                            loadFiltersByMode() // Reload filters with new sort order
                        }
                        true
                    }
                    else -> false
                }
            }

            popupMenu.show()
        }
    }

    private fun saveSortOrderPreference(sortOrder: FilterSortOrder) {
        val sharedPrefs = getSharedPreferences("app_preferences", MODE_PRIVATE)
        sharedPrefs.edit { putString(PREF_FILTER_SORT_ORDER, sortOrder.name) }
    }

    private fun loadSortOrderPreference() {
        val sharedPrefs = getSharedPreferences("app_preferences", MODE_PRIVATE)
        val savedSortOrder = sharedPrefs.getString(PREF_FILTER_SORT_ORDER, FilterSortOrder.DEFAULT.name)
        currentSortOrder = try {
            FilterSortOrder.valueOf(savedSortOrder ?: FilterSortOrder.DEFAULT.name)
        } catch (e: IllegalArgumentException) {
            FilterSortOrder.DEFAULT
        }
    }


    private fun showFiltersOverlay() {
        if (filterOverlayView == null) {
            setupFiltersOverlay()
        }

        // Load saved sort order preferences
        loadSortOrderPreference()

        // Setup the overflow menu
        setupOverflowMenu()

        // Hide main overlay first
        overlayView?.visibility = View.GONE

        // Add filters overlay to window manager if not already added
        if (filterOverlayView?.parent == null) {
            val params = WindowManager.LayoutParams().apply {
                width = WRAP_CONTENT
                height = WRAP_CONTENT
                type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    TYPE_APPLICATION_OVERLAY
                else
                    TYPE_SYSTEM_ALERT
                flags = FLAG_NOT_FOCUSABLE
                format = PixelFormat.TRANSLUCENT
                gravity = Gravity.CENTER
            }

            val dragHandle = filterOverlayView?.findViewById<ImageButton>(R.id.drag_handle)
            dragHandle?.setOnTouchListener(DragTouchListener(params, windowManager, filterOverlayView!!))

            try {
                windowManager.addView(filterOverlayView, params)
                isFilterVisible = true
            } catch (e: Exception) {
                Log.e(TAG, "Error showing filters overlay: ${e.message}")
            }
        } else {
            // Just make it visible if already added
            filterOverlayView?.visibility = View.VISIBLE
            isFilterVisible = true
        }

        // Load the appropriate filter list based on current mode
        loadFiltersByMode()
    }

    private fun loadFiltersByMode() {
        val filterNames = if (currentMode == "quests") {
            // Load quest filter names
            filterPreferences.listQuestFilterNames()
        } else {
            // Load invasion/rocket filter names
            filterPreferences.listFilterNames()
        }

        // Apply sorting based on current sort order preference
        val sortedFilters = when (currentSortOrder) {
            FilterSortOrder.DEFAULT -> filterNames.toMutableList()  // Convert to MutableList
            FilterSortOrder.NAME -> filterNames.sortedBy { it.lowercase() }.toMutableList()  // Sort and convert to MutableList
        }

        // Update the adapter with the filter names
        filtersAdapter.submitList(sortedFilters)
    }

    private fun applyFilter(filterName: String) {
        val filterType = if (currentMode == "quests") "Quest" else "Rocket"

        // Apply the filter using the FilterPreferences
        filterPreferences.loadFilter(filterName, filterType)

        // Show a toast message
        showOverlayToast("Applied filter: $filterName")

        // Hide the filter overlay
        hideFiltersOverlay()

        // Refresh data based on selected filter
        if (currentMode == "quests") {
            fetchQuests()
        } else {
            fetchInvasions()
        }
    }

    private fun hideFiltersOverlay() {
        filterOverlayView?.visibility = View.GONE
        isFilterVisible = false

        // Show main overlay again
        overlayView?.visibility = View.VISIBLE
    }

    private fun showFavoritesOverlay() {
        if (favoritesOverlayView == null) {
            setupFavoritesOverlay()
        }

        // Load saved sort order preferences
        loadFavoritesSortOrderPreference()

        // Setup the overflow menu
        setupFavoritesOverflowMenu()

        // Hide main overlay first
        overlayView?.visibility = View.GONE

        // Add favorites overlay to window manager if not already added
        if (favoritesOverlayView?.parent == null) {
            val params = WindowManager.LayoutParams().apply {
                width = WRAP_CONTENT
                height = WRAP_CONTENT
                type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    TYPE_APPLICATION_OVERLAY
                else
                    TYPE_SYSTEM_ALERT
                flags = FLAG_NOT_FOCUSABLE
                format = PixelFormat.TRANSLUCENT
                gravity = Gravity.CENTER
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

        // Load favorites with the appropriate sort order
        loadFavoritesWithSort()
    }

    private fun setupFavoritesOverflowMenu() {
        val overflowButton = favoritesOverlayView?.findViewById<ImageButton>(R.id.overflow_menu_button)

        overflowButton?.setOnClickListener { view ->
            val popupMenu = PopupMenu(this, view)
            popupMenu.menuInflater.inflate(R.menu.overlay_favorites_menu, popupMenu.menu)

            // Check the currently active sort method
            when (currentFavoritesSortOrder) {
                FilterSortOrder.DEFAULT -> popupMenu.menu.findItem(R.id.sort_default).isChecked = true
                FilterSortOrder.NAME -> popupMenu.menu.findItem(R.id.sort_by_name).isChecked = true
            }

            popupMenu.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.sort_default -> {
                        if (currentFavoritesSortOrder != FilterSortOrder.DEFAULT) {
                            currentFavoritesSortOrder = FilterSortOrder.DEFAULT
                            saveFavoritesSortOrderPreference(FilterSortOrder.DEFAULT)
                            sortFavsByDefault()
                        }
                        true
                    }
                    R.id.sort_by_name -> {
                        if (currentFavoritesSortOrder != FilterSortOrder.NAME) {
                            currentFavoritesSortOrder = FilterSortOrder.NAME
                            saveFavoritesSortOrderPreference(FilterSortOrder.NAME)
                            sortFavsByName()
                        }
                        true
                    }
                    else -> false
                }
            }

            popupMenu.show()
        }
    }

    private fun saveFavoritesSortOrderPreference(sortOrder: FilterSortOrder) {
        val sharedPrefs = getSharedPreferences("app_preferences", MODE_PRIVATE)
        sharedPrefs.edit { putString(PREF_FAVORITES_SORT_ORDER, sortOrder.name) }
    }

    private fun loadFavoritesSortOrderPreference() {
        val sharedPrefs = getSharedPreferences("app_preferences", MODE_PRIVATE)
        val savedSortOrder = sharedPrefs.getString(PREF_FAVORITES_SORT_ORDER, FilterSortOrder.DEFAULT.name)
        currentFavoritesSortOrder = try {
            FilterSortOrder.valueOf(savedSortOrder ?: FilterSortOrder.DEFAULT.name)
        } catch (e: IllegalArgumentException) {
            FilterSortOrder.DEFAULT
        }
    }

    private fun sortFavsByName() {
        val favorites = FavoritesManager.getFavorites(this)
        val sortedList = favorites.sortedBy { it.name }
        favoritesAdapter.submitList(sortedList)
    }

    private fun sortFavsByDefault() {
        val favorites = FavoritesManager.getFavorites(this)
        favoritesAdapter.submitList(favorites)
    }

    private fun loadFavoritesWithSort() {
        val favorites = FavoritesManager.getFavorites(this)

        // Apply sorting based on current sort order preference
        val sortedFavorites = when (currentFavoritesSortOrder) {
            FilterSortOrder.DEFAULT -> favorites
            FilterSortOrder.NAME -> favorites.sortedBy { it.name }
        }

        favoritesAdapter.submitList(sortedFavorites)
    }

    private fun hideFavoritesOverlay() {
        favoritesOverlayView?.visibility = View.GONE
        isFavoritesVisible = false

        // Show main overlay again
        overlayView?.visibility = View.VISIBLE
    }

    private fun hideFilterOverlay() {
        filterOverlayView?.visibility = View.GONE
        isFilterVisible = false

        // Show main overlay again
        overlayView?.visibility = View.VISIBLE
    }

    private fun applyCustomizationToOverlay() {
        overlayView ?: return

        val buttonSize = customizationManager.getButtonSize()
        val buttonVisibility = customizationManager.getButtonVisibility()
        val buttonOrder = customizationManager.getButtonOrder()

        // Convert dp to pixels
        val buttonSizePx = (buttonSize * resources.displayMetrics.density).toInt()

        // Apply size and visibility to all buttons
        val buttonMap = mapOf(
            "drag_handle" to R.id.drag_handle,
            "close_button" to R.id.close_button,
            "right_button" to R.id.right_button,
            "left_button" to R.id.left_button,
            "home_button" to R.id.home_button,
            "refresh_button" to R.id.refresh_button,
            "switch_modes" to R.id.switch_modes,
            "filter_tab" to R.id.filter_tab,
            "favorites_tab" to R.id.favorites_tab
        )

        // Reorder buttons vertically based on saved order
        var topMargin = 0
        buttonOrder.forEach { buttonId ->
            val buttonViewId = buttonMap[buttonId]
            if (buttonViewId != null) {
                val button = overlayView?.findViewById<ImageButton>(buttonViewId)
                button?.let {
                    // Set size
                    val layoutParams = it.layoutParams as android.widget.FrameLayout.LayoutParams
                    layoutParams.width = buttonSizePx
                    layoutParams.height = buttonSizePx
                    layoutParams.topMargin = topMargin
                    it.layoutParams = layoutParams

                    // Set visibility
                    val isVisible = buttonVisibility[buttonId] ?: true
                    it.visibility = if (isVisible) View.VISIBLE else View.GONE

                    // Increment top margin for next button (only if visible)
                    if (isVisible) {
                        topMargin += buttonSizePx + (2 * resources.displayMetrics.density).toInt() // 2dp spacing
                    }
                }
            }
        }
    }

    private fun getButtonDisplayName(buttonId: String): String {
        return when (buttonId) {
            "drag_handle" -> "Drag Handle"
            "close_button" -> "Close"
            "right_button" -> "Next"
            "left_button" -> "Previous"
            "home_button" -> "Home"
            "refresh_button" -> "Refresh"
            "switch_modes" -> "Switch Mode"
            "filter_tab" -> "Filters"
            "favorites_tab" -> "Favorites"
            else -> buttonId
        }
    }

    private fun getButtonIcon(buttonId: String): Int {
        return when (buttonId) {
            "drag_handle" -> R.drawable.ic_drag_handle_overlay
            "close_button" -> R.drawable.close_24px
            "right_button" -> R.drawable.arrow_right_24px
            "left_button" -> R.drawable.arrow_left_24px
            "home_button" -> R.drawable.home_24px
            "refresh_button" -> R.drawable.refresh_24px
            "switch_modes" -> R.drawable.team_rocket_logo
            "filter_tab" -> R.drawable.tune_24px
            "favorites_tab" -> R.drawable.ic_favorite
            else -> R.drawable.ic_launcher_foreground
        }
    }


}
