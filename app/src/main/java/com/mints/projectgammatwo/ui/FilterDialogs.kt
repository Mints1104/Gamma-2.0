package com.mints.projectgammatwo.ui

import android.app.Dialog
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.mints.projectgammatwo.R
import com.mints.projectgammatwo.data.FilterPreferences

/*
 * The filter screen's dialogs. They are DialogFragments so they survive rotation (the AlertDialogs
 * they replace were dismissed, losing a typed filter name). Each reports back through a fragment
 * result on the manager it was shown on; FilterFragment shows them on its childFragmentManager
 * and registers the listeners in onCreate, the only place a listener exists when a restored
 * dialog reports back. The filter type is "Rocket" or "Quest", as in FilterFragment.
 */

/**
 * Names a new filter, or updates / copies the active one. Reports [REQUEST_KEY] with the name and
 * type; the caller snapshots its live selection under that name.
 */
class SaveFilterDialogFragment : DialogFragment() {

    companion object {
        const val REQUEST_KEY = "save_filter"
        const val RESULT_NAME = "name"
        const val RESULT_TYPE = "type"
        /** True when the active filter was overwritten rather than a new one saved. */
        const val RESULT_UPDATED = "updated"
        private const val ARG_TYPE = "type"

        fun newInstance(filterType: String) = SaveFilterDialogFragment().apply {
            arguments = bundleOf(ARG_TYPE to filterType)
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val filterType = requireNotNull(requireArguments().getString(ARG_TYPE))
        val isRocket = filterType == "Rocket"
        val prefs = FilterPreferences(requireContext())
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_save_filter, null)

        val titleTextView   = view.findViewById<TextView>(R.id.saveFilterTitle)
        val editText        = view.findViewById<EditText>(R.id.editFilterName)
        val cancelButton    = view.findViewById<Button>(R.id.cancelFilterButton)
        val saveAsNewButton = view.findViewById<Button>(R.id.saveAsNewFilterButton)
        val saveButton      = view.findViewById<Button>(R.id.saveFilterButton)

        fun report(name: String, updated: Boolean) {
            setFragmentResult(
                REQUEST_KEY,
                bundleOf(RESULT_NAME to name, RESULT_TYPE to filterType, RESULT_UPDATED to updated)
            )
            dismiss()
        }

        cancelButton.setOnClickListener { dismiss() }

        val activeFilterName = if (isRocket) prefs.getActiveRocketFilter() else prefs.getActiveQuestFilter()
        if (activeFilterName.isNotEmpty()) {
            // Editing an existing filter: pre-fill the name (a restored dialog keeps what was
            // typed instead) and show all three buttons.
            titleTextView.text = "Editing: $activeFilterName"
            editText.setText(activeFilterName)
            editText.hint = "New name for Save as New"
            saveButton.text = "Update"
            saveAsNewButton.visibility = View.VISIBLE

            // Update — overwrite the existing snapshot in place.
            saveButton.setOnClickListener { report(activeFilterName, updated = true) }

            // Save as New — require a different name, leave the original untouched.
            saveAsNewButton.setOnClickListener {
                val newName = editText.text.toString().trim()
                if (newName.isEmpty() || newName == activeFilterName) {
                    Toast.makeText(requireContext(), "Enter a different name to save as new", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                report(newName, updated = false)
            }
        } else {
            // No active filter — standard save flow, two buttons only.
            titleTextView.text = "Enter a name for the new ${filterType.lowercase()} filter"
            saveButton.setOnClickListener {
                val newName = editText.text.toString().trim()
                if (newName.isEmpty()) {
                    Toast.makeText(requireContext(), "Please enter a name", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                report(newName, updated = false)
            }
        }

        return AlertDialog.Builder(requireContext())
            .setView(view)
            .create()
    }
}

/**
 * Lists the saved filters of one type. Selecting one reports [REQUEST_KEY] with its name; the
 * caller loads it. Deleting asks first ([DeleteFilterDialogFragment]), deletes it here, keeps the
 * list open and reports [REQUEST_DELETED_KEY] so the caller can re-read its live selection.
 */
class SelectFilterDialogFragment : DialogFragment() {

    companion object {
        const val REQUEST_KEY = "select_filter"
        const val REQUEST_DELETED_KEY = "filter_deleted"
        const val RESULT_NAME = "name"
        const val RESULT_TYPE = "type"
        private const val ARG_TYPE = "type"
        private const val DELETE_DIALOG_TAG = "delete_filter_dialog"

        fun newInstance(filterType: String) = SelectFilterDialogFragment().apply {
            arguments = bundleOf(ARG_TYPE to filterType)
        }
    }

    private val filterType: String
        get() = requireNotNull(requireArguments().getString(ARG_TYPE))

    private var listContainer: LinearLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        childFragmentManager.setFragmentResultListener(
            DeleteFilterDialogFragment.REQUEST_KEY, this
        ) { _, result ->
            val name = result.getString(DeleteFilterDialogFragment.RESULT_NAME) ?: return@setFragmentResultListener
            FilterPreferences(requireContext()).deleteFilter(name, filterType)
            Toast.makeText(requireContext(), "Filter '$name' deleted", Toast.LENGTH_SHORT).show()
            populateList()
            setFragmentResult(REQUEST_DELETED_KEY, bundleOf(RESULT_TYPE to filterType))
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_select_filter, null)
        view.findViewById<TextView>(R.id.selectFilterTitle).text = "Select $filterType Filter"
        view.findViewById<Button>(R.id.cancelSelectButton).setOnClickListener { dismiss() }
        listContainer = view.findViewById(R.id.filterListContainer)
        populateList()

        return AlertDialog.Builder(requireContext())
            .setView(view)
            .create()
    }

    override fun onDestroy() {
        listContainer = null
        super.onDestroy()
    }

    private fun populateList() {
        val container = listContainer ?: return
        val context = requireContext()
        val inflater = LayoutInflater.from(context)
        container.removeAllViews()

        val prefs = FilterPreferences(context)
        val filterNames = if (filterType == "Rocket") prefs.listFilterNames() else prefs.listQuestFilterNames()
        if (filterNames.isEmpty()) {
            container.addView(TextView(context).apply {
                text = "No saved filters available"
                setPadding(16, 16, 16, 16)
                gravity = Gravity.CENTER
                textSize = 16f
                setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray))
            })
            return
        }

        filterNames.forEach { filterName ->
            val itemView = inflater.inflate(R.layout.filter_list_item, container, false)
            itemView.findViewById<TextView>(R.id.filterNameText).text = filterName
            itemView.findViewById<Button>(R.id.selectFilterButton).setOnClickListener {
                setFragmentResult(REQUEST_KEY, bundleOf(RESULT_NAME to filterName, RESULT_TYPE to filterType))
                dismiss()
            }
            itemView.findViewById<ImageButton>(R.id.deleteFilterButton).setOnClickListener {
                if (childFragmentManager.findFragmentByTag(DELETE_DIALOG_TAG) == null) {
                    DeleteFilterDialogFragment.newInstance(filterName)
                        .show(childFragmentManager, DELETE_DIALOG_TAG)
                }
            }
            container.addView(itemView)
        }
    }
}

/** Confirms deleting a saved filter; reports [REQUEST_KEY] with its name only on confirm. */
class DeleteFilterDialogFragment : DialogFragment() {

    companion object {
        const val REQUEST_KEY = "delete_filter"
        const val RESULT_NAME = "name"
        private const val ARG_NAME = "name"

        fun newInstance(filterName: String) = DeleteFilterDialogFragment().apply {
            arguments = bundleOf(ARG_NAME to filterName)
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val name = requireNotNull(requireArguments().getString(ARG_NAME))
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_delete_filter, null)
        view.findViewById<TextView>(R.id.filterNameDisplay).text = name
        view.findViewById<Button>(R.id.cancelDeleteButton).setOnClickListener { dismiss() }
        view.findViewById<Button>(R.id.confirmDeleteButton).setOnClickListener {
            setFragmentResult(REQUEST_KEY, bundleOf(RESULT_NAME to name))
            dismiss()
        }
        return AlertDialog.Builder(requireContext())
            .setView(view)
            .create()
    }
}
