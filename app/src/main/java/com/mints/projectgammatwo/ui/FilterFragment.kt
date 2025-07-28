package com.mints.projectgammatwo.ui

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
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
import android.widget.CompoundButton
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
        questsViewModel.fetchSpindaFormsFromApi()

        questsViewModel.spindaFormsLiveData.observe(viewLifecycleOwner) { spindaFormsMap ->
            Log.d("FilterFragment", "spindaFormsLiveData emitted: ${spindaFormsMap.keys}")
            DataMappings.initializePokemonData(requireContext()) {
                if (!isAdded) return@initializePokemonData
                Log.d("App", "Pokemon data loaded with ${DataMappings.pokemonEncounterMapNew.size} entries")

                setupQuestFilters(questLayout)
            }

        }

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


        getAvailableSpindaForms()
        questsViewModel.questsLiveData.observe(viewLifecycleOwner) { quests ->
            getAvailableSpindaForms()
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
        val inflater = requireActivity().layoutInflater
        val dialogView = inflater.inflate(R.layout.dialog_save_filter, null)

        val titleTextView = dialogView.findViewById<TextView>(R.id.saveFilterTitle)
        val editText = dialogView.findViewById<EditText>(R.id.editFilterName)
        val cancelButton = dialogView.findViewById<Button>(R.id.cancelFilterButton)
        val saveButton = dialogView.findViewById<Button>(R.id.saveFilterButton)

        // Set the dynamic title
        val type = if(isRocket) "rocket" else "quest"
        titleTextView.text = "Enter a name for the new $type filter"

        builder.setView(dialogView)
        val dialog = builder.create()

        // Set up button click listeners
        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        saveButton.setOnClickListener {
            val newFilterName = editText.text.toString().trim()
            if (newFilterName.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter a name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
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
                    originalSettingsOfLoadedRocketFilter = HashSet(enabledRocketFilters)
                }
                setupRocketFilters(rocketLayoutGlobal)

            } else {
                filterPreferences.saveEnabledQuestFilters(enabledQuestFilters)
                filterPreferences.saveCurrentQuestFilter(newFilterName)
                Log.d("SaveFilter","Current enabled spinda forms: ${filterPreferences.getEnabledSpindaForms()}")

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

        dialog.show()
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

        if(currentFilterType != "Rocket") {
            updateCurrentQuestFilter()
        }
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

        if(currentFilterType != "Quest") {
            updateCurrentRocketFilter()
        }


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
        val inflater = requireActivity().layoutInflater
        val dialogView = inflater.inflate(R.layout.dialog_select_filter, null)

        val title = dialogView.findViewById<TextView>(R.id.selectFilterTitle)
        val listContainer = dialogView.findViewById<LinearLayout>(R.id.filterListContainer)
        val cancelButton = dialogView.findViewById<Button>(R.id.cancelSelectButton)

        title.text = "Select $filterType Filter"

        builder.setView(dialogView)
        val dialog = builder.create()

        // Set up cancel button
        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

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
                        filterPreferences.getEnabledSpindaForms()
                        Log.d("SelectingFilter","Spinda forms enabled: ${filterPreferences.getEnabledSpindaForms()}")

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
                    showDeleteConfirmationDialog(filterName, filterType) {
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
                        updateDialogContent()
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
        onDeleted: () -> Unit
    ) {
        val builder = AlertDialog.Builder(requireContext())
        val inflater = requireActivity().layoutInflater
        val dialogView = inflater.inflate(R.layout.dialog_delete_filter, null)

        val filterNameDisplay = dialogView.findViewById<TextView>(R.id.filterNameDisplay)
        val cancelButton = dialogView.findViewById<Button>(R.id.cancelDeleteButton)
        val deleteButton = dialogView.findViewById<Button>(R.id.confirmDeleteButton)

        filterNameDisplay.text = filterName

        builder.setView(dialogView)
        val dialog = builder.create()

        // Set up button listeners
        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        deleteButton.setOnClickListener {
            val wasActiveRocket = filterType == "Rocket" && filterName == filterPreferences.getActiveRocketFilter()
            val wasActiveQuest = filterType == "Quest" && filterName == filterPreferences.getActiveQuestFilter()

            filterPreferences.deleteFilter(filterName, filterType)

            if (wasActiveRocket) {
                originalSettingsOfLoadedRocketFilter = null
            }
            if (wasActiveQuest) {
                originalSettingsOfLoadedQuestFilter = null
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
                        if (allSelected) {
                            enabledRocketFilters.clear()
                        } else {
                            enabledRocketFilters.clear()
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
                                        enabledQuestFilters.size == allPossibleQuestFilters.size

                                if (allCurrentlySelected) {
                                    enabledQuestFilters.clear()
                                } else {
                                    enabledQuestFilters.clear()
                                    enabledQuestFilters.addAll(allPossibleQuestFilters)
                                }
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
        if(currentFilterType == "Quest") updateCurrentQuestFilter()

        val filtersJson = questPrefs.getString("quest_api_filters", null)
        if (filtersJson != null) {
            val filters = Gson().fromJson(filtersJson, Quests.Filters::class.java)

            val spindaFormsMap: Map<String, Int> = questsViewModel.spindaFormsLiveData.value ?: emptyMap()
            Log.d("FilterFragment", "All available Spinda forms (cached): ${spindaFormsMap.keys}")

            addFilterSection(parent, "Stardust", filters.t3)
            addFilterSection(parent, "Pokémon Candy", filters.t4)
            addFilterSection(parent, "Mega Energy", filters.t12)

            addFilterSection(parent, "Pokémon Encounter", filters.t7, spindaFormsMap)

            addFilterSection(parent, "Item", filters.t2)
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
            setPadding(32, 8, 16, 8)
            setOnCheckedChangeListener { _, checked ->
                if (checked) {
                    enabledSet.add(id)
                } else {
                    enabledSet.remove(id)
                }
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
                if (checked) {
                    enabledSet.add(id)
                } else {
                    enabledSet.remove(id)
                }
                onCheckedChangeExternal(checked)
            }
            parent.addView(this)
        }
    }

    private fun buildQuestFilterString(section: String, rawValue: String): String {
        return when (section) {
            "Stardust" -> "3,$rawValue,0"
            "Mega Energy" -> "12,0,$rawValue"
            "Pokémon Encounter" -> "7,0,$rawValue"
            "Item" -> "2,0,$rawValue"
            "Pokémon Candy" -> "4,0,$rawValue"
            else -> rawValue
        }
    }

    private fun addFilterSection(
        parent: LinearLayout,
        sectionName: String,
        filterList: List<String>
    ) {
        addSectionHeader(parent, sectionName)

        if (filterList.isEmpty()) {
            TextView(context).apply {
                text = "None available for $sectionName"
                setPadding(16) // consistent 16dp padding around text
                parent.addView(this)
            }
        } else {
            // Sort differently if it’s one of the special categories; else alphabetical
            val sortedList = when (sectionName) {
                "Pokémon Encounter", "Mega Energy", "Pokémon Candy" ->
                    filterList.sortedBy { DataMappings.pokemonEncounterMapNew[it] ?: it }
                else ->
                    filterList.sorted()
            }

            sortedList.forEach { rawValue ->
                val displayText = when (sectionName) {
                    "Pokémon Encounter" -> DataMappings.pokemonEncounterMapNew[rawValue]
                        ?: "ID: $rawValue"
                    "Item"              -> DataMappings.itemMap["item$rawValue"]
                        ?: "Item ID: $rawValue"
                    "Mega Energy"       -> DataMappings.pokemonEncounterMapNew[rawValue]
                        ?: "Energy for ID: $rawValue"
                    "Pokémon Candy"     -> DataMappings.pokemonEncounterMapNew[rawValue]
                        ?: "Candy for ID: $rawValue"
                    else                -> rawValue
                }

                val compositeValue = buildQuestFilterString(sectionName, rawValue)

                // Only if it's **not** Spinda (ID 327) do we use a plain checkbox
                if (!(sectionName == "Pokémon Encounter" && rawValue == "327")) {
                    addQuestCheckBox(
                        parent,
                        displayText,
                        compositeValue,
                        enabledQuestFilters
                    ) {
                        filterPreferences.saveEnabledQuestFilters(enabledQuestFilters)
                    }
                }
            }
        }
    }
    private fun addFilterSection(
        parent: LinearLayout,
        sectionName: String,
        filterList: List<String>,
        spindaFormsMap: Map<String, Int>
    ) {
        addSectionHeader(parent, sectionName)

        if (filterList.isEmpty()) {
            TextView(context).apply {
                text = "None available for $sectionName"
                setPadding(16)
                parent.addView(this)
            }
        } else {
            val sortedList = filterList.sortedBy { DataMappings.pokemonEncounterMapNew[it] ?: it }

            sortedList.forEach { rawValue ->
                val displayText = DataMappings.pokemonEncounterMapNew[rawValue] ?: "ID: $rawValue"
                val compositeValue = buildQuestFilterString(sectionName, rawValue)

                if (rawValue == "327") {
                    addSpindaFilterWithForms(
                        parent,
                        displayText,
                        compositeValue,
                        spindaFormsMap
                    )
                } else {
                    addQuestCheckBox(
                        parent,
                        displayText,
                        compositeValue,
                        enabledQuestFilters
                    ) {
                        filterPreferences.saveEnabledQuestFilters(enabledQuestFilters)
                    }
                }
            }
        }
    }




    private fun getAvailableSpindaForms(): Map<String, Int> {
        val spindaForms = mutableMapOf<String, Int>()
        val quests = CurrentQuestData.currentQuests ?: emptyList() // Ensure null safety
        val spindaQuests = quests.filter { quest ->
            quest.rewardsIds.split(",").any { it == "327" }
        }
        spindaQuests.forEach { quest ->
            val formPattern = "\\((\\d+)\\)".toRegex()
            val matches = formPattern.findAll(quest.rewardsString)
            matches.forEach { matchResult ->
                val formNumber = matchResult.groupValues[1]
                val formKey = "spinda_form_$formNumber"
                spindaForms[formKey] = spindaForms.getOrDefault(formKey, 0) + 1
            }
        }
        Log.d("FilterFragment", "getAvailableSpindaForms returning: ${spindaForms.keys}")
        return spindaForms
    }

    private fun addSpindaFilterWithForms(
        parent: LinearLayout,
        displayText: String,
        baseCompositeValue: String,
        spindaFormsMap: Map<String, Int>
    ) {
        // Container for the entire “Spinda” block
        val spindaContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // Top row: main “Spinda” checkbox + spacer + expand/collapse button
        val topRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // We need to access formCheckboxes inside the main checkbox listener,
        // so we declare the list before the main checkbox is defined.
        val formCheckboxes = mutableListOf<CheckBox>()

        // Main “Spinda” checkbox itself
        val mainSpindaCheckbox = CheckBox(requireContext()).apply {
            text = displayText
            isChecked = baseCompositeValue in enabledQuestFilters
            setPadding(32, 8, 16, 8)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    enabledQuestFilters.add(baseCompositeValue)
                    formCheckboxes.forEach { it.isChecked = true }

                } else {
                    //  If the main toggle is turned off...
                    enabledQuestFilters.remove(baseCompositeValue)

                    // ...uncheck all specific form checkboxes in the UI...
                    formCheckboxes.forEach { it.isChecked = false }

                    // ...and clear them from saved preferences in the backend.
                    filterPreferences.clearEnabledSpindaForms()
                }
                filterPreferences.saveEnabledQuestFilters(enabledQuestFilters)

                // Enable or disable all child checkboxes based on the parent's state.
                formCheckboxes.forEach { it.isEnabled = isChecked }
            }
        }

        // Spacer
        val spacer = View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
        }

        // Expand/Collapse button
        val expandButton = MaterialButton(requireContext()).apply {
            text = if (spindaFormsMap.isNotEmpty()) "▶" else ""
            isEnabled = spindaFormsMap.isNotEmpty()
            backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            val onSurface = TypedValue().also {
                requireContext().theme.resolveAttribute(
                    com.google.android.material.R.attr.colorOnSurface, it, true
                )
            }.data
            setTextColor(onSurface)
            elevation = 0f
            minimumWidth = 0
            minWidth = 0
            setPadding(8.dpToPx(), 4.dpToPx(), 8.dpToPx(), 4.dpToPx())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        topRow.addView(mainSpindaCheckbox)
        topRow.addView(spacer)
        if (spindaFormsMap.isNotEmpty()) {
            topRow.addView(expandButton)
        }

        // Container for specific‐form checkboxes (hidden initially)
        val formsContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding((32 + 16).dpToPx(), 0, 16.dpToPx(), 8.dpToPx())
        }

        // Populate one checkbox per formKey
        if (spindaFormsMap.isNotEmpty()) {
            val enabledSpecificForms = filterPreferences.getEnabledSpindaForms()
            spindaFormsMap.keys.sorted().forEach { formKey ->
                val formNumber = formKey.removePrefix("spinda_form_")
                val formLabel = "Form #$formNumber"

                val formCheckbox = CheckBox(requireContext()).apply {
                    text = formLabel
                    isChecked = formKey in enabledSpecificForms

                    //  Child checkboxes are only enabled if the main checkbox is checked.
                    // This sets the initial state correctly on view creation.
                    isEnabled = mainSpindaCheckbox.isChecked

                    setPadding(0, 8.dpToPx(), 0, 8.dpToPx())
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )

                    setOnCheckedChangeListener { _, isChecked ->
                        updateSpindaFormSelection(formKey, isChecked)
                    }
                }

                formCheckboxes.add(formCheckbox)
                formsContainer.addView(formCheckbox)
            }

            // Expand/Collapse logic
            var isExpanded = false
            expandButton.setOnClickListener {
                isExpanded = !isExpanded
                formsContainer.visibility = if (isExpanded) View.VISIBLE else View.GONE
                expandButton.text = if (isExpanded) "▼" else "▶"
            }
        }

        // Assemble into parent
        spindaContainer.addView(topRow)
        spindaContainer.addView(formsContainer)
        parent.addView(spindaContainer)
    }





    private fun Int.dpToPx(): Int {
        return (this * requireContext().resources.displayMetrics.density).toInt()
    }

    private fun updateSpindaFormSelection(formKey: String, isChecked: Boolean) {
        val currentForms = filterPreferences.getEnabledSpindaForms().toMutableSet()
        if (isChecked) {
            currentForms.add(formKey)
        } else {
            currentForms.remove(formKey)
        }
        filterPreferences.saveEnabledSpindaForms(currentForms)
        Log.d("FilterFragmentSpinda", "Updated specific Spinda forms: $currentForms")
    }



    override fun onResume() {
        super.onResume()
        if (::questLayout.isInitialized) {
            setupQuestFilters(questLayout)
        }
        if (::rocketLayoutGlobal.isInitialized) {
            setupRocketFilters(rocketLayoutGlobal)
        }
    }
}