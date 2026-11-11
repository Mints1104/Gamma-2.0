package com.mints.projectgammatwo.data

import com.google.gson.annotations.SerializedName

class Quests {

    /** A quest as the rest of the app uses it: every field present. Built by [RawQuest.toQuest]. */
    data class Quest(
        val name: String,
        val lat: Double,
        val lng: Double,
        val rewardsString: String,
        val conditionsString: String,
        val image: String,
        val rewardsTypes: String,
        val rewardsAmounts: String,
        val rewardsIds: String,
        val source: String = ""
    )

    /**
     * A quest exactly as the API sends it. Every field is nullable because Gson ignores Kotlin's
     * nullability: a quest missing one of them used to come back with null in a non-null field,
     * and the first `.split` on it threw, failing the fetch for every source at once.
     */
    data class RawQuest(
        val name: String? = null,
        val lat: Double? = null,
        val lng: Double? = null,
        @SerializedName("rewards_string")
        val rewardsString: String? = null,
        @SerializedName("conditions_string")
        val conditionsString: String? = null,
        val image: String? = null,
        @SerializedName("rewards_types")
        val rewardsTypes: String? = null,
        @SerializedName("rewards_amounts")
        val rewardsAmounts: String? = null,
        @SerializedName("rewards_ids")
        val rewardsIds: String? = null,
    ) {
        /**
         * Null when the quest can't be listed or matched against filters: no name, no position,
         * or no reward to match. Display-only text that's missing becomes empty.
         */
        fun toQuest(source: String = ""): Quest? {
            if (name.isNullOrBlank() || lat == null || lng == null) return null
            if (rewardsTypes.isNullOrBlank() || rewardsIds == null || rewardsAmounts == null) return null
            return Quest(
                name = name,
                lat = lat,
                lng = lng,
                rewardsString = rewardsString.orEmpty(),
                conditionsString = conditionsString.orEmpty(),
                image = image.orEmpty(),
                rewardsTypes = rewardsTypes,
                rewardsAmounts = rewardsAmounts,
                rewardsIds = rewardsIds,
                source = source,
            )
        }
    }

    // Nullable throughout, for the same reason as RawQuest: Gson leaves anything absent null.
    data class QuestsResponse(
        val quests: List<RawQuest?>? = null,
        val meta: Meta? = null,
        val filters: Filters? = null
    ) {
        /** The usable quests, tagged with [source]; malformed entries are dropped, not fatal. */
        fun validQuests(source: String = ""): List<Quest> =
            quests.orEmpty().mapNotNull { it?.toQuest(source) }
    }

    data class Meta(
        val time: Long? = null
    )

    /**
     * Also read back from [QuestCache], a Gson re-serialization of this very model. Every section
     * is nullable: one an API response (or a cache written by an older version) lacks is simply
     * absent, and Gson ignores Kotlin default values. A missing t8 once crashed the quest filter
     * screen; reading the sections through the non-null accessors below keeps that from
     * recurring for the others.
     */
    data class Filters(
        val t3: List<String?>? = null,
        val t4: List<String?>? = null,
        val t2: List<String?>? = null,
        val t7: List<String?>? = null,
        val t8: List<String?>? = null,
        val t9: List<String?>? = null,
        val t12: List<String?>? = null
    ) {
        val stardust: List<String> get() = t3.clean()
        val pokecoins: List<String> get() = t8.clean()
        val candy: List<String> get() = t4.clean()
        val candyXl: List<String> get() = t9.clean()
        val megaEnergy: List<String> get() = t12.clean()
        val encounters: List<String> get() = t7.clean()
        val items: List<String> get() = t2.clean()

        private fun List<String?>?.clean(): List<String> = orEmpty().filterNotNull().filter { it.isNotBlank() }
    }
}
