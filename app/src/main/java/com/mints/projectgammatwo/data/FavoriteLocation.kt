package com.mints.projectgammatwo.data

import java.io.Serializable

@kotlinx.serialization.Serializable
data class FavoriteLocation(
    var name: String,
    var lat: Double,
    var lng: Double,
    // IANA timezone resolved from lat/lng, cached so it isn't recomputed on every bind.
    // null = not resolved yet, or the coordinates fall outside any zone (ocean/poles).
    // Must keep a default: old exports and old saved JSON don't carry this field.
    var timezoneId: String? = null
) : Serializable
