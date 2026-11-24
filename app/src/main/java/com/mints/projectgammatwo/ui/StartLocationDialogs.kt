package com.mints.projectgammatwo.ui

import android.app.Dialog
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.mints.projectgammatwo.R
import com.mints.projectgammatwo.data.LastLocations
import com.mints.projectgammatwo.helpers.parseLatLng
import com.mints.projectgammatwo.helpers.toPlainCoordinate

/**
 * Shows and sets the point a list orders from: the invasion list's distance sort, or the quest
 * list's nearest-first order ([LastLocations]).
 *
 * The field shows the stored point and follows it live — the overlay's ▶ or a tap on the list
 * moves it while the dialog is open — until the player types in it, so their text is never
 * overwritten. Set stores the typed point (marked as set by hand) and reports [RESULT_SET] on
 * [requestKey]; the host re-orders its list. Show it on the host's childFragmentManager and
 * register the result listener in the host's onCreate.
 */
class StartLocationDialogFragment : DialogFragment() {

    companion object {
        private const val ARG_KIND = "kind"
        private const val STATE_EDITED = "edited"
        const val RESULT_SET = "set"

        fun requestKey(kind: LastLocations.Kind) = "start_location_${kind.name}"

        fun newInstance(kind: LastLocations.Kind) = StartLocationDialogFragment().apply {
            arguments = bundleOf(ARG_KIND to kind.name)
        }

        /** "lat, lng" as plain decimals, the form the field accepts back. */
        fun format(point: Pair<Double, Double>): String =
            "${point.first.toPlainCoordinate()}, ${point.second.toPlainCoordinate()}"
    }

    private lateinit var kind: LastLocations.Kind
    private lateinit var lastLocations: LastLocations
    private lateinit var input: EditText
    private lateinit var source: TextView
    private lateinit var error: TextView

    /** Whether the player has typed in the field; it then stops following the stored point. */
    private var edited = false
    /** Set while the field is filled from the stored point, so that isn't taken as typing. */
    private var filling = false
    private var stopObserving: (() -> Unit)? = null
    private var watcherAdded = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        kind = LastLocations.Kind.valueOf(requireNotNull(requireArguments().getString(ARG_KIND)))
        lastLocations = LastLocations(requireContext())
        edited = savedInstanceState?.getBoolean(STATE_EDITED) ?: false

        // Not this fragment's layoutInflater: in a DialogFragment that is built by calling
        // onCreateDialog, so using it here recurses.
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_start_location, null)
        input = view.findViewById(R.id.startLocationInput)
        source = view.findViewById(R.id.startLocationSource)
        error = view.findViewById(R.id.startLocationError)
        if (savedInstanceState == null) showStored()

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(
                if (kind == LastLocations.Kind.QUEST) R.string.start_location_title_quests
                else R.string.start_location_title_rockets
            )
            .setView(view)
            // Listeners are set in setOnShowListener below, so these buttons don't dismiss.
            .setPositiveButton(R.string.start_location_set, null)
            .setNegativeButton(R.string.action_cancel, null)
            .setNeutralButton(R.string.start_location_paste, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { save() }
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener { dismiss() }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { paste() }
        }
        return dialog
    }

    override fun onStart() {
        super.onStart()
        // Added here, after a restored dialog has put its text back, so the restore isn't
        // mistaken for typing.
        if (!watcherAdded) {
            watcherAdded = true
            input.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    if (filling) return
                    edited = true
                    error.visibility = View.GONE
                }
            })
        }
        // The point may have moved while the dialog was in the background.
        showStored()
        stopObserving = lastLocations.observe(kind) { showStored() }
    }

    override fun onStop() {
        stopObserving?.invoke()
        stopObserving = null
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_EDITED, edited)
    }

    /** Shows where the point came from, and fills the field with it unless the player typed. */
    private fun showStored() {
        val point = lastLocations.get(kind)
        source.setText(
            when {
                point == null && kind == LastLocations.Kind.QUEST -> R.string.start_location_not_set_quests
                point == null -> R.string.start_location_not_set_rockets
                lastLocations.isManual(kind) -> R.string.start_location_manual
                kind == LastLocations.Kind.QUEST -> R.string.start_location_from_quest
                else -> R.string.start_location_from_rocket
            }
        )
        if (edited) return
        val text = point?.let(::format).orEmpty()
        if (input.text.toString() == text) return
        filling = true
        input.setText(text)
        input.setSelection(text.length)
        filling = false
    }

    private fun paste() {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val text = clipboard?.primaryClip?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)?.coerceToText(requireContext())?.toString()?.trim()
        if (text.isNullOrEmpty()) return
        input.setText(text)
        input.setSelection(input.text.length)
    }

    private fun save() {
        val point = parseLatLng(input.text.toString())
        if (point == null) {
            error.visibility = View.VISIBLE
            return
        }
        lastLocations.set(kind, point.first, point.second, manual = true)
        setFragmentResult(requestKey(kind), bundleOf(RESULT_SET to true))
        dismiss()
    }
}
