package com.mints.projectgammatwo.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import com.mints.projectgammatwo.data.DataMappings.PokemonResponse

class PokemonRepository(private val context: Context) {
    private val TAG = "PokemonRepository"
    private val PREFS_NAME = "pokemon_cache"
    private val POKEMON_DATA_KEY = "pokemon_map"
    private val LAST_UPDATED_KEY = "last_updated"

    // Cache expiration time (30 days in milliseconds)
    private val CACHE_EXPIRATION = 30L * 24 * 60 * 60 * 1000

    private val sharedPrefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val gson = Gson()

    // In-memory cache
    private var pokemonMap: Map<String, String>? = null

    /**
     * Fetch Pokémon data with caching
     * @param forceRefresh Force a refresh from the network
     * @param callback Callback with the Pokémon map
     */
    fun getPokemonData(forceRefresh: Boolean = false, callback: (Map<String, String>) -> Unit) {
        // Check in-memory cache first
        pokemonMap?.let {
            if (!forceRefresh) {
                Log.d(TAG, "Using in-memory cache with ${it.size} Pokémon")
                callback(it)
                return
            }
        }

        // Check if we have valid cached data in SharedPreferences
        if (!forceRefresh && isCacheValid()) {
            val cachedData = loadFromPreferences()
            if (cachedData.isNotEmpty()) {
                Log.d(TAG, "Using SharedPreferences cache with ${cachedData.size} Pokémon")
                pokemonMap = cachedData
                callback(cachedData)
                return
            }
        }

        // No valid cache, fetch from network
        fetchFromNetwork { networkData ->
            if (networkData.isNotEmpty()) {
                // Save to both in-memory and SharedPreferences cache
                pokemonMap = networkData
                saveToPreferences(networkData)
                callback(networkData)
            } else {
                // Network fetch failed, try to use expired cache as fallback
                val cachedData = loadFromPreferences()
                if (cachedData.isNotEmpty()) {
                    Log.d(TAG, "Network failed, using expired cache as fallback")
                    pokemonMap = cachedData
                    callback(cachedData)
                } else {
                    // No cache available, return empty map
                    Log.e(TAG, "No data available - network failed and no cache")
                    callback(emptyMap())
                }
            }
        }
    }

    private fun fetchFromNetwork(callback: (Map<String, String>) -> Unit) {
        Log.d(TAG, "Fetching Pokémon data from network")

        val api = DataMappings.RetrofitInstance.api
        val pokemonMap = mutableMapOf<String, String>()

        fun fetchPage(offset: Int) {
            api.getPokemon(limit = 100, offset = offset).enqueue(object : Callback<PokemonResponse> {
                override fun onResponse(call: Call<PokemonResponse>, response: Response<PokemonResponse>) {
                    if (response.isSuccessful) {
                        val results = response.body()?.results ?: emptyList()

                        results.forEach { pokemon ->
                            val id = pokemon.url.split("/").dropLast(1).last()
                            val name = pokemon.name.capitalize()
                            pokemonMap[id] = name
                        }

                        Log.d(TAG, "Fetched ${results.size} Pokémon (Total: ${pokemonMap.size})")

                        // If we received a full page, fetch the next page
                        if (results.isNotEmpty()) {
                            fetchPage(offset + 100) // Adjust page size if needed
                        } else {
                            // No more pages, return the result
                            Log.d(TAG, "Finished fetching all Pokémon: ${pokemonMap.size} total")
                            callback(pokemonMap)
                            saveToPreferences(pokemonMap) // Save cache
                        }
                    } else {
                        Log.e(TAG, "Network error: ${response.code()}")
                        callback(emptyMap())
                    }
                }

                override fun onFailure(call: Call<PokemonResponse>, t: Throwable) {
                    Log.e(TAG, "Network request failed: ${t.message}")
                    callback(emptyMap())
                }
            })
        }

        // Start fetching from the first page
        fetchPage(0)
    }

    private fun saveToPreferences(data: Map<String, String>) {
        try {
            val json = gson.toJson(data)
            sharedPrefs.edit()
                .putString(POKEMON_DATA_KEY, json)
                .putLong(LAST_UPDATED_KEY, System.currentTimeMillis())
                .apply()

            Log.d(TAG, "Saved ${data.size} Pokémon to SharedPreferences")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving to SharedPreferences: ${e.message}")
        }
    }

    private fun loadFromPreferences(): Map<String, String> {
        try {
            val json = sharedPrefs.getString(POKEMON_DATA_KEY, null) ?: return emptyMap()
            val type = object : TypeToken<Map<String, String>>() {}.type
            val data: Map<String, String> = gson.fromJson(json, type)

            Log.d(TAG, "Loaded ${data.size} Pokémon from SharedPreferences")
            return data
        } catch (e: Exception) {
            Log.e(TAG, "Error loading from SharedPreferences: ${e.message}")
            return emptyMap()
        }
    }

    private fun isCacheValid(): Boolean {
        val lastUpdated = sharedPrefs.getLong(LAST_UPDATED_KEY, 0)
        val isValid = lastUpdated > 0 && System.currentTimeMillis() - lastUpdated < CACHE_EXPIRATION

        Log.d(TAG, "Cache validity check: $isValid (age: ${(System.currentTimeMillis() - lastUpdated) / 1000 / 60} minutes)")
        return isValid
    }

    /**
     * Clear all cached data
     */
    fun clearCache() {
        pokemonMap = null
        sharedPrefs.edit().clear().apply()
        Log.d(TAG, "Cache cleared")
    }
}