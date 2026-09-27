package com.mints.projectgammatwo.helpers

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.core.net.toUri
import com.mints.projectgammatwo.R
import com.mints.projectgammatwo.data.DeeplinkManager

/**
 * The single entry point for every teleport action in the app — list items, favorites and the
 * overlay alike — so the teleport method chosen in Settings applies everywhere.
 *
 * "joystick" sends GPS Joystick its teleport intent; anything else opens the deeplink built by
 * [DeeplinkManager]. Both hand off to a third-party app that may not be installed, so failures
 * are reported with a toast instead of crashing.
 */
object Teleporter {
    private const val TAG = "Teleporter"

    private const val TELEPORT_PREFS_NAME = "teleport_prefs"
    private const val KEY_TELEPORT_METHOD = "teleport_method"
    private const val METHOD_JOYSTICK = "joystick"

    private const val JOYSTICK_ACTION = "theappninjas.gpsjoystick.TELEPORT"

    /** Known GPS Joystick builds; must stay in sync with the <queries> block in the manifest. */
    private val KNOWN_JOYSTICK_SERVICES = listOf(
        ComponentName(
            "com.theappninjas.fakegpsjoystick",
            "com.theappninjas.fakegpsjoystick.service.OverlayService"
        ),
        ComponentName(
            "com.thekkgqtaoxz.ymaaammipjyfatw",
            "com.thekkgqtaoxz.ymaaammipjyfatw.service.OverlayService"
        )
    )

    /**
     * Teleports to [lat]/[lng] using the user's chosen method.
     *
     * @return true if the teleport was handed off to another app. Callers that mark an item as
     * handled should only do so on success, so a failed teleport doesn't consume it.
     */
    fun teleport(context: Context, lat: Double, lng: Double): Boolean {
        val method = context.getSharedPreferences(TELEPORT_PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_TELEPORT_METHOD, null)
        return if (method == METHOD_JOYSTICK) {
            sendToJoystick(context, lat, lng)
        } else {
            openDeeplink(context, lat, lng)
        }
    }

    private fun openDeeplink(context: Context, lat: Double, lng: Double): Boolean {
        val url = DeeplinkManager.getInstance(context).generateDeeplink(lat, lng)
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        // Services and the application context have no task to launch into.
        if (context.findActivity() == null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            // Typically a custom deeplink scheme whose app isn't installed.
            Log.w(TAG, "No app can open teleport link: $url", e)
            Toast.makeText(context, R.string.teleport_failed_no_app, Toast.LENGTH_SHORT).show()
            false
        }
    }

    private fun sendToJoystick(context: Context, lat: Double, lng: Double): Boolean {
        val baseIntent = Intent(JOYSTICK_ACTION).apply {
            putExtra("lat", lat.toFloat())
            putExtra("lng", lng.toFloat())
        }

        // Try the known builds first, then fall back to anything that handles the action.
        val candidates = KNOWN_JOYSTICK_SERVICES + context.packageManager
            .queryIntentServices(baseIntent, 0)
            .map { ComponentName(it.serviceInfo.packageName, it.serviceInfo.name) }

        for (component in candidates.distinct()) {
            try {
                if (context.startService(Intent(baseIntent).setComponent(component)) != null) {
                    return true
                }
            } catch (e: Exception) {
                // Not installed, or refused to start (e.g. background start restrictions).
                Log.w(TAG, "Joystick service $component did not start", e)
            }
        }

        Toast.makeText(context, R.string.teleport_failed_joystick, Toast.LENGTH_SHORT).show()
        return false
    }

    private fun Context.findActivity(): Activity? {
        var current: Context = this
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }
}
