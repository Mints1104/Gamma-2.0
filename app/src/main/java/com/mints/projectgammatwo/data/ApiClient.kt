package com.mints.projectgammatwo.data

import com.mints.projectgammatwo.data.InvasionResponse
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET

object ApiClient {
    private const val BASE_URL = "https://nycpokemap.com"

    val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    interface PokeMapApi {
        @GET("/pokestop.php")
        suspend fun getInvasions(): InvasionResponse  // Changed return type to InvasionResponse
    }

    val api: PokeMapApi = retrofit.create(PokeMapApi::class.java)
}