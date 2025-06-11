package com.mints.projectgammatwo.ui

import android.app.AlertDialog
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.*
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mints.projectgammatwo.R
import com.mints.projectgammatwo.data.FavoriteLocation
import com.mints.projectgammatwo.recyclerviews.FavoritesAdapter
import java.util.Collections
import androidx.core.content.edit

private const val PREFS_NAME = "FavoritesPrefs"
private const val SORT_ORDER_KEY = "sort_order"
private const val SORT_ORDER_NAME = "name"
private const val SORT_ORDER_DEFAULT = "default"


class FavoritesFragment : Fragment(), FavoriteDialogFragment.FavoriteDialogListener {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: FavoritesAdapter
    private lateinit var addFavoriteFab: View
    private var favoritesList = mutableListOf<FavoriteLocation>()
    private val gson = Gson()
    private val FAVORITES_PREFS_NAME = "favorites_prefs"
    private val KEY_FAVORITES = "favorites_list"
    private val FAVORITES_SORTED = "favorites_sorted"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_favorites, container, false)
        recyclerView = view.findViewById(R.id.favoritesRecyclerView)
        addFavoriteFab = view.findViewById(R.id.addFavoriteFab)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = FavoritesAdapter(
            onDeleteFavorite = { favorite -> deleteFavorite(favorite) },
            onEditFavorite = { favorite, position -> showEditFavoriteDialog(favorite, position) },
            onCopyFavorite = { favorite -> copyFavorite(favorite) },
            onTeleportFavorite = { favorite -> teleportToFavorite(favorite) }
        )
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        loadFavorites()



        addFavoriteFab.setOnClickListener {
            showAddFavoriteDialog()
        }
        val itemTouchHelper = ItemTouchHelper(itemTouchHelperCallback)
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }


    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.favorites_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_import_favorites -> {
                showImportFavoritesDialog()
                true
            }
            R.id.action_sortByName -> {
                saveSortOrderPreference(SORT_ORDER_NAME)
                sortFavsByName()
                true
            }

            R.id.action_sortByDefault -> {
                sortFavsByDefault()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun saveSortOrderPreference(sortOrder: String) {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putString(SORT_ORDER_KEY, sortOrder) }
    }

    private fun sortFavsByName() {
        val sortedList = favoritesList.toMutableList().apply {
            sortBy { it.name }
        }
        adapter.submitList(sortedList)
    }

    private fun sortFavsByDefault() {
        loadFavorites()
    }



    private fun loadSortOrderPreference(): String {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(SORT_ORDER_KEY, SORT_ORDER_DEFAULT) ?: SORT_ORDER_DEFAULT
    }

    private fun applySavedSortOrder() {
        when (loadSortOrderPreference()) {
            SORT_ORDER_NAME -> sortFavsByName()
            SORT_ORDER_DEFAULT -> sortFavsByDefault()
        }
    }

    /**
     * Displays a dialog for importing favorites via JSON.
     */
    private fun showImportFavoritesDialog() {
        val builder = AlertDialog.Builder(requireContext())
        val inflater = requireActivity().layoutInflater
        val dialogView = inflater.inflate(R.layout.dialog_import_favorites, null)

        val editText = dialogView.findViewById<EditText>(R.id.editImportJson)
        val cancelButton = dialogView.findViewById<Button>(R.id.cancelImportButton)
        val importButton = dialogView.findViewById<Button>(R.id.importButton)

        builder.setView(dialogView)
        val dialog = builder.create()

        // Set up button click listeners
        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        importButton.setOnClickListener {
            val jsonString = editText.text.toString()
            if (jsonString.isBlank()) {
                Toast.makeText(requireContext(), "Input cannot be empty", Toast.LENGTH_SHORT).show()
            } else {
                importFavorites(jsonString)
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    /**
     * Imports favorites from the provided JSON string.
     */
    private fun importFavorites(jsonString: String) {
        try {
            val importType = TypeToken
                .getParameterized(List::class.java, FavoriteLocation::class.java)
                .type
            val importedFavorites: List<FavoriteLocation> = gson.fromJson(jsonString, importType)
            // Merge imported favorites with the current list, avoiding duplicates.
            for (fav in importedFavorites) {
                if (!favoritesList.any { it.lat == fav.lat && it.lng == fav.lng && it.name == fav.name }) {
                    favoritesList.add(fav)
                }
            }
            adapter.submitList(favoritesList.toList())
            saveFavorites()
            Toast.makeText(requireContext(), "Favorites imported", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Failed to import favorites", Toast.LENGTH_SHORT).show()
        }
    }


    private fun loadFavorites() {
        val prefs = requireContext().getSharedPreferences(FAVORITES_PREFS_NAME, Context.MODE_PRIVATE)

        val json = prefs.getString(KEY_FAVORITES, "[]") ?: "[]"
        val type = TypeToken
            .getParameterized(List::class.java, FavoriteLocation::class.java)
            .type
        val listType = TypeToken
            .getParameterized(List::class.java, FavoriteLocation::class.java)
            .type
        val loadedFavorites: List<FavoriteLocation> = gson.fromJson(json, listType)

        val orderJson = prefs.getString("favorites_order", "[]")
        val orderType = TypeToken
            .getParameterized(List::class.java, String::class.java)
            .type
        val originalOrder: List<String> = gson.fromJson(orderJson, orderType)

        // Reorder the loadedFavorites to match the original order
        favoritesList = if (originalOrder.isNotEmpty()) {
            loadedFavorites.sortedBy { originalOrder.indexOf(it.name) }.toMutableList()
        } else {
            loadedFavorites.toMutableList()
        }

        adapter.submitList(favoritesList.toList())
    }


    private fun saveFavorites() {
        val ctx = context ?: return
        val prefs = ctx
            .getSharedPreferences(FAVORITES_PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putString(KEY_FAVORITES, gson.toJson(favoritesList))
        editor.putString("favorites_order", gson.toJson(favoritesList.map { it.name }))
        editor.apply()
    }



    private fun saveSortingOrder() {
        val prefs = requireContext().getSharedPreferences(FAVORITES_SORTED, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_FAVORITES, gson.toJson(favoritesList)).apply()

    }

    private fun deleteFavorite(favorite: FavoriteLocation) {
        val rootView = requireActivity().findViewById<View>(android.R.id.content)
        val deletedIndex = favoritesList.indexOf(favorite).takeIf { it != -1 } ?: return
        favoritesList.removeAt(deletedIndex)
        adapter.submitList(favoritesList.toList())
        saveFavorites()
        Snackbar.make(rootView, "Deleted: ${favorite.name}", Snackbar.LENGTH_LONG)
            .setAction("UNDO") {
                favoritesList.add(deletedIndex, favorite)
                adapter.submitList(favoritesList.toList())
                saveFavorites()
            }
            .show()
    }








    private fun copyFavorite(favorite: FavoriteLocation) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = ClipData.newPlainText("Favorite Coordinates", "${favorite.lat}, ${favorite.lng}")
        clipboard.setPrimaryClip(clip)
        Toast.makeText(requireContext(), "Coordinates copied to clipboard", Toast.LENGTH_SHORT).show()
    }


    /**
     * Teleports to the favorite location based on the user's teleport method preference.
     * If "ipogo" is selected, it opens an ipogo URL; if "joystick" is selected, it sends an intent.
     */
    private fun teleportToFavorite(favorite: FavoriteLocation) {
        val context = requireContext()
        val teleportPrefs = context.getSharedPreferences("teleport_prefs", Context.MODE_PRIVATE)
        val method = teleportPrefs.getString("teleport_method", "ipogo") ?: "ipogo"

        if (method == "ipogo") {
            val url = "https://ipogo.app/?coords=${favorite.lat},${favorite.lng}"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
            return
        }

        val baseIntent = Intent().apply {
            action = "theappninjas.gpsjoystick.TELEPORT"
            putExtra("lat", favorite.lat.toFloat())
            putExtra("lng", favorite.lng.toFloat())
        }
        val knownComponents = listOf(
            ComponentName(
                "com.theappninjas.fakegpsjoystick",
                "com.theappninjas.fakegpsjoystick.service.OverlayService"
            ),
            ComponentName(
                "com.thekkgqtaoxz.ymaaammipjyfatw",
                "com.thekkgqtaoxz.ymaaammipjyfatw.service.OverlayService"
            )
        )

        var serviceStarted = false
        for (component in knownComponents) {
            val intent = Intent(baseIntent).apply {
                this.component = component
            }
            try {
                val compName = context.startService(intent)
                if (compName != null) {
                    serviceStarted = true
                    break
                }
            } catch (e: Exception) {
            }
        }

        if (!serviceStarted) {
            val dynamicIntent = Intent(baseIntent).apply { component = null }
            val pm = context.packageManager
            val services = pm.queryIntentServices(dynamicIntent, 0)
            if (services.isNotEmpty()) {
                val serviceInfo = services.first().serviceInfo
                dynamicIntent.component = ComponentName(serviceInfo.packageName, serviceInfo.name)
                try {
                    val compName = context.startService(dynamicIntent)
                    serviceStarted = (compName != null)
                } catch (e: Exception) {
                }
            }
        }

        if (!serviceStarted) {
            Toast.makeText(context, "Error: Joystick not found", Toast.LENGTH_SHORT).show()
        }
    }







    /**
     * Opens the dialog to add a new favorite.
     */
    private fun showAddFavoriteDialog() {
        val dialog = FavoriteDialogFragment.newInstance(null, -1)
        dialog.setTargetFragment(this, 0)
        dialog.show(parentFragmentManager, "FavoriteDialogFragment")
    }

    /**
     * Opens the dialog to edit an existing favorite.
     */
    private fun showEditFavoriteDialog(favorite: FavoriteLocation, position: Int) {
        val dialog = FavoriteDialogFragment.newInstance(favorite, position)
        dialog.setTargetFragment(this, 0)
        dialog.show(parentFragmentManager, "FavoriteDialogFragment")
    }


    override fun onFavoriteSaved(favorite: FavoriteLocation, position: Int) {
        if (position == -1) {
            favoritesList.add(favorite)
            Toast.makeText(requireContext(), "Favorite added", Toast.LENGTH_SHORT).show()
        } else {
            favoritesList[position] = favorite
            Toast.makeText(requireContext(), "Favorite updated", Toast.LENGTH_SHORT).show()
        }
        adapter.submitList(favoritesList.toList())
        saveFavorites()
    }


    private val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
    ) {
        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            // Use adapterPosition everywhere
            val from = viewHolder.adapterPosition
            val to   = target.adapterPosition
            if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false

            // Make a mutable copy, swap, and resubmit
            // Update BOTH the favorites list AND the adapter
            Collections.swap(favoritesList, from, to)
            adapter.submitList(favoritesList.toList())
            return true
        }


        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            // No swipe action.
        }

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            saveFavorites()
        }
    }
}
