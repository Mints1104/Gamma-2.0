package com.mints.projectgammatwo.data

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface RaidApiService {
    @GET("raids.php")
    fun getRaids(
        @Query("time") timestamp: Long
    ): Call<Raids.RaidsResponse>
}