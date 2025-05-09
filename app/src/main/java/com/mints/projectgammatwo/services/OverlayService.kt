// File: src/main/java/com/mints/projectgammatwo/services/OverlayService.kt
package com.mints.projectgammatwo.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationChannel
import android.app.NotificationManager
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
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Observer
import com.mints.projectgammatwo.R
import com.mints.projectgammatwo.data.CurrentInvasionData
import com.mints.projectgammatwo.data.Invasion
import com.mints.projectgammatwo.data.CurrentQuestData
import com.mints.projectgammatwo.data.CurrentQuestData.currentQuests
import com.mints.projectgammatwo.data.HomeCoordinatesManager
import com.mints.projectgammatwo.helpers.DragTouchListener
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

        if (dragHandle == null || closeBtn == null || rightBtn == null || leftBtn == null || homeBtn == null) {
            Log.e(TAG, "One or more buttons not found in layout")
            return
        }



        dragHandle.setOnTouchListener(DragTouchListener(params, windowManager, overlayView!!))
        closeBtn.setOnClickListener {
            Log.d(TAG, "Close button clicked")
            showOverlayMessage("Closing overlay")
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
                showOverlayMessage("Home coordinates not set")
            }
        }

        rightBtn.setOnClickListener {
            if (mode == "quests") {
                val currentQuests = CurrentQuestData.currentQuests
                if (currentQuests.isEmpty()) {
                    Log.d(TAG, "No quests available, attempting to fetch...")
                    showOverlayMessage("No quests available, fetching...")
                    fetchQuests()
                    return@setOnClickListener
                }
                currentIndex = (currentIndex + 1) % currentQuests.size
                Log.d(TAG, "Navigating to quest at index $currentIndex: ${currentQuests[currentIndex].lat}, ${currentQuests[currentIndex].lng}")
           //     showOverlayMessage("Quest ${currentIndex + 1}/${currentQuests.size}")
                Toast.makeText(this, "Teleporting to ${currentQuests[currentIndex].name}", Toast.LENGTH_SHORT).show()
                launchQuest(currentQuests[currentIndex])
            } else {
                val currentInvasions = CurrentInvasionData.currentInvasions
                if (currentInvasions.isEmpty()) {
                    Log.d(TAG, "No invasions available, attempting to fetch...")
                    showOverlayMessage("No invasions available, fetching...")
                    fetchInvasions()
                    return@setOnClickListener
                }
                currentIndex = (currentIndex + 1) % currentInvasions.size
                Log.d(TAG, "Navigating to invasion at index $currentIndex: ${currentInvasions[currentIndex].lat}, ${currentInvasions[currentIndex].lng}")
             //   showOverlayMessage("Invasion ${currentIndex + 1}/${currentInvasions.size}")
                Toast.makeText(this, "Teleporting to ${currentInvasions[currentIndex].characterName}", Toast.LENGTH_SHORT).show()

                launchMap(currentInvasions[currentIndex])
            }
        }

        leftBtn.setOnClickListener {
            if (mode == "quests") {
                val currentQuests = CurrentQuestData.currentQuests
                if (currentQuests.isEmpty()) {
                    Log.d(TAG, "No quests available, attempting to fetch...")
                    showOverlayMessage("No quests available, fetching...")
                    fetchQuests()
                    return@setOnClickListener
                }
                currentIndex = if (currentIndex - 1 < 0) currentQuests.size - 1 else currentIndex - 1
             //   showOverlayMessage("Quest ${currentIndex + 1}/${currentQuests.size}")

                Toast.makeText(this, "Teleporting to ${currentQuests[currentIndex].name}", Toast.LENGTH_SHORT).show()

                launchQuest(currentQuests[currentIndex])
            } else {
                val currentInvasions = CurrentInvasionData.currentInvasions
                if (currentInvasions.isEmpty()) {
                    Log.d(TAG, "No invasions available, attempting to fetch...")
                    showOverlayMessage("No invasions available, fetching...")
                    fetchInvasions()
                    return@setOnClickListener
                }
                currentIndex = if (currentIndex - 1 < 0) currentInvasions.size - 1 else currentIndex - 1
              //  showOverlayMessage("Invasion ${currentIndex + 1}/${currentInvasions.size}")
                Toast.makeText(this, "Teleporting to ${currentInvasions[currentIndex].characterName}", Toast.LENGTH_SHORT).show()
                launchMap(currentInvasions[currentIndex])
            }
        }
    }

    private fun updateOverlayBasedOnMode(mode: String) {
        if (mode == "quests") {
            showOverlayMessage("Updated: Quests mode")
        } else {
            showOverlayMessage("Updated: Invasions mode")
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
        showOverlayMessage("Fetching invasions...")
        cleanupObservers()
        viewModel = HomeViewModel(application)
        invasionsObserver = Observer { invasions ->
            Log.d(TAG, "Received ${invasions.size} invasions")
            CurrentInvasionData.currentInvasions = invasions.toMutableList()
            if (invasions.isNotEmpty()) {
                showOverlayMessage("Found ${invasions.size} invasions")
                currentIndex = 0
            } else {
                showOverlayMessage("No invasions found")
            }
            updateOverlayBasedOnMode("invasions")
        }
        errorObserver = Observer { errorMsg ->
            Log.e(TAG, "Error fetching invasions: $errorMsg")
            showOverlayMessage("Error: $errorMsg")
        }
        viewModel?.invasions?.observeForever(invasionsObserver!!)
        viewModel?.error?.observeForever(errorObserver!!)
        viewModel?.fetchInvasions()
    }


    private fun fetchQuests() {
        Log.d(TAG, "Fetching quests...")
        showOverlayMessage("Fetching quests...")

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
}