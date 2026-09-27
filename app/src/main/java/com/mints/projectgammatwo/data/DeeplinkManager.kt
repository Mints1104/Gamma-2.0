package com.mints.projectgammatwo.data

import android.content.Context
import androidx.core.content.edit
import java.math.BigDecimal

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

        private const val PLACEHOLDER = "%s"
        private const val IPOGO_FORMAT = "https://ipogo.app/?coords=$PLACEHOLDER"
        private const val POKEMOD_FORMAT = "https://pk.md/$PLACEHOLDER"

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
        // Double.toString switches to scientific notation below 0.001, which turns a spot near
        // the equator or prime meridian (a strip running through London) into "51.5,-7.47E-4".
        val coords = "${lat.toPlainString()},${lng.toPlainString()}"

        // Substitute the placeholder literally rather than via String.format: a user-supplied
        // template is a URL, and any percent-encoding in it ("%20", "%2C") or a literal "%" is
        // parsed as a format specifier and throws — crashing every teleport button.
        return when (getDeeplinkType()) {
            TYPE_POKEMOD -> POKEMOD_FORMAT.replace(PLACEHOLDER, coords)
            TYPE_CUSTOM -> {
                val customUrl = getCustomUrl()
                when {
                    customUrl.isEmpty() -> IPOGO_FORMAT.replace(PLACEHOLDER, coords)
                    PLACEHOLDER in customUrl -> customUrl.replace(PLACEHOLDER, coords)
                    // No placeholder: append coordinates at the end
                    else -> customUrl + coords
                }
            }
            else -> IPOGO_FORMAT.replace(PLACEHOLDER, coords)
        }
    }

    private fun Double.toPlainString(): String = BigDecimal.valueOf(this).toPlainString()
}
