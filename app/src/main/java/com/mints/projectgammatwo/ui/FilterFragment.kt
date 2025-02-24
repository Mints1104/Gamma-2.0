package com.mints.projectgammatwo.ui

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import com.google.gson.Gson
import com.mints.projectgammatwo.R
import com.mints.projectgammatwo.data.DataMappings
import com.mints.projectgammatwo.data.FilterPreferences

class FilterFragment : Fragment() {

    // For Rocket filters we use your existing FilterPreferences.
    private lateinit var filterPreferences: FilterPreferences
    private val enabledRocketFilters = mutableSetOf<Int>()

    // For Quest filters we use a separate SharedPreferences instance.
    private val questPrefs: SharedPreferences by lazy {
        requireContext().getSharedPreferences("quest_filters", Context.MODE_PRIVATE)
    }
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

        // Load saved rocket filters from preferences.
        enabledRocketFilters.addAll(filterPreferences.getEnabledCharacters())
        // Load saved quest filters from our dedicated SharedPreferences.
        enabledQuestFilters.addAll(getEnabledQuestFilters())

        // Get references to UI containers.
        val radioGroup = view.findViewById<RadioGroup>(R.id.filterTypeRadioGroup)
        val rbRocket = view.findViewById<RadioButton>(R.id.rbRocket)
        val rbQuest = view.findViewById<RadioButton>(R.id.rbQuest)
        val rocketLayout = view.findViewById<LinearLayout>(R.id.rocketFiltersLayout)
         questLayout = view.findViewById(R.id.questFiltersLayout)

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
        setupQuestFilters(questLayout)
    }

    // Setup Rocket Filters (using your DataMappings.characterNamesMap).
    private fun setupRocketFilters(parent: LinearLayout) {
        parent.removeAllViews()
        addSectionHeader(parent, "Rocket Filters")
        DataMappings.characterNamesMap.forEach { (id, name) ->
            addCheckBox(parent, name, id, enabledRocketFilters) { checked ->
                if (checked) enabledRocketFilters.add(id) else enabledRocketFilters.remove(id)
                filterPreferences.saveEnabledCharacters(enabledRocketFilters)
            }
        }
    }



    // Setup Quest Filters using dynamic API data.
    private fun setupQuestFilters(parent: LinearLayout) {
        parent.removeAllViews()
        addSectionHeader(parent, "Quest Filters")
        // Try to get the stored API filters JSON.
        val filtersJson = questPrefs.getString("quest_api_filters", null)
        if (filtersJson != null) {
            // Parse the JSON into the Filters data class.
            val filters = Gson().fromJson(filtersJson, Filters::class.java)
            // Build the sections in the desired order:
            // Stardust (t3), Pokémon Candy (t4), Mega Energy (t12),
            // Pokémon Encounter (t7), and Item (t2)
            addFilterSection(parent, "Stardust", filters.t3)
            addFilterSection(parent, "Pokémon Candy", filters.t4)
            addFilterSection(parent, "Mega Energy", filters.t12)
            addFilterSection(parent, "Pokémon Encounter", filters.t7)
            addFilterSection(parent, "Item", filters.t2)
        } else {
            addSectionHeader(parent, "No quest filters available from API")
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
            filterList.forEach { rawValue ->
                // Map raw values to friendly names when appropriate.
                val displayText = when (sectionName) {
                    "Pokémon Encounter" -> DataMappings.pokemonEncounterMap[rawValue] ?: rawValue
                    "Item" -> DataMappings.itemMap["item$rawValue"] ?: rawValue
                    "Mega Energy" -> DataMappings.megaEnergyMap[rawValue] ?: rawValue
                    else -> rawValue
                }
                // Build the full composite filter string.
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
