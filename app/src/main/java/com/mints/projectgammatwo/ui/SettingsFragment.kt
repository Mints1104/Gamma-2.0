package com.mints.projectgammatwo.ui

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.*
import android.widget.*
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mints.projectgammatwo.R
import com.mints.projectgammatwo.data.DataSourcePreferences
import com.mints.projectgammatwo.data.DeletedEntry
import com.mints.projectgammatwo.data.DeletedInvasionsRepository
import com.mints.projectgammatwo.data.FavoriteLocation
import com.mints.projectgammatwo.data.FilterPreferences
import com.mints.projectgammatwo.data.HomeCoordinatesManager
import com.mints.projectgammatwo.data.QuestFilterPreferences
import androidx.core.content.edit
import com.mints.projectgammatwo.data.ExportData
import kotlin.jvm.java

class SettingsFragment : Fragment() {

    private lateinit var checkboxNYC: CheckBox
    private lateinit var checkboxLondon: CheckBox
    private lateinit var checkboxSG: CheckBox
    private lateinit var checkboxVancouver: CheckBox
    private lateinit var checkboxSydney: CheckBox
    private lateinit var btnExportSettings: Button
    private lateinit var btnImportSettings: Button
    private lateinit var radioGroupTeleport: RadioGroup
    private lateinit var radioIpogo: RadioButton
    private lateinit var radioJoystick: RadioButton

    private lateinit var dataSourcePreferences: DataSourcePreferences
    private lateinit var filterPreferences: FilterPreferences
    private lateinit var deletedRepo: DeletedInvasionsRepository
    private lateinit var discordTextView: TextView
    private lateinit var homeCoordinates: EditText
    private lateinit var homeCoordinatesManager: HomeCoordinatesManager

    private val gson = Gson()

    private val FAVORITES_PREFS_NAME = "favorites_prefs"
    private val KEY_FAVORITES = "favorites_list"
    private val TELEPORT_PREFS_NAME = "teleport_prefs"
    private val KEY_TELEPORT_METHOD = "teleport_method"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        dataSourcePreferences = DataSourcePreferences(requireContext())
        filterPreferences = FilterPreferences(requireContext())
        deletedRepo = DeletedInvasionsRepository(requireContext())
        homeCoordinatesManager = HomeCoordinatesManager.getInstance(requireContext())

        checkboxNYC = view.findViewById(R.id.checkbox_nyc)
        checkboxLondon = view.findViewById(R.id.checkbox_london)
        checkboxSG = view.findViewById(R.id.checkbox_sg)
        checkboxVancouver = view.findViewById(R.id.checkbox_vancouver)
        checkboxSydney = view.findViewById(R.id.checkbox_sydney)
        btnExportSettings = view.findViewById(R.id.btnExportSettings)
        btnImportSettings = view.findViewById(R.id.btnImportSettings)
        radioGroupTeleport = view.findViewById(R.id.radioGroupTeleport)
        radioIpogo = view.findViewById(R.id.radio_ipogo)
        radioJoystick = view.findViewById(R.id.radio_joystick)
        discordTextView = view.findViewById(R.id.discordInvite)
        homeCoordinates = view.findViewById(R.id.homeCoordinates)

