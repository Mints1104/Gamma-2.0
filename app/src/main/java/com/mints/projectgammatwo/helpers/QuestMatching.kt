package com.mints.projectgammatwo.helpers

import com.mints.projectgammatwo.data.Quests.Quest

/**
 * Key for one quest variant, "type|id|amount|condition|reward". The filter screen stores these in
 * enabledEncounterConditions and the quest fetch matches against them, so both must build them
 * here, with the same format.
 */
fun questConditionKey(type: String, id: String, amount: String, condition: String, reward: String): String =
    "$type|$id|$amount|$condition|$reward"

/** The pre-reward-label form of [questConditionKey], still found in older saved selections. */
fun legacyQuestConditionKey(type: String, id: String, amount: String, condition: String): String =
    "$type|$id|$amount|$condition"

/**
 * The (type, id) a base filter string selects, e.g. "4,0,483" -> ("4", "483"). Stardust (3) and
 * Pokécoins (8) carry their discriminator in the middle field instead: "3,200,0" -> ("3", "200").
 */
fun questFilterPrefix(filter: String): Pair<String, String>? {
    val parts = filter.split(",")
    if (parts.size < 3) return null
    val type = parts[0]
    val id = if (type == "3" || type == "8") parts[1] else parts[2]
    return type to id
}

/**
 * Narrows [quests] to the variants the user ticked.
 *
 * A quest is kept if any of its rewards matches an enabled base filter and, when that base filter
 * has variant selections in [enabledConditions], the reward's exact variant (amount, condition
 * text and reward label) is one of them. Base filters with no variant selections keep every
 * variant. With no filters or no variant selections at all there is nothing to narrow.
 */
fun filterQuestsByConditions(
    quests: List<Quest>,
    enabledFilters: Collection<String>,
    enabledConditions: Set<String>,
): List<Quest> {
    if (enabledConditions.isEmpty() || enabledFilters.isEmpty()) return quests

    val enabledPrefixes = enabledFilters.mapNotNull { questFilterPrefix(it) }.toSet()
    // The base filters that have variant selections, in the same (type, id) form.
    val prefixesWithConditions = enabledConditions.mapNotNull { key ->
        val parts = key.split("|")
        when {
            parts.size < 3 -> null
            parts[0] == "8" -> parts[0] to parts[2]
            else -> parts[0] to parts[1]
        }
    }.toSet()

    return quests.filter { quest ->
        val types = quest.rewardsTypes.split(",").map { it.trim() }
        val ids = quest.rewardsIds.split(",").map { it.trim() }
        val amounts = quest.rewardsAmounts.split(",").map { it.trim() }
        val condition = quest.conditionsString.trim()
        val rewardLabel = quest.rewardsString.trim()

        types.indices.any { i ->
            val type = types[i]
            val id = ids.getOrNull(i) ?: return@any false
            val amount = amounts.getOrNull(i) ?: return@any false
            val prefix = if (type == "8") type to amount else type to id
            if (prefix !in enabledPrefixes) return@any false

            if (prefix in prefixesWithConditions) {
                questConditionKey(type, id, amount, condition, rewardLabel) in enabledConditions ||
                    legacyQuestConditionKey(type, id, amount, condition) in enabledConditions
            } else {
                true
            }
        }
    }
}
