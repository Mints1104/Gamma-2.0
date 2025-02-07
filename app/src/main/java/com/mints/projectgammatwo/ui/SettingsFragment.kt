package com.mints.projectgammatwo.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.mints.projectgammatwo.R
import com.mints.projectgammatwo.data.DataSourcePreferences
import com.mints.projectgammatwo.data.DeletedInvasionsRepository
import com.mints.projectgammatwo.data.FilterPreferences

class SettingsFragment : Fragment() {

    private lateinit var resetVisitedButton: Button
    private lateinit var resetFiltersButton: Button
    private lateinit var wipeFiltersButton: Button  // New button for wiping invasion filters
    private lateinit var checkboxNYC: CheckBox
    private lateinit var checkboxLondon: CheckBox
    private lateinit var checkboxSG: CheckBox
    private lateinit var checkboxVancouver: CheckBox
    private lateinit var checkboxSydney: CheckBox

    private lateinit var dataSourcePreferences: DataSourcePreferences
    private lateinit var filterPreferences: FilterPreferences
    private lateinit var deletedRepo: DeletedInvasionsRepository

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        dataSourcePreferences = DataSourcePreferences(requireContext())
        filterPreferences = FilterPreferences(requireContext())
        deletedRepo = DeletedInvasionsRepository(requireContext())

        resetVisitedButton = view.findViewById(R.id.resetVisitedButton)
        resetFiltersButton = view.findViewById(R.id.resetFiltersButton)
        wipeFiltersButton = view.findViewById(R.id.wipeFiltersButton)  // Initialize the new button
        checkboxNYC = view.findViewById(R.id.checkbox_nyc)
        checkboxLondon = view.findViewById(R.id.checkbox_london)
        checkboxSG = view.findViewById(R.id.checkbox_sg)
        checkboxVancouver = view.findViewById(R.id.checkbox_vancouver)
        checkboxSydney = view.findViewById(R.id.checkbox_sydney)

        // Load saved data source selections.
        val selectedSources = dataSourcePreferences.getSelectedSources()
        checkboxNYC.isChecked = selectedSources.contains("NYC")
        checkboxLondon.isChecked = selectedSources.contains("LONDON")
        checkboxSG.isChecked = selectedSources.contains("SG")
        checkboxVancouver.isChecked = selectedSources.contains("VANCOUVER")
        checkboxSydney.isChecked = selectedSources.contains("SYDNEY")

        // Reset Visited Invasions button.
        resetVisitedButton.setOnClickListener {
            deletedRepo.resetDeletedInvasions()
            Toast.makeText(requireContext(), "Visited invasions reset", Toast.LENGTH_SHORT).show()
        }

        // Reset Filters button: Resets the enabled characters key.
        resetFiltersButton.setOnClickListener {
            filterPreferences.resetToDefault()
            Toast.makeText(requireContext(), "Invasion filters reset to default", Toast.LENGTH_SHORT).show()
        }

        // Wipe Filters button: Fully clear all invasion filter preferences.
        wipeFiltersButton.setOnClickListener {
            filterPreferences.wipeFilters()
            Toast.makeText(requireContext(), "All invasion filters wiped", Toast.LENGTH_SHORT).show()
        }


        // When any data source checkbox is clicked, update the stored selection.
        val checkListener = View.OnClickListener {
            val newSelection = mutableSetOf<String>()
            if (checkboxNYC.isChecked) newSelection.add("NYC")
            if (checkboxLondon.isChecked) newSelection.add("LONDON")
            if (checkboxSG.isChecked) newSelection.add("SG")
            if (checkboxVancouver.isChecked) newSelection.add("VANCOUVER")
            if (checkboxSydney.isChecked) newSelection.add("SYDNEY")
            // Ensure at least one data source is selected; default to NYC if none.
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
}
