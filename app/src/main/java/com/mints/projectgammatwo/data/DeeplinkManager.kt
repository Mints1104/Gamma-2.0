package com.mints.projectgammatwo.data

import android.content.Context
import androidx.core.content.edit

/**
 * Manages deeplink preferences for teleporting to coordinates.
 * Supports iPogo, Pokemod, and custom deeplink formats.
 */
class DeeplinkManager private constructor(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "deeplink_prefs"
        private const val KEY_DEEPLINK_TYPE = "deeplink_type"
        private const val KEY_CUSTOM_URL = "custom_url"

        const val TYPE_IPOGO = "ipogo"
        const val TYPE_POKEMOD = "pokemod"
        const val TYPE_CUSTOM = "custom"

        private const val IPOGO_FORMAT = "https://ipogo.app/?coords=%s"
        private const val POKEMOD_FORMAT = "https://pk.md/%s"

        @Volatile
        private var instance: DeeplinkManager? = null

        fun getInstance(context: Context): DeeplinkManager {
            return instance ?: synchronized(this) {
                instance ?: DeeplinkManager(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * Get the current deeplink type (ipogo, pokemod, or custom)
     */
    fun getDeeplinkType(): String {
        return prefs.getString(KEY_DEEPLINK_TYPE, TYPE_IPOGO) ?: TYPE_IPOGO
    }

    /**
     * Set the deeplink type
     */
    fun setDeeplinkType(type: String) {
        prefs.edit {
            putString(KEY_DEEPLINK_TYPE, type)
        }
    }

    /**
     * Get the custom URL template
     */
    fun getCustomUrl(): String {
        return prefs.getString(KEY_CUSTOM_URL, "") ?: ""
    }

    /**
     * Set the custom URL template
     * Should contain %s placeholder for coordinates (e.g., "https://example.com/?coords=%s")
     */
    fun setCustomUrl(url: String) {
        prefs.edit {
            putString(KEY_CUSTOM_URL, url)
        }
    }

    /**
     * Generate a deeplink URL for the given coordinates
     * @param lat Latitude
     * @param lng Longitude
     * @return The formatted deeplink URL
     */
    fun generateDeeplink(lat: Double, lng: Double): String {
        val coords = "$lat,$lng"

        return when (getDeeplinkType()) {
            TYPE_IPOGO -> String.format(IPOGO_FORMAT, coords)
            TYPE_POKEMOD -> String.format(POKEMOD_FORMAT, coords)
            TYPE_CUSTOM -> {
                val customUrl = getCustomUrl()
                if (customUrl.isNotEmpty() && customUrl.contains("%s")) {
                    String.format(customUrl, coords)
                } else if (customUrl.isNotEmpty()) {
                    // If no placeholder, append coordinates at the end
                    "$customUrl$coords"
                } else {
                    // Fallback to iPogo if custom URL is empty
                    String.format(IPOGO_FORMAT, coords)
                }
            }
            else -> String.format(IPOGO_FORMAT, coords)
        }
    }
}
