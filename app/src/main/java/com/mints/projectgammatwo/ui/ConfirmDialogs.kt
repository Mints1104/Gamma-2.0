package com.mints.projectgammatwo.ui

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.widget.Button
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.setFragmentResult
import com.mints.projectgammatwo.R

/**
 * A yes/no confirmation built on dialog_confirm_import_hotspots.
 *
 * A DialogFragment so it survives rotation: the bare AlertDialogs it replaces were dismissed (and
 * leaked their window) whenever the activity was recreated. Show it on the caller's
 * childFragmentManager and register a listener for [requestKey] there in the caller's onCreate;
 * the result is sent only when the user confirms. Texts left at 0 keep the layout's own.
 */
class ConfirmDialogFragment : DialogFragment() {

    companion object {
        private const val ARG_REQUEST_KEY = "request_key"
        private const val ARG_TITLE = "title"
        private const val ARG_MESSAGE = "message"
        private const val ARG_CONFIRM = "confirm"
        private const val ARG_CANCEL = "cancel"

        fun newInstance(
            requestKey: String,
            @StringRes title: Int = 0,
            @StringRes message: Int = 0,
            @StringRes confirm: Int = 0,
            @StringRes cancel: Int = 0,
        ) = ConfirmDialogFragment().apply {
            arguments = bundleOf(
                ARG_REQUEST_KEY to requestKey,
                ARG_TITLE to title,
                ARG_MESSAGE to message,
                ARG_CONFIRM to confirm,
                ARG_CANCEL to cancel,
            )
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val args = requireArguments()
        val requestKey = requireNotNull(args.getString(ARG_REQUEST_KEY))
        // Not this fragment's layoutInflater: in a DialogFragment that calls onCreateDialog.
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_confirm_import_hotspots, null)

        fun TextView.setTextRes(key: String) {
            val res = args.getInt(key)
            if (res != 0) setText(res)
        }
        view.findViewById<TextView>(R.id.confirmTitle).setTextRes(ARG_TITLE)
        view.findViewById<TextView>(R.id.confirmMessage).setTextRes(ARG_MESSAGE)
        val cancelButton = view.findViewById<Button>(R.id.cancelConfirmButton)
        val confirmButton = view.findViewById<Button>(R.id.confirmImportButton)
        cancelButton.setTextRes(ARG_CANCEL)
        confirmButton.setTextRes(ARG_CONFIRM)

        cancelButton.setOnClickListener { dismiss() }
        confirmButton.setOnClickListener {
            setFragmentResult(requestKey, Bundle.EMPTY)
            dismiss()
        }

        return AlertDialog.Builder(requireContext())
            .setView(view)
            .create()
    }
}

/**
 * Explains that the overlay needs "display over other apps" and offers to open that setting.
 * It acts on its own, so it needs no result listener.
 */
class OverlayPermissionDialogFragment : DialogFragment() {

    companion object {
        private const val TAG = "overlay_permission_dialog"

        /** Shows the dialog unless it is already showing (e.g. a double tap on Start). */
        fun show(fragmentManager: FragmentManager) {
            if (fragmentManager.findFragmentByTag(TAG) != null || fragmentManager.isStateSaved) return
            OverlayPermissionDialogFragment().show(fragmentManager, TAG)
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        isCancelable = false
        val context = requireContext()
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_overlay_permission, null)

        view.findViewById<Button>(R.id.notNowButton).setOnClickListener { dismiss() }
        view.findViewById<Button>(R.id.openSettingsButton).setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri())
            )
            dismiss()
        }

        return AlertDialog.Builder(context)
            .setView(view)
            .create()
    }
}
