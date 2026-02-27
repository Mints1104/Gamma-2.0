package com.mints.projectgammatwo.data

import android.util.Base64
import kotlinx.serialization.Serializable

@Serializable
data class ExportData(
    val dataSources: Set<String>? = null,
    val enabledCharacters: Set<Int>? = null,
    val favorites: List<FavoriteLocation>? = null,
    val deletedEntries: Set<DeletedEntry>? = null,
    // No longer written by the exporter; kept as a default for old exports.
    val enabledQuests: Set<String>? = null,
    // Base64-encoded encounter conditions (encoded for compactness).
    val enabledEncounterConditionsB64: String? = null,
    // Old unencoded field — kept for backwards-compatible deserialisation only.
    val enabledEncounterConditions: Set<String>? = null,
    val homeCoordinates: String? = null,
    val savedRocketFilters: Map<String, Set<Int>>? = null,
    val savedQuestFilters: Map<String, Set<String>>? = null,
    // Base64-encoded per-filter encounter condition snapshots.
    val savedQuestEncounterConditionsB64: Map<String, String>? = null,
    // Old unencoded field — kept for backwards-compatible deserialisation only.
    val savedQuestEncounterConditions: Map<String, Set<String>>? = null,
    // Kept for backwards-compatible deserialisation of old exports; no longer written or used.
    val savedQuestSpindaForms: Map<String, Set<String>>? = null,
    val activeRocketFilter: String? = null,
    val activeQuestFilter: String? = null,
    val overlayButtonSize: Int? = null,
    val overlayButtonOrder: List<String>? = null,
    val overlayButtonVisibility: Map<String, Boolean>? = null,
    val deeplinkType: String? = null,
    val deeplinkCustomUrl: String? = null
)

/** Separator used when joining condition strings for Base64 encoding. */
private const val COND_SEP = "\u0000"

/** Encodes a set of condition strings into a compact Base64 string. */
fun encodeConditionSet(conditions: Set<String>): String {
    if (conditions.isEmpty()) return ""
    val joined = conditions.joinToString(COND_SEP)
    return Base64.encodeToString(joined.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
}

/**
 * Decodes a Base64 string back to a set of condition strings.
 * Falls back to [legacy] (the old unencoded field) when the encoded string is empty,
 * so old exports that didn't have the B64 field still work correctly.
 */
fun decodeConditionSet(encoded: String?, legacy: Set<String>? = null): Set<String> {
    if (encoded.isNullOrEmpty()) return legacy ?: emptySet()
    return try {
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        bytes.toString(Charsets.UTF_8).split(COND_SEP).filter { it.isNotEmpty() }.toSet()
    } catch (e: Exception) {
        legacy ?: emptySet()
    }
}

/** Encodes each value in a map of condition sets. */
fun encodeConditionMap(map: Map<String, Set<String>>): Map<String, String> =
    map.mapValues { encodeConditionSet(it.value) }

/**
 * Decodes each value in a map of encoded condition strings.
 * Falls back to [legacy] entries where the encoded value is empty.
 */
fun decodeConditionMap(
    encoded: Map<String, String>?,
    legacy: Map<String, Set<String>>? = null
): Map<String, Set<String>> {
    val safeEncoded = encoded ?: emptyMap()
    val safeLegacy  = legacy  ?: emptyMap()
    val keys = (safeEncoded.keys + safeLegacy.keys).toSet()
    return keys.associateWith { name ->
        decodeConditionSet(safeEncoded[name], safeLegacy[name])
    }
}

