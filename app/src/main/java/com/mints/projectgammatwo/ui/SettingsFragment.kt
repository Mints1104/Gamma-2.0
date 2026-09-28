package com.mints.projectgammatwo.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.reflect.TypeToken
import com.mints.projectgammatwo.R
import com.mints.projectgammatwo.data.DataSourcePreferences
import com.mints.projectgammatwo.data.DeletedInvasionsRepository
import com.mints.projectgammatwo.data.FavoriteLocation
import com.mints.projectgammatwo.data.FavoritesManager
import com.mints.projectgammatwo.data.FilterPreferences
import com.mints.projectgammatwo.data.HomeCoordinatesManager
import com.mints.projectgammatwo.data.ExportData
import com.mints.projectgammatwo.data.DeeplinkManager
import com.mints.projectgammatwo.data.decodeConditionMap
import com.mints.projectgammatwo.data.decodeConditionSet
import com.mints.projectgammatwo.data.encodeConditionMap
import com.mints.projectgammatwo.data.encodeConditionSet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.json.Json
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsFragment : Fragment() {

    companion object {
        private const val TELEPORT_PREFS_NAME = "teleport_prefs"
        private const val KEY_TELEPORT_METHOD = "teleport_method"

        private const val SOURCE_NYC = "NYC"
        private const val SOURCE_LONDON = "LONDON"
        private const val SOURCE_SINGAPORE = "Singapore"
        private const val SOURCE_VANCOUVER = "VANCOUVER"
        private const val SOURCE_SYDNEY = "SYDNEY"

        private const val FILTER_TYPE_ROCKET = "Rocket"
        private const val FILTER_TYPE_QUEST = "Quest"

        private const val IMPORT_REQUEST_KEY = "import_settings"
        private const val TAG_IMPORT_DIALOG = "import_settings_dialog"
        private const val TAG_CONFIRM_DEEPLINK = "confirm_imported_deeplink"
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

    private lateinit var radioGroupDeeplink: RadioGroup
    private lateinit var radioDeeplinkIpogo: RadioButton
    private lateinit var radioDeeplinkPokemod: RadioButton
    private lateinit var radioDeeplinkCustom: RadioButton
    private lateinit var customDeeplinkUrl: EditText
    private lateinit var customDeeplinkExample: TextView

    private lateinit var dataSourcePreferences: DataSourcePreferences
    private lateinit var filterPreferences: FilterPreferences
    private lateinit var deletedRepo: DeletedInvasionsRepository
    private lateinit var discordTextView: TextView
    private lateinit var homeCoordinates: EditText
    private lateinit var homeCoordinatesManager: HomeCoordinatesManager
    private lateinit var deeplinkManager: DeeplinkManager

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

    private val exportSettingsLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) writeExport(uri) // null: the user backed out of the picker
        }

    private val importSettingsFileLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) readImportFile(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Registered here rather than where the dialogs are shown: after a rotation the dialogs
        // come back on their own, and only a listener set up in onCreate is there to hear them.
        childFragmentManager.setFragmentResultListener(IMPORT_REQUEST_KEY, this) { _, result ->
            if (result.getBoolean(JsonImportDialogFragment.RESULT_CHOOSE_FILE)) {
                // Export writes a file, so importing one is the matching path back in; pasting
                // JSON still works for backups made by older versions.
                importSettingsFileLauncher.launch(
                    arrayOf("application/json", "text/plain", "application/octet-stream")
                )
            } else {
                result.getString(JsonImportDialogFragment.RESULT_JSON)?.let { importSettings(it) }
            }
        }
        childFragmentManager.setFragmentResultListener(ConfirmImportedDeeplinkDialogFragment.REQUEST_KEY, this) { _, result ->
            result.getString(ConfirmImportedDeeplinkDialogFragment.RESULT_URL)?.let { applyCustomDeeplink(it) }
        }
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
        deeplinkManager = DeeplinkManager.getInstance(requireContext())

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
        radioGroupDeeplink = view.findViewById(R.id.radioGroupDeeplink)
        radioDeeplinkIpogo = view.findViewById(R.id.radio_deeplink_ipogo)
        radioDeeplinkPokemod = view.findViewById(R.id.radio_deeplink_pokemod)
        radioDeeplinkCustom = view.findViewById(R.id.radio_deeplink_custom)
        customDeeplinkUrl = view.findViewById(R.id.customDeeplinkUrl)
        customDeeplinkExample = view.findViewById(R.id.customDeeplinkExample)
        discordTextView = view.findViewById(R.id.discordInvite)
        homeCoordinates = view.findViewById(R.id.homeCoordinates)
        btnCustomizeOverlay = view.findViewById(R.id.btnCustomizeOverlay)

        setupDiscordText()
        setupDataSourceCheckboxes()
        setupExportImportButtons()
        setupTeleportMethod()
        setupDeeplinkMethod()
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

    private fun setupDeeplinkMethod() {
        // Load saved deeplink type
        val savedType = deeplinkManager.getDeeplinkType()
        when (savedType) {
            DeeplinkManager.TYPE_IPOGO -> radioDeeplinkIpogo.isChecked = true
            DeeplinkManager.TYPE_POKEMOD -> radioDeeplinkPokemod.isChecked = true
            DeeplinkManager.TYPE_CUSTOM -> {
                radioDeeplinkCustom.isChecked = true
                customDeeplinkUrl.visibility = View.VISIBLE
                customDeeplinkExample.visibility = View.VISIBLE
            }
        }

        // Load saved custom URL
        customDeeplinkUrl.setText(deeplinkManager.getCustomUrl())

        // Handle radio button changes
        radioGroupDeeplink.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.radio_deeplink_ipogo -> {
                    deeplinkManager.setDeeplinkType(DeeplinkManager.TYPE_IPOGO)
                    customDeeplinkUrl.visibility = View.GONE
                    customDeeplinkExample.visibility = View.GONE
                }
                R.id.radio_deeplink_pokemod -> {
                    deeplinkManager.setDeeplinkType(DeeplinkManager.TYPE_POKEMOD)
                    customDeeplinkUrl.visibility = View.GONE
                    customDeeplinkExample.visibility = View.GONE
                }
                R.id.radio_deeplink_custom -> {
                    deeplinkManager.setDeeplinkType(DeeplinkManager.TYPE_CUSTOM)
                    customDeeplinkUrl.visibility = View.VISIBLE
                    customDeeplinkExample.visibility = View.VISIBLE
                }
            }
        }

        // Handle custom URL changes
        customDeeplinkUrl.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val url = customDeeplinkUrl.text.toString().trim()
                if (url.isNotEmpty()) {
                    deeplinkManager.setCustomUrl(url)
                    if (!url.contains("%s")) {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.deeplink_custom_url_invalid),
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.deeplink_custom_url_saved),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
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
        // The in-memory flag, not the old "overlay_running" pref: a crash or force-stop left the
        // pref true, and saving a customization then started an overlay nobody had open.
        val isOverlayRunning = com.mints.projectgammatwo.services.OverlayService.running.value == true
        val sharedPrefs = requireContext().getSharedPreferences("overlay_prefs", Context.MODE_PRIVATE)
        val currentMode = sharedPrefs.getString("overlay_mode", "invasions") ?: "invasions"

        if (isOverlayRunning) {
            // Application context: the restart runs after a delay, by which time this fragment
            // may be gone and requireContext() would throw.
            val appContext = requireContext().applicationContext
            val stopIntent = Intent(appContext, com.mints.projectgammatwo.services.OverlayService::class.java)
            appContext.stopService(stopIntent)

            // Wait a bit before restarting
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                // Restart the service
                val startIntent = Intent(appContext, com.mints.projectgammatwo.services.OverlayService::class.java)
                startIntent.putExtra("mode", currentMode)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appContext.startForegroundService(startIntent)
                } else {
                    appContext.startService(startIntent)
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
     * Saves settings to a JSON file the user chooses; [writeExport] does the work once they have.
     *
     * Replaces sharing the JSON as intent text, which could exceed the binder transaction limit
     * (TransactionTooLargeException) once favorites and a day's deletions were included, and
     * sent home coordinates as plain text into whichever app was picked from the share sheet.
     */
    private fun exportSettings() {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        exportSettingsLauncher.launch("gamma-settings-$date.json")
    }

    /**
     * Builds the export and writes it to [uri], off the main thread. The JSON is built here, once
     * the location is known, rather than before opening the picker: the picker can outlive this
     * fragment across a rotation, and JSON held in a field would be lost with it.
     */
    private fun writeExport(uri: Uri) {
        val context = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val error: Exception? = withContext(Dispatchers.IO) {
                try {
                    val json = buildExportJson(context)
                    val out = context.contentResolver.openOutputStream(uri, "wt")
                        ?: throw IOException("Couldn't open the chosen file")
                    out.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                    null
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    e
                }
            }
            if (error == null) {
                Toast.makeText(context, R.string.settings_export_saved, Toast.LENGTH_SHORT).show()
            } else {
                Log.e("SettingsExport", "Error exporting settings: ${error.message}", error)
                Toast.makeText(context, getString(R.string.settings_export_failed, error.message ?: "Unknown error"), Toast.LENGTH_LONG).show()
            }
        }
    }

    /** Snapshots every exported setting as JSON. Only reads preferences, so safe off the main thread. */
    private fun buildExportJson(context: Context): String {
        Log.d("SettingsExport", "Starting export process")
        val dataSources = dataSourcePreferences.getSelectedSources()
        Log.d("SettingsExport", "Data sources: $dataSources")

        val enabledCharacters = filterPreferences.getEnabledCharacters()
        Log.d("SettingsExport", "Enabled characters: $enabledCharacters")

        // Read through FavoritesManager so the export carries favorites in the user's manual
        // order; the raw stored list is not in that order, so exporting it directly lost the
        // arrangement on the way back in.
        val favorites: List<FavoriteLocation> = FavoritesManager.getFavorites(context)
        Log.d("SettingsExport", "Favorites count: ${favorites.size}")

        val enabledEncounterConditions = filterPreferences.getEnabledEncounterConditions()
        Log.d("SettingsExport", "Enabled encounter conditions: ${enabledEncounterConditions.size}")
        val deletedEntries = deletedRepo.getDeletedEntries()
        Log.d("SettingsExport", "Deleted entries count: ${deletedEntries.size}")

        // Get home coordinates from manager
        val homeCoords = homeCoordinatesManager.getHomeCoordinatesString()
        Log.d("SettingsExport", "Home coordinates: $homeCoords")

        // Get all saved rocket filters
        val savedRocketFilters = filterPreferences.getAllSavedFilters()
        Log.d("SettingsExport", "Saved rocket filters: ${savedRocketFilters.keys}")

        // Get all saved quest filters and their encounter condition snapshots
        val savedQuestFilters = filterPreferences.getSavedQuestFilters()
        Log.d("SettingsExport", "Saved quest filters: ${savedQuestFilters.keys}")
        val savedQuestEncounterConditions = filterPreferences.getSavedQuestEncounterConditions()
        Log.d("SettingsExport", "Saved quest encounter conditions: ${savedQuestEncounterConditions.keys}")

        // Get active filter names
        val activeRocketFilter = filterPreferences.getActiveRocketFilter()
        val activeQuestFilter = filterPreferences.getActiveQuestFilter()
        Log.d("SettingsExport", "Active filters - Rocket: $activeRocketFilter, Quest: $activeQuestFilter")

        // Get overlay customization settings
        val overlayButtonSize = customizationManager.getButtonSize()
        val overlayButtonOrder = customizationManager.getButtonOrder()
        val overlayButtonVisibility = customizationManager.getButtonVisibility()
        Log.d("SettingsExport", "Overlay customization - Size: $overlayButtonSize, Order: $overlayButtonOrder")

        // Get deeplink preferences
        val deeplinkType = deeplinkManager.getDeeplinkType()
        val deeplinkCustomUrl = deeplinkManager.getCustomUrl()
        Log.d("SettingsExport", "Deeplink settings - Type: $deeplinkType, Custom URL: $deeplinkCustomUrl")

        val exportData = ExportData(
            dataSources = dataSources,
            enabledCharacters = enabledCharacters,
            favorites = favorites,
            deletedEntries = deletedEntries,
            enabledEncounterConditionsB64 = encodeConditionSet(enabledEncounterConditions),
            enabledQuestFilters = filterPreferences.getEnabledQuestFilters(),
            homeCoordinates = homeCoords,
            savedRocketFilters = savedRocketFilters,
            savedQuestFilters = savedQuestFilters,
            savedQuestEncounterConditionsB64 = encodeConditionMap(savedQuestEncounterConditions),
            activeRocketFilter = activeRocketFilter,
            activeQuestFilter = activeQuestFilter,
            overlayButtonSize = overlayButtonSize,
            overlayButtonOrder = overlayButtonOrder,
            overlayButtonVisibility = overlayButtonVisibility,
            deeplinkType = deeplinkType,
            deeplinkCustomUrl = deeplinkCustomUrl
        )

        val exportJson = kxJson.encodeToString(exportData)
        Log.d("SettingsExport", "JSON created successfully, length: ${exportJson.length}")
        return exportJson
    }

    /**
     * Displays a dialog for importing settings from JSON.
     */
    private fun importSettingsDialog() {
        // The result is handled by the listener registered in onCreate.
        JsonImportDialogFragment.newInstance(IMPORT_REQUEST_KEY, R.layout.dialog_import_settings)
            .show(childFragmentManager, TAG_IMPORT_DIALOG)
    }

    /** Whether [json] is an object carrying at least one [ExportData] field. */
    private fun looksLikeSettingsBackup(json: String): Boolean {
        // Parsed as leniently as the Gson fallback in importSettings, so nothing it accepts is
        // turned away here.
        val root = try {
            gson.fromJson(json, JsonElement::class.java)
        } catch (e: Exception) {
            return false
        }
        if (root == null || !root.isJsonObject) return false
        val knownKeys = ExportData.serializer().descriptor.elementNames.toSet()
        return root.asJsonObject.keySet().any { it in knownKeys }
    }

    /** Points teleports at [url] and shows it in the custom-link field. */
    private fun applyCustomDeeplink(url: String) {
        deeplinkManager.setCustomUrl(url)
        deeplinkManager.setDeeplinkType(DeeplinkManager.TYPE_CUSTOM)
        customDeeplinkUrl.setText(url)
        // Its change listener also reveals the custom-link field.
        radioDeeplinkCustom.isChecked = true
    }

    /** Reads a settings file off the main thread, then imports it. */
    private fun readImportFile(uri: Uri) {
        val context = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val json = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("SettingsImport", "Couldn't read settings file: ${e.message}", e)
                    null
                }
            }
            if (json.isNullOrBlank()) {
                Toast.makeText(context, R.string.settings_import_read_failed, Toast.LENGTH_LONG).show()
            } else {
                importSettings(json)
            }
        }
    }

    /**
     * Imports settings from the provided JSON string.
     */
    private fun importSettings(jsonString: String) {
        try {
            Log.d("SettingsImport", "Starting import process with JSON: ${jsonString.take(100)}...")

            // Import replaces every setting, and the Gson fallback below is lenient: it read
            // text like "{foo:bar}", or any JSON that isn't a Gamma backup, as a backup with
            // every field missing, and the import then wiped favorites, filters and sources.
            if (!looksLikeSettingsBackup(jsonString)) {
                Toast.makeText(requireContext(), R.string.settings_import_not_a_backup, Toast.LENGTH_LONG).show()
                return
            }

            val importData: ExportData = try {
                val parsed = kxJson.decodeFromString<ExportData>(jsonString)
                Log.d("SettingsImport", "Parsed with kotlinx.serialization")
                parsed
            } catch (e: Exception) {
                Log.w("SettingsImport", "kotlinx.serialization failed: ${e.message}. Falling back to Gson")
                gson.fromJson(jsonString, ExportData::class.java)
            }
            Log.d("SettingsImport", "Successfully parsed JSON into ExportData")

            // Basic settings import — guard every field with ?: in case Gson returned
            // null for a missing key (Gson does not apply Kotlin default parameter values).
            Log.d("SettingsImport", "Importing data sources: ${importData.dataSources}")
            dataSourcePreferences.setSelectedSources(importData.dataSources ?: emptySet())

            Log.d("SettingsImport", "Importing enabled characters: ${importData.enabledCharacters}")
            // The live filter selections are written after the saved filters below: saving each
            // filter goes through the live sets, which would otherwise be left holding whichever
            // saved filter the loop happened to process last. Capture the device's current quest
            // selection first, as a fallback for older exports that don't carry one — deleting
            // the active quest filter below would otherwise wipe it.
            val questFiltersBeforeImport = filterPreferences.getEnabledQuestFilters()
            val encounterConditionsBeforeImport = filterPreferences.getEnabledEncounterConditions()

            Log.d("SettingsImport", "Importing ${importData.deletedEntries?.size ?: 0} deleted entries")
            deletedRepo.setDeletedEntries(importData.deletedEntries ?: emptySet())

            Log.d("SettingsImport", "Importing ${importData.favorites?.size ?: 0} favorites")
            // Goes through FavoritesManager so the saved name order is rewritten alongside the
            // list. Writing only the list left the previous favorites' order in place, which
            // then got applied to the imported ones.
            FavoritesManager.saveFavorites(requireContext(), importData.favorites ?: emptyList())

            // Import home coordinates if available and valid
            Log.d("SettingsImport", "Importing home coordinates: ${importData.homeCoordinates}")
            val homeCoords = importData.homeCoordinates ?: ""
            if (homeCoords.isNotEmpty() && homeCoordinatesManager.validateCoordinates(homeCoords)) {
                homeCoordinatesManager.saveHomeCoordinates(homeCoords)
                homeCoordinates.setText(homeCoords)
                Log.d("SettingsImport", "Home coordinates imported successfully")
            } else {
                Log.d("SettingsImport", "Home coordinates empty or invalid")
            }

            // Import saved rocket filters
            val rocketFilters = importData.savedRocketFilters ?: emptyMap()
            Log.d("SettingsImport", "Found ${rocketFilters.size} rocket filters to import")

            // Clear existing filters first
            val existingFilterNames = filterPreferences.listFilterNames()
            Log.d("SettingsImport", "Clearing ${existingFilterNames.size} existing rocket filters: $existingFilterNames")
            for (name in existingFilterNames) {
                filterPreferences.deleteFilter(name, FILTER_TYPE_ROCKET)
            }

            // Clear active rocket filter before the import loop so saveEnabledCharacters
            // doesn't auto-overwrite the previously active filter's snapshot on each iteration.
            filterPreferences.clearActiveRocketFilter()

            for ((name, characters) in rocketFilters) {
                Log.d("SettingsImport", "Importing rocket filter '$name' with ${characters.size} characters")
                try {
                    filterPreferences.saveEnabledCharacters(characters)
                    filterPreferences.saveCurrentAsFilter(name)
                    Log.d("SettingsImport", "Successfully saved rocket filter: $name")
                } catch (e: Exception) {
                    Log.e("SettingsImport", "Error saving rocket filter '$name': ${e.message}", e)
                }
            }

            // Set active rocket filter
            val activeRocketFilter = importData.activeRocketFilter ?: ""
            Log.d("SettingsImport", "Active rocket filter from import: $activeRocketFilter")
            val activeRocketLoaded = activeRocketFilter.isNotEmpty() && rocketFilters.containsKey(activeRocketFilter)
            if (activeRocketLoaded) {
                Log.d("SettingsImport", "Setting active rocket filter: $activeRocketFilter")
                try {
                    filterPreferences.setActiveRocketFilter(activeRocketFilter)
                    filterPreferences.loadFilter(activeRocketFilter, FILTER_TYPE_ROCKET)
                    Log.d("SettingsImport", "Successfully activated rocket filter: $activeRocketFilter")
                } catch (e: Exception) {
                    Log.e("SettingsImport", "Error activating rocket filter: ${e.message}", e)
                }
            } else {
                // No named filter to load it from, so restore the live selection as exported.
                filterPreferences.saveEnabledCharacters(importData.enabledCharacters ?: emptySet())
            }

            // Import saved quest filters
            val questFilters = importData.savedQuestFilters ?: emptyMap()
            Log.d("SettingsImport", "Found ${questFilters.size} quest filters to import")

            // Clear existing quest filters first
            val existingQuestFilterNames = filterPreferences.listQuestFilterNames()
            Log.d("SettingsImport", "Clearing ${existingQuestFilterNames.size} existing quest filters")
            for (name in existingQuestFilterNames) {
                filterPreferences.deleteFilter(name, FILTER_TYPE_QUEST)
            }

            // Decode per-filter encounter condition snapshots (B64 field, fallback to legacy).
            val decodedQuestEncounterConditions = decodeConditionMap(
                importData.savedQuestEncounterConditionsB64,
                importData.savedQuestEncounterConditions
            )
            for ((name, questIds) in questFilters) {
                val questStrings = questIds.toSet()
                val encounterConditions = decodedQuestEncounterConditions[name] ?: emptySet()
                Log.d("SettingsImport", "Importing quest filter '$name' with ${questStrings.size} quests and ${encounterConditions.size} encounter conditions")
                try {
                    filterPreferences.saveEnabledQuestFilters(questStrings)
                    filterPreferences.saveEnabledEncounterConditions(encounterConditions)
                    filterPreferences.saveCurrentQuestFilter(name)
                    Log.d("SettingsImport", "Successfully saved quest filter: $name")
                } catch (e: Exception) {
                    Log.e("SettingsImport", "Error saving quest filter '$name': ${e.message}", e)
                }
            }

            // Set active quest filter
            val activeQuestFilter = importData.activeQuestFilter ?: ""
            Log.d("SettingsImport", "Active quest filter from import: $activeQuestFilter")
            val activeQuestLoaded = activeQuestFilter.isNotEmpty() && questFilters.containsKey(activeQuestFilter)
            if (activeQuestLoaded) {
                Log.d("SettingsImport", "Setting active quest filter: $activeQuestFilter")
                try {
                    filterPreferences.setActiveQuestFilter(activeQuestFilter)
                    filterPreferences.loadFilter(activeQuestFilter, FILTER_TYPE_QUEST)
                    Log.d("SettingsImport", "Successfully activated quest filter: $activeQuestFilter")
                } catch (e: Exception) {
                    Log.e("SettingsImport", "Error activating quest filter: ${e.message}", e)
                }
            }

            // Restore the live quest selection only when no active named filter was loaded —
            // loadFilter already wrote both halves from the snapshot. Base filters and encounter
            // conditions are always restored together: they are two halves of one selection, and
            // leaving either as the last saved filter's value pairs conditions with the wrong
            // bases. That included an exported *empty* condition set, which used to be skipped.
            if (!activeQuestLoaded) {
                // Older exports lack the live quest filters; keep what the device had.
                filterPreferences.saveEnabledQuestFilters(
                    importData.enabledQuestFilters ?: questFiltersBeforeImport
                )
                val conditionsExported = importData.enabledEncounterConditionsB64 != null ||
                        importData.enabledEncounterConditions != null
                val importedEncounterConditions = if (conditionsExported) {
                    decodeConditionSet(
                        importData.enabledEncounterConditionsB64,
                        importData.enabledEncounterConditions
                    )
                } else {
                    encounterConditionsBeforeImport
                }
                filterPreferences.saveEnabledEncounterConditions(importedEncounterConditions)
                Log.d("SettingsImport", "Restored ${importedEncounterConditions.size} active encounter conditions")
            }

            // Update UI checkboxes
            val sources = importData.dataSources ?: emptySet()
            checkboxNYC.isChecked = sources.contains(SOURCE_NYC)
            checkboxLondon.isChecked = sources.contains(SOURCE_LONDON)
            checkboxSG.isChecked = sources.contains(SOURCE_SINGAPORE)
            checkboxVancouver.isChecked = sources.contains(SOURCE_VANCOUVER)
            checkboxSydney.isChecked = sources.contains(SOURCE_SYDNEY)

            customizationManager.saveButtonSize(importData.overlayButtonSize ?: 48)
            customizationManager.saveButtonOrder(importData.overlayButtonOrder ?: emptyList())
            customizationManager.saveButtonVisibility(importData.overlayButtonVisibility ?: emptyMap())

            // Import deeplink preferences. A custom link is where every teleport goes, so a new
            // one is only applied once the user has seen it and agreed (the import used to
            // switch it silently, letting a shared backup redirect all teleports). Built-in
            // types are applied directly and leave the user's own custom link alone.
            val dlType = importData.deeplinkType ?: DeeplinkManager.TYPE_IPOGO
            val dlUrl = importData.deeplinkCustomUrl?.trim().orEmpty()
            Log.d("SettingsImport", "Importing deeplink settings - Type: $dlType, Custom URL: $dlUrl")
            var confirmDeeplink = false
            when (dlType) {
                DeeplinkManager.TYPE_CUSTOM -> {
                    val alreadyInUse = deeplinkManager.getDeeplinkType() == DeeplinkManager.TYPE_CUSTOM &&
                            deeplinkManager.getCustomUrl() == dlUrl
                    // A blank link falls back to iPogo, so there's nothing to confirm.
                    if (dlUrl.isNotEmpty() && !alreadyInUse) confirmDeeplink = true
                    else radioDeeplinkCustom.isChecked = true
                }
                DeeplinkManager.TYPE_IPOGO -> radioDeeplinkIpogo.isChecked = true
                DeeplinkManager.TYPE_POKEMOD -> radioDeeplinkPokemod.isChecked = true
            }

            Toast.makeText(requireContext(), getString(R.string.settings_import_success), Toast.LENGTH_LONG).show()
            if (confirmDeeplink) {
                ConfirmImportedDeeplinkDialogFragment.newInstance(dlUrl)
                    .show(childFragmentManager, TAG_CONFIRM_DEEPLINK)
            }
        } catch (ex: Exception) {
            Log.e("SettingsImport", "Import failed with exception: ${ex.message}", ex)
            Toast.makeText(requireContext(), getString(R.string.settings_import_failed, ex.message ?: "Unknown error"), Toast.LENGTH_LONG).show()
        }
    }
}
