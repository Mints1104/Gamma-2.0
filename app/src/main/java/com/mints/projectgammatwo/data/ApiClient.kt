package com.mints.projectgammatwo.data

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {
    val DATA_SOURCE_URLS = mapOf(
        "NYC" to "https://nycpokemap.com",
        "LONDON" to "https://londonpogomap.com",
        "Singapore" to "https://sgpokemap.com/",
        "VANCOUVER" to "https://vanpokemap.com/",
        "SYDNEY" to "https://sydneypogomap.com/"
    )

    fun getApiForBaseUrl(baseUrl: String): InvasionApi {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(InvasionApi::class.java)
    }
}
