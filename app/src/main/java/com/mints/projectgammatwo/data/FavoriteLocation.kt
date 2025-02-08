package com.mints.projectgammatwo.data

import java.io.Serializable

data class FavoriteLocation(
    var name: String,
    var lat: Double,
    var lng: Double
) : Serializable
