package com.mints.projectgammatwo.helpers

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
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
     * Check if the overlay accessibility service is enabled using a more reliable method
     */
    fun isOverlayServiceEnabled(): Boolean {
        // Method 1: Check via Settings Secure
        val serviceComponentName = ComponentName(context.packageName,
            "com.mints.projectgammatwo.services.OverlayService").flattenToString()

        val enabledServicesString = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )

        Log.d(TAG, "Enabled services: $enabledServicesString")
        Log.d(TAG, "Looking for service: $serviceComponentName")

        // Check if our service is in the enabled services string
        val isEnabledByString = enabledServicesString?.contains(serviceComponentName) ?: false

        // Method 2: Check via AccessibilityManager (as a backup)
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)

        val isEnabledByManager = enabledServices.any {
            val serviceName = it.resolveInfo.serviceInfo.name
            val packageName = it.resolveInfo.serviceInfo.packageName
            Log.d(TAG, "Found service: $packageName/$serviceName")
            packageName == context.packageName &&
                    serviceName == "com.mints.projectgammatwo.services.OverlayService"
        }

        Log.d(TAG, "Service enabled by string check: $isEnabledByString")
        Log.d(TAG, "Service enabled by manager check: $isEnabledByManager")

        // If either method confirms the service is enabled, return true
        return isEnabledByString || isEnabledByManager
    }

    /**
     * Utility method to check if accessibility is enabled for the app
     */
    fun isAccessibilityEnabled(): Boolean {
        var accessibilityEnabled = 0
        val serviceName = "${context.packageName}/com.mints.projectgammatwo.services.OverlayService"

        try {
            accessibilityEnabled = Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED
            )
        } catch (e: Settings.SettingNotFoundException) {
            Log.e(TAG, "Error finding setting, accessibility not enabled: ${e.message}")
        }

        val enabledServicesSetting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""

        Log.d(TAG, "Accessibility enabled: $accessibilityEnabled")
        Log.d(TAG, "Enabled services: $enabledServicesSetting")
        Log.d(TAG, "Looking for service: $serviceName")

        return accessibilityEnabled == 1 && enabledServicesSetting.contains(serviceName)
    }
}