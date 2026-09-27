package com.mints.projectgammatwo.data

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object ApiClient {
    val DATA_SOURCE_URLS = mapOf(
        "NYC" to "https://nycpokemap.com",
        "LONDON" to "https://londonpogomap.com",
        "Singapore" to "https://sgpokemap.com/",
        "VANCOUVER" to "https://vanpokemap.com/",
        "SYDNEY" to "https://sydneypogomap.com/"
    )

    /**
     * The one HTTP client for the whole app, so every request shares a connection pool and
     * dispatcher. Invasion fetches used to build a new client — and with it a new pool and
     * thread pool — on every call, while quests and raids each kept their own.
     *
     * 30s timeouts: the quest endpoints are slow, and the invasion fetch's former 10s default
     * was the odd one out.
     */
    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    // Concurrent: the fetches build these from several IO threads at once. The view models'
    // previous plain HashMap caches were written concurrently from their async blocks.
    private val retrofits = ConcurrentHashMap<String, Retrofit>()

    private fun retrofitFor(baseUrl: String): Retrofit = retrofits.getOrPut(baseUrl) {
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    fun getApiForBaseUrl(baseUrl: String): InvasionApi =
        retrofitFor(baseUrl).create(InvasionApi::class.java)

    fun questsApi(baseUrl: String): QuestsApiService =
        retrofitFor(baseUrl).create(QuestsApiService::class.java)

    fun raidsApi(baseUrl: String): RaidApiService =
        retrofitFor(baseUrl).create(RaidApiService::class.java)
}
