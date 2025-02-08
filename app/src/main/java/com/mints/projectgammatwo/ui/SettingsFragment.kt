package com.mints.projectgammatwo.ui

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mints.projectgammatwo.R
import com.mints.projectgammatwo.data.DataSourcePreferences
import com.mints.projectgammatwo.data.DeletedInvasionsRepository
import com.mints.projectgammatwo.data.DeletedEntry
import com.mints.projectgammatwo.data.FilterPreferences
import com.mints.projectgammatwo.data.FavoriteLocation

class SettingsFragment : Fragment() {

    private lateinit var resetVisitedButton: Button
    private lateinit var resetFiltersButton: Button
    private lateinit var wipeFiltersButton: Button
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

        resetVisitedButton = view.findViewById(R.id.resetVisitedButton)
        resetFiltersButton = view.findViewById(R.id.resetFiltersButton)
        wipeFiltersButton = view.findViewById(R.id.wipeFiltersButton)
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

        // Load data source selections.
        val selectedSources = dataSourcePreferences.getSelectedSources()
        checkboxNYC.isChecked = selectedSources.contains("NYC")
        checkboxLondon.isChecked = selectedSources.contains("LONDON")
        checkboxSG.isChecked = selectedSources.contains("SG")
        checkboxVancouver.isChecked = selectedSources.contains("VANCOUVER")
        checkboxSydney.isChecked = selectedSources.contains("SYDNEY")

        // Data source check listener.
        val checkListener = View.OnClickListener {
            val newSelection = mutableSetOf<String>()
            if (checkboxNYC.isChecked) newSelection.add("NYC")
            if (checkboxLondon.isChecked) newSelection.add("LONDON")
            if (checkboxSG.isChecked) newSelection.add("SG")
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

        // Reset and wipe buttons.
        resetVisitedButton.setOnClickListener {
            deletedRepo.resetDeletedInvasions()
            Toast.makeText(requireContext(), "Visited invasions reset", Toast.LENGTH_SHORT).show()
        }
        resetFiltersButton.setOnClickListener {
            filterPreferences.resetToDefault()
            Toast.makeText(requireContext(), "Invasion filters reset to default", Toast.LENGTH_SHORT).show()
        }
        wipeFiltersButton.setOnClickListener {
            filterPreferences.wipeFilters()
            Toast.makeText(requireContext(), "All invasion filters wiped", Toast.LENGTH_SHORT).show()
        }

        // Export settings.
        btnExportSettings.setOnClickListener {
            exportSettings()
        }

        // Import settings.
        btnImportSettings.setOnClickListener {
            importSettingsDialog()
        }

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

    /**
     * Data class representing all settings to be exported/imported.
     */
    data class ExportData(
        val dataSources: Set<String>,
        val enabledCharacters: Set<Int>,
        val favorites: List<FavoriteLocation>,
        val deletedEntries: Set<DeletedEntry>
    )

    /**
     * Exports settings to a JSON string and launches a share intent.
     */
    private fun exportSettings() {
        val dataSources = dataSourcePreferences.getSelectedSources()
        val enabledCharacters = filterPreferences.getEnabledCharacters()
        val favoritesPrefs = requireContext().getSharedPreferences(FAVORITES_PREFS_NAME, Context.MODE_PRIVATE)
        val favoritesJson = favoritesPrefs.getString(KEY_FAVORITES, "[]")
        val favoritesType = object : TypeToken<List<FavoriteLocation>>() {}.type
        val favorites: List<FavoriteLocation> = gson.fromJson(favoritesJson, favoritesType)
        val deletedEntries = deletedRepo.getDeletedEntries()

        val exportData = ExportData(
            dataSources = dataSources,
            enabledCharacters = enabledCharacters,
            favorites = favorites,
            deletedEntries = deletedEntries
        )
        val exportJson = gson.toJson(exportData)

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Exported Settings")
            putExtra(Intent.EXTRA_TEXT, exportJson)
        }
        startActivity(Intent.createChooser(shareIntent, "Share Settings JSON"))
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
            val importData = gson.fromJson(jsonString, ExportData::class.java)
            dataSourcePreferences.setSelectedSources(importData.dataSources)
            filterPreferences.saveEnabledCharacters(importData.enabledCharacters)
            val favoritesPrefs = requireContext().getSharedPreferences(FAVORITES_PREFS_NAME, Context.MODE_PRIVATE)
            favoritesPrefs.edit().putString(KEY_FAVORITES, gson.toJson(importData.favorites)).apply()
            deletedRepo.setDeletedEntries(importData.deletedEntries)

            // Update UI checkboxes.
            checkboxNYC.isChecked = importData.dataSources.contains("NYC")
            checkboxLondon.isChecked = importData.dataSources.contains("LONDON")
            checkboxSG.isChecked = importData.dataSources.contains("SG")
            checkboxVancouver.isChecked = importData.dataSources.contains("VANCOUVER")
            checkboxSydney.isChecked = importData.dataSources.contains("SYDNEY")

            Toast.makeText(requireContext(), "Settings imported successfully", Toast.LENGTH_LONG).show()
        } catch (ex: Exception) {
            ex.printStackTrace()
            Toast.makeText(requireContext(), "Failed to import settings: ${ex.message}", Toast.LENGTH_LONG).show()
        }
    }
}
