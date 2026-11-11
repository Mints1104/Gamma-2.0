package com.mints.projectgammatwo.data

/**
 * Nullable, element and list both: Gson ignores Kotlin nullability, so a response without the
 * list, or with a null entry in it, would otherwise put null where the type promises a value.
 */
data class InvasionResponse(
    val invasions: List<Invasion?>? = null
)
