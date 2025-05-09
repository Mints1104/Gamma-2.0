package com.mints.projectgammatwo.helpers

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import com.mints.projectgammatwo.services.OverlayService

/**
 * Helper class to manage overlay service permissions and starting
 */
class OverlayServiceManager(private val context: Context) {
    private val TAG = "OverlayServiceManager"

    /**
     * Start the overlay service
     */
    fun startOverlayService() {
        Log.d(TAG, "Starting OverlayService")
        val serviceIntent = Intent(context, OverlayService::class.java)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            Toast.makeText(context, "Overlay service started", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start service: ${e.message}", e)
            Toast.makeText(context, "Failed to start service: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Check if the overlay accessibility service is enabled
     */
    fun isOverlayServiceEnabled(): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)

        val componentName = ComponentName(context.packageName, "com.mints.projectgammatwo.services.OverlayService")
        val isEnabled = enabledServices.any {
            it.resolveInfo.serviceInfo.packageName == componentName.packageName &&
                    it.resolveInfo.serviceInfo.name == componentName.className
        }

        Log.d(TAG, "OverlayService enabled: $isEnabled")
        return isEnabled
    }
}