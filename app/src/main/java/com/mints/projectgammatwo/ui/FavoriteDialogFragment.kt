package com.mints.projectgammatwo.ui

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.mints.projectgammatwo.R
import com.mints.projectgammatwo.data.FavoriteLocation
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast

/**
 * DialogFragment used to add or edit a favorite location.
 */
class FavoriteDialogFragment : DialogFragment() {

    /**
     * Listener interface to deliver favorite save events.
     */
    interface FavoriteDialogListener {
        fun onFavoriteSaved(favorite: FavoriteLocation, position: Int)
    }

    private var listener: FavoriteDialogListener? = null
    private var favorite: FavoriteLocation? = null
    private var position: Int = -1

    companion object {
        private const val ARG_FAVORITE = "arg_favorite"
        private const val ARG_POSITION = "arg_position"

        fun newInstance(favorite: FavoriteLocation?, position: Int): FavoriteDialogFragment {
            val fragment = FavoriteDialogFragment()
            val args = Bundle()
            args.putSerializable(ARG_FAVORITE, favorite)
            args.putInt(ARG_POSITION, position)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = when {
            targetFragment is FavoriteDialogListener -> targetFragment as FavoriteDialogListener
            parentFragment is FavoriteDialogListener -> parentFragment as FavoriteDialogListener
            context is FavoriteDialogListener -> context
            else -> null
        }
        if (listener == null) {
            throw RuntimeException("Parent must implement FavoriteDialogListener")
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view: View = LayoutInflater.from(context).inflate(R.layout.dialog_favorite, null)
        val editName = view.findViewById<EditText>(R.id.editFavoriteName)
        val editCoordinates = view.findViewById<EditText>(R.id.editFavoriteCoordinates)
        val saveButton = view.findViewById<Button>(R.id.saveFavoriteButton)
        val cancelButton = view.findViewById<Button>(R.id.cancelFavoriteButton)

        favorite = arguments?.getSerializable(ARG_FAVORITE) as? FavoriteLocation
        position = arguments?.getInt(ARG_POSITION) ?: -1

        if (favorite != null) {
            editName.setText(favorite!!.name)
            editCoordinates.setText("${favorite!!.lat},${favorite!!.lng}")
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(view)
            .create()

        saveButton.setOnClickListener {
            val name = editName.text.toString().trim()
            val coordinates = editCoordinates.text.toString().trim()

            if (TextUtils.isEmpty(name) || TextUtils.isEmpty(coordinates)) {
                Toast.makeText(context, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val parts = coordinates.split(",")
            if (parts.size != 2) {
                Toast.makeText(context, "Please enter coordinates as 'latitude,longitude'", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val lat = parts[0].toDoubleOrNull()
            val lng = parts[1].toDoubleOrNull()
            if (lat == null || lng == null) {
                Toast.makeText(context, "Invalid coordinates", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val newFavorite = FavoriteLocation(name, lat, lng)
            listener?.onFavoriteSaved(newFavorite, position)
            dialog.dismiss()
        }

        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        return dialog
    }
}
