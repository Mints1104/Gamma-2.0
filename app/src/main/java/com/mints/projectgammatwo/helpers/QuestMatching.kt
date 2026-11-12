package com.mints.projectgammatwo.helpers

import com.mints.projectgammatwo.data.Quests.Quest

/**
 * Key for one quest variant, "type|id|amount|condition|reward". The filter screen stores these in
 * enabledEncounterConditions and the quest fetch matches against them, so both must build them
 * here, with the same format.
 */
fun questConditionKey(type: String, id: String, amount: String, condition: String, reward: String): String =
    "$type|$id|$amount|$condition|$reward"

/**
 * The id a visited quest is stored under in VisitedQuestsPreferences, "name|lat|lng". The quest
 * list, its fetch and the overlay all build it here so a visit recorded by one is seen by the others.
 */
fun questVisitId(quest: Quest): String = "${quest.name}|${quest.lat}|${quest.lng}"

/**
 * Splits a [questVisitId] back into name, lat and lng; null if it isn't one. The coordinates are
 * taken from the end, so a stop whose name contains "|" keeps its whole name: splitting on every
 * "|" used to drop such stops from the visited history.
 */
fun parseQuestVisitId(id: String): Triple<String, Double, Double>? {
    val lngSep = id.lastIndexOf('|')
    if (lngSep <= 0) return null
    val latSep = id.lastIndexOf('|', lngSep - 1)
    if (latSep < 0) return null
    val lat = id.substring(latSep + 1, lngSep).toDoubleOrNull() ?: return null
    val lng = id.substring(lngSep + 1).toDoubleOrNull() ?: return null
    return Triple(id.substring(0, latSep), lat, lng)
}

/** The pre-reward-label form of [questConditionKey], still found in older saved selections. */
fun legacyQuestConditionKey(type: String, id: String, amount: String, condition: String): String =
    "$type|$id|$amount|$condition"

/**
 * Stardust (3) and PokéCoins (8) rewards are told apart by amount: the API sends their reward id
 * as "0" (a 200 Stardust reward is type 3, id 0, amount 200). Every other reward type is told
 * apart by id (a Pokémon, an item), whatever the amount.
 */
private fun keyedByAmount(type: String) = type == "3" || type == "8"

/**
 * The base filter string the filter screen uses for a reward: "3,200,0" for 200 Stardust,
 * "8,50,0" for 50 PokéCoins, "4,0,483" for Pokémon 483's candy.
 */
fun questBaseFilter(type: String, id: String, amount: String): String =
    if (keyedByAmount(type)) "$type,$amount,0" else "$type,0,$id"

/**
 * The (type, discriminator) a base filter string selects: "4,0,483" -> ("4", "483"), and for the
 * amount-keyed types "3,200,0" -> ("3", "200").
 */
fun questFilterPrefix(filter: String): Pair<String, String>? {
    val parts = filter.split(",")
    if (parts.size < 3) return null
    val type = parts[0]
    return type to if (keyedByAmount(type)) parts[1] else parts[2]
}

/**
 * The (type, discriminator) a condition key belongs to, in [questFilterPrefix]'s form:
 * "7|25|1|Spin 1 stop|Pikachu" -> ("7", "25"), "3|0|200|…" -> ("3", "200"). Null for anything
 * that isn't a condition key.
 */
fun conditionKeyPrefix(key: String): Pair<String, String>? {
    val parts = key.split("|")
    return when {
        parts.size < 4 -> null
        keyedByAmount(parts[0]) -> parts[0] to parts[2]
        else -> parts[0] to parts[1]
    }
}

