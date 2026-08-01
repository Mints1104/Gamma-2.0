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
import com.mints.projectgammatwo.data.FavoriteTimeFormatter


class OverlayFavoritesAdapter(
    private val onTeleportFavorite: (FavoriteLocation) -> Unit
) : ListAdapter<FavoriteLocation, OverlayFavoritesAdapter.FavoriteViewHolder>(FavoriteDiffCallback()) {

    companion object {
        private const val PAYLOAD_TIME = "payload_time"
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FavoriteViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_favorite_overlay, parent, false)
        return FavoriteViewHolder(view, onTeleportFavorite)
    }

    override fun onBindViewHolder(holder: FavoriteViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onBindViewHolder(
        holder: FavoriteViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        // A clock tick only needs the time line repainted, not a full rebind.
        if (payloads.contains(PAYLOAD_TIME)) {
            holder.bindTime(getItem(position))
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    /**
     * Repaints the local-time line on every visible row. Needed because the favorites themselves
     * don't change when the clock advances, so re-submitting the list would be a DiffUtil no-op.
     */
    fun refreshTimes() {
        if (itemCount > 0) notifyItemRangeChanged(0, itemCount, PAYLOAD_TIME)
    }

    class FavoriteViewHolder(
        itemView: View,
        private val onTeleportFavorite: (FavoriteLocation) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val favoriteName: TextView = itemView.findViewById(R.id.favoriteName)
        private val favoriteLocation: TextView = itemView.findViewById(R.id.favoriteLocation)
        private val favoriteLocalTime: TextView = itemView.findViewById(R.id.favoriteLocalTime)
        private val overflowButton: ImageButton = itemView.findViewById(R.id.overflowButton)

        fun bind(favorite: FavoriteLocation) {
            favoriteName.text = favorite.name
            favoriteLocation.text = "${favorite.lat}, ${favorite.lng}"
            bindTime(favorite)

            // Make the entire item view clickable for teleport
            itemView.setOnClickListener {
                onTeleportFavorite(favorite)
            }

            // Keep the overflow button functionality for future use
            overflowButton.setOnClickListener {
                // You can implement menu functionality here later
            }
        }

        /** Local time at this favorite; hidden when its coordinates have no known timezone. */
        fun bindTime(favorite: FavoriteLocation) {
            val localTime = FavoriteTimeFormatter.formatLocalTime(
                itemView.context,
                favorite.timezoneId
            )
            favoriteLocalTime.text = localTime.orEmpty()
            favoriteLocalTime.visibility = if (localTime == null) View.GONE else View.VISIBLE
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
