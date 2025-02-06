package com.mints.projectgammatwo.ui


import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import com.mints.projectgammatwo.R
import com.mints.projectgammatwo.data.DataMappings
import com.mints.projectgammatwo.data.FilterPreferences


// This fragment displays a list of character filters and allows users to select which ones are enabled.
class FilterFragment : Fragment() {
    private lateinit var filterPreferences: FilterPreferences // To handle saving/loading preferences
    private val enabledCharacters = mutableSetOf<Int>() // To store enabled character IDs

    // Called when the fragment's view is created
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_list, container, false) // Inflate the fragment layout
    }

    // Called after the view is created. We access and modify the view here.
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        filterPreferences = FilterPreferences(requireContext()) // Initialize FilterPreferences

        // Load the saved enabled characters
        enabledCharacters.addAll(filterPreferences.getEnabledCharacters())

        // Setup the filters on the UI
        setupFilters(view)
    }

    // Setup character filters
    private fun setupFilters(view: View) {
        val characterLayout = view.findViewById<LinearLayout>(R.id.characterFiltersLayout) // Find the layout for character filters

        // Add character filters to the layout
        addSectionHeader(characterLayout, "Character Filters")
        // Loop through all characters and add checkboxes for each one
        DataMappings.characterNamesMap.forEach { (id, name) ->
            addCheckBox(characterLayout, name, id, enabledCharacters) { checked ->
                // Update the set of enabled characters when the checkbox is toggled
                if (checked) enabledCharacters.add(id) else enabledCharacters.remove(id)
                filterPreferences.saveEnabledCharacters(enabledCharacters) // Save updated preferences
            }
        }
    }

    // Add a section header to the layout
    private fun addSectionHeader(parent: LinearLayout, text: String) {
        TextView(context).apply {
            this.text = text
            textSize = 18f // Set the size of the header text
            setPadding(16) // Add padding around the text
            parent.addView(this) // Add the header to the layout
        }
    }

    // Add a checkbox to the layout for a given filter (character)
    private fun addCheckBox(
        parent: LinearLayout,
        text: String,
        id: Int,
        enabledSet: Set<Int>,
        onCheckedChange: (Boolean) -> Unit
    ) {
        CheckBox(context).apply {
            this.text = text // Set the checkbox text
            isChecked = id in enabledSet // Set the initial state based on whether the item is enabled
            setPadding(32, 8, 16, 8) // Add padding around the checkbox
            setOnCheckedChangeListener { _, checked -> onCheckedChange(checked) } // Handle checkbox toggling
            parent.addView(this) // Add the checkbox to the parent layout
        }
    }
}
