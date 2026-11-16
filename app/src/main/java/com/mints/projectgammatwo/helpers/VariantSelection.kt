package com.mints.projectgammatwo.helpers

import com.mints.projectgammatwo.viewmodels.QuestsViewModel.SubVariant

/**
 * Brings the live quest selection in line with the known reward variants, in place: a reward's
 * base filter in [enabledFilters] is ticked exactly when one of its variants is ticked in
 * [enabledConditions]. Returns whether anything changed (the filter screen then saves it).
 *
 * - A base filter ticked with no variant ticked was ticked before its variants were known: by
 *   Toggle All or a tap while they were loading (the variant cache expires after a day), or by an
 *   older backup. The quest list already shows all of its quests, so all of its variants are
 *   ticked to match. This used to drop the reward instead, undoing Toggle All for it.
 * - Legacy per-variant filter strings ("9,1,483") are replaced by their variants' condition keys.
 * - A ticked variant whose base filter is missing gets it back.
 *
 * Encounters (type 7) only get their base filter added when one of their variants is ticked;
 * a ticked encounter with no variants ticked is left as it is, which the fetch reads as all of
 * its variants.
 */
fun reconcileVariantSelections(
    subVariants: Map<String, List<SubVariant>>,
    enabledFilters: MutableSet<String>,
    enabledConditions: MutableSet<String>,
): Boolean {
    var changed = false

    subVariants.forEach { (baseFilter, variants) ->
        if (variants.isEmpty()) return@forEach

        if (baseFilter.startsWith("7,")) {
            val encounterPrefix = "7|${variants.first().id}|"
            if (enabledConditions.any { it.startsWith(encounterPrefix) } && enabledFilters.add(baseFilter)) {
                changed = true
            }
            return@forEach
        }

        val conditionKeys = variants.map { it.conditionKey() }

        // Take the base filter and any legacy per-variant filter strings out; the base is put
        // back below exactly when one of its variants is ticked.
        val baseWasSelected = enabledFilters.remove(baseFilter)
        val legacyVariants = variants.filter { it.filterString in enabledFilters }
        legacyVariants.forEach { enabledFilters.remove(it.filterString) }
        if (legacyVariants.isNotEmpty()) changed = true

        if (conditionKeys.none { it in enabledConditions }) {
            val adopted = when {
                legacyVariants.isNotEmpty() -> legacyVariants.map { it.conditionKey() }
                baseWasSelected -> conditionKeys
                else -> emptyList()
            }
            if (enabledConditions.addAll(adopted)) changed = true
        }

        if (conditionKeys.any { it in enabledConditions }) {
            enabledFilters.add(baseFilter)
            if (!baseWasSelected) changed = true
        } else if (baseWasSelected) {
            changed = true
        }
    }
    return changed
}

private fun SubVariant.conditionKey() = questConditionKey(type, id, amount, condition, reward)