        setupDiscordText()
        setupDataSourceCheckboxes()
        setupExportImportButtons()
        setupTeleportMethod()
        setupHomeCoordinatesField()
    }

    private fun setupDiscordText() {
        discordTextView.text = HtmlCompat.fromHtml(getString(R.string.discord_link), HtmlCompat.FROM_HTML_MODE_LEGACY)
        discordTextView.movementMethod = LinkMovementMethod.getInstance()
        discordTextView.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                v.clearFocus()
            }
        }
    }

    private fun setupDataSourceCheckboxes() {
        // Load data source selections.
        val selectedSources = dataSourcePreferences.getSelectedSources()
        checkboxNYC.isChecked = selectedSources.contains("NYC")
        checkboxLondon.isChecked = selectedSources.contains("LONDON")
        checkboxSG.isChecked = selectedSources.contains("Singapore")
        checkboxVancouver.isChecked = selectedSources.contains("VANCOUVER")
        checkboxSydney.isChecked = selectedSources.contains("SYDNEY")

        // Data source check listener.
        val checkListener = View.OnClickListener {
            val newSelection = mutableSetOf<String>()
            if (checkboxNYC.isChecked) newSelection.add("NYC")
            if (checkboxLondon.isChecked) newSelection.add("LONDON")
            if (checkboxSG.isChecked) newSelection.add("Singapore")
            if (checkboxVancouver.isChecked) newSelection.add("VANCOUVER")
            if (checkboxSydney.isChecked) newSelection.add("SYDNEY")
            if (newSelection.isEmpty()) {
                newSelection.add("NYC")
                checkboxNYC.isChecked = true
            }
            dataSourcePreferences.setSelectedSources(newSelection)
        }
        checkboxNYC.setOnClickListener(checkListener)
        checkboxLondon.setOnClickListener(checkListener)
        checkboxSG.setOnClickListener(checkListener)
        checkboxVancouver.setOnClickListener(checkListener)
        checkboxSydney.setOnClickListener(checkListener)
    }

    private fun setupExportImportButtons() {
        // Export settings.
        btnExportSettings.setOnClickListener {
            exportSettings()
        }

        // Import settings.
        btnImportSettings.setOnClickListener {
            importSettingsDialog()
        }
    }

    private fun setupTeleportMethod() {
        // Teleport method: load saved method.
        val teleportPrefs = requireContext().getSharedPreferences(TELEPORT_PREFS_NAME, Context.MODE_PRIVATE)
        val savedMethod = teleportPrefs.getString(KEY_TELEPORT_METHOD, "ipogo") ?: "ipogo"
        if (savedMethod == "ipogo") {
            radioIpogo.isChecked = true
        } else {
            radioJoystick.isChecked = true
        }
        radioGroupTeleport.setOnCheckedChangeListener { _, checkedId ->
            val method = if (checkedId == R.id.radio_ipogo) "ipogo" else "joystick"
            teleportPrefs.edit().putString(KEY_TELEPORT_METHOD, method).apply()
        }
    }

    private fun setupHomeCoordinatesField() {
        // Load saved coordinates
        val savedCoords = homeCoordinatesManager.getHomeCoordinatesString()
        homeCoordinates.setText(savedCoords)

        // Set input hint
        homeCoordinates.hint = "Enter coords (e.g. 40.121, -32.121)"

        // Set input type for decimal numbers and comma
        homeCoordinates.inputType = InputType.TYPE_CLASS_TEXT

        // Apply input filter for validation
        homeCoordinates.filters = arrayOf(CoordinatesInputFilter())

        // Save coordinates when focus changes
        homeCoordinates.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val coords = homeCoordinates.text.toString().trim()
                if (homeCoordinatesManager.validateCoordinates(coords)) {
                    homeCoordinatesManager.saveHomeCoordinates(coords)
                    Toast.makeText(requireContext(), "Coordinates saved: $coords", Toast.LENGTH_SHORT).show()
                } else if (coords.isNotEmpty()) {
                    Toast.makeText(
                        requireContext(),
                        "Invalid coordinates format. Use: Lat, Long (e.g., 40.7128, -74.0060)",
                        Toast.LENGTH_LONG
                    ).show()
                    // Keep previous valid value if available
                    if (savedCoords.isNotEmpty() && homeCoordinatesManager.validateCoordinates(savedCoords)) {
                        homeCoordinates.setText(savedCoords)
                    } else {
                        homeCoordinates.text.clear()
                    }
                }
            }
        }
    }

    /**
     * Custom input filter for coordinates validation
     */
    inner class CoordinatesInputFilter : InputFilter {
        override fun filter(
            source: CharSequence,
            start: Int,
            end: Int,
            dest: Spanned,
            dstart: Int,
            dend: Int
        ): CharSequence? {
            val input = dest.toString().substring(0, dstart) +
                    source.toString().substring(start, end) +
                    dest.toString().substring(dend)

            // Allow empty field for clearing
            if (input.isEmpty()) return null

            // Allow partial valid input for coordinates
            // Valid chars: digits, minus sign, period, comma, space
            val validChars = "0123456789-., "
            for (i in start until end) {
                if (!validChars.contains(source[i])) {
                    return ""
                }
            }

            return null
        }
    }


    /**
     * Exports settings to a JSON string and launches a share intent.
     */
    private fun exportSettings() {
        Log.d("SettingsExport", "Starting export process")
        val dataSources = dataSourcePreferences.getSelectedSources()
        Log.d("SettingsExport", "Data sources: $dataSources")

        val enabledCharacters = filterPreferences.getEnabledCharacters()
        Log.d("SettingsExport", "Enabled characters: $enabledCharacters")

        val favoritesPrefs = requireContext().getSharedPreferences(FAVORITES_PREFS_NAME, Context.MODE_PRIVATE)
        val favoritesJson = favoritesPrefs.getString(KEY_FAVORITES, "[]")
        val favoritesType = object : TypeToken<List<FavoriteLocation>>() {}.type
        val favorites: List<FavoriteLocation> = gson.fromJson(favoritesJson, favoritesType)
        Log.d("SettingsExport", "Favorites count: ${favorites.size}")

        val enabledQuests = filterPreferences.getEnabledQuestFilters()
        Log.d("SettingsExport", "Enabled quests: $enabledQuests")

        val deletedEntries = deletedRepo.getDeletedEntries()
        Log.d("SettingsExport", "Deleted entries count: ${deletedEntries.size}")

        // Get home coordinates from manager
        val homeCoords = homeCoordinatesManager.getHomeCoordinatesString()
        Log.d("SettingsExport", "Home coordinates: $homeCoords")

        // Get all saved rocket filters
        val savedRocketFilters = filterPreferences.getAllSavedFilters()
        Log.d("SettingsExport", "Saved rocket filters: ${savedRocketFilters.keys}")

        // Get all saved quest filters
        val savedQuestFilters = filterPreferences.getSavedQuestFilters()
        Log.d("SettingsExport", "Saved quest filters: ${savedQuestFilters.keys}")
        val savedQuestSpindaForms = savedQuestFilters.keys.associateWith { name ->
            // you used QUEST_SPINDA_PREFIX = "spinda_"
            requireContext()
                .getSharedPreferences("quest_filters", Context.MODE_PRIVATE)
                .getStringSet("spinda_$name", emptySet())!!
        }

        // Get active filter names
        val activeRocketFilter = filterPreferences.getActiveRocketFilter()
        val activeQuestFilter = filterPreferences.getActiveQuestFilter()
        Log.d("SettingsExport", "Active filters - Rocket: $activeRocketFilter, Quest: $activeQuestFilter")

        val exportData = ExportData(
            dataSources = dataSources,
            enabledCharacters = enabledCharacters,
            favorites = favorites,
            deletedEntries = deletedEntries,
            enabledQuests = enabledQuests,
            homeCoordinates = homeCoords,
            savedRocketFilters = savedRocketFilters,
            savedQuestFilters = savedQuestFilters,
            savedQuestSpindaForms   = savedQuestSpindaForms,
            activeRocketFilter = activeRocketFilter,
            activeQuestFilter = activeQuestFilter
        )

        try {
            val exportJson = gson.toJson(exportData)
            Log.d("SettingsExport", "JSON created successfully, length: ${exportJson.length}")
            Log.d("SettingsExport", "JSON sample: ${exportJson.take(100)}...")

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Exported Settings")
                putExtra(Intent.EXTRA_TEXT, exportJson)
            }
            Log.d("SettingsExport", "Starting share intent")
            startActivity(Intent.createChooser(shareIntent, "Share Settings JSON"))
        } catch (e: Exception) {
            Log.e("SettingsExport", "Error creating JSON: ${e.message}", e)
            Toast.makeText(requireContext(), "Failed to export settings: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Displays a dialog for importing settings from JSON.
     */
    private fun importSettingsDialog() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Import Settings (Paste JSON)")
        val input = EditText(requireContext())
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        input.hint = "Paste settings JSON here"
        builder.setView(input)
        builder.setPositiveButton("Import") { dialog, _ ->
            val jsonString = input.text.toString()
            if (jsonString.isBlank()) {
                Toast.makeText(requireContext(), "Input cannot be empty", Toast.LENGTH_SHORT).show()
            } else {
                importSettings(jsonString)
            }
            dialog.dismiss()
        }
        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
        builder.show()
    }

    /**
     * Imports settings from the provided JSON string.
     */
    private fun importSettings(jsonString: String) {
        try {
            Log.d("SettingsImport", "Starting import process with JSON: ${jsonString.take(100)}...")

            val importData = gson.fromJson(jsonString, ExportData::class.java)
            Log.d("SettingsImport", "Successfully parsed JSON into ExportData")

            // Basic settings import
            Log.d("SettingsImport", "Importing data sources: ${importData.dataSources}")
            dataSourcePreferences.setSelectedSources(importData.dataSources)

            Log.d("SettingsImport", "Importing enabled characters: ${importData.enabledCharacters}")
            filterPreferences.saveEnabledCharacters(importData.enabledCharacters)

            Log.d("SettingsImport", "Importing enabled quests: ${importData.enabledQuests}")
            filterPreferences.saveEnabledQuestFilters(importData.enabledQuests)

            val favoritesPrefs = requireContext().getSharedPreferences(FAVORITES_PREFS_NAME, Context.MODE_PRIVATE)
            Log.d("SettingsImport", "Importing ${importData.favorites.size} favorites")
            favoritesPrefs.edit().putString(KEY_FAVORITES, gson.toJson(importData.favorites)).apply()

            Log.d("SettingsImport", "Importing ${importData.deletedEntries.size} deleted entries")
            deletedRepo.setDeletedEntries(importData.deletedEntries)

            // Import home coordinates if available and valid
            Log.d("SettingsImport", "Importing home coordinates: ${importData.homeCoordinates}")
            if (importData.homeCoordinates.isNotEmpty() &&
                homeCoordinatesManager.validateCoordinates(importData.homeCoordinates)) {
                homeCoordinatesManager.saveHomeCoordinates(importData.homeCoordinates)
                homeCoordinates.setText(importData.homeCoordinates)
                Log.d("SettingsImport", "Home coordinates imported successfully")
            } else {
                Log.d("SettingsImport", "Home coordinates empty or invalid")
            }

            // Import saved rocket filters if available
            if (importData.savedRocketFilters != null) {
                Log.d("SettingsImport", "Found ${importData.savedRocketFilters.size} rocket filters to import")

                // Check if savedRocketFilters field exists but is null
                if (importData.savedRocketFilters == null) {
                    Log.e("SettingsImport", "savedRocketFilters exists in class but null in instance")
                }

                // Clear existing filters first
                val existingFilterNames = filterPreferences.listFilterNames()
                Log.d("SettingsImport", "Clearing ${existingFilterNames.size} existing rocket filters: $existingFilterNames")
                for (name in existingFilterNames) {
                    Log.d("SettingsImport", "Deleting rocket filter: $name")
                    filterPreferences.deleteFilter(name, "Rocket")
                }

                // Import the filters
                for ((name, characters) in importData.savedRocketFilters) {
                    Log.d("SettingsImport", "Importing rocket filter '$name' with ${characters.size} characters: $characters")
                    try {
                        filterPreferences.saveEnabledCharacters(characters)
                        filterPreferences.saveCurrentAsFilter(name)
                        Log.d("SettingsImport", "Successfully saved rocket filter: $name")
                    } catch (e: Exception) {
                        Log.e("SettingsImport", "Error saving rocket filter '$name': ${e.message}", e)
                    }
                }

                // Set active rocket filter if it exists in the imported data
                Log.d("SettingsImport", "Active rocket filter from import: ${importData.activeRocketFilter}")
                if (importData.activeRocketFilter.isNotEmpty() &&
                    importData.savedRocketFilters.containsKey(importData.activeRocketFilter)) {
                    Log.d("SettingsImport", "Setting active rocket filter: ${importData.activeRocketFilter}")
                    try {
                        filterPreferences.setActiveRocketFilter(importData.activeRocketFilter)
                        // If active, also load it
                        filterPreferences.loadFilter(importData.activeRocketFilter, "Rocket")
                        Log.d("SettingsImport", "Successfully activated rocket filter: ${importData.activeRocketFilter}")
                    } catch (e: Exception) {
                        Log.e("SettingsImport", "Error activating rocket filter: ${e.message}", e)
                    }
                }

                // Verify filters were imported
                val verifyFilters = filterPreferences.listFilterNames()
                Log.d("SettingsImport", "Verification - Imported rocket filters: $verifyFilters")
            } else {
                Log.d("SettingsImport", "No rocket filters to import (null)")
            }

            // Import saved quest filters if available
            if (importData.savedQuestFilters != null) {
                Log.d("SettingsImport", "Found ${importData.savedQuestFilters.size} quest filters to import")

                // Clear existing quest filters first
                val existingQuestFilterNames = filterPreferences.listQuestFilterNames()
                Log.d("SettingsImport", "Clearing ${existingQuestFilterNames.size} existing quest filters: $existingQuestFilterNames")
                for (name in existingQuestFilterNames) {
                    Log.d("SettingsImport", "Deleting quest filter: $name")
                    filterPreferences.deleteFilter(name, "Quest")
                }

                // Import the quest filters
                for ((name, questIds) in importData.savedQuestFilters) {
                    // Convert quest IDs to strings for storage
                    val questStrings = questIds.map { it.toString() }.toSet()
                    Log.d("SettingsImport", "Importing quest filter '$name' with ${questStrings.size} quests: $questStrings")

                    try {
                        // Use saveCurrentQuestFilter method since it's more reliable
                        // First, set the current enabled quests to match this filter
                        filterPreferences.saveEnabledQuestFilters(questStrings)
                        filterPreferences.saveCurrentQuestFilter(name)
                        val forms = importData.savedQuestSpindaForms[name] ?: emptySet()
                        filterPreferences.saveEnabledSpindaForms(forms)
                        val questStringss = questIds.toSet()
                        filterPreferences.saveEnabledQuestFilters(questStringss)
                        filterPreferences.saveCurrentQuestFilter(name)

                        Log.d("SettingsImport", "Successfully saved quest filter: $name")
                    } catch (e: Exception) {
                        Log.e("SettingsImport", "Error saving quest filter '$name': ${e.message}", e)
                    }
                }

                // Set active quest filter if it exists in the imported data
                Log.d("SettingsImport", "Active quest filter from import: ${importData.activeQuestFilter}")
                if (importData.activeQuestFilter.isNotEmpty() &&
                    importData.savedQuestFilters.containsKey(importData.activeQuestFilter)) {
                    Log.d("SettingsImport", "Setting active quest filter: ${importData.activeQuestFilter}")
                    try {
                        filterPreferences.setActiveQuestFilter(importData.activeQuestFilter)
                        // If active, also load it
                        filterPreferences.loadFilter(importData.activeQuestFilter, "Quest")
                        Log.d("SettingsImport", "Successfully activated quest filter: ${importData.activeQuestFilter}")
                    } catch (e: Exception) {
                        Log.e("SettingsImport", "Error activating quest filter: ${e.message}", e)
                    }
                }

                // Verify filters were imported
                val verifyQuestFilters = filterPreferences.listQuestFilterNames()
                Log.d("SettingsImport", "Verification - Imported quest filters: $verifyQuestFilters")
            } else {
                Log.d("SettingsImport", "No quest filters to import (null)")
            }

            // Update UI checkboxes.
            checkboxNYC.isChecked = importData.dataSources.contains("NYC")
            checkboxLondon.isChecked = importData.dataSources.contains("LONDON")
            checkboxSG.isChecked = importData.dataSources.contains("Singapore")
            checkboxVancouver.isChecked = importData.dataSources.contains("VANCOUVER")
            checkboxSydney.isChecked = importData.dataSources.contains("SYDNEY")

            Log.d("SettingsImport", "Settings import completed successfully")
            Toast.makeText(requireContext(), "Settings imported successfully", Toast.LENGTH_LONG).show()
        } catch (ex: Exception) {
            Log.e("SettingsImport", "Import failed with exception: ${ex.message}", ex)
            // Log the JSON as well to see what might be wrong with it
            Log.e("SettingsImport", "JSON that caused failure: ${jsonString.take(200)}...")
            ex.printStackTrace()
            Toast.makeText(requireContext(), "Failed to import settings: ${ex.message}", Toast.LENGTH_LONG).show()
        }
    }
}