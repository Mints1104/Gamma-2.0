package com.mints.projectgammatwo.data

data class ExportData(
    val dataSources: Set<String>,
    val enabledCharacters: Set<Int>,
    val favorites: List<FavoriteLocation>,
    val deletedEntries: Set<DeletedEntry>,
    val enabledQuests: Set<String>,
    val homeCoordinates: String,
    val savedRocketFilters: Map<String, Set<Int>>,
    val savedQuestFilters: Map<String, Set<String>>,
    val savedQuestSpindaForms: Map<String, Set<String>>,
    val activeRocketFilter: String,
    val activeQuestFilter: String,
    // Overlay customization settings
    val overlayButtonSize: Int = 48,
    val overlayButtonOrder: List<String> = emptyList(),
    val overlayButtonVisibility: Map<String, Boolean> = emptyMap()
)