/** Both key forms ([questConditionKey] and the legacy one) of every reward of [quest]. */
private fun conditionKeysOf(quest: Quest): List<String> {
    val types = quest.rewardsTypes.split(",").map { it.trim() }
    val ids = quest.rewardsIds.split(",").map { it.trim() }
    val amounts = quest.rewardsAmounts.split(",").map { it.trim() }
    val condition = quest.conditionsString.trim()
    val rewardLabel = quest.rewardsString.trim()
    return types.indices.flatMap { i ->
        val id = ids.getOrNull(i) ?: return@flatMap emptyList()
        val amount = amounts.getOrNull(i) ?: return@flatMap emptyList()
        listOf(
            questConditionKey(types[i], id, amount, condition, rewardLabel),
            legacyQuestConditionKey(types[i], id, amount, condition),
        )
    }
}

/**
 * Narrows [quests] to the variants the user ticked.
 *
 * A quest is kept if any of its rewards matches an enabled base filter and, when that base filter
 * has variant selections in [enabledConditions], the reward's exact variant (amount, condition
 * text and reward label) is one of them. Base filters with no variant selections keep every
 * variant. With no filters or no variant selections at all there is nothing to narrow.
 *
 * Only selections for variants that still exist in [availableQuests] count. Quest tasks rotate
 * daily, so a ticked variant can stop existing; it used to keep narrowing its reward, which then
 * matched nothing and vanished from the list until the filter screen was opened (and for
 * encounters, until it was re-ticked by hand). A reward whose ticked variants are all gone now
 * keeps every variant, as one with none ticked does. [availableQuests] must be every fetched
 * quest, before visited ones are removed: otherwise visiting every quest of a ticked variant
 * would make it look gone and bring in the variants the user left unticked.
 */
fun filterQuestsByConditions(
    quests: List<Quest>,
    enabledFilters: Collection<String>,
    enabledConditions: Set<String>,
    availableQuests: List<Quest> = quests,
): List<Quest> {
    if (enabledConditions.isEmpty() || enabledFilters.isEmpty()) return quests

    val availableKeys = availableQuests.flatMapTo(HashSet()) { conditionKeysOf(it) }
    val liveConditions = enabledConditions.filterTo(HashSet()) { it in availableKeys }

    val enabledPrefixes = enabledFilters.mapNotNull { questFilterPrefix(it) }.toSet()
    // The base filters that have (still existing) variant selections, in the same form.
    val prefixesWithConditions = liveConditions.mapNotNull { conditionKeyPrefix(it) }.toSet()

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
            // Stardust used to be matched by id here, which is always "0", so any variant
            // selection elsewhere dropped every stardust quest.
            val prefix = if (keyedByAmount(type)) type to amount else type to id
            if (prefix !in enabledPrefixes) return@any false

            if (prefix in prefixesWithConditions) {
                questConditionKey(type, id, amount, condition, rewardLabel) in liveConditions ||
                    legacyQuestConditionKey(type, id, amount, condition) in liveConditions
            } else {
                true
            }
        }
    }
}

/**
 * [enabledConditions] without the selections for variants that no longer exist: those whose
 * reward is among [currentVariantKeys]'s rewards (so its variants are known) but which match none
 * of its variants, in either key form. Selections for rewards not offered at all are kept, since
 * nothing says their variants changed, and so is anything that isn't a condition key.
 *
 * [currentVariantKeys] must come from a complete variant discovery: with a source missing, its
 * variants would look gone and the user's selections for them would be deleted.
 */
fun pruneStaleConditionKeys(enabledConditions: Set<String>, currentVariantKeys: Collection<String>): Set<String> {
    val current = HashSet<String>()
    val knownPrefixes = HashSet<Pair<String, String>>()
    for (key in currentVariantKeys) {
        current += key
        val parts = key.split("|")
        if (parts.size >= 4) current += legacyQuestConditionKey(parts[0], parts[1], parts[2], parts[3])
        conditionKeyPrefix(key)?.let { knownPrefixes += it }
    }
    return enabledConditions.filterTo(LinkedHashSet()) { key ->
        val prefix = conditionKeyPrefix(key)
        prefix == null || prefix !in knownPrefixes || key in current
    }
}
