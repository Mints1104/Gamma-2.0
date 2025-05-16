package com.mints.projectgammatwo.helpers

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
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
     * Check if we have overlay permission
     */
    fun hasOverlayPermission(): Boolean {
        return Settings.canDrawOverlays(context)
    }

    /**
     * Open system settings to request overlay permission
     */
    fun openOverlayPermissionSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}