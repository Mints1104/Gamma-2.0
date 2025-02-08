package com.mints.projectgammatwo.recyclerviews

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mints.projectgammatwo.R
import com.mints.projectgammatwo.data.FavoriteLocation

/**
 * Adapter for displaying favorite locations.
 *
 * @param onDeleteFavorite Called when a favorite should be deleted.
 * @param onEditFavorite Called when a favorite should be edited.
 * @param onCopyFavorite Called when a favorite’s coordinates are to be copied.
 * @param onTeleportFavorite Called when teleport action is requested.
 */
class FavoritesAdapter(
    private val onDeleteFavorite: (FavoriteLocation) -> Unit,
    private val onEditFavorite: (FavoriteLocation, Int) -> Unit,
    private val onCopyFavorite: (FavoriteLocation) -> Unit,
    private val onTeleportFavorite: (FavoriteLocation) -> Unit
) : ListAdapter<FavoriteLocation, FavoritesAdapter.FavoriteViewHolder>(FavoriteDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FavoriteViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_favorite, parent, false)
        return FavoriteViewHolder(view, onDeleteFavorite, onEditFavorite, onCopyFavorite, onTeleportFavorite)
    }

    override fun onBindViewHolder(holder: FavoriteViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class FavoriteViewHolder(
        itemView: View,
        private val onDeleteFavorite: (FavoriteLocation) -> Unit,
        private val onEditFavorite: (FavoriteLocation, Int) -> Unit,
        private val onCopyFavorite: (FavoriteLocation) -> Unit,
        private val onTeleportFavorite: (FavoriteLocation) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val dragHandle: ImageView = itemView.findViewById(R.id.dragHandle)
        private val favoriteName: TextView = itemView.findViewById(R.id.favoriteName)
        private val favoriteLocation: TextView = itemView.findViewById(R.id.favoriteLocation)
        private val overflowButton: ImageButton = itemView.findViewById(R.id.overflowButton)
        private val copyButton: Button = itemView.findViewById(R.id.copyButton)
        private val teleportButton: Button = itemView.findViewById(R.id.teleportButton)
        private val deleteButton: Button = itemView.findViewById(R.id.deleteButton)

        fun bind(favorite: FavoriteLocation) {
            favoriteName.text = favorite.name
            favoriteLocation.text = "${favorite.lat}, ${favorite.lng}"

            // Copy button: copy coordinates to clipboard.
            copyButton.setOnClickListener { onCopyFavorite(favorite) }

            // Teleport button: open a map app at the given coordinates.
            teleportButton.setOnClickListener { onTeleportFavorite(favorite) }

            // Delete button.
            deleteButton.setOnClickListener { onDeleteFavorite(favorite) }

            // Overflow menu for editing.
            overflowButton.setOnClickListener {
                val popup = PopupMenu(itemView.context, overflowButton)
                popup.inflate(R.menu.favorite_item_menu)
                popup.setOnMenuItemClickListener { menuItem: MenuItem ->
                    when (menuItem.itemId) {
                        R.id.menu_edit -> {
                            onEditFavorite(favorite, adapterPosition)
                            true
                        }
                        else -> false
                    }
                }
                popup.show()
            }
            // The dragHandle view is used to indicate the draggable area.
        }
    }

    class FavoriteDiffCallback : DiffUtil.ItemCallback<FavoriteLocation>() {
        override fun areItemsTheSame(oldItem: FavoriteLocation, newItem: FavoriteLocation): Boolean {
            // Compare using fields that uniquely identify the favorite.
            return oldItem.name == newItem.name && oldItem.lat == newItem.lat && oldItem.lng == newItem.lng
        }

        override fun areContentsTheSame(oldItem: FavoriteLocation, newItem: FavoriteLocation): Boolean {
            return oldItem == newItem
        }
    }
}
