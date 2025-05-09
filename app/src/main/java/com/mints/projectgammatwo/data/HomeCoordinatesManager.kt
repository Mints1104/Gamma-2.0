package com.mints.projectgammatwo.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Utility class for managing home coordinates throughout the application.
 * Provides methods to get, set, and validate coordinates from any class.
 */
class HomeCoordinatesManager(private val context: Context) {

    companion object {
        const val HOME_COORDS_PREFS_NAME = "home_coords_prefs"
        const val KEY_HOME_COORDS = "home_coordinates"

        // Singleton instance
        @Volatile
        private var INSTANCE: HomeCoordinatesManager? = null

        fun getInstance(context: Context): HomeCoordinatesManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: HomeCoordinatesManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences(HOME_COORDS_PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Returns the saved home coordinates as a string in format "lat, lng"
     * @return String representation of coordinates or empty string if not set
     */
    fun getHomeCoordinatesString(): String {
        return sharedPreferences.getString(KEY_HOME_COORDS, "") ?: ""
    }

    /**
     * Returns the saved home coordinates as a Pair of Doubles
     * @return Pair<Double, Double> where first is latitude and second is longitude,
     *         or null if coordinates are not set or invalid
     */
    fun getHomeCoordinates(): Pair<Double, Double>? {
        val coordsString = getHomeCoordinatesString()
        if (coordsString.isEmpty()) return null

        return try {
            val parts = coordsString.split(",").map { it.trim() }
            if (parts.size != 2) return null

            val lat = parts[0].toDouble()
            val lng = parts[1].toDouble()

            // Validate coordinates are in valid range
            if (lat < -90 || lat > 90 || lng < -180 || lng > 180) return null

            Pair(lat, lng)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Saves new home coordinates
     * @param coordinates String in format "lat, lng"
     * @return Boolean indicating if coordinates were successfully saved
     */
    fun saveHomeCoordinates(coordinates: String): Boolean {
        val isValid = validateCoordinates(coordinates)
        if (isValid || coordinates.isEmpty()) {
            sharedPreferences.edit().putString(KEY_HOME_COORDS, coordinates).apply()
            return true
        }
        return false
    }

    /**
     * Saves home coordinates as latitude and longitude values
     * @param latitude Double latitude value (-90 to 90)
     * @param longitude Double longitude value (-180 to 180)
     * @return Boolean indicating if coordinates were successfully saved
     */
    fun saveHomeCoordinates(latitude: Double, longitude: Double): Boolean {
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            return false
        }

        val coordString = "$latitude, $longitude"
        sharedPreferences.edit().putString(KEY_HOME_COORDS, coordString).apply()
        return true
    }

    /**
     * Validates if the given string is a valid coordinate format
     * @param coordinates String to validate
     * @return Boolean indicating if coordinates are valid
     */
    fun validateCoordinates(coordinates: String): Boolean {
        if (coordinates.isEmpty()) return true

        return try {
            val parts = coordinates.split(",").map { it.trim() }
            if (parts.size != 2) return false

            val lat = parts[0].toDouble()
            val lng = parts[1].toDouble()

            lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if home coordinates have been set
     * @return Boolean indicating if coordinates exist
     */
    fun hasHomeCoordinates(): Boolean {
        return getHomeCoordinates() != null
    }

    /**
     * Clears saved home coordinates
     */
    fun clearHomeCoordinates() {
        sharedPreferences.edit().remove(KEY_HOME_COORDS).apply()
    }
}