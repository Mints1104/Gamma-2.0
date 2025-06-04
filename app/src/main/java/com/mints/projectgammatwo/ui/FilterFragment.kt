package com.mints.projectgammatwo.ui

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.button.MaterialButton
import com.google.gson.Gson
import com.mints.projectgammatwo.R
import com.mints.projectgammatwo.data.CurrentQuestData
import com.mints.projectgammatwo.data.DataMappings
import com.mints.projectgammatwo.data.FilterPreferences
// Removed PokemonRepository as it's unused in this fragment
import com.mints.projectgammatwo.data.Quests
import com.mints.projectgammatwo.viewmodels.QuestsViewModel

class FilterFragment : Fragment() {

    private lateinit var filterPreferences: FilterPreferences
    private val enabledRocketFilters = mutableSetOf<Int>()
    private lateinit var questsViewModel: QuestsViewModel
    private var currentFilterType = "Rocket"
    private lateinit var questPrefs: SharedPreferences
    private val enabledQuestFilters = mutableSetOf<String>()
    private lateinit var questLayout: LinearLayout
    private lateinit var rocketLayoutGlobal: LinearLayout
    private lateinit var currentFilterTextView: TextView
    private var originalSettingsOfLoadedRocketFilter: Set<Int>? = null
    private var originalSettingsOfLoadedQuestFilter: Set<String>? = null


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
        enabledRocketFilters.clear()
        enabledRocketFilters.addAll(filterPreferences.getEnabledCharacters())

        enabledQuestFilters.clear()
        enabledQuestFilters.addAll(filterPreferences.getEnabledQuestFilters())

        questsViewModel = ViewModelProvider(this)[QuestsViewModel::class.java]

        val testList = CurrentQuestData.currentQuests
        Log.d("FilterFragment", "Current quests size: ${testList.size}")

        val spindaQuests = testList.filter { quest ->
            quest.rewardsIds.split(",").any { it == "327" }
        }
        val spindaType1 = spindaQuests.filter { quest ->
            quest.rewardsString.contains("01")

        }
        val spindaType2 = spindaQuests.filter { quest ->
            quest.rewardsString.contains("02")
        }

        Log.d("FilterFragment", "Spinda (01) quests: ${spindaType1.size}")
        Log.d("FilterFragment", "Spinda (02) quests: ${spindaType2.size}")



