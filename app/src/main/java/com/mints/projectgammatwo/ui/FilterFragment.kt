package com.mints.projectgammatwo.ui

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.button.MaterialButton
import com.google.gson.Gson
import com.mints.projectgammatwo.R
import com.mints.projectgammatwo.data.DataMappings
import com.mints.projectgammatwo.data.FilterPreferences
import com.mints.projectgammatwo.data.PokemonRepository
import com.mints.projectgammatwo.data.Quests
import com.mints.projectgammatwo.viewmodels.QuestsViewModel

class FilterFragment : Fragment() {

    // For Rocket filters we use your existing FilterPreferences.
    private lateinit var filterPreferences: FilterPreferences
    private val enabledRocketFilters = mutableSetOf<Int>()
    private lateinit var questsViewModel: QuestsViewModel

    private lateinit var questPrefs: SharedPreferences

    // We'll now store the full composite quest filter strings.
    private val enabledQuestFilters = mutableSetOf<String>()
    private lateinit var questLayout: LinearLayout

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





        // Load saved rocket filters from preferences.
        enabledRocketFilters.addAll(filterPreferences.getEnabledCharacters())
        // Load saved quest filters from our dedicated SharedPreferences.
        enabledQuestFilters.addAll(getEnabledQuestFilters())
        questsViewModel = ViewModelProvider(this)[QuestsViewModel::class.java]

        // Get references to UI containers.
        val radioGroup = view.findViewById<RadioGroup>(R.id.filterTypeRadioGroup)
        val rbRocket = view.findViewById<RadioButton>(R.id.rbRocket)
        val rbQuest = view.findViewById<RadioButton>(R.id.rbQuest)
        val rocketLayout = view.findViewById<LinearLayout>(R.id.rocketFiltersLayout)
         questLayout = view.findViewById(R.id.questFiltersLayout)

        DataMappings.initializePokemonData(requireContext()) {
            if (!isAdded) return@initializePokemonData  // Fragment is no longer attached, so exit early.
            Log.d("App", "Pokemon data loaded with ${DataMappings.pokemonEncounterMapNew.size} entries")
            setupQuestFilters(questLayout)
        }


        // Set up radio group listener to toggle between filter UIs.
        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbRocket -> {
                    rocketLayout.visibility = View.VISIBLE
                    questLayout.visibility = View.GONE
                }
                R.id.rbQuest -> {
                    rocketLayout.visibility = View.GONE
                    questLayout.visibility = View.VISIBLE
                }
            }
        }

        // Populate the rocket filters UI.
        setupRocketFilters(rocketLayout)
        // Populate the quest filters UI using dynamic API data.
      //  setupQuestFilters(questLayout)
    }

    // Setup Rocket Filters (using your DataMappings.characterNamesMap).
    private fun setupRocketFilters(parent: LinearLayout) {
        parent.removeAllViews()
        // Add Reset and Toggle All buttons for Rocket Filters
        addResetButton(parent, "Rocket")
        addToggleAllButton(parent, "Rocket")
        addSectionHeader(parent, "Rocket Filters")
        DataMappings.characterNamesMap.forEach { (id, name) ->
            addCheckBox(parent, name, id, enabledRocketFilters) { checked ->
                if (checked) enabledRocketFilters.add(id) else enabledRocketFilters.remove(id)
                filterPreferences.saveEnabledCharacters(enabledRocketFilters)
            }
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
                        saveEnabledQuestFilters(enabledQuestFilters)
                        setupQuestFilters(parent)
                    }
                }
            }
        }
        parent.addView(resetButton, 0)
    }

    private fun addToggleAllButton(parent: LinearLayout, filterType: String) {
        val toggleButton = MaterialButton(requireContext()).apply {
            text = "Toggle All $filterType Filters"
            setPadding(16, 8, 16, 8)
            setOnClickListener {
                when (filterType) {
                    "Rocket" -> {
                        // Check if all rocket filters are selected
                        val allSelected = DataMappings.characterNamesMap.keys.all { it in enabledRocketFilters }
                        if (allSelected) {
                            enabledRocketFilters.clear()
                        } else {
                            enabledRocketFilters.addAll(DataMappings.characterNamesMap.keys)
                        }
                        filterPreferences.saveEnabledCharacters(enabledRocketFilters)
                        setupRocketFilters(parent)
                    }
                    "Quest" -> {
                        // For quest filters, iterate over all checkboxes
                        // Assuming that each quest filter checkbox is added after a header, you might need to traverse your layout
                        // Here, we'll simply rebuild the list by toggling the enabledQuestFilters set.
                        val filtersJson = questPrefs.getString("quest_api_filters", null)
                        if (filtersJson != null) {
                            val filters = Gson().fromJson(filtersJson, Quests.Filters::class.java)
                            // Gather all composite quest filter strings from all sections:
                            val allFilters = mutableSetOf<String>()
                            listOf(filters.t3, filters.t4, filters.t12, filters.t7, filters.t2).forEach { list ->
                                list.forEach { rawValue ->
                                    val section = when {
                                        filters.t3.contains(rawValue) -> "Stardust"
                                        filters.t12.contains(rawValue) -> "Mega Energy"
                                        filters.t7.contains(rawValue) -> "Pokémon Encounter"
                                        filters.t2.contains(rawValue) -> "Item"
                                        else -> "Pokémon Candy"
                                    }
                                    allFilters.add(buildQuestFilterString(section, rawValue))
                                }
                            }
                            // Toggle logic: if all are selected, clear; otherwise select all.
                            if (enabledQuestFilters.containsAll(allFilters)) {
                                enabledQuestFilters.clear()
                            } else {
                                enabledQuestFilters.clear()
                                enabledQuestFilters.addAll(allFilters)
                            }
                            saveEnabledQuestFilters(enabledQuestFilters)
                            setupQuestFilters(parent)
                        }
                    }
                }
            }
        }
        // Add the toggle button at the top (or wherever you prefer)
        parent.addView(toggleButton, 0)
    }




    // Setup Quest Filters using dynamic API data.
    private fun setupQuestFilters(parent: LinearLayout) {
        parent.removeAllViews()
        // Add Reset and Toggle All buttons for Quest Filters
        addResetButton(parent, "Quest")
        addToggleAllButton(parent, "Quest")
        addSectionHeader(parent, "Quest Filters")
        val filtersJson = questPrefs.getString("quest_api_filters", null)
        if (filtersJson != null) {
            val filters = Gson().fromJson(filtersJson, Quests.Filters::class.java)
            addFilterSection(parent, "Stardust", filters.t3)
            addFilterSection(parent, "Pokémon Candy", filters.t4)
            addFilterSection(parent, "Mega Energy", filters.t12)
            addFilterSection(parent, "Pokémon Encounter", filters.t7)
            addFilterSection(parent, "Item", filters.t2)
        } else {
            questsViewModel.fetchQuests()
            addSectionHeader(parent, "Please open quests tab to update data")
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

    // Existing addCheckBox for rocket filters.
    private fun addCheckBox(
        parent: LinearLayout,
        text: String,
        id: Int,
        enabledSet: Set<Int>,
        onCheckedChange: (Boolean) -> Unit
    ) {
        CheckBox(context).apply {
            this.text = text
            isChecked = id in enabledSet
            setPadding(32, 8, 16, 8)
            setOnCheckedChangeListener { _, checked -> onCheckedChange(checked) }
            parent.addView(this)
        }
    }

    // Helper for quest filters using String IDs.
    private fun addQuestCheckBox(
        parent: LinearLayout,
        text: String,
        id: String,
        enabledSet: Set<String>,
        onCheckedChange: (Boolean) -> Unit
    ) {
        CheckBox(context).apply {
            this.text = text
            isChecked = id in enabledSet
            setPadding(32, 8, 16, 8)
            setOnCheckedChangeListener { _, checked -> onCheckedChange(checked) }
            parent.addView(this)
        }
    }

    /**
     * Constructs the full quest filter string (used in the API call)
     * based on the section and the raw value from the API.
     *
     * For example:
     * - For "Stardust" filters, the API expects "3,{value},0".
     * - For "Mega Energy" filters, it expects "2,0,{value}".
     * - For "Pokémon Encounter", it expects "7,0,{value}".
     * - For "Item", we now return "2,0,{value}".
     */
    private fun buildQuestFilterString(section: String, rawValue: String): String {
        return when (section) {
            "Stardust" -> "3,$rawValue,0"
            "Mega Energy" -> "12,0,$rawValue"
            "Pokémon Encounter" -> "7,0,$rawValue"
            "Item" -> "2,0,$rawValue"  // Updated for item filters.
            "Pokémon Candy" -> "4,0,$rawValue"
            else -> rawValue // For "Pokémon Candy" (or any other), no extra formatting.
        }
    }

    // Helper to add a section of quest filters.
    private fun addFilterSection(parent: LinearLayout, sectionName: String, filterList: List<String>) {
        addSectionHeader(parent, sectionName)
        if (filterList.isEmpty()) {
            TextView(context).apply {
                text = "None available"
                parent.addView(this)
            }
        } else {
            val sortedList = when (sectionName) {
                "Pokémon Encounter" -> filterList.sortedBy { DataMappings.pokemonEncounterMapNew[it] ?: it }
                "Mega Energy" -> filterList.sortedBy { DataMappings.pokemonEncounterMapNew[it] ?: it }
                "Pokémon Candy" -> filterList.sortedBy { DataMappings.pokemonEncounterMapNew[it] ?: it }
                else -> filterList
            }
            sortedList.forEach { rawValue ->
                val displayText = when (sectionName) {
                    "Pokémon Encounter" -> DataMappings.pokemonEncounterMapNew[rawValue] ?: rawValue
                    "Item" -> DataMappings.itemMap["item$rawValue"] ?: rawValue
                    "Mega Energy" -> DataMappings.pokemonEncounterMapNew[rawValue] ?: rawValue
                    "Pokémon Candy" -> DataMappings.pokemonEncounterMapNew[rawValue] ?: rawValue
                    else -> rawValue
                }
                val compositeValue = buildQuestFilterString(sectionName, rawValue)
                addQuestCheckBox(parent, displayText, compositeValue, enabledQuestFilters) { checked ->
                    if (checked) enabledQuestFilters.add(compositeValue)
                    else enabledQuestFilters.remove(compositeValue)
                    saveEnabledQuestFilters(enabledQuestFilters)
                }
            }
        }
    }



    // Save quest filters using a dedicated key.
    private fun saveEnabledQuestFilters(filters: Set<String>) {
        questPrefs.edit().putStringSet("enabled_quest_filters", filters).apply()
    }

    // Retrieve quest filters.
    private fun getEnabledQuestFilters(): Set<String> {
        return questPrefs.getStringSet("enabled_quest_filters", null) ?: setOf()
    }

    override fun onResume() {
        //refresh available quests on fragment resume
        setupQuestFilters(questLayout)
        super.onResume()
    }
}
