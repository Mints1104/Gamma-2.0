package com.mints.projectgammatwo.recyclerviews

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

class FavoritesAdapter(
    private val onDeleteFavorite: (FavoriteLocation) -> Unit,
    private val onEditFavorite: (FavoriteLocation, Int) -> Unit,
    private val onCopyFavorite: (FavoriteLocation) -> Unit,
    private val onTeleportFavorite: (FavoriteLocation) -> Unit
) : ListAdapter<FavoriteLocation, FavoritesAdapter.FavoriteViewHolder>(FavoriteDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FavoriteViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_favorite, parent, false)
        return FavoriteViewHolder(view)
    }

    override fun onBindViewHolder(holder: FavoriteViewHolder, position: Int) {
        holder.bind()
    }

    inner class FavoriteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val dragHandle: ImageView = itemView.findViewById(R.id.dragHandle)
        private val favoriteName: TextView = itemView.findViewById(R.id.favoriteName)
        private val favoriteLocation: TextView = itemView.findViewById(R.id.favoriteLocation)
        private val overflowButton: ImageButton = itemView.findViewById(R.id.overflowButton)
        private val copyButton: Button = itemView.findViewById(R.id.copyButton)
        private val teleportButton: Button = itemView.findViewById(R.id.teleportButton)
        private val deleteButton: Button = itemView.findViewById(R.id.deleteButton)

        fun bind() {
            // Always look up the current adapter position
            val pos = adapterPosition
            if (pos == RecyclerView.NO_POSITION) return

            val favorite = getItem(pos)

            favoriteName.text = favorite.name
            favoriteLocation.text = "${favorite.lat}, ${favorite.lng}"

            copyButton.setOnClickListener {
                val currentPos = adapterPosition
                if (currentPos != RecyclerView.NO_POSITION) {
                    onCopyFavorite(getItem(currentPos))
                }
            }

            teleportButton.setOnClickListener {
                val currentPos = adapterPosition
                if (currentPos != RecyclerView.NO_POSITION) {
                    onTeleportFavorite(getItem(currentPos))
                }
            }

            deleteButton.setOnClickListener {
                val currentPos = adapterPosition
                if (currentPos != RecyclerView.NO_POSITION) {
                    onDeleteFavorite(getItem(currentPos))
                }
            }

            overflowButton.setOnClickListener {
                val popup = PopupMenu(itemView.context, overflowButton)
                popup.inflate(R.menu.favorite_item_menu)
                popup.setOnMenuItemClickListener { menuItem: MenuItem ->
                    if (menuItem.itemId == R.id.menu_edit) {
                        val currentPos = adapterPosition
                        if (currentPos != RecyclerView.NO_POSITION) {
                            onEditFavorite(getItem(currentPos), currentPos)
                        }
                        true
                    } else {
                        false
                    }
                }
                popup.show()
            }
        }
    }

    class FavoriteDiffCallback : DiffUtil.ItemCallback<FavoriteLocation>() {
        override fun areItemsTheSame(oldItem: FavoriteLocation, newItem: FavoriteLocation): Boolean {
            // Still comparing by name+coords
            return oldItem.name == newItem.name
                    && oldItem.lat  == newItem.lat
                    && oldItem.lng  == newItem.lng
        }

        override fun areContentsTheSame(oldItem: FavoriteLocation, newItem: FavoriteLocation): Boolean {
            return oldItem == newItem
        }
    }
}
