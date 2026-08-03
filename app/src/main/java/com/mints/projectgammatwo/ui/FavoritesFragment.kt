package com.mints.projectgammatwo.ui

import android.app.AlertDialog
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import com.mints.projectgammatwo.data.DeeplinkManager
import com.mints.projectgammatwo.data.FavoritesManager
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
    private val KEY_ORDER = "favorites_order"

    /** Mirrors the persisted sort choice so it doesn't have to be re-read on every mutation. */
    private var currentSortOrder: String = SORT_ORDER_DEFAULT

    private val timeHandler = Handler(Looper.getMainLooper())

    /** Repaints the per-favorite local times, re-posting itself on each wall-clock minute. */
    private val timeTicker = object : Runnable {
        override fun run() {
            if (::adapter.isInitialized) adapter.refreshTimes()
            timeHandler.postDelayed(this, millisUntilNextMinute())
        }
    }

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

        // Restore the sort the user last chose before the first load applies it.
        currentSortOrder = loadSortOrderPreference()
        loadFavorites()



        addFavoriteFab.setOnClickListener {
            showAddFavoriteDialog()
        }
        val itemTouchHelper = ItemTouchHelper(itemTouchHelperCallback)
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    override fun onResume() {
        super.onResume()
        // Refresh immediately (the device timezone may have changed while we were away),
        // then keep ticking on the minute for as long as the screen is visible.
        if (::adapter.isInitialized) adapter.refreshTimes()
        timeHandler.removeCallbacks(timeTicker)
        timeHandler.postDelayed(timeTicker, millisUntilNextMinute())
    }

    override fun onPause() {
        super.onPause()
        timeHandler.removeCallbacks(timeTicker)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        timeHandler.removeCallbacks(timeTicker)
    }

    /** Aligns the tick to the wall clock so the displayed time flips when the minute does. */
    private fun millisUntilNextMinute(): Long =
        60_000L - (System.currentTimeMillis() % 60_000L)


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
            R.id.menu_import_hotspots -> {
                showImportHotspotsDialog()
                true
            }
            R.id.action_sortByName -> {
                setSortOrder(SORT_ORDER_NAME)
                sortFavsByName()
                true
            }

            R.id.action_sortByDefault -> {
                setSortOrder(SORT_ORDER_DEFAULT)
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

    /** Records the sort choice both in memory and on disk so it survives leaving the screen. */
    private fun setSortOrder(sortOrder: String) {
        currentSortOrder = sortOrder
        saveSortOrderPreference(sortOrder)
    }

    /**
     * Re-applies the active sort to [favoritesList] after it has been mutated, so an edited or
     * newly added favorite lands in its correct place instead of keeping the slot it happened
     * to occupy.
     */
    private fun applyCurrentSort() {
        if (currentSortOrder == SORT_ORDER_NAME) {
            favoritesList.sortBy { it.name }
        }
    }

    private fun sortFavsByName() {
        // Sort the backing list itself rather than a display-only copy. The adapter hands
        // back positions into whatever it is currently showing, and both edit and drag write
        // those positions straight into favoritesList — so if the two orders diverge they
        // target the wrong favorite and overwrite it.
        favoritesList.sortBy { it.name }
        adapter.submitList(favoritesList.toList())
    }

    private fun sortFavsByDefault() {
        loadFavorites()
    }



    private fun loadSortOrderPreference(): String {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(SORT_ORDER_KEY, SORT_ORDER_DEFAULT) ?: SORT_ORDER_DEFAULT
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
     * Displays a confirmation dialog for importing hotspots.
     */
    private fun showImportHotspotsDialog() {
        val builder = AlertDialog.Builder(requireContext())
        val inflater = requireActivity().layoutInflater
        val dialogView = inflater.inflate(R.layout.dialog_confirm_import_hotspots, null)

        val cancelButton = dialogView.findViewById<Button>(R.id.cancelConfirmButton)
        val confirmButton = dialogView.findViewById<Button>(R.id.confirmImportButton)

        builder.setView(dialogView)
        val dialog = builder.create()

        // Set up button click listeners
        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        confirmButton.setOnClickListener {
            importHotspots()
            dialog.dismiss()
        }

        dialog.show()
    }

    /**
     * Imports hotspots from the hotspots.txt file in the res folder.
     */
    private fun importHotspots() {
        try {
            val inputStream = resources.openRawResource(R.raw.hotspots)
            val jsonString = inputStream.bufferedReader().use { it.readText() }

            val importType = TypeToken
                .getParameterized(List::class.java, FavoriteLocation::class.java)
                .type
            val importedHotspots: List<FavoriteLocation> = gson.fromJson(jsonString, importType)

            // Store the current list before making changes
            val previousList = favoritesList.toList()
            val addedLocations = mutableListOf<FavoriteLocation>()

            // Merge imported hotspots with the current list, avoiding duplicates
            for (hotspot in importedHotspots) {
                if (!favoritesList.any { it.lat == hotspot.lat && it.lng == hotspot.lng && it.name == hotspot.name }) {
                    favoritesList.add(hotspot)
                    addedLocations.add(hotspot)
                }
            }

            FavoritesManager.ensureTimezones(favoritesList)
            applyCurrentSort()
            adapter.submitList(favoritesList.toList())
            saveFavorites()

            // Show snackbar with undo option
            val rootView = requireActivity().findViewById<View>(android.R.id.content)
            val message = if (addedLocations.isNotEmpty()) {
                "Imported ${addedLocations.size} hotspot(s)"
            } else {
                "No new hotspots to import"
            }

            Snackbar.make(rootView, message, Snackbar.LENGTH_LONG)
                .setAction("UNDO") {
                    // Restore the previous list
                    favoritesList.clear()
                    favoritesList.addAll(previousList)
                    adapter.submitList(favoritesList.toList())
                    saveFavorites()
                    Toast.makeText(requireContext(), "Import undone", Toast.LENGTH_SHORT).show()
                }
                .show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Failed to import hotspots: ${e.message}", Toast.LENGTH_SHORT).show()
        }
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
            FavoritesManager.ensureTimezones(favoritesList)
            applyCurrentSort()
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
        val listType = TypeToken
            .getParameterized(List::class.java, FavoriteLocation::class.java)
            .type
        val loadedFavorites: List<FavoriteLocation> = gson.fromJson(json, listType)

        val orderJson = prefs.getString(KEY_ORDER, "[]")
        val orderType = TypeToken
            .getParameterized(List::class.java, String::class.java)
            .type
        val originalOrder: List<String> = gson.fromJson(orderJson, orderType)

        favoritesList = FavoritesManager.applyStoredOrder(loadedFavorites, originalOrder)
            .toMutableList()

        // Favorites saved before timezones existed have no timezoneId. Resolve them once here
        // and write the result back so this doesn't run on every load.
        if (FavoritesManager.ensureTimezones(favoritesList)) {
            saveFavorites()
        }

        applyCurrentSort()
        adapter.submitList(favoritesList.toList())
    }


    private fun saveFavorites() {
        val ctx = context ?: return
        val prefs = ctx
            .getSharedPreferences(FAVORITES_PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            putString(KEY_FAVORITES, gson.toJson(favoritesList))
            // Only record what's on screen as the manual order when the user is actually
            // arranging by hand. While name-sorted the displayed order is derived, so
            // overwriting the stored order would destroy their arrangement and leave
            // "Sort by default" with nothing to restore.
            if (currentSortOrder == SORT_ORDER_DEFAULT) {
                putString(KEY_ORDER, gson.toJson(favoritesList.map { it.name }))
            }
        }
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
            val deeplinkManager = DeeplinkManager.getInstance(context)
            val url = deeplinkManager.generateDeeplink(favorite.lat, favorite.lng)
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
        applyCurrentSort()
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
            // Dragging is an explicit request for a manual arrangement, so drop out of
            // name-sort mode — otherwise the next load would sort the drag straight back out.
            if (currentSortOrder != SORT_ORDER_DEFAULT) {
                setSortOrder(SORT_ORDER_DEFAULT)
            }
            saveFavorites()
        }
    }
}
