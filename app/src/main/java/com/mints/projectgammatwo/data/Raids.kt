package com.mints.projectgammatwo.data

class Raids {

    data class Raid(
       val gym_name : String,
        val cell_id: String,
        val ex_raid_eligible: Int,
        val sponsor: Int,
        val lat: Double,
        val lng: Double,
        val raid_spawn: Int,
        val raid_start: Int,
        val raid_end: Int,
        val pokemon_id: Int,
        val level: Int,
        val cp: Int,
        val team: Int,
        val move1: Int,
        val move2: Int,
        val is_exclusive: Int,
        val form: Int,
        val gender: Int,
       val source: String
    )

    data class WeatherCell(
        val cellId: String,
        val weather: Int
    )

    data class RaidsResponse(
        val raids: List<Raid>,
        val battles: List<Any>,
        val weathers: List<WeatherCell>,
        val meta: Meta
    )

    data class Meta(
        val time: Long
    )
}