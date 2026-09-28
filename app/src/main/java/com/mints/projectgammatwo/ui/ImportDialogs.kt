package com.mints.projectgammatwo.ui

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.mints.projectgammatwo.R

/**
 * The "paste JSON" import dialog, used for both settings and favorites.
 *
 * A DialogFragment rather than a bare AlertDialog so it survives rotation with the pasted text
 * intact; the plain dialogs vanished and took the text with them. Show it on the caller's
 * childFragmentManager and listen there for [requestKey]: the result carries either [RESULT_JSON]
 * or, when the layout has a choose-file button and it was tapped, [RESULT_CHOOSE_FILE].
 *
 * The layout must use the ids editImportJson, cancelImportButton and importButton, and may have
 * chooseImportFileButton.
 */
class JsonImportDialogFragment : DialogFragment() {

    companion object {
        private const val ARG_REQUEST_KEY = "request_key"
        private const val ARG_LAYOUT = "layout"

        const val RESULT_JSON = "json"
        const val RESULT_CHOOSE_FILE = "choose_file"

        fun newInstance(requestKey: String, @LayoutRes layout: Int) = JsonImportDialogFragment().apply {
            arguments = bundleOf(ARG_REQUEST_KEY to requestKey, ARG_LAYOUT to layout)
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val args = requireArguments()
        val requestKey = requireNotNull(args.getString(ARG_REQUEST_KEY))
        // Not this fragment's layoutInflater: in a DialogFragment that is built by calling
        // onCreateDialog, so using it here recurses.
        val view = LayoutInflater.from(requireContext()).inflate(args.getInt(ARG_LAYOUT), null)
        val editText = view.findViewById<EditText>(R.id.editImportJson)

        view.findViewById<Button>(R.id.cancelImportButton).setOnClickListener { dismiss() }
        view.findViewById<Button?>(R.id.chooseImportFileButton)?.setOnClickListener {
            setFragmentResult(requestKey, bundleOf(RESULT_CHOOSE_FILE to true))
            dismiss()
        }
        view.findViewById<Button>(R.id.importButton).setOnClickListener {
            val json = editText.text.toString()
            if (json.isBlank()) {
                Toast.makeText(requireContext(), R.string.settings_import_input_empty, Toast.LENGTH_SHORT).show()
            } else {
                setFragmentResult(requestKey, bundleOf(RESULT_JSON to json))
                dismiss()
            }
        }

        return AlertDialog.Builder(requireContext())
            .setView(view)
            .create()
    }
}

/**
 * Asks before a settings import points teleports at a custom link.
 *
 * Every teleport opens that link, so a backup shared by someone else could silently redirect
 * them; showing it and asking is the whole point. Accepting sets [RESULT_URL] on [REQUEST_KEY];
 * declining (or backing out) leaves the current teleport link alone.
 */
class ConfirmImportedDeeplinkDialogFragment : DialogFragment() {

    companion object {
        const val REQUEST_KEY = "confirm_imported_deeplink"
        const val RESULT_URL = "url"
        private const val ARG_URL = "url"

        fun newInstance(url: String) = ConfirmImportedDeeplinkDialogFragment().apply {
            arguments = bundleOf(ARG_URL to url)
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val url = requireNotNull(requireArguments().getString(ARG_URL))
        return AlertDialog.Builder(requireContext())
            .setTitle(R.string.import_deeplink_title)
            .setMessage(getString(R.string.import_deeplink_message, url))
            .setPositiveButton(R.string.import_deeplink_use) { _, _ ->
                setFragmentResult(REQUEST_KEY, bundleOf(RESULT_URL to url))
            }
            .setNegativeButton(R.string.import_deeplink_keep, null)
            .create()
    }
}
