package com.mints.projectgammatwo.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object FavoritesManager {
    private const val FAVORITES_PREFS_NAME = "favorites_prefs"
    private const val KEY_FAVORITES = "favorites_list"
    private const val KEY_ORDER = "favorites_order"
    private val gson = Gson()

    fun getFavorites(context: Context): List<FavoriteLocation> {
        val prefs = context.getSharedPreferences(FAVORITES_PREFS_NAME, Context.MODE_PRIVATE)

        // Load the full favorites list
        val json = prefs.getString(KEY_FAVORITES, "[]")
        val type = object : TypeToken<List<FavoriteLocation>>() {}.type
        val loadedFavorites: List<FavoriteLocation> = gson.fromJson(json, type) ?: mutableListOf()

        // Load the original order of names
        val orderJson = prefs.getString(KEY_ORDER, "[]")
        val orderType = object : TypeToken<List<String>>() {}.type
        val originalOrder: List<String> = gson.fromJson(orderJson, orderType) ?: emptyList()

        // Reorder the loadedFavorites to match the original order
        return if (originalOrder.isNotEmpty()) {
            loadedFavorites.sortedBy { originalOrder.indexOf(it.name) }
        } else {
            loadedFavorites
        }
    }

    fun saveFavorites(context: Context, favorites: List<FavoriteLocation>) {
        val prefs = context.getSharedPreferences(FAVORITES_PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()

        // Save the full favorites list as JSON
        editor.putString(KEY_FAVORITES, gson.toJson(favorites))

        // Save the order separately
        val originalOrder = favorites.map { it.name } // Store names as order reference
        editor.putString(KEY_ORDER, gson.toJson(originalOrder))

        editor.apply()
    }

    fun teleportToLocation(context: Context, favorite: FavoriteLocation): Boolean {
        // Check teleport preferences
        val teleportPrefs = context.getSharedPreferences("teleport_prefs", Context.MODE_PRIVATE)
        val method = teleportPrefs.getString("teleport_method", "ipogo") ?: "ipogo"

        if (method == "ipogo") {
            val url = "https://ipogo.app/?coords=${favorite.lat},${favorite.lng}"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(intent)
            return true
        }

        // GPS Joystick implementation
        val baseIntent = Intent().apply {
            action = "theappninjas.gpsjoystick.TELEPORT"
            putExtra("lat", favorite.lat.toFloat())
            putExtra("lng", favorite.lng.toFloat())
        }

        val knownComponents = listOf(
            ComponentName(
                "com.theappninjas.fakegpsjoystick",
                "com.theappninjas.fakegpsjoystick.service.OverlayService"
            ),
            ComponentName(
                "com.thekkgqtaoxz.ymaaammipjyfatw",
                "com.thekkgqtaoxz.ymaaammipjyfatw.service.OverlayService"
            )
        )

        var serviceStarted = false
        for (component in knownComponents) {
            val intent = Intent(baseIntent).apply { this.component = component }
            try {
                val compName = context.startService(intent)
                if (compName != null) {
                    serviceStarted = true
                    break
                }
            } catch (e: Exception) {
                // Try next component
            }
        }

        if (!serviceStarted) {
            val dynamicIntent = Intent(baseIntent).apply { component = null }
            val pm = context.packageManager
            val services = pm.queryIntentServices(dynamicIntent, 0)
            if (services.isNotEmpty()) {
                val serviceInfo = services.first().serviceInfo
                dynamicIntent.component = ComponentName(serviceInfo.packageName, serviceInfo.name)
                try {
                    val compName = context.startService(dynamicIntent)
                    serviceStarted = (compName != null)
                } catch (e: Exception) {
                    // dynamic lookup failed
                }
            }
        }

        return serviceStarted
    }
}