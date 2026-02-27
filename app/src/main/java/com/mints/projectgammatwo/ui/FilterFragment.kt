package com.mints.projectgammatwo.ui

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.button.MaterialButton
import com.google.gson.Gson
import com.mints.projectgammatwo.R
import com.mints.projectgammatwo.data.DataMappings
import com.mints.projectgammatwo.data.FilterPreferences
import com.mints.projectgammatwo.data.Quests
import com.mints.projectgammatwo.viewmodels.QuestsViewModel
import java.text.DateFormat
import java.util.Date
import java.util.Locale

class FilterFragment : Fragment() {

    private lateinit var filterPreferences: FilterPreferences
    private val enabledRocketFilters = mutableSetOf<Int>()
    private lateinit var questsViewModel: QuestsViewModel
    private var currentFilterType = "Rocket"
    private lateinit var questPrefs: SharedPreferences
    private val enabledQuestFilters = mutableSetOf<String>()
    private val enabledEncounterConditions = mutableSetOf<String>()
    private lateinit var questLayout: LinearLayout
    private lateinit var questLoadingGroup: LinearLayout
    private lateinit var filterLastRefreshedText: TextView
    private lateinit var rocketLayoutGlobal: LinearLayout
    private lateinit var currentFilterTextView: TextView
    /** Set to true once DataMappings.initializePokemonData completes. */
    private var pokemonDataReady = false
    /** Guard to avoid persisting state while rebuilding the quest filter UI. */
    private var isRebuildingQuestFilters = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        filterPreferences = FilterPreferences(requireContext())
        questPrefs = requireContext().getSharedPreferences("quest_filters", Context.MODE_PRIVATE)
        currentFilterTextView = view.findViewById(R.id.currentFilterText)
        rocketLayoutGlobal = view.findViewById(R.id.rocketFiltersLayout)
        questLayout = view.findViewById(R.id.questFiltersLayout)
        questLoadingGroup = view.findViewById(R.id.questLoadingGroup)
        filterLastRefreshedText = view.findViewById(R.id.filterLastRefreshedText)
        enabledRocketFilters.clear()
        enabledRocketFilters.addAll(filterPreferences.getEnabledCharacters())

        enabledQuestFilters.clear()
        enabledQuestFilters.addAll(filterPreferences.getEnabledQuestFilters())
        enabledEncounterConditions.clear()
        enabledEncounterConditions.addAll(filterPreferences.getEnabledEncounterConditions())

        questsViewModel = ViewModelProvider(requireActivity())[QuestsViewModel::class.java]

        questsViewModel.variantsLoadingLiveData.observe(viewLifecycleOwner) { isLoading ->
            updateQuestLoadingVisibility(isLoading)
        }

        // Initialise Pokemon name data then build the filter UI once with whatever
        // sub-variants are already cached; the observer below will rebuild if fresh
        // data arrives from the network.
        DataMappings.initializePokemonData(requireContext()) {
            pokemonDataReady = true
            if (isAdded) setupQuestFilters(questLayout)
        }

        questsViewModel.rewardSubVariantsLiveData.observe(viewLifecycleOwner) {
            // Only rebuild the quest filter UI when the quest tab is active AND pokemon
            // data is ready. Rebuilding while on another tab can overwrite user state.
            if (isAdded && pokemonDataReady && currentFilterType == "Quest") {
                setupQuestFilters(questLayout)
            }
            // Read the timestamp that the ViewModel wrote alongside the fresh network data.
            val lastRefreshed = questPrefs.getLong("filters_last_refreshed", 0L)
            if (lastRefreshed > 0L) updateFiltersLastRefreshed(lastRefreshed)
        }

        val radioGroup = view.findViewById<RadioGroup>(R.id.filterTypeRadioGroup)

        radioGroup.post {
            val isQuestVisible = questLayout.visibility == View.VISIBLE
            val isRocketVisible = rocketLayoutGlobal.visibility == View.VISIBLE

            when {
                isQuestVisible -> {
                    radioGroup.check(R.id.rbQuest)
                    updateCurrentQuestFilter()
                    currentFilterType = "Quest"
                    val initialLastRefreshed = questPrefs.getLong("filters_last_refreshed", 0L)
                    if (initialLastRefreshed > 0L) updateFiltersLastRefreshed(initialLastRefreshed)
                }
                isRocketVisible -> {
                    radioGroup.check(R.id.rbRocket)
                    updateCurrentRocketFilter()
                }
                else -> {
                    radioGroup.check(R.id.rbRocket)
                    rocketLayoutGlobal.visibility = View.VISIBLE
                    questLayout.visibility = View.GONE
                    updateCurrentRocketFilter()
                    currentFilterType = "Rocket"
                }
            }
            activity?.invalidateOptionsMenu()

            radioGroup.setOnCheckedChangeListener { _, checkedId ->
                when (checkedId) {
                    R.id.rbRocket -> {
                        rocketLayoutGlobal.visibility = View.VISIBLE
                        questLayout.visibility = View.GONE
                        currentFilterType = "Rocket"
                        updateCurrentRocketFilter()
                        updateQuestLoadingVisibility(false)
                        filterLastRefreshedText.visibility = View.GONE
                    }
                    R.id.rbQuest -> {
                        rocketLayoutGlobal.visibility = View.GONE
                        questLayout.visibility = View.VISIBLE
                        currentFilterType = "Quest"
                        updateCurrentQuestFilter()
                        updateQuestLoadingVisibility(questsViewModel.variantsLoadingLiveData.value == true)
                        val ts = questPrefs.getLong("filters_last_refreshed", 0L)
                        if (ts > 0L) updateFiltersLastRefreshed(ts)
                        // Rebuild the quest filter UI to reflect any state changes that
                        // occurred while the Rocket tab was active (e.g. onResume re-read
                        // prefs but skipped rebuilding the quest view tree).
                        if (pokemonDataReady) setupQuestFilters(questLayout)
                    }
                }
                activity?.invalidateOptionsMenu()
            }
        }

