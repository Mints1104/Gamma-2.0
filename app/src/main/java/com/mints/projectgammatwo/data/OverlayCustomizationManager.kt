package com.mints.projectgammatwo.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import androidx.core.content.edit

/**
 * Manages overlay customization preferences including button visibility, order, and size.
 *
 * Every read goes through the sanitizers below, so whatever is stored — including an empty
 * order or an out-of-range size written by an older import — comes back usable.
 */
class OverlayCustomizationManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("overlay_customization", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_BUTTON_ORDER = "button_order"
        private const val KEY_BUTTON_VISIBILITY = "button_visibility"
        private const val KEY_BUTTON_SIZE = "button_size"

        /** The size slider's range, in dp. */
        const val MIN_BUTTON_SIZE = 32
        const val MAX_BUTTON_SIZE = 64
        const val DEFAULT_BUTTON_SIZE = 48

        private const val DRAG_HANDLE = "drag_handle"

        /** Buttons that can't be hidden: without them the overlay can't be moved or closed. */
        private val ALWAYS_VISIBLE = setOf(DRAG_HANDLE, "close_button")

        // Default button order
        val DEFAULT_BUTTON_ORDER = listOf(
            DRAG_HANDLE,
            "close_button",
            "right_button",
            "left_button",
            "home_button",
            "refresh_button",
            "switch_modes",
            "filter_tab",
            "favorites_tab"
        )

        /** [size] clamped to the slider's range; the default when absent. */
        fun sanitizeButtonSize(size: Int?): Int =
            size?.coerceIn(MIN_BUTTON_SIZE, MAX_BUTTON_SIZE) ?: DEFAULT_BUTTON_SIZE

        /**
         * [order] reduced to known buttons, each once, with any missing ones appended in their
         * default place and the drag handle first. An empty order — what an import of a backup
         * without overlay settings used to save — left the customization list empty.
         */
        fun sanitizeButtonOrder(order: List<String?>?): List<String> {
            val known = order.orEmpty().filterNotNull().filter { it in DEFAULT_BUTTON_ORDER }.distinct()
            val complete = known + DEFAULT_BUTTON_ORDER.filter { it !in known }
            return listOf(DRAG_HANDLE) + complete.filter { it != DRAG_HANDLE }
        }

        /** A visibility for every known button: true unless [visibility] hides it, and never
         *  false for the drag handle or close button. Unknown keys are dropped. */
        fun sanitizeButtonVisibility(visibility: Map<String, Boolean?>?): Map<String, Boolean> =
            DEFAULT_BUTTON_ORDER.associateWith { id ->
                id in ALWAYS_VISIBLE || visibility?.get(id) != false
            }
    }

    /**
     * Get the current button order
     */
    fun getButtonOrder(): List<String> {
        val json = prefs.getString(KEY_BUTTON_ORDER, null) ?: return DEFAULT_BUTTON_ORDER
        val type = object : TypeToken<List<String?>>() {}.type
        val stored: List<String?>? = runCatching { gson.fromJson<List<String?>>(json, type) }.getOrNull()
        return sanitizeButtonOrder(stored)
    }

    /**
     * Save the button order
     */
    fun saveButtonOrder(order: List<String>) {
        val json = gson.toJson(sanitizeButtonOrder(order))
        prefs.edit { putString(KEY_BUTTON_ORDER, json) }
    }

    /**
     * Get button visibility settings
     */
    fun getButtonVisibility(): Map<String, Boolean> {
        val json = prefs.getString(KEY_BUTTON_VISIBILITY, null)
            ?: return sanitizeButtonVisibility(null)
        val type = object : TypeToken<Map<String, Boolean?>>() {}.type
        val stored: Map<String, Boolean?>? =
            runCatching { gson.fromJson<Map<String, Boolean?>>(json, type) }.getOrNull()
        return sanitizeButtonVisibility(stored)
    }

    /**
     * Save button visibility settings
     */
    fun saveButtonVisibility(visibility: Map<String, Boolean>) {
        val json = gson.toJson(sanitizeButtonVisibility(visibility))
        prefs.edit { putString(KEY_BUTTON_VISIBILITY, json) }
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
    fun getButtonSize(): Int = sanitizeButtonSize(prefs.getInt(KEY_BUTTON_SIZE, DEFAULT_BUTTON_SIZE))

    /**
     * Save the button size. Clamped: the slider's minimum isn't enforced below API 26, and an
     * imported backup could carry any number.
     */
    fun saveButtonSize(size: Int) {
        prefs.edit { putInt(KEY_BUTTON_SIZE, sanitizeButtonSize(size)) }
    }

    /**
     * Reset to default settings
     */
    fun resetToDefaults() {
        prefs.edit { clear() }
    }
}
