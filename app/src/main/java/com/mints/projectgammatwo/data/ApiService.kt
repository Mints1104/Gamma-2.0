package com.mints.projectgammatwo.data

import retrofit2.http.GET

interface ApiService {
    @GET("/pokestop.php")
    suspend fun getInvasions(): InvasionResponse
}