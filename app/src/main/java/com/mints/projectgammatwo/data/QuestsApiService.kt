package com.mints.projectgammatwo.data

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Suspend functions, not blocking Calls: cancelling the coroutine cancels the HTTP request. With
 * `Call.execute()` a superseded quest fetch kept every request running to completion.
 */
interface QuestsApiService {
    @GET("quests.php")
    suspend fun getQuests(
        @Query("quests[]") quests: List<String>,
        @Query("time") timestamp: Long
    ): Response<Quests.QuestsResponse>

    @GET("quests.php")
    suspend fun getAllQuests(
        @Query("time") timestamp: Long
    ): Response<Quests.QuestsResponse>
}