        questsViewModel.questsLiveData.observe(viewLifecycleOwner) { quests ->
            val spindaQuests = quests.filter { quest ->
                quest.rewardsIds.split(",").any { it == "327" }
            }



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
                    Log.d("Test","Current filter type: $currentFilterType")
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
                    Log.d("Test","Current filter type: $currentFilterType")

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
                    }
                    R.id.rbQuest -> {
                        rocketLayoutGlobal.visibility = View.GONE
                        questLayout.visibility = View.VISIBLE
                        currentFilterType = "Quest"

                        updateCurrentQuestFilter()
                    }
                }
                activity?.invalidateOptionsMenu()
            }
        }


        DataMappings.initializePokemonData(requireContext()) {
            if (!isAdded) return@initializePokemonData
            Log.d("App", "Pokemon data loaded with ${DataMappings.pokemonEncounterMapNew.size} entries")

            setupQuestFilters(questLayout)
        }




        val initialActiveRocketFilter = filterPreferences.getActiveRocketFilter()
        if (initialActiveRocketFilter.isNotEmpty()) {
            if (originalSettingsOfLoadedRocketFilter == null) {
                originalSettingsOfLoadedRocketFilter = HashSet(filterPreferences.getEnabledCharacters())
            }
        }

        val initialActiveQuestFilter = filterPreferences.getActiveQuestFilter()
        if (initialActiveQuestFilter.isNotEmpty()) {
            if (originalSettingsOfLoadedQuestFilter == null) {
                originalSettingsOfLoadedQuestFilter = HashSet(filterPreferences.getEnabledQuestFilters())
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

        saveRocketItem?.isVisible = (currentFilterType == "Rocket")
        saveQuestItem?.isVisible  = (currentFilterType == "Quest")
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_save_rocket -> {
                showSaveFilterDialog(true)
                true
            }
            R.id.action_save_quest -> {
                showSaveFilterDialog(false)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showSaveFilterDialog(isRocket: Boolean) {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Enter a name for the new filter")
        val input = EditText(requireContext())
        input.inputType = InputType.TYPE_CLASS_TEXT
        input.hint = "New filter name"
        builder.setView(input)

        builder.setPositiveButton("Save New") { dialog, _ ->
            val newFilterName = input.text.toString().trim()
            if (newFilterName.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter a name", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }

            if (isRocket) {

                filterPreferences.saveEnabledCharacters(enabledRocketFilters)
                filterPreferences.saveCurrentAsFilter(newFilterName)
                Toast.makeText(requireContext(), "Filter '$newFilterName' saved", Toast.LENGTH_SHORT).show()
                val activeFilterNameToRevert = filterPreferences.getActiveRocketFilter()

                if (activeFilterNameToRevert.isNotEmpty() && originalSettingsOfLoadedRocketFilter != null) {
                    enabledRocketFilters.clear()
                    enabledRocketFilters.addAll(originalSettingsOfLoadedRocketFilter!!)
                    filterPreferences.saveEnabledCharacters(enabledRocketFilters)
                } else {

                    filterPreferences.setActiveRocketFilter(newFilterName)
                    originalSettingsOfLoadedRocketFilter = HashSet(enabledRocketFilters) // B is now the baseline
                }
                setupRocketFilters(rocketLayoutGlobal)

            } else {
                filterPreferences.saveEnabledQuestFilters(enabledQuestFilters)
                filterPreferences.saveCurrentQuestFilter(newFilterName)
                Toast.makeText(requireContext(), "Filter '$newFilterName' saved", Toast.LENGTH_SHORT).show()

                val activeFilterNameToRevert = filterPreferences.getActiveQuestFilter()
                if (activeFilterNameToRevert.isNotEmpty() && originalSettingsOfLoadedQuestFilter != null) {
                    enabledQuestFilters.clear()
                    enabledQuestFilters.addAll(originalSettingsOfLoadedQuestFilter!!)
                    filterPreferences.saveEnabledQuestFilters(enabledQuestFilters)
                } else {
                    filterPreferences.setActiveQuestFilter(newFilterName)
                    originalSettingsOfLoadedQuestFilter = HashSet(enabledQuestFilters)
                }
                setupQuestFilters(questLayout)
            }
            dialog.dismiss()
        }
        builder.setNegativeButton("Cancel") { innerDialog, _ -> innerDialog.cancel() }
        builder.show()
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
        val currentFilterName = filterPreferences.getActiveRocketFilter()
        if (currentFilterName.isNotEmpty()) {
            currentFilterTextView.visibility = View.VISIBLE
            currentFilterTextView.text = "Current selected filter: $currentFilterName"
        } else {
            currentFilterTextView.visibility = View.VISIBLE
            currentFilterTextView.text = "Current selected filter: Unsaved"
        }
    }
    private fun updateCurrentQuestFilter() {
        val currentFilterName = filterPreferences.getActiveQuestFilter()
        if (currentFilterName.isNotEmpty()) {
            currentFilterTextView.visibility = View.VISIBLE
            currentFilterTextView.text = "Current selected filter: $currentFilterName"
        } else {
            currentFilterTextView.visibility = View.VISIBLE
            currentFilterTextView.text = "Current selected filter: Unsaved"
        }
    }


    private fun addResetButton(parent: LinearLayout, filterType: String) {
        val resetButton = MaterialButton(requireContext()).apply {
            text = "Reset $filterType Filters"
            setPadding(16, 8, 16, 8)
            setOnClickListener {
                when (filterType) {
                    "Rocket" -> {
                        enabledRocketFilters.clear()
                        filterPreferences.saveEnabledCharacters(enabledRocketFilters)
                        setupRocketFilters(parent)
                    }
                    "Quest" -> {
                        enabledQuestFilters.clear()
                        filterPreferences.saveEnabledQuestFilters(enabledQuestFilters)
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
            setOnClickListener {
                showSelectFilterDialog(parent, filterType)
            }
        }
        parent.addView(selectButton, 1)
    }

    private fun showSelectFilterDialog(parentLayoutForRefresh: LinearLayout, filterType: String) {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Select a $filterType filter")

        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_filter_list, null)
        val listContainer = dialogView.findViewById<LinearLayout>(R.id.filterListContainer)

        builder.setView(dialogView)
        builder.setNegativeButton("Cancel", null)
        val dialog = builder.create()

        fun updateDialogContent() {
            listContainer.removeAllViews()

            val filterNames: Array<String> = if (filterType == "Rocket") {
                filterPreferences.listFilterNames().toTypedArray()
            } else {
                filterPreferences.listQuestFilterNames().toTypedArray()
            }

            if (filterNames.isEmpty()) {
                val emptyView = TextView(requireContext()).apply {
                    text = "No saved filters available"
                    setPadding(16) // Simplified padding
                    gravity = android.view.Gravity.CENTER
                }
                listContainer.addView(emptyView)
                return
            }

            filterNames.forEach { filterName ->
                val itemView = LayoutInflater.from(requireContext()).inflate(R.layout.filter_list_item, listContainer, false)
                val nameTextView = itemView.findViewById<TextView>(R.id.filterNameText)
                val deleteButton = itemView.findViewById<Button>(R.id.deleteFilterButton)
                val selectButton = itemView.findViewById<Button>(R.id.selectFilterButton)

                nameTextView.text = filterName

                selectButton.setOnClickListener {
                    if (filterType == "Rocket") {
                        filterPreferences.loadFilter(filterName, "Rocket")
                        enabledRocketFilters.clear()
                        enabledRocketFilters.addAll(filterPreferences.getEnabledCharacters())

                        originalSettingsOfLoadedRocketFilter = HashSet(enabledRocketFilters)

                        setupRocketFilters(parentLayoutForRefresh)
                    } else {
                        filterPreferences.loadFilter(filterName, "Quest")
                        enabledQuestFilters.clear()
                        enabledQuestFilters.addAll(filterPreferences.getEnabledQuestFilters())

                        originalSettingsOfLoadedQuestFilter = HashSet(enabledQuestFilters)

                        setupQuestFilters(parentLayoutForRefresh)
                    }
                    Toast.makeText(requireContext(), "Filter '$filterName' applied", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }

                deleteButton.setOnClickListener {
                    showDeleteConfirmationDialog(filterName, filterType, parentLayoutForRefresh) {

                        if (filterType == "Rocket") {
                            enabledRocketFilters.clear()
                            enabledRocketFilters.addAll(filterPreferences.getEnabledCharacters())
                            originalSettingsOfLoadedRocketFilter = null
                            setupRocketFilters(parentLayoutForRefresh)
                        } else {
                            enabledQuestFilters.clear()
                            enabledQuestFilters.addAll(filterPreferences.getEnabledQuestFilters())
                            originalSettingsOfLoadedQuestFilter = null
                            setupQuestFilters(parentLayoutForRefresh)
                        }
                        updateDialogContent() // Refresh the dialog list
                    }
                }
                listContainer.addView(itemView)
            }
        }
        updateDialogContent()
        dialog.show()
    }

    private fun showDeleteConfirmationDialog(
        filterName: String,
        filterType: String,
        parentLayoutForRefresh: LinearLayout, // MODIFIED: Changed name for clarity
        onDeleted: () -> Unit
    ) {
        val confirmBuilder = AlertDialog.Builder(requireContext())
        confirmBuilder.setTitle("Delete Filter")
        confirmBuilder.setMessage("Are you sure you want to delete the filter '$filterName'?")
        confirmBuilder.setPositiveButton("Delete") { _, _ ->
            val wasActiveRocket = filterType == "Rocket" && filterName == filterPreferences.getActiveRocketFilter()
            val wasActiveQuest = filterType == "Quest" && filterName == filterPreferences.getActiveQuestFilter()

            filterPreferences.deleteFilter(filterName, filterType) // Deletes from prefs and clears active if it was active

            // ADDED: If the deleted filter was the one whose original state we'd snapped, clear the snapshot.
            if (wasActiveRocket) {
                originalSettingsOfLoadedRocketFilter = null
            }
            if (wasActiveQuest) {
                originalSettingsOfLoadedQuestFilter = null
            }

            Toast.makeText(requireContext(), "Filter '$filterName' deleted", Toast.LENGTH_SHORT).show()
            onDeleted() // This will trigger UI refresh and dialog list refresh
        }
        confirmBuilder.setNegativeButton("Cancel", null)
        confirmBuilder.show()
    }

    private fun addToggleAllButton(parent: LinearLayout, filterType: String) {
        val toggleButton = MaterialButton(requireContext()).apply {
            text = "Toggle All $filterType Filters"
            setPadding(16, 8, 16, 8)
            setOnClickListener {
                when (filterType) {
                    "Rocket" -> {
                        val allSelected = DataMappings.characterNamesMap.keys.all { it in enabledRocketFilters }
                        if (allSelected) {
                            enabledRocketFilters.clear()
                        } else {
                            enabledRocketFilters.clear() // Clear first to ensure no duplicates if some were already selected
                            enabledRocketFilters.addAll(DataMappings.characterNamesMap.keys)
                        }
                        filterPreferences.saveEnabledCharacters(enabledRocketFilters)
                        setupRocketFilters(parent)
                    }
                    "Quest" -> {
                        val filtersJson = questPrefs.getString("quest_api_filters", null)
                        if (filtersJson != null) {
                            val filtersFromApi = Gson().fromJson(filtersJson, Quests.Filters::class.java)
                            val allPossibleQuestFilters = mutableSetOf<String>()
                            // Simplified logic for gathering all possible filters
                            listOfNotNull(
                                filtersFromApi.t3 to "Stardust",
                                filtersFromApi.t4 to "Pokémon Candy",
                                filtersFromApi.t12 to "Mega Energy",
                                filtersFromApi.t7 to "Pokémon Encounter",
                                filtersFromApi.t2 to "Item"
                            ).forEach { (list, section) ->
                                list.forEach { rawValue ->
                                    allPossibleQuestFilters.add(buildQuestFilterString(section, rawValue))
                                }
                            }

                            if (allPossibleQuestFilters.isNotEmpty()) {
                                val allCurrentlySelected = enabledQuestFilters.containsAll(allPossibleQuestFilters) &&
                                        enabledQuestFilters.size == allPossibleQuestFilters.size // ensure exact match

                                if (allCurrentlySelected) {
                                    enabledQuestFilters.clear()
                                } else {
                                    enabledQuestFilters.clear()
                                    enabledQuestFilters.addAll(allPossibleQuestFilters)
                                }
                                // Use FilterPreferences for saving enabled quest filters
                                filterPreferences.saveEnabledQuestFilters(enabledQuestFilters)
                                setupQuestFilters(parent)
                            }
                        }
                    }
                }
            }
        }
        parent.addView(toggleButton, 0)
    }

    private fun setupQuestFilters(parent: LinearLayout) {
        parent.removeAllViews()
        addResetButton(parent, "Quest")
        addToggleAllButton(parent, "Quest")
        addSectionHeader(parent, "Quest Filters")
        addSelectFilterButton(parent, "Quest")

        updateCurrentQuestFilter()

        // This part uses `questPrefs` which seems to be for raw API structure,
        // while `enabledQuestFilters` (from FilterPreferences) stores the processed filter strings.
        val filtersJson = questPrefs.getString("quest_api_filters", null)



        if (filtersJson != null) {
            val filters = Gson().fromJson(filtersJson, Quests.Filters::class.java)
            addFilterSection(parent, "Stardust", filters.t3)
            addFilterSection(parent, "Pokémon Candy", filters.t4)
            addFilterSection(parent, "Mega Energy", filters.t12)
            addFilterSection(parent, "Pokémon Encounter", filters.t7)
            addFilterSection(parent, "Item", filters.t2)


            if(filters.t7.contains("Spinda")) {
                Log.d("FilterFragment", "Spinda quest found in Pokémon Encounter section")
            }


        } else {
            questsViewModel.fetchQuests()
            addSectionHeader(parent, "Please open quests tab to update data (or data loading)")
        }
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
            setPadding(32, 8, 16, 8) // Consider dp values
            setOnCheckedChangeListener { _, checked ->
                // Direct modification of the fragment's set
                if (checked) {
                    enabledSet.add(id)
                } else {
                    enabledSet.remove(id)
                }
                onCheckedChangeExternal(checked) // Call the passed lambda (which saves to FilterPreferences)
            }
            parent.addView(this)
        }
    }

    private fun addQuestCheckBox(
        parent: LinearLayout,
        text: String,
        id: String, // This is the composite quest filter string
        enabledSet: MutableSet<String>, // MODIFIED: Pass the mutable set
        onCheckedChangeExternal: (Boolean) -> Unit
    ) {
        CheckBox(context).apply {
            this.text = text
            isChecked = id in enabledSet
            setPadding(32, 8, 16, 8)
            setOnCheckedChangeListener { _, checked ->
                // Direct modification of the fragment's set
                if (checked) {
                    enabledSet.add(id)
                } else {
                    enabledSet.remove(id)
                }
                onCheckedChangeExternal(checked) // Call the passed lambda (which saves to FilterPreferences)
            }
            parent.addView(this)
        }
    }

    private fun buildQuestFilterString(section: String, rawValue: String): String {
        return when (section) {
            "Stardust" -> "3,$rawValue,0"
            "Mega Energy" -> "12,0,$rawValue" // Assuming 12 is correct based on your API
            "Pokémon Encounter" -> "7,0,$rawValue"
            "Item" -> "2,0,$rawValue"
            "Pokémon Candy" -> "4,0,$rawValue" // Assuming 4 is correct
            else -> rawValue // Should ideally not happen if sections are well-defined
        }
    }

    private fun addFilterSection(parent: LinearLayout, sectionName: String, filterList: List<String>) {
        addSectionHeader(parent, sectionName)
        if (filterList.isEmpty()) {
            TextView(context).apply {
                text = "None available for $sectionName" // More specific
                setPadding(16)
                parent.addView(this)
            }
        } else {
            // Sorting logic seems fine
            val sortedList = when (sectionName) {
                "Pokémon Encounter", "Mega Energy", "Pokémon Candy" -> filterList.sortedBy {
                    DataMappings.pokemonEncounterMapNew[it] ?: it
                }
                else -> filterList.sorted() // Default sort for others like Stardust, Item amounts
            }

            sortedList.forEach { rawValue ->
                val displayText = when (sectionName) {
                    "Pokémon Encounter" -> DataMappings.pokemonEncounterMapNew[rawValue] ?: "ID: $rawValue"
                    "Item" -> DataMappings.itemMap["item$rawValue"] ?: "Item ID: $rawValue"
                    "Mega Energy" -> DataMappings.pokemonEncounterMapNew[rawValue] ?: "Energy for ID: $rawValue"
                    "Pokémon Candy" -> DataMappings.pokemonEncounterMapNew[rawValue] ?: "Candy for ID: $rawValue"
                    else -> rawValue // For Stardust amounts, etc.
                }
                val compositeValue = buildQuestFilterString(sectionName, rawValue)




                // Use the fragment's enabledQuestFilters and save via FilterPreferences
                addQuestCheckBox(parent, displayText, compositeValue, enabledQuestFilters) { checked ->
                    filterPreferences.saveEnabledQuestFilters(enabledQuestFilters)
                }
            }
        }
    }


    override fun onResume() {
        super.onResume()
        // Refresh filters when fragment resumes, in case underlying data changed
        // (e.g. quest_api_filters from network or another tab)
        if (::questLayout.isInitialized) { // Ensure layout is initialized
            setupQuestFilters(questLayout)
        }
        if (::rocketLayoutGlobal.isInitialized) {
            setupRocketFilters(rocketLayoutGlobal)
        }
    }
}