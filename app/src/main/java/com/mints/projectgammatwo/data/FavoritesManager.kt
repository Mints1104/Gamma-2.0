package com.mints.projectgammatwo.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import androidx.core.content.edit

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

        // Favorites saved before timezones existed (or imported from an older export) have no
        // timezoneId; fill it in so callers can render a local time straight away.
        ensureTimezones(loadedFavorites)

        return applyStoredOrder(loadedFavorites, originalOrder)
    }

    /**
     * Restores the user's manual arrangement, [order] being the saved list of names.
     *
     * Names missing from [order] sort to the end rather than the front — they are entries added
     * or imported since the order was written, and putting them first would silently reshuffle
     * the list the user arranged.
     *
     * Builds a rank map instead of calling [List.indexOf] from the sort selector: the selector
     * runs on every comparison, so indexOf made loading a large favorites/hotspots list
     * quadratic.
     */
    fun applyStoredOrder(
        favorites: List<FavoriteLocation>,
        order: List<String>
    ): List<FavoriteLocation> {
        if (order.isEmpty()) return favorites
        val rank = HashMap<String, Int>(order.size)
        // First occurrence wins, matching the old indexOf behaviour for duplicate names.
        order.forEachIndexed { index, name -> if (!rank.containsKey(name)) rank[name] = index }
        return favorites.sortedBy { rank[it.name] ?: Int.MAX_VALUE }
    }

    /**
     * Resolves [FavoriteLocation.timezoneId] for every entry that doesn't have one yet, in place.
     * Returns true if anything changed, so callers can persist the result once instead of
     * re-resolving on every load.
     */
    fun ensureTimezones(favorites: List<FavoriteLocation>): Boolean {
        var changed = false
        favorites.forEach { favorite ->
            if (favorite.timezoneId == null) {
                // Record the miss as UNKNOWN_ZONE rather than leaving it null, otherwise every
                // load would re-run the (fairly expensive) polygon lookup for ocean coordinates.
                favorite.timezoneId = FavoriteTimeFormatter.resolveZoneId(favorite.lat, favorite.lng)
                    ?: FavoriteTimeFormatter.UNKNOWN_ZONE
                changed = true
            }
        }
        return changed
    }

    fun saveFavorites(context: Context, favorites: List<FavoriteLocation>) {
        val prefs = context.getSharedPreferences(FAVORITES_PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit {

            // Save the full favorites list as JSON
            putString(KEY_FAVORITES, gson.toJson(favorites))

            // Save the order separately
            val originalOrder = favorites.map { it.name } // Store names as order reference
            putString(KEY_ORDER, gson.toJson(originalOrder))

        }
    }
}