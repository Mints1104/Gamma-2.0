package com.mints.projectgammatwo.data

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface QuestsApiService {
    @GET("quests.php")
    fun getQuests(
        @Query("quests[]") quests: List<String>,
        @Query("time") timestamp: Long
    ): Call<Quests.QuestsResponse>
}