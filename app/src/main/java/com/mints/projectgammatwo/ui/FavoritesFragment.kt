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
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mints.projectgammatwo.R
import com.mints.projectgammatwo.data.FavoriteLocation
import com.mints.projectgammatwo.recyclerviews.FavoritesAdapter

class FavoritesFragment : Fragment(), FavoriteDialogFragment.FavoriteDialogListener {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: FavoritesAdapter
    private lateinit var addFavoriteFab: View  // For example, a FloatingActionButton
    private var favoritesList = mutableListOf<FavoriteLocation>()
    private val gson = Gson()

    // SharedPreferences keys for favorites.
    private val FAVORITES_PREFS_NAME = "favorites_prefs"
    private val KEY_FAVORITES = "favorites_list"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Enable options menu for the Favorites tab.
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

        // Attach ItemTouchHelper for drag & drop reordering.
        val itemTouchHelper = ItemTouchHelper(itemTouchHelperCallback)
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    // --- Options Menu for Importing Favorites ---

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
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * Displays a dialog for importing favorites via JSON.
     */
    private fun showImportFavoritesDialog() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Import Favorites (Paste JSON)")
        val input = EditText(requireContext())
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        input.hint = "Paste favorites JSON here"
        builder.setView(input)
        builder.setPositiveButton("Import") { dialog, _ ->
            val jsonString = input.text.toString()
            if (jsonString.isBlank()) {
                Toast.makeText(requireContext(), "Input cannot be empty", Toast.LENGTH_SHORT).show()
            } else {
                importFavorites(jsonString)
            }
            dialog.dismiss()
        }
        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
        builder.show()
    }

    /**
     * Imports favorites from the provided JSON string.
     */
    private fun importFavorites(jsonString: String) {
        try {
            val type = object : TypeToken<List<FavoriteLocation>>() {}.type
            val importedFavorites: List<FavoriteLocation> = gson.fromJson(jsonString, type)
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

    // --- Persistence Methods ---

    private fun loadFavorites() {
        val prefs = requireContext().getSharedPreferences(FAVORITES_PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_FAVORITES, "[]")
        val type = object : TypeToken<List<FavoriteLocation>>() {}.type
        favoritesList = gson.fromJson(json, type) ?: mutableListOf()
        adapter.submitList(favoritesList.toList())
    }

    private fun saveFavorites() {
        val prefs = requireContext().getSharedPreferences(FAVORITES_PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_FAVORITES, gson.toJson(favoritesList)).apply()
    }

    private fun deleteFavorite(favorite: FavoriteLocation) {
        favoritesList.remove(favorite)
        adapter.submitList(favoritesList.toList())
        saveFavorites()
        Toast.makeText(requireContext(), "Favorite deleted", Toast.LENGTH_SHORT).show()
    }

    private fun copyFavorite(favorite: FavoriteLocation) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = ClipData.newPlainText("Favorite Coordinates", "${favorite.lat}, ${favorite.lng}")
        clipboard.setPrimaryClip(clip)
        Toast.makeText(requireContext(), "Coordinates copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    // --- Teleport Functionality ---

    /**
     * Teleports to the favorite location based on the user's teleport method preference.
     * If "ipogo" is selected, it opens an ipogo URL; if "joystick" is selected, it sends an intent.
     */
    private fun teleportToFavorite(favorite: FavoriteLocation) {
        val context = requireContext()
        val teleportPrefs = context.getSharedPreferences("teleport_prefs", Context.MODE_PRIVATE)
        val method = teleportPrefs.getString("teleport_method", "ipogo") ?: "ipogo"

        if (method == "ipogo") {
            // IPoGo: launch the URL with coordinates.
            val url = "https://ipogo.app/?coords=${favorite.lat},${favorite.lng}"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
            return
        }

        // GPS Joystick: build the base intent.
        val baseIntent = Intent().apply {
            action = "theappninjas.gpsjoystick.TELEPORT"
            putExtra("lat", favorite.lat.toFloat())
            putExtra("lng", favorite.lng.toFloat())
        }

        // List of known service components—in the order you know work.
        // First is the one you showed working; second is the alternate.
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
        // Try each known component.
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
                // This known component didn't work; try the next one.
            }
        }

        // If neither known component worked, fall back to a dynamic lookup.
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
                    // dynamic lookup failed.
                }
            }
        }

        if (!serviceStarted) {
            Toast.makeText(context, "Error: Joystick not found", Toast.LENGTH_SHORT).show()
        }
    }






    // --- Dialogs for Adding and Editing Favorites ---

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

    // --- Callback from FavoriteDialogFragment ---

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

    // --- ItemTouchHelper for Drag & Drop Reordering ---

    private val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
    ) {
        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            val fromPos = viewHolder.adapterPosition
            val toPos = target.adapterPosition
            val movedItem = favoritesList.removeAt(fromPos)
            favoritesList.add(toPos, movedItem)
            adapter.notifyItemMoved(fromPos, toPos)
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
