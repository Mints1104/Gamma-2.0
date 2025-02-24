package com.mints.projectgammatwo.data

import com.google.gson.annotations.SerializedName

class Quests {

    // Data model for a quest (matching the JSON exactly)
    data class Quest(
        val name: String,
        val lat: Double,
        val lng: Double,
        @SerializedName("rewards_string")
        val rewardsString: String,
        @SerializedName("conditions_string")
        val conditionsString: String,
        val image: String,
        @SerializedName("rewards_types")
        val rewardsTypes: String,
        @SerializedName("rewards_amounts")
        val rewardsAmounts: String,
        @SerializedName("rewards_ids")
        val rewardsIds: String,
        val source: String = ""  // New property; default is empty
    )


    data class QuestsResponse(
        val quests: List<Quest>,
        val meta: Meta,
        val filters: Filters
    )

    data class Meta(
        val time: Long
    )

    // Data model for filters returned by the API.
    data class Filters(
        val t3: List<String>,
        val t4: List<String>,
        val t2: List<String>,
        val t7: List<String>,
        val t12: List<String>
    )
}