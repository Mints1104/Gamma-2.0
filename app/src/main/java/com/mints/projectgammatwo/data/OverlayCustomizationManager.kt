package com.mints.projectgammatwo.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Manages overlay customization preferences including button visibility, order, and size
 */
class OverlayCustomizationManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("overlay_customization", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_BUTTON_ORDER = "button_order"
        private const val KEY_BUTTON_VISIBILITY = "button_visibility"
        private const val KEY_BUTTON_SIZE = "button_size"

        // Default button order
        val DEFAULT_BUTTON_ORDER = listOf(
            "drag_handle",
            "close_button",
            "right_button",
            "left_button",
            "home_button",
            "refresh_button",
            "switch_modes",
            "filter_tab",
            "favorites_tab"
        )
    }

    /**
     * Get the current button order
     */
    fun getButtonOrder(): List<String> {
        val json = prefs.getString(KEY_BUTTON_ORDER, null) ?: return DEFAULT_BUTTON_ORDER
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(json, type)
    }

    /**
     * Save the button order
     */
    fun saveButtonOrder(order: List<String>) {
        val json = gson.toJson(order)
        prefs.edit().putString(KEY_BUTTON_ORDER, json).apply()
    }

    /**
     * Get button visibility settings
     */
    fun getButtonVisibility(): Map<String, Boolean> {
        val json = prefs.getString(KEY_BUTTON_VISIBILITY, null)
        if (json == null) {
            // Default: all buttons visible except drag_handle (always visible)
            return DEFAULT_BUTTON_ORDER.associateWith { true }
        }
        val type = object : TypeToken<Map<String, Boolean>>() {}.type
        return gson.fromJson(json, type)
    }

    /**
     * Save button visibility settings
     */
    fun saveButtonVisibility(visibility: Map<String, Boolean>) {
        val json = gson.toJson(visibility)
        prefs.edit().putString(KEY_BUTTON_VISIBILITY, json).apply()
    }

    /**
     * Set visibility for a specific button
     */
    fun setButtonVisibility(buttonId: String, visible: Boolean) {
        val currentVisibility = getButtonVisibility().toMutableMap()
        currentVisibility[buttonId] = visible
        saveButtonVisibility(currentVisibility)
    }

    /**
     * Get the button size (in dp)
     */
    fun getButtonSize(): Int {
        return prefs.getInt(KEY_BUTTON_SIZE, 48) // Default 48dp
    }

    /**
     * Save the button size
     */
    fun saveButtonSize(size: Int) {
        prefs.edit().putInt(KEY_BUTTON_SIZE, size).apply()
    }

    /**
     * Reset to default settings
     */
    fun resetToDefaults() {
        prefs.edit().clear().apply()
    }
}