        setupRocketFilters(rocketLayoutGlobal)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.filter_nav_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        val saveRocketItem = menu.findItem(R.id.action_save_rocket)
        val saveQuestItem  = menu.findItem(R.id.action_save_quest)
        val refreshFiltersItem = menu.findItem(R.id.action_refresh_filters)

        saveRocketItem?.isVisible = (currentFilterType == "Rocket")
        saveQuestItem?.isVisible  = (currentFilterType == "Quest")
        refreshFiltersItem?.isVisible = (currentFilterType == "Quest")
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_save_rocket -> { showSaveFilterDialog(true); true }
            R.id.action_save_quest  -> { showSaveFilterDialog(false); true }
            R.id.action_refresh_filters -> { questsViewModel.fetchQuests(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showSaveFilterDialog(isRocket: Boolean) {
        val builder = AlertDialog.Builder(requireContext())
        val inflater = requireActivity().layoutInflater
        val dialogView = inflater.inflate(R.layout.dialog_save_filter, null)

        val titleTextView    = dialogView.findViewById<TextView>(R.id.saveFilterTitle)
        val editText         = dialogView.findViewById<EditText>(R.id.editFilterName)
        val cancelButton     = dialogView.findViewById<Button>(R.id.cancelFilterButton)
        val saveAsNewButton  = dialogView.findViewById<Button>(R.id.saveAsNewFilterButton)
        val saveButton       = dialogView.findViewById<Button>(R.id.saveFilterButton)

        val type = if (isRocket) "rocket" else "quest"
        builder.setView(dialogView)
        val dialog = builder.create()

        // Cancel always just closes the dialog.
        cancelButton.setOnClickListener { dialog.dismiss() }

        if (isRocket) {
            val activeFilterName = filterPreferences.getActiveRocketFilter()
            val hasActiveFilter = activeFilterName.isNotEmpty()

            if (hasActiveFilter) {
                // Editing an existing filter: pre-fill name, show all three buttons.
                titleTextView.text = "Editing: $activeFilterName"
                editText.setText(activeFilterName)
                editText.hint = "New name for Save as New"
                saveButton.text = "Update"
                saveAsNewButton.visibility = View.VISIBLE

                // Update — overwrite the existing snapshot in-place.
                saveButton.setOnClickListener {
                    saveRocketFilterSnapshot(activeFilterName, dialog)
                    Toast.makeText(requireContext(), "Filter '$activeFilterName' updated", Toast.LENGTH_SHORT).show()
                }

                // Save as New — require a different name, leave the original untouched.
                saveAsNewButton.setOnClickListener {
                    val newName = editText.text.toString().trim()
                    if (newName.isEmpty() || newName == activeFilterName) {
                        Toast.makeText(requireContext(), "Enter a different name to save as new", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    saveRocketFilterSnapshot(newName, dialog)
                    Toast.makeText(requireContext(), "Filter '$newName' saved", Toast.LENGTH_SHORT).show()
                }
            } else {
                // No active filter — standard save flow, two buttons only.
                titleTextView.text = "Enter a name for the new rocket filter"
                saveButton.setOnClickListener {
                    val newFilterName = editText.text.toString().trim()
                    if (newFilterName.isEmpty()) {
                        Toast.makeText(requireContext(), "Please enter a name", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    saveRocketFilterSnapshot(newFilterName, dialog)
                    Toast.makeText(requireContext(), "Filter '$newFilterName' saved", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            val activeFilterName = filterPreferences.getActiveQuestFilter()
            val hasActiveFilter = activeFilterName.isNotEmpty()

            if (hasActiveFilter) {
                // Editing an existing filter: pre-fill name, show all three buttons.
                titleTextView.text = "Editing: $activeFilterName"
                editText.setText(activeFilterName)
                editText.hint = "New name for Save as New"
                saveButton.text = "Update"
                saveAsNewButton.visibility = View.VISIBLE

                // Update — overwrite the existing snapshot in-place.
                saveButton.setOnClickListener {
                    saveQuestFilterSnapshot(activeFilterName, dialog)
                    Toast.makeText(requireContext(), "Filter '$activeFilterName' updated", Toast.LENGTH_SHORT).show()
                }

                // Save as New — require a different name, leave the original untouched.
                saveAsNewButton.setOnClickListener {
                    val newName = editText.text.toString().trim()
                    if (newName.isEmpty() || newName == activeFilterName) {
                        Toast.makeText(requireContext(), "Enter a different name to save as new", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    saveQuestFilterSnapshot(newName, dialog)
                    Toast.makeText(requireContext(), "Filter '$newName' saved", Toast.LENGTH_SHORT).show()
                }
            } else {
                // No active filter — standard save flow, two buttons only.
                titleTextView.text = "Enter a name for the new $type filter"
                saveButton.setOnClickListener {
                    val newFilterName = editText.text.toString().trim()
                    if (newFilterName.isEmpty()) {
                        Toast.makeText(requireContext(), "Please enter a name", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    saveQuestFilterSnapshot(newFilterName, dialog)
                    Toast.makeText(requireContext(), "Filter '$newFilterName' saved", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.show()
    }

    /**
     * Writes the current live rocket filter state as a named snapshot and sets it as
     * the active filter. Clears the active name before writing so no auto-update hook
     * in saveEnabledCharacters can fire against the old active filter during the save.
     */
    private fun saveRocketFilterSnapshot(name: String, dialog: AlertDialog) {
        filterPreferences.clearActiveRocketFilter()
        filterPreferences.saveEnabledCharacters(enabledRocketFilters)
        filterPreferences.saveCurrentAsFilter(name)
        filterPreferences.setActiveRocketFilter(name)
        setupRocketFilters(rocketLayoutGlobal)
        dialog.dismiss()
    }

    /**
     * Writes the current live quest filter state (enabledQuestFilters +
     * enabledEncounterConditions) as a named snapshot and sets it as the
     * active filter. Safe to call regardless of whether a filter was
     * previously active — it always clears the active name before writing
     * so the save path is corruption-free.
     */
    private fun saveQuestFilterSnapshot(name: String, dialog: AlertDialog) {
        // Clear active first so no auto-update hook can fire during the writes.
        Log.d("QuestFilterDebug", "Saving quest filter '$name' (before) -> enabledQuestFilters=${enabledQuestFilters.size}, enabledEncounterConditions=${enabledEncounterConditions.size}")
        filterPreferences.clearActiveQuestFilter()
        filterPreferences.saveEnabledQuestFilters(enabledQuestFilters)
        filterPreferences.saveEnabledEncounterConditions(enabledEncounterConditions)
        filterPreferences.saveCurrentQuestFilter(name)
        filterPreferences.setActiveQuestFilter(name)
        Log.d("QuestFilterDebug", "Saved quest filter '$name' (after) -> enabledQuestFilters=${enabledQuestFilters.size}, enabledEncounterConditions=${enabledEncounterConditions.size}")
        // Do NOT call setupQuestFilters here. The UI already shows exactly what the
        // user selected — tearing down and rebuilding all checkboxes during/after a
        // dialog interaction causes Android to re-evaluate isEnabled/isChecked on
        // newly-created views, which re-ticks variants the user had explicitly unticked.
        // Just refresh the "Current filter:" label instead.
        updateCurrentQuestFilter()
        dialog.dismiss()
    }

    private fun setupRocketFilters(parent: LinearLayout) {
        parent.removeAllViews()
        addResetButton(parent, "Rocket")
        addToggleAllButton(parent, "Rocket")
        addSectionHeader(parent, "Rocket Filters")
        addSelectFilterButton(parent, "Rocket")
        updateCurrentRocketFilter()
        DataMappings.characterNamesMap.forEach { (id, name) ->
            addCheckBox(parent, name, id, enabledRocketFilters) { checked ->
                if (checked) enabledRocketFilters.add(id) else enabledRocketFilters.remove(id)
                filterPreferences.saveEnabledCharacters(enabledRocketFilters)
            }
        }
    }

    private fun updateCurrentRocketFilter() {
        if (currentFilterType != "Rocket") { updateCurrentQuestFilter(); return }
        val currentFilterName = filterPreferences.getActiveRocketFilter()
        currentFilterTextView.visibility = View.VISIBLE
        currentFilterTextView.text = if (currentFilterName.isNotEmpty())
            "Current selected filter: $currentFilterName"
        else
            "Current selected filter: Unsaved"
    }

    private fun updateCurrentQuestFilter() {
        if (currentFilterType != "Quest") { updateCurrentRocketFilter(); return }
        val currentFilterName = filterPreferences.getActiveQuestFilter()
        currentFilterTextView.visibility = View.VISIBLE
        currentFilterTextView.text = if (currentFilterName.isNotEmpty())
            "Current selected filter: $currentFilterName"
        else
            "Current selected filter: Unsaved"
    }

    private fun updateQuestLoadingVisibility(isLoading: Boolean) {
        questLoadingGroup.visibility = if (currentFilterType == "Quest" && isLoading) View.VISIBLE else View.GONE
    }

    private fun updateFiltersLastRefreshed(timestampMillis: Long) {
        if (timestampMillis <= 0L || currentFilterType != "Quest") {
            filterLastRefreshedText.visibility = View.GONE
            return
        }
        val formatter = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.getDefault())
        filterLastRefreshedText.text = "Filters Last Refreshed: ${formatter.format(Date(timestampMillis))}"
        filterLastRefreshedText.visibility = View.VISIBLE
    }

    private fun addResetButton(parent: LinearLayout, filterType: String) {
        val resetButton = MaterialButton(requireContext()).apply {
            text = "Reset $filterType Filters"
            setPadding(16, 8, 16, 8)
            setOnClickListener {
                when (filterType) {
                    "Rocket" -> {
                        enabledRocketFilters.clear()
                        filterPreferences.clearActiveRocketFilter()
                        filterPreferences.saveEnabledCharacters(enabledRocketFilters)
                        setupRocketFilters(parent)
                    }
                    "Quest" -> {
                        enabledQuestFilters.clear()
                        enabledEncounterConditions.clear()
                        filterPreferences.clearActiveQuestFilter()
                        filterPreferences.saveEnabledQuestFilters(enabledQuestFilters)
                        filterPreferences.saveEnabledEncounterConditions(enabledEncounterConditions)
                        setupQuestFilters(parent)
                    }
                }
            }
        }
        parent.addView(resetButton, 0)
    }

    private fun addSelectFilterButton(parent: LinearLayout, filterType: String) {
        val selectButton = MaterialButton(requireContext()).apply {
            text = "Select $filterType Filter"
            setPadding(16, 8, 16, 8)
            setOnClickListener { showSelectFilterDialog(parent, filterType) }
        }
        parent.addView(selectButton, 1)
    }

    private fun showSelectFilterDialog(parentLayoutForRefresh: LinearLayout, filterType: String) {
        val builder = AlertDialog.Builder(requireContext())
        val inflater = requireActivity().layoutInflater
        val dialogView = inflater.inflate(R.layout.dialog_select_filter, null)

        val title = dialogView.findViewById<TextView>(R.id.selectFilterTitle)
        val listContainer = dialogView.findViewById<LinearLayout>(R.id.filterListContainer)
        val cancelButton = dialogView.findViewById<Button>(R.id.cancelSelectButton)

        title.text = "Select $filterType Filter"
        builder.setView(dialogView)
        val dialog = builder.create()
        cancelButton.setOnClickListener { dialog.dismiss() }

        fun updateDialogContent() {
            listContainer.removeAllViews()
            val filterNames: Array<String> = if (filterType == "Rocket")
                filterPreferences.listFilterNames().toTypedArray()
            else
                filterPreferences.listQuestFilterNames().toTypedArray()

            if (filterNames.isEmpty()) {
                val emptyView = TextView(requireContext()).apply {
                    text = "No saved filters available"
                    setPadding(16, 16, 16, 16)
                    gravity = android.view.Gravity.CENTER
                    textSize = 16f
                    setTextColor(ContextCompat.getColor(requireContext(), android.R.color.darker_gray))
                }
                listContainer.addView(emptyView)
                return
            }

            filterNames.forEach { filterName ->
                val itemView = inflater.inflate(R.layout.filter_list_item, listContainer, false)
                val nameTextView = itemView.findViewById<TextView>(R.id.filterNameText)
                val deleteButton = itemView.findViewById<ImageButton>(R.id.deleteFilterButton)
                val selectButton = itemView.findViewById<Button>(R.id.selectFilterButton)

                nameTextView.text = filterName

                selectButton.setOnClickListener {
                    if (filterType == "Rocket") {
                        filterPreferences.loadFilter(filterName, "Rocket")
                        enabledRocketFilters.clear()
                        enabledRocketFilters.addAll(filterPreferences.getEnabledCharacters())
                        setupRocketFilters(parentLayoutForRefresh)
                    } else {
                        filterPreferences.loadFilter(filterName, "Quest")
                        enabledQuestFilters.clear()
                        enabledQuestFilters.addAll(filterPreferences.getEnabledQuestFilters())
                        enabledEncounterConditions.clear()
                        enabledEncounterConditions.addAll(filterPreferences.getEnabledEncounterConditions())
                        Log.d("QuestFilterDebug", "Loaded quest filter '$filterName' -> enabledQuestFilters=${enabledQuestFilters.size}, enabledEncounterConditions=${enabledEncounterConditions.size}")
                        setupQuestFilters(parentLayoutForRefresh)
                    }
                    Toast.makeText(requireContext(), "Filter '$filterName' applied", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }

                deleteButton.setOnClickListener {
                    showDeleteConfirmationDialog(filterName, filterType) {
                        if (filterType == "Rocket") {
                            enabledRocketFilters.clear()
                            enabledRocketFilters.addAll(filterPreferences.getEnabledCharacters())
                            setupRocketFilters(parentLayoutForRefresh)
                        } else {
                            enabledQuestFilters.clear()
                            enabledQuestFilters.addAll(filterPreferences.getEnabledQuestFilters())
                            enabledEncounterConditions.clear()
                            enabledEncounterConditions.addAll(filterPreferences.getEnabledEncounterConditions())
                            setupQuestFilters(parentLayoutForRefresh)
                        }
                        updateDialogContent()
                    }
                }
                listContainer.addView(itemView)
            }
        }

        updateDialogContent()
        dialog.show()
    }

    private fun showDeleteConfirmationDialog(filterName: String, filterType: String, onDeleted: () -> Unit) {
        val builder = AlertDialog.Builder(requireContext())
        val inflater = requireActivity().layoutInflater
        val dialogView = inflater.inflate(R.layout.dialog_delete_filter, null)

        val filterNameDisplay = dialogView.findViewById<TextView>(R.id.filterNameDisplay)
        val cancelButton = dialogView.findViewById<Button>(R.id.cancelDeleteButton)
        val deleteButton = dialogView.findViewById<Button>(R.id.confirmDeleteButton)

        filterNameDisplay.text = filterName
        builder.setView(dialogView)
        val dialog = builder.create()

        cancelButton.setOnClickListener { dialog.dismiss() }

        deleteButton.setOnClickListener {
            val wasActiveRocket = filterType == "Rocket" && filterName == filterPreferences.getActiveRocketFilter()
            val wasActiveQuest  = filterType == "Quest"  && filterName == filterPreferences.getActiveQuestFilter()
            filterPreferences.deleteFilter(filterName, filterType)
            if (wasActiveRocket) {
                enabledRocketFilters.clear()
                enabledRocketFilters.addAll(filterPreferences.getEnabledCharacters())
            }
            if (wasActiveQuest) {
                enabledQuestFilters.clear()
                enabledQuestFilters.addAll(filterPreferences.getEnabledQuestFilters())
                enabledEncounterConditions.clear()
                enabledEncounterConditions.addAll(filterPreferences.getEnabledEncounterConditions())
            }
            Toast.makeText(requireContext(), "Filter '$filterName' deleted", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
            onDeleted()
        }

        dialog.show()
    }

    private fun addToggleAllButton(parent: LinearLayout, filterType: String) {
        val toggleButton = MaterialButton(requireContext()).apply {
            text = "Toggle All $filterType Filters"
            setPadding(16, 8, 16, 8)
            setOnClickListener {
                when (filterType) {
                    "Rocket" -> {
                        val allSelected = DataMappings.characterNamesMap.keys.all { it in enabledRocketFilters }
                        if (allSelected) enabledRocketFilters.clear()
                        else { enabledRocketFilters.clear(); enabledRocketFilters.addAll(DataMappings.characterNamesMap.keys) }
                        filterPreferences.clearActiveRocketFilter()
                        filterPreferences.saveEnabledCharacters(enabledRocketFilters)
                        setupRocketFilters(parent)
                    }
                    "Quest" -> {
                        val filtersJson = questPrefs.getString("quest_api_filters", null) ?: return@setOnClickListener
                        val filtersFromApi = Gson().fromJson(filtersJson, Quests.Filters::class.java)
                        val subVariants = questsViewModel.rewardSubVariantsLiveData.value ?: emptyMap()
                        val allPossibleQuestFilters = mutableSetOf<String>()

                        fun addFiltersForSection(list: List<String>, section: String) {
                            list.forEach { rawValue ->
                                val baseFilter = buildQuestFilterString(section, rawValue)
                                val variants = subVariants[baseFilter]
                                if (!variants.isNullOrEmpty()) {
                                    // Use base filter + condition keys so variants with the same amount
                                    // don't collapse into a single filterString.
                                    allPossibleQuestFilters.add(baseFilter)
                                    variants.forEach { variant ->
                                        enabledEncounterConditions.add(
                                            buildConditionKey(variant.type, variant.id, variant.amount, variant.condition, variant.reward)
                                        )
                                    }
                                } else {
                                    allPossibleQuestFilters.add(baseFilter)
                                }
                            }
                        }

                        addFiltersForSection(filtersFromApi.t3, "Stardust")
                        addFiltersForSection(filtersFromApi.t4, "Pokémon Candy")
                        addFiltersForSection(filtersFromApi.t9 ?: emptyList(), "Pokémon Candy XL")
                        addFiltersForSection(filtersFromApi.t12, "Mega Energy")
                        addFiltersForSection(filtersFromApi.t7, "Pokémon Encounter")
                        addFiltersForSection(filtersFromApi.t2, "Item")

                        if (allPossibleQuestFilters.isNotEmpty()) {
                            val allCurrentlySelected = enabledQuestFilters.containsAll(allPossibleQuestFilters) &&
                                    enabledQuestFilters.size == allPossibleQuestFilters.size
                            if (allCurrentlySelected) {
                                enabledQuestFilters.clear()
                                enabledEncounterConditions.clear()
                            } else {
                                enabledQuestFilters.clear()
                                enabledQuestFilters.addAll(allPossibleQuestFilters)
                            }
                            filterPreferences.clearActiveQuestFilter()
                            filterPreferences.saveEnabledQuestFilters(enabledQuestFilters)
                            filterPreferences.saveEnabledEncounterConditions(enabledEncounterConditions)
                            setupQuestFilters(parent)
                        }
                    }
                }
            }
        }
        parent.addView(toggleButton, 0)
    }

    private fun setupQuestFilters(parent: LinearLayout) {
        isRebuildingQuestFilters = true
        Log.d("QuestFilterDebug", "setupQuestFilters: start (enabledQuestFilters=${enabledQuestFilters.size}, enabledEncounterConditions=${enabledEncounterConditions.size})")
        parent.removeAllViews()
        addResetButton(parent, "Quest")
        addToggleAllButton(parent, "Quest")
        addSectionHeader(parent, "Quest Filters")
        addSelectFilterButton(parent, "Quest")
        if (currentFilterType == "Quest") updateCurrentQuestFilter()

        val filtersJson = questPrefs.getString("quest_api_filters", null)
        if (filtersJson != null) {
            val filters = Gson().fromJson(filtersJson, Quests.Filters::class.java)
            val subVariants: Map<String, List<QuestsViewModel.SubVariant>> =
                questsViewModel.rewardSubVariantsLiveData.value ?: emptyMap()
            Log.d("QuestFilterDebug", "setupQuestFilters: subVariants keys=${subVariants.size}")

            reconcileVariantSelections(subVariants)

            addFilterSectionWithVariants(parent, "Stardust",        filters.t3,                subVariants)
            addFilterSectionWithVariants(parent, "Pokémon Candy",   filters.t4,                subVariants)
            addFilterSectionWithVariants(parent, "Pokémon Candy XL",filters.t9 ?: emptyList(), subVariants)
            addFilterSectionWithVariants(parent, "Mega Energy",     filters.t12,               subVariants)
            addFilterSectionWithVariants(parent, "Pokémon Encounter",filters.t7,               subVariants)
            addFilterSectionWithVariants(parent, "Item",            filters.t2,                subVariants)
        } else {
            questsViewModel.fetchQuests()
            addSectionHeader(parent, "Please open quests tab to update data (or data loading)")
        }
        isRebuildingQuestFilters = false
        Log.d("QuestFilterDebug", "setupQuestFilters: end (enabledQuestFilters=${enabledQuestFilters.size}, enabledEncounterConditions=${enabledEncounterConditions.size})")
    }

    private fun addSectionHeader(parent: LinearLayout, text: String) {
        TextView(context).apply {
            this.text = text
            textSize = 18f
            setPadding(16)
            parent.addView(this)
        }
    }

    private fun addCheckBox(
        parent: LinearLayout,
        text: String,
        id: Int,
        enabledSet: MutableSet<Int>,
        onCheckedChangeExternal: (Boolean) -> Unit
    ) {
        CheckBox(context).apply {
            this.text = text
            isChecked = id in enabledSet
            setPadding(32, 8, 16, 8)
            setOnCheckedChangeListener { _, checked ->
                if (isRebuildingQuestFilters) return@setOnCheckedChangeListener
                if (checked) enabledSet.add(id) else enabledSet.remove(id)
                onCheckedChangeExternal(checked)
            }
            parent.addView(this)
        }
    }

    private fun addQuestCheckBox(
        parent: LinearLayout,
        text: String,
        id: String,
        enabledSet: MutableSet<String>,
        onCheckedChangeExternal: (Boolean) -> Unit
    ) {
        CheckBox(context).apply {
            this.text = text
            isChecked = id in enabledSet
            setPadding(32, 8, 16, 8)
            setOnCheckedChangeListener { _, checked ->
                if (isRebuildingQuestFilters) return@setOnCheckedChangeListener
                if (checked) enabledSet.add(id) else enabledSet.remove(id)
                onCheckedChangeExternal(checked)
            }
            parent.addView(this)
        }
    }

    private fun buildQuestFilterString(section: String, rawValue: String): String {
        return when (section) {
            "Stardust"         -> "3,$rawValue,0"
            "Mega Energy"      -> "12,0,$rawValue"
            "Pokémon Encounter"-> "7,0,$rawValue"
            "Item"             -> "2,0,$rawValue"
            "Pokémon Candy"    -> "4,0,$rawValue"
            "Pokémon Candy XL" -> "9,0,$rawValue"
            else               -> rawValue
        }
    }

    private fun buildConditionKey(type: String, id: String, amount: String, condition: String, reward: String): String {
        return "$type|$id|$amount|$condition|$reward"
    }

    private fun ensureVariantSelectionConsistency(variants: List<QuestsViewModel.SubVariant>) {
        // Intentionally empty — variant/condition pairs are written together atomically.
    }

    private fun ensureEncounterSelectionConsistency(
        baseFilter: String,
        variants: List<QuestsViewModel.SubVariant>
    ) {
        // Intentionally empty — see ensureVariantSelectionConsistency.
    }

    private fun reconcileVariantSelections(subVariants: Map<String, List<QuestsViewModel.SubVariant>>) {
        if (subVariants.isEmpty()) return

        var changed = false
        var addedFilters = 0

        subVariants.forEach { (baseFilter, variants) ->
            if (variants.isEmpty()) return@forEach
            val isEncounter = baseFilter.startsWith("7,")
            val encounterPrefix = if (isEncounter) {
                "7|${variants.first().id}|"
            } else {
                ""
            }

            // Remove legacy base filter keys (non-encounters only).
            if (!isEncounter && enabledQuestFilters.remove(baseFilter)) {
                // We'll add it back only if a condition key exists.
                changed = true
            }

            // Migrate any legacy variant filterStrings to the base filter.
            if (!isEncounter) {
                val hadLegacyVariant = variants.any { it.filterString in enabledQuestFilters }
                if (hadLegacyVariant) {
                    variants.forEach { enabledQuestFilters.remove(it.filterString) }
                    // Base filter will be re-added below if any condition key exists.
                    changed = true
                }
            }

            // Ensure base filter exists when any condition key is present; remove it otherwise.
            val hasAnyCondition = variants.any { variant ->
                buildConditionKey(variant.type, variant.id, variant.amount, variant.condition, variant.reward) in enabledEncounterConditions
            }
            if (!isEncounter) {
                if (hasAnyCondition && baseFilter !in enabledQuestFilters) {
                    enabledQuestFilters.add(baseFilter)
                    addedFilters += 1
                    changed = true
                }
                if (!hasAnyCondition && baseFilter in enabledQuestFilters) {
                    enabledQuestFilters.remove(baseFilter)
                    changed = true
                }
            }

            if (isEncounter && enabledEncounterConditions.any { it.startsWith(encounterPrefix) }) {
                if (baseFilter !in enabledQuestFilters) {
                    enabledQuestFilters.add(baseFilter)
                    addedFilters += 1
                    changed = true
                }
            }
        }

        if (changed) {
            Log.d("QuestFilterDebug", "reconcileVariantSelections: addedFilters=$addedFilters")
        }

        if (changed && currentFilterType == "Quest") {
            filterPreferences.saveEnabledQuestFilters(enabledQuestFilters)
            filterPreferences.saveEnabledEncounterConditions(enabledEncounterConditions)
        }
    }

    private fun addFilterSectionWithVariants(
        parent: LinearLayout,
        sectionName: String,
        filterList: List<String>,
        subVariants: Map<String, List<QuestsViewModel.SubVariant>>
    ) {
        addSectionHeader(parent, sectionName)

        if (filterList.isEmpty()) {
            TextView(context).apply {
                text = "None available for $sectionName"
                setPadding(16)
                parent.addView(this)
            }
            return
        }

        val sortedList = when (sectionName) {
            "Stardust" -> filterList.sortedBy { it.toIntOrNull() ?: 0 }
            "Item"     -> filterList.sortedBy { DataMappings.itemMap["item$it"] ?: it }
            else       -> filterList.sortedBy { DataMappings.pokemonEncounterMapNew[it] ?: it }
        }

        sortedList.forEach { rawValue ->
            val displayText = when (sectionName) {
                "Pokémon Candy"    -> DataMappings.pokemonEncounterMapNew[rawValue] ?: "Candy for ID: $rawValue"
                "Pokémon Candy XL" -> DataMappings.pokemonEncounterMapNew[rawValue] ?: "XL Candy for ID: $rawValue"
                "Mega Energy"      -> DataMappings.pokemonEncounterMapNew[rawValue] ?: "Energy for ID: $rawValue"
                "Item"             -> DataMappings.itemMap["item$rawValue"] ?: "Item ID: $rawValue"
                "Stardust"         -> "$rawValue Stardust"
                else               -> DataMappings.pokemonEncounterMapNew[rawValue] ?: "ID: $rawValue"
            }
            val baseFilter = buildQuestFilterString(sectionName, rawValue)
            val variants = subVariants[baseFilter]

            if (sectionName == "Pokémon Encounter") {
                if (!variants.isNullOrEmpty() && variants.size > 1) {
                    ensureEncounterSelectionConsistency(baseFilter, variants)
                    addEncounterFilterWithVariants(parent, displayText, rawValue, variants)
                } else {
                    val variant = variants?.firstOrNull()
                    val filterStr = baseFilter
                    addQuestCheckBox(parent, displayText, filterStr, enabledQuestFilters) {
                        filterPreferences.saveEnabledQuestFilters(enabledQuestFilters)
                        if (variant != null) {
                            val key = buildConditionKey(variant.type, variant.id, variant.amount, variant.condition, variant.reward)
                            if (enabledQuestFilters.contains(filterStr)) enabledEncounterConditions.add(key)
                            else enabledEncounterConditions.remove(key)
                            filterPreferences.saveEnabledEncounterConditions(enabledEncounterConditions)
                        }
                    }
                }
            } else {
                if (!variants.isNullOrEmpty()) {
                    ensureVariantSelectionConsistency(variants)
                    addCandyFilterWithVariants(parent, displayText, baseFilter, variants)
                } else {
                    addQuestCheckBox(parent, displayText, baseFilter, enabledQuestFilters) {
                        filterPreferences.saveEnabledQuestFilters(enabledQuestFilters)
                    }
                }
            }
        }
    }

    private fun addEncounterFilterWithVariants(
        parent: LinearLayout,
        displayText: String,
        pokemonId: String,
        variants: List<QuestsViewModel.SubVariant>
    ) {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val topRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val variantCheckboxes = mutableListOf<CheckBox>()
        val baseFilter = "7,0,$pokemonId"
        val allConditionKeys = variants.map { buildConditionKey(it.type, it.id, it.amount, it.condition, it.reward) }.toSet()
        val encounterPrefix = "7|$pokemonId|"

        val mainCheckbox = CheckBox(requireContext()).apply {
            text = displayText
            // Use the stable prefix "7|<id>|" rather than exact conditionKey matching so
            // the checkbox remains ticked even if API condition text drifts between refreshes.
            isChecked = baseFilter in enabledQuestFilters ||
                        enabledEncounterConditions.any { it.startsWith(encounterPrefix) }
            setPadding(32, 8, 16, 8)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setOnCheckedChangeListener { _, isChecked ->
                if (isRebuildingQuestFilters) return@setOnCheckedChangeListener
                Log.d("QuestFilterDebug", "Encounter main '$displayText' -> $isChecked")
                if (isChecked) {
                    enabledQuestFilters.add(baseFilter)
                    enabledEncounterConditions.addAll(allConditionKeys)
                    variantCheckboxes.forEach { it.isChecked = true }
                } else {
                    enabledEncounterConditions.removeAll(allConditionKeys)
                    variantCheckboxes.forEach { it.isChecked = false }
                    if (enabledEncounterConditions.none { it.startsWith(encounterPrefix) })
                        enabledQuestFilters.remove(baseFilter)
                }
                variantCheckboxes.forEach { it.isEnabled = isChecked }
                filterPreferences.saveEnabledQuestFilters(enabledQuestFilters)
                filterPreferences.saveEnabledEncounterConditions(enabledEncounterConditions)
            }
        }

        val spacer = View(requireContext()).apply { layoutParams = LinearLayout.LayoutParams(0, 0, 1f) }
        val expandButton = makeExpandButton()

        topRow.addView(mainCheckbox)
        topRow.addView(spacer)
        topRow.addView(expandButton)

        val variantsContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding((32 + 16).dpToPx(), 0, 16.dpToPx(), 8.dpToPx())
        }

        variants.forEach { variant ->
            val key = buildConditionKey(variant.type, variant.id, variant.amount, variant.condition, variant.reward)
            val cb = CheckBox(requireContext()).apply {
                text = variant.label
                isChecked = key in enabledEncounterConditions
                isEnabled = mainCheckbox.isChecked
                setPadding(0, 8.dpToPx(), 0, 8.dpToPx())
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setOnCheckedChangeListener { _, isChecked ->
                    if (isRebuildingQuestFilters) return@setOnCheckedChangeListener
                    Log.d("QuestFilterDebug", "Encounter variant '$displayText' -> '${variant.label}' = $isChecked")
                    enabledQuestFilters.remove(baseFilter)
                    if (isChecked) {
                        enabledQuestFilters.add(variant.filterString)
                        enabledEncounterConditions.add(key)
                    } else {
                        enabledQuestFilters.remove(variant.filterString)
                        enabledEncounterConditions.remove(key)
                    }
                    mainCheckbox.setOnCheckedChangeListener(null)
                    mainCheckbox.isChecked = variantCheckboxes.any { it.isChecked }
                    mainCheckbox.setOnCheckedChangeListener { _, chk ->
                        if (isRebuildingQuestFilters) return@setOnCheckedChangeListener
                        if (chk) {
                            enabledQuestFilters.add(baseFilter)
                            enabledEncounterConditions.addAll(allConditionKeys)
                            variantCheckboxes.forEach { it.isChecked = true }
                        } else {
                            enabledEncounterConditions.removeAll(allConditionKeys)
                            variantCheckboxes.forEach { it.isChecked = false }
                            if (enabledEncounterConditions.none { it.startsWith(encounterPrefix) })
                                enabledQuestFilters.remove(baseFilter)
                        }
                        variantCheckboxes.forEach { it.isEnabled = chk }
                        filterPreferences.saveEnabledQuestFilters(enabledQuestFilters)
                        filterPreferences.saveEnabledEncounterConditions(enabledEncounterConditions)
                    }
                    filterPreferences.saveEnabledQuestFilters(enabledQuestFilters)
                    filterPreferences.saveEnabledEncounterConditions(enabledEncounterConditions)
                }
            }
            variantCheckboxes.add(cb)
            variantsContainer.addView(cb)
        }

        wireExpandButton(expandButton, variantsContainer)
        container.addView(topRow)
        container.addView(variantsContainer)
        parent.addView(container)
    }

    // Used for Stardust, Pokémon Candy, Pokémon Candy XL, Mega Energy, and Item sections.
    // Single-variant entries share the same row structure but without the expand button,
    // giving consistent alignment across all entries in a section.
    private fun addCandyFilterWithVariants(
        parent: LinearLayout,
        displayText: String,
        baseFilter: String,
        variants: List<QuestsViewModel.SubVariant>
    ) {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val topRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val variantCheckboxes = mutableListOf<CheckBox>()
        val allConditionKeys  = variants.map { buildConditionKey(it.type, it.id, it.amount, it.condition, it.reward) }.toSet()
        val hasMultipleVariants = variants.size > 1

        val mainCheckbox = CheckBox(requireContext()).apply {
            text = displayText
            isChecked = enabledEncounterConditions.any { it in allConditionKeys }
            setPadding(32, 8, 16, 8)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setOnCheckedChangeListener { _, isChecked ->
                if (isRebuildingQuestFilters) return@setOnCheckedChangeListener
                Log.d("QuestFilterDebug", "Main toggle '$displayText' -> $isChecked")
                if (isChecked) {
                    enabledQuestFilters.add(baseFilter)
                    enabledEncounterConditions.addAll(allConditionKeys)
                    variantCheckboxes.forEach { it.isChecked = true }
                } else {
                    enabledQuestFilters.remove(baseFilter)
                    enabledEncounterConditions.removeAll(allConditionKeys)
                    variantCheckboxes.forEach { it.isChecked = false }
                }
                variantCheckboxes.forEach { it.isEnabled = isChecked }
                filterPreferences.saveEnabledQuestFilters(enabledQuestFilters)
                filterPreferences.saveEnabledEncounterConditions(enabledEncounterConditions)
            }
        }

        topRow.addView(mainCheckbox)

        val variantsContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding((32 + 16).dpToPx(), 0, 16.dpToPx(), 8.dpToPx())
        }

        if (hasMultipleVariants) {
            val spacer = View(requireContext()).apply { layoutParams = LinearLayout.LayoutParams(0, 0, 1f) }
            val expandButton = makeExpandButton()
            topRow.addView(spacer)
            topRow.addView(expandButton)

            variants.forEach { variant ->
                val key = buildConditionKey(variant.type, variant.id, variant.amount, variant.condition, variant.reward)
                val cb = CheckBox(requireContext()).apply {
                    text = variant.label
                    isChecked = key in enabledEncounterConditions
                    isEnabled = mainCheckbox.isChecked
                    setPadding(0, 8.dpToPx(), 0, 8.dpToPx())
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    setOnCheckedChangeListener { _, isChecked ->
                        if (isRebuildingQuestFilters) return@setOnCheckedChangeListener
                        Log.d("QuestFilterDebug", "Variant toggle '$displayText' -> '${variant.label}' = $isChecked")
                        if (isChecked) {
                            enabledEncounterConditions.add(key)
                            enabledQuestFilters.add(baseFilter)
                        } else {
                            enabledEncounterConditions.remove(key)
                            if (enabledEncounterConditions.intersect(allConditionKeys).isEmpty()) {
                                enabledQuestFilters.remove(baseFilter)
                            }
                        }
                        mainCheckbox.setOnCheckedChangeListener(null)
                        mainCheckbox.isChecked = variantCheckboxes.any { it.isChecked }
                        mainCheckbox.setOnCheckedChangeListener { _, chk ->
                            if (isRebuildingQuestFilters) return@setOnCheckedChangeListener
                            if (chk) {
                                enabledQuestFilters.add(baseFilter)
                                enabledEncounterConditions.addAll(allConditionKeys)
                                variantCheckboxes.forEach { it.isChecked = true }
                            } else {
                                enabledQuestFilters.remove(baseFilter)
                                enabledEncounterConditions.removeAll(allConditionKeys)
                                variantCheckboxes.forEach { it.isChecked = false }
                            }
                            variantCheckboxes.forEach { it.isEnabled = chk }
                            filterPreferences.saveEnabledQuestFilters(enabledQuestFilters)
                            filterPreferences.saveEnabledEncounterConditions(enabledEncounterConditions)
                        }
                        filterPreferences.saveEnabledQuestFilters(enabledQuestFilters)
                        filterPreferences.saveEnabledEncounterConditions(enabledEncounterConditions)
                    }
                }
                variantCheckboxes.add(cb)
                variantsContainer.addView(cb)
            }
            wireExpandButton(expandButton, variantsContainer)
        }

        container.addView(topRow)
        container.addView(variantsContainer)
        parent.addView(container)
    }

    // Creates the themed ▶/▼ expand button used in both encounter and candy rows.
    private fun makeExpandButton(): MaterialButton = MaterialButton(requireContext()).apply {
        text = "▶"
        backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
        val onSurface = TypedValue().also {
            requireContext().theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, it, true)
        }.data
        setTextColor(onSurface)
        elevation = 0f
        minimumWidth = 0
        minWidth = 0
        setPadding(8.dpToPx(), 4.dpToPx(), 8.dpToPx(), 4.dpToPx())
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    // Wires the expand/collapse toggle between a button and its container.
    private fun wireExpandButton(button: MaterialButton, container: LinearLayout) {
        var isExpanded = false
        button.setOnClickListener {
            isExpanded = !isExpanded
            container.visibility = if (isExpanded) View.VISIBLE else View.GONE
            button.text = if (isExpanded) "▼" else "▶"
        }
    }

    private fun Int.dpToPx(): Int =
        (this * requireContext().resources.displayMetrics.density).toInt()

    override fun onResume() {
        super.onResume()
        // Reload from prefs so any saves made while this fragment was paused are picked up.
        if (::rocketLayoutGlobal.isInitialized) {
            enabledRocketFilters.clear()
            enabledRocketFilters.addAll(filterPreferences.getEnabledCharacters())
            setupRocketFilters(rocketLayoutGlobal)
        }
        if (::questLayout.isInitialized) {
            enabledQuestFilters.clear()
            enabledQuestFilters.addAll(filterPreferences.getEnabledQuestFilters())
            enabledEncounterConditions.clear()
            enabledEncounterConditions.addAll(filterPreferences.getEnabledEncounterConditions())
            // Only rebuild the quest filter view tree if the quest tab is currently visible
            // AND pokemon data is ready. If data isn't ready yet, the initializePokemonData
            // callback will trigger the first build once it completes.
            if (currentFilterType == "Quest" && pokemonDataReady) {
                setupQuestFilters(questLayout)
            }
        }
    }
}
