package com.mints.projectgammatwo.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExportDataTest {

    // The same settings SettingsFragment reads and writes backups with.
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val conditions = setOf(
        "7|25|1|Spin 1 stop|Pikachu",
        "7|25|1|Catch 5 Pokémon|Pikachu — shiny?",
        "4|0|483|Win a raid|3 Rare Candy",
    )

    @Test
    fun `condition sets survive an encode-decode round trip`() {
        assertEquals(conditions, decodeConditionSet(encodeConditionSet(conditions)))
        assertEquals("", encodeConditionSet(emptySet()))
    }

    @Test
    fun `encoding matches what older app versions wrote`() {
        // Older versions used android.util.Base64 with NO_WRAP: standard alphabet, padded.
        assertEquals("YQ==", encodeConditionSet(setOf("a")))
        assertEquals(setOf("a"), decodeConditionSet("YQ=="))
    }

    @Test
    fun `decoding falls back to the legacy unencoded field`() {
        val legacy = setOf("7|25|1|Spin 1 stop")
        assertEquals(legacy, decodeConditionSet(null, legacy))
        assertEquals(legacy, decodeConditionSet("", legacy))
        assertEquals(legacy, decodeConditionSet("not base64!", legacy))
        assertEquals(emptySet<String>(), decodeConditionSet(null))
    }

    @Test
    fun `condition maps round trip and merge in legacy-only filters`() {
        val saved = mapOf("Pikachu" to conditions, "Empty" to emptySet())
        val encoded = encodeConditionMap(saved)
        assertEquals(saved, decodeConditionMap(encoded))

        val legacy = mapOf("Old filter" to setOf("4|0|483|Win a raid"))
        assertEquals(saved + legacy, decodeConditionMap(encoded, legacy))
    }

    @Test
    fun `a full backup survives export and import`() {
        val export = ExportData(
            dataSources = setOf("NYC", "LONDON"),
            enabledCharacters = setOf(4, 5, 41),
            favorites = listOf(FavoriteLocation("Central Park", 40.7829, -73.9654, "America/New_York")),
            deletedEntries = setOf(DeletedEntry(40.1, -73.2, 1_790_000_000_000, "Stop", "NYC", 4, 1)),
            enabledEncounterConditionsB64 = encodeConditionSet(conditions),
            homeCoordinates = "40.7, -73.9",
            savedRocketFilters = mapOf("Grunts" to setOf(4, 5)),
            savedQuestFilters = mapOf("Pikachu" to setOf("7,0,25")),
            savedQuestEncounterConditionsB64 = encodeConditionMap(mapOf("Pikachu" to conditions)),
            activeRocketFilter = "Grunts",
            activeQuestFilter = "",
            overlayButtonSize = 56,
            overlayButtonOrder = listOf("close_button", "right_button"),
            overlayButtonVisibility = mapOf("close_button" to true, "home_button" to false),
            deeplinkType = "custom",
            deeplinkCustomUrl = "https://example.com/tp?name=My%20Spot&c=%s",
            enabledQuestFilters = setOf("7,0,25"),
        )

        val restored = json.decodeFromString<ExportData>(json.encodeToString(export))

        assertEquals(export, restored)
        assertEquals(conditions, decodeConditionSet(restored.enabledEncounterConditionsB64))
    }

    @Test
    fun `an old backup without newer fields still imports`() {
        val old = """{"dataSources":["NYC"],"enabledCharacters":[4],"favorites":[{"name":"Home","lat":1.5,"lng":2.5}],
            |"enabledEncounterConditions":["7|25|1|Spin 1 stop"],"someFieldFromTheFuture":true}""".trimMargin()

        val restored = json.decodeFromString<ExportData>(old)

        assertEquals(setOf("NYC"), restored.dataSources)
        assertNull(restored.enabledQuestFilters)
        assertNull(restored.favorites!!.single().timezoneId)
        assertEquals(
            setOf("7|25|1|Spin 1 stop"),
            decodeConditionSet(restored.enabledEncounterConditionsB64, restored.enabledEncounterConditions),
        )
    }
}
