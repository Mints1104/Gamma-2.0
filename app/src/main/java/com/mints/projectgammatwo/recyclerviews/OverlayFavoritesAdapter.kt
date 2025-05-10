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


class OverlayFavoritesAdapter(
    private val onTeleportFavorite: (FavoriteLocation) -> Unit
) : ListAdapter<FavoriteLocation, OverlayFavoritesAdapter.FavoriteViewHolder>(FavoriteDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FavoriteViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_favorite_overlay, parent, false)
        return FavoriteViewHolder(view, onTeleportFavorite)
    }

    override fun onBindViewHolder(holder: FavoriteViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class FavoriteViewHolder(
        itemView: View,
        private val onTeleportFavorite: (FavoriteLocation) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val favoriteName: TextView = itemView.findViewById(R.id.favoriteName)
        private val favoriteLocation: TextView = itemView.findViewById(R.id.favoriteLocation)
        private val overflowButton: ImageButton = itemView.findViewById(R.id.overflowButton)

        fun bind(favorite: FavoriteLocation) {
            favoriteName.text = favorite.name
            favoriteLocation.text = "${favorite.lat}, ${favorite.lng}"

            // Make the entire item view clickable for teleport
            itemView.setOnClickListener {
                onTeleportFavorite(favorite)
            }

            // Keep the overflow button functionality for future use
            overflowButton.setOnClickListener {
                // You can implement menu functionality here later
            }
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
