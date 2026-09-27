package com.mints.projectgammatwo.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import com.mints.projectgammatwo.data.DataMappings.PokemonResponse
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Pokémon id → name mapping from PokeAPI, cached in memory and in SharedPreferences.
 *
 * One instance per process ([getInstance]) so the in-memory cache actually gets reused: it was
 * previously constructed per call, so every visit to the filter screen re-parsed the stored map.
 * Disk reads and writes happen on a background thread; callbacks are delivered on the main
 * thread. Call [getPokemonData] from the main thread.
 */
class PokemonRepository private constructor(private val context: Context) {
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

    private val io = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    // In-memory cache
    private var pokemonMap: Map<String, String>? = null

    // Main thread only. Callers waiting on a load already in progress, so that overlapping
    // requests share one disk read / network fetch instead of each starting their own.
    private val pendingCallbacks = mutableListOf<(Map<String, String>) -> Unit>()
    private var loading = false

    companion object {
        /**
         * PokeAPI page size. Large enough for every Pokémon at once (1,351 at the time of
         * writing, ~93 KB); previously 100, which took 15 sequential requests.
         */
        private const val PAGE_SIZE = 2000

        // Holds only the application context (see getInstance), which lives as long as the
        // process anyway, so a static reference can't leak an Activity.
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: PokemonRepository? = null

        fun getInstance(context: Context): PokemonRepository =
            instance ?: synchronized(this) {
                instance ?: PokemonRepository(context.applicationContext).also { instance = it }
            }
    }

    /**
     * Fetch Pokémon data with caching
     * @param forceRefresh Force a refresh from the network
     * @param callback Callback with the Pokémon map, delivered on the main thread
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

        pendingCallbacks += callback
        if (loading) return
        loading = true

        io.execute {
            // Check if we have valid cached data in SharedPreferences
            val cachedData = if (!forceRefresh && isCacheValid()) loadFromPreferences() else emptyMap()
            mainHandler.post {
                if (cachedData.isNotEmpty()) {
                    Log.d(TAG, "Using SharedPreferences cache with ${cachedData.size} Pokémon")
                    finish(cachedData)
                } else {
                    // No valid cache, fetch from network
                    fetchFromNetwork { networkData ->
                        if (networkData.isNotEmpty()) {
                            io.execute { saveToPreferences(networkData) }
                            finish(networkData)
                        } else {
                            // Network fetch failed, try to use expired cache as fallback
                            io.execute {
                                val expired = loadFromPreferences()
                                mainHandler.post {
                                    if (expired.isNotEmpty()) {
                                        Log.d(TAG, "Network failed, using expired cache as fallback")
                                    } else {
                                        Log.e(TAG, "No data available - network failed and no cache")
                                    }
                                    finish(expired)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** Main thread: delivers [data] to everyone waiting on this load. */
    private fun finish(data: Map<String, String>) {
        if (data.isNotEmpty()) pokemonMap = data
        loading = false
        val callbacks = pendingCallbacks.toList()
        pendingCallbacks.clear()
        callbacks.forEach { it(data) }
    }

    private fun fetchFromNetwork(callback: (Map<String, String>) -> Unit) {
        Log.d(TAG, "Fetching Pokémon data from network")

        val api = DataMappings.RetrofitInstance.api
        val pokemonMap = mutableMapOf<String, String>()

        fun fetchPage(offset: Int) {
            api.getPokemon(limit = PAGE_SIZE, offset = offset).enqueue(object : Callback<PokemonResponse> {
                override fun onResponse(call: Call<PokemonResponse>, response: Response<PokemonResponse>) {
                    if (response.isSuccessful) {
                        val results = response.body()?.results ?: emptyList()

                        results.forEach { pokemon ->
                            val id = pokemon.url.split("/").dropLast(1).last()
                            val name = pokemon.name.replaceFirstChar { it.titlecase(Locale.ROOT) }
                            pokemonMap[id] = name
                        }

                        Log.d(TAG, "Fetched ${results.size} Pokémon (Total: ${pokemonMap.size})")

                        // A full page means there may be more; a short one is the last.
                        if (results.size == PAGE_SIZE) {
                            fetchPage(offset + PAGE_SIZE)
                        } else {
                            Log.d(TAG, "Finished fetching all Pokémon: ${pokemonMap.size} total")
                            callback(pokemonMap)
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
        io.execute { sharedPrefs.edit { clear() } }
        Log.d(TAG, "Cache cleared")
    }
}
