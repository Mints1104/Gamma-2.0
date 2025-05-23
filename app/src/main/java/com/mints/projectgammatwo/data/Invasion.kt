package com.mints.projectgammatwo.data

data class Invasion(
    var name: String? = "Unnamed Invasion",
    val lat: Double,
    val lng: Double,
    val invasion_start: Long,
    val invasion_end: Long,
    var character: Int,
    val type: Int,
    val source: String

)
 {
    val characterName: String
        get() = DataMappings.characterNamesMap[character] ?: "Unknown Character"

    val typeDescription: String
        get() = DataMappings.typeDescriptionsMap[type] ?: "Unknown Type"
}