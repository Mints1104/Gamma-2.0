package com.mints.projectgammatwo.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mints.projectgammatwo.R
import com.mints.projectgammatwo.data.DataSourcePreferences
import com.mints.projectgammatwo.data.DeletedInvasionsRepository
import com.mints.projectgammatwo.data.FavoriteLocation
import com.mints.projectgammatwo.data.FilterPreferences
import com.mints.projectgammatwo.data.HomeCoordinatesManager
import com.mints.projectgammatwo.data.ExportData
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SettingsFragment : Fragment() {

    companion object {
        private const val FAVORITES_PREFS_NAME = "favorites_prefs"
        private const val KEY_FAVORITES = "favorites_list"
        private const val TELEPORT_PREFS_NAME = "teleport_prefs"
        private const val KEY_TELEPORT_METHOD = "teleport_method"

        private const val SOURCE_NYC = "NYC"
        private const val SOURCE_LONDON = "LONDON"
        private const val SOURCE_SINGAPORE = "Singapore"
        private const val SOURCE_VANCOUVER = "VANCOUVER"
        private const val SOURCE_SYDNEY = "SYDNEY"

        private const val FILTER_TYPE_ROCKET = "Rocket"
        private const val FILTER_TYPE_QUEST = "Quest"
    }

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

    // Overlay customization views
    private lateinit var btnCustomizeOverlay: Button
    private lateinit var customizationManager: com.mints.projectgammatwo.data.OverlayCustomizationManager

    private val gson = Gson()
    private val kxJson: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        allowTrailingComma = true
        prettyPrint = false
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        dataSourcePreferences = DataSourcePreferences(requireContext())
        filterPreferences = FilterPreferences(requireContext())
        deletedRepo = DeletedInvasionsRepository(requireContext())
        homeCoordinatesManager = HomeCoordinatesManager.getInstance(requireContext())
        customizationManager = com.mints.projectgammatwo.data.OverlayCustomizationManager(requireContext())

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
        btnCustomizeOverlay = view.findViewById(R.id.btnCustomizeOverlay)

        setupDiscordText()
        setupDataSourceCheckboxes()
        setupExportImportButtons()
        setupTeleportMethod()
        setupHomeCoordinatesField()
        setupOverlayCustomization()
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
        checkboxNYC.isChecked = selectedSources.contains(SOURCE_NYC)
        checkboxLondon.isChecked = selectedSources.contains(SOURCE_LONDON)
        checkboxSG.isChecked = selectedSources.contains(SOURCE_SINGAPORE)
        checkboxVancouver.isChecked = selectedSources.contains(SOURCE_VANCOUVER)
        checkboxSydney.isChecked = selectedSources.contains(SOURCE_SYDNEY)

        // Data source check listener.
        val checkListener = View.OnClickListener {
            val newSelection = mutableSetOf<String>()
            if (checkboxNYC.isChecked) newSelection.add(SOURCE_NYC)
            if (checkboxLondon.isChecked) newSelection.add(SOURCE_LONDON)
            if (checkboxSG.isChecked) newSelection.add(SOURCE_SINGAPORE)
            if (checkboxVancouver.isChecked) newSelection.add(SOURCE_VANCOUVER)
            if (checkboxSydney.isChecked) newSelection.add(SOURCE_SYDNEY)
            if (newSelection.isEmpty()) {
                newSelection.add(SOURCE_NYC)
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
        val teleportPrefs = requireContext().getSharedPreferences(TELEPORT_PREFS_NAME, Context.MODE_PRIVATE)
        val savedMethod = teleportPrefs.getString(KEY_TELEPORT_METHOD, "ipogo") ?: "ipogo"
        if (savedMethod == "ipogo") {
            radioIpogo.isChecked = true
        } else {
            radioJoystick.isChecked = true
        }
        radioGroupTeleport.setOnCheckedChangeListener { _, checkedId ->
            val method = if (checkedId == R.id.radio_ipogo) "ipogo" else "joystick"
            teleportPrefs.edit { putString(KEY_TELEPORT_METHOD, method) }
        }
    }

    private fun setupHomeCoordinatesField() {
        // Load saved coordinates
        val initialCoords = homeCoordinatesManager.getHomeCoordinatesString()
        homeCoordinates.setText(initialCoords)

        // Set input hint
        homeCoordinates.hint = getString(R.string.home_coords_hint)

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
                    Toast.makeText(requireContext(), getString(R.string.home_coords_saved, coords), Toast.LENGTH_SHORT).show()
                } else if (coords.isNotEmpty()) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.home_coords_invalid),
                        Toast.LENGTH_LONG
                    ).show()
                    // Use latest saved value if available
                    val previous = homeCoordinatesManager.getHomeCoordinatesString()
                    if (previous.isNotEmpty() && homeCoordinatesManager.validateCoordinates(previous)) {
                        homeCoordinates.setText(previous)
                    } else {
                        homeCoordinates.text.clear()
                    }
                }
            }
        }
    }

    private fun setupOverlayCustomization() {
        btnCustomizeOverlay.setOnClickListener {
            showOverlayCustomizationDialog()
        }
    }

    private fun restartOverlayService() {
        // Check if overlay service is running by checking shared preferences or a flag
        val sharedPrefs = requireContext().getSharedPreferences("overlay_prefs", Context.MODE_PRIVATE)
        val isOverlayRunning = sharedPrefs.getBoolean("overlay_running", false)
        val currentMode = sharedPrefs.getString("overlay_mode", "invasions") ?: "invasions"
        
        if (isOverlayRunning) {
            // Stop the service
            val stopIntent = Intent(requireContext(), com.mints.projectgammatwo.services.OverlayService::class.java)
            requireContext().stopService(stopIntent)
            
            // Wait a bit before restarting
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                // Restart the service
                val startIntent = Intent(requireContext(), com.mints.projectgammatwo.services.OverlayService::class.java)
                startIntent.putExtra("mode", currentMode)
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    requireContext().startForegroundService(startIntent)
                } else {
                    requireContext().startService(startIntent)
                }
            }, 500)
        }
    }

    private fun showOverlayCustomizationDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_overlay_customization, null)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        // Set up button size slider
        val sizeSeekbar = dialogView.findViewById<SeekBar>(R.id.size_seekbar)
        val sizeValue = dialogView.findViewById<TextView>(R.id.size_value)
        val currentSize = customizationManager.getButtonSize()

        sizeSeekbar.progress = currentSize
        sizeValue.text = getString(R.string.overlay_size_dp, currentSize)

        sizeSeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                sizeValue.text = getString(R.string.overlay_size_dp, progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val newSize = seekBar?.progress ?: 48
                customizationManager.saveButtonSize(newSize)
                restartOverlayService()
            }
        })

        // Set up RecyclerView for buttons
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.buttons_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        val buttonOrder = customizationManager.getButtonOrder()
        val buttonVisibility = customizationManager.getButtonVisibility()

        // Filter out drag_handle from the list since it can't be changed
        val buttonItems = buttonOrder
            .filter { it != "drag_handle" }
            .map { buttonId ->
                com.mints.projectgammatwo.recyclerviews.OverlayButtonItem(
                    id = buttonId,
                    name = getButtonDisplayName(buttonId),
                    iconResId = getButtonIcon(buttonId),
                    isVisible = buttonVisibility[buttonId] ?: true,
                    isRequired = buttonId == "close_button"
                )
            }.toMutableList()

        // Set up ItemTouchHelper for drag-and-drop FIRST (before creating adapter)
        var itemTouchHelper: ItemTouchHelper? = null

        val adapter = com.mints.projectgammatwo.recyclerviews.OverlayCustomizationAdapter(
            items = buttonItems,
            onItemChanged = { updatedItems ->
                // Re-add drag_handle at the beginning when saving
                val newOrder = listOf("drag_handle") + updatedItems.map { it.id }
                val newVisibility = updatedItems.associate { it.id to it.isVisible }.toMutableMap()
                newVisibility["drag_handle"] = true // Always visible
                customizationManager.saveButtonOrder(newOrder)
                customizationManager.saveButtonVisibility(newVisibility)
                restartOverlayService()
            },
            onStartDrag = { viewHolder ->
                itemTouchHelper?.startDrag(viewHolder)
            }
        )

        recyclerView.adapter = adapter

        // Force RecyclerView to properly layout its items
        recyclerView.post {
            adapter.notifyDataSetChanged()
            recyclerView.requestLayout()
        }

        // Now initialize itemTouchHelper (all items can be dragged now)
        itemTouchHelper = ItemTouchHelper(
            com.mints.projectgammatwo.helpers.ItemTouchHelperCallback(object : com.mints.projectgammatwo.helpers.ItemTouchHelperAdapter {
                override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
                    return adapter.onItemMove(fromPosition, toPosition)
                }
            })
        )
        itemTouchHelper.attachToRecyclerView(recyclerView)

        // Set up reset button
        dialogView.findViewById<Button>(R.id.reset_button).setOnClickListener {
            customizationManager.resetToDefaults()
            
            // Update the current dialog without dismissing it
            val newButtonOrder = customizationManager.getButtonOrder()
            val newButtonVisibility = customizationManager.getButtonVisibility()
            
            // Filter out drag_handle again
            val newButtonItems = newButtonOrder
                .filter { it != "drag_handle" }
                .map { buttonId ->
                    com.mints.projectgammatwo.recyclerviews.OverlayButtonItem(
                        id = buttonId,
                        name = getButtonDisplayName(buttonId),
                        iconResId = getButtonIcon(buttonId),
                        isVisible = newButtonVisibility[buttonId] ?: true,
                        isRequired = buttonId == "close_button"
                    )
                }
            
            adapter.updateItems(newButtonItems)
            
            // Update size slider
            val defaultSize = customizationManager.getButtonSize()
            sizeSeekbar.progress = defaultSize
            sizeValue.text = getString(R.string.overlay_size_dp, defaultSize)

            restartOverlayService()
        }

        // Set up Apply button (closes the dialog; changes are applied live)
        dialogView.findViewById<Button>(R.id.close_button).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun getButtonDisplayName(buttonId: String): String {
        return when (buttonId) {
            "drag_handle" -> getString(R.string.overlay_button_drag_handle)
            "close_button" -> getString(R.string.overlay_button_close)
            "right_button" -> getString(R.string.overlay_button_next)
            "left_button" -> getString(R.string.overlay_button_previous)
            "home_button" -> getString(R.string.overlay_button_home)
            "refresh_button" -> getString(R.string.overlay_button_refresh)
            "switch_modes" -> getString(R.string.overlay_button_switch_mode)
            "filter_tab" -> getString(R.string.overlay_button_filters)
            "favorites_tab" -> getString(R.string.overlay_button_favorites)
            else -> buttonId
        }
    }

    private fun getButtonIcon(buttonId: String): Int {
        return when (buttonId) {
            "drag_handle" -> R.drawable.ic_drag_handle_overlay
            "close_button" -> R.drawable.close_24px
            "right_button" -> R.drawable.arrow_right_24px
            "left_button" -> R.drawable.arrow_left_24px
            "home_button" -> R.drawable.home_24px
            "refresh_button" -> R.drawable.refresh_24px
            "switch_modes" -> R.drawable.team_rocket_logo
            "filter_tab" -> R.drawable.tune_24px
            "favorites_tab" -> R.drawable.ic_favorite
            else -> R.drawable.ic_launcher_foreground
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
            requireContext()
                .getSharedPreferences("quest_filters", Context.MODE_PRIVATE)
                .getStringSet("spinda_$name", emptySet())!!
        }

        // Get active filter names
        val activeRocketFilter = filterPreferences.getActiveRocketFilter()
        val activeQuestFilter = filterPreferences.getActiveQuestFilter()
        Log.d("SettingsExport", "Active filters - Rocket: $activeRocketFilter, Quest: $activeQuestFilter")

        // Get overlay customization settings
        val overlayButtonSize = customizationManager.getButtonSize()
        val overlayButtonOrder = customizationManager.getButtonOrder()
        val overlayButtonVisibility = customizationManager.getButtonVisibility()
        Log.d("SettingsExport", "Overlay customization - Size: $overlayButtonSize, Order: $overlayButtonOrder")

        val exportData = ExportData(
            dataSources = dataSources,
            enabledCharacters = enabledCharacters,
            favorites = favorites,
            deletedEntries = deletedEntries,
            enabledQuests = enabledQuests,
            homeCoordinates = homeCoords,
            savedRocketFilters = savedRocketFilters,
            savedQuestFilters = savedQuestFilters,
            savedQuestSpindaForms = savedQuestSpindaForms,
            activeRocketFilter = activeRocketFilter,
            activeQuestFilter = activeQuestFilter,
            overlayButtonSize = overlayButtonSize,
            overlayButtonOrder = overlayButtonOrder,
            overlayButtonVisibility = overlayButtonVisibility
        )

        try {
            val exportJson = kxJson.encodeToString(exportData)
            Log.d("SettingsExport", "JSON created successfully, length: ${exportJson.length}")
            Log.d("SettingsExport", "JSON sample: ${exportJson.take(100)}...")

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.settings_share_subject))
                putExtra(Intent.EXTRA_TEXT, exportJson)
            }
            Log.d("SettingsExport", "Starting share intent")
            startActivity(Intent.createChooser(shareIntent, getString(R.string.settings_share_chooser_title)))
        } catch (e: Exception) {
            Log.e("SettingsExport", "Error creating JSON: ${e.message}", e)
            Toast.makeText(requireContext(), getString(R.string.settings_export_failed, e.message ?: "Unknown error"), Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Displays a dialog for importing settings from JSON.
     */
    private fun importSettingsDialog() {
        val builder = AlertDialog.Builder(requireContext())
        val inflater = requireActivity().layoutInflater
        val dialogView = inflater.inflate(R.layout.dialog_import_settings, null)

        val editText = dialogView.findViewById<EditText>(R.id.editImportSettingsJson)
        val cancelButton = dialogView.findViewById<Button>(R.id.cancelSettingsImportButton)
        val importButton = dialogView.findViewById<Button>(R.id.importSettingsButton)

        builder.setView(dialogView)
        val dialog = builder.create()

        cancelButton.setOnClickListener { dialog.dismiss() }

        importButton.setOnClickListener {
            val jsonString = editText.text.toString()
            if (jsonString.isBlank()) {
                Toast.makeText(requireContext(), getString(R.string.settings_import_input_empty), Toast.LENGTH_SHORT).show()
            } else {
                importSettings(jsonString)
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    /**
     * Imports settings from the provided JSON string.
     */
    private fun importSettings(jsonString: String) {
        try {
            Log.d("SettingsImport", "Starting import process with JSON: ${jsonString.take(100)}...")

            val importData: ExportData = try {
                val parsed = kxJson.decodeFromString<ExportData>(jsonString)
                Log.d("SettingsImport", "Parsed with kotlinx.serialization")
                parsed
            } catch (e: Exception) {
                Log.w("SettingsImport", "kotlinx.serialization failed: ${e.message}. Falling back to Gson")
                gson.fromJson(jsonString, ExportData::class.java)
            }
            Log.d("SettingsImport", "Successfully parsed JSON into ExportData")

            // Basic settings import
            Log.d("SettingsImport", "Importing data sources: ${importData.dataSources}")
            dataSourcePreferences.setSelectedSources(importData.dataSources)

            Log.d("SettingsImport", "Importing enabled characters: ${importData.enabledCharacters}")
            filterPreferences.saveEnabledCharacters(importData.enabledCharacters)

            Log.d("SettingsImport", "Importing enabled quests: ${importData.enabledQuests}")
            filterPreferences.saveEnabledQuestFilters(importData.enabledQuests)
            Log.d("SettingsImport", "Importing ${importData.deletedEntries.size} deleted entries")
            deletedRepo.setDeletedEntries(importData.deletedEntries)


            val favoritesPrefs = requireContext().getSharedPreferences(FAVORITES_PREFS_NAME, Context.MODE_PRIVATE)
            Log.d("SettingsImport", "Importing ${importData.favorites.size} favorites")
            favoritesPrefs.edit { putString(KEY_FAVORITES, gson.toJson(importData.favorites)) }

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

            // Import saved rocket filters (non-null in ExportData)
            val rocketFilters = importData.savedRocketFilters
            Log.d("SettingsImport", "Found ${rocketFilters.size} rocket filters to import")

            // Clear existing filters first
            val existingFilterNames = filterPreferences.listFilterNames()
            Log.d("SettingsImport", "Clearing ${existingFilterNames.size} existing rocket filters: $existingFilterNames")
            for (name in existingFilterNames) {
                Log.d("SettingsImport", "Deleting rocket filter: $name")
                filterPreferences.deleteFilter(name, FILTER_TYPE_ROCKET)
            }

            // Import the filters
            for ((name, characters) in rocketFilters) {
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
                rocketFilters.containsKey(importData.activeRocketFilter)) {
                Log.d("SettingsImport", "Setting active rocket filter: ${importData.activeRocketFilter}")
                try {
                    filterPreferences.setActiveRocketFilter(importData.activeRocketFilter)
                    // If active, also load it
                    filterPreferences.loadFilter(importData.activeRocketFilter, FILTER_TYPE_ROCKET)
                    Log.d("SettingsImport", "Successfully activated rocket filter: ${importData.activeRocketFilter}")
                } catch (e: Exception) {
                    Log.e("SettingsImport", "Error activating rocket filter: ${e.message}", e)
                }
            }

            // Import saved quest filters (non-null in ExportData)
            val questFilters = importData.savedQuestFilters
            Log.d("SettingsImport", "Found ${questFilters.size} quest filters to import")

            // Clear existing quest filters first
            val existingQuestFilterNames = filterPreferences.listQuestFilterNames()
            Log.d("SettingsImport", "Clearing ${existingQuestFilterNames.size} existing quest filters: $existingQuestFilterNames")
            for (name in existingQuestFilterNames) {
                Log.d("SettingsImport", "Deleting quest filter: $name")
                filterPreferences.deleteFilter(name, FILTER_TYPE_QUEST)
            }

            // Import the quest filters
            for ((name, questIds) in questFilters) {
                val questStrings = questIds.toSet()
                Log.d("SettingsImport", "Importing quest filter '$name' with ${questStrings.size} quests: $questStrings")
                try {
                    filterPreferences.saveEnabledQuestFilters(questStrings)
                    val forms: Set<String> = importData.savedQuestSpindaForms[name] ?: emptySet()
                    filterPreferences.saveEnabledSpindaForms(forms)
                    filterPreferences.saveCurrentQuestFilter(name)
                    Log.d("SettingsImport", "Successfully saved quest filter: $name")
                } catch (e: Exception) {
                    Log.e("SettingsImport", "Error saving quest filter '$name': ${e.message}", e)
                }
            }

            // Set active quest filter if it exists in the imported data
            Log.d("SettingsImport", "Active quest filter from import: ${importData.activeQuestFilter}")
            if (importData.activeQuestFilter.isNotEmpty() &&
                questFilters.containsKey(importData.activeQuestFilter)) {
                Log.d("SettingsImport", "Setting active quest filter: ${importData.activeQuestFilter}")
                try {
                    filterPreferences.setActiveQuestFilter(importData.activeQuestFilter)
                    // If active, also load it
                    filterPreferences.loadFilter(importData.activeQuestFilter, FILTER_TYPE_QUEST)
                    Log.d("SettingsImport", "Successfully activated quest filter: ${importData.activeQuestFilter}")
                } catch (e: Exception) {
                    Log.e("SettingsImport", "Error activating quest filter: ${e.message}", e)
                }
            }

            // Update UI checkboxes and overlay customization
            checkboxNYC.isChecked = importData.dataSources.contains(SOURCE_NYC)
            checkboxLondon.isChecked = importData.dataSources.contains(SOURCE_LONDON)
            checkboxSG.isChecked = importData.dataSources.contains(SOURCE_SINGAPORE)
            checkboxVancouver.isChecked = importData.dataSources.contains(SOURCE_VANCOUVER)
            checkboxSydney.isChecked = importData.dataSources.contains(SOURCE_SYDNEY)

            customizationManager.saveButtonSize(importData.overlayButtonSize)
            customizationManager.saveButtonOrder(importData.overlayButtonOrder)
            customizationManager.saveButtonVisibility(importData.overlayButtonVisibility)


            Toast.makeText(requireContext(), getString(R.string.settings_import_success), Toast.LENGTH_LONG).show()
        } catch (ex: Exception) {
            Log.e("SettingsImport", "Import failed with exception: ${ex.message}", ex)
            Toast.makeText(requireContext(), getString(R.string.settings_import_failed, ex.message ?: "Unknown error"), Toast.LENGTH_LONG).show()
        }
    }
}
