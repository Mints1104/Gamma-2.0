package com.mints.projectgammatwo.data

import retrofit2.http.GET

interface InvasionApi {
    // This endpoint should return a response containing a list of invasions.
    @GET("/pokestop.php")
    suspend fun getInvasions(): InvasionResponse
}
