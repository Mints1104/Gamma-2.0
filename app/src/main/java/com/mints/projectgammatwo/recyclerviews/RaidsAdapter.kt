package com.mints.projectgammatwo.recyclerviews

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mints.projectgammatwo.R
import com.mints.projectgammatwo.data.Raids.Raid
import java.text.SimpleDateFormat
import java.util.*

class RaidsAdapter(
    private val onRaidAction: (Raid) -> Unit
) : ListAdapter<Raid, RaidsAdapter.RaidViewHolder>(RaidDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RaidViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_raid, parent, false)
        return RaidViewHolder(view, onRaidAction)
    }

    override fun onBindViewHolder(holder: RaidViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class RaidViewHolder(
        itemView: View,
        private val onRaidActionCallback: (Raid) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val gymNameText: TextView = itemView.findViewById(R.id.gymNameText)
        private val pokemonNameText: TextView = itemView.findViewById(R.id.pokemonNameText)
        private val raidLevelText: TextView = itemView.findViewById(R.id.raidLevelText)
        private val raidSpawnText: TextView = itemView.findViewById(R.id.raidSpawnText)
        private val raidStartText: TextView = itemView.findViewById(R.id.raidStartText)
        private val raidEndText: TextView = itemView.findViewById(R.id.raidEndText)
        private val coordinatesText: TextView = itemView.findViewById(R.id.coordinatesText)
        private val teleportButton: Button = itemView.findViewById(R.id.teleportButton)
        private val copyButton: Button = itemView.findViewById(R.id.copyButton)
        private val deleteButton: Button = itemView.findViewById(R.id.deleteButton)

        private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

        fun bind(raid: Raid) {
            gymNameText.text = raid.gym_name

            // Display Pokemon name or "Egg" if no Pokemon yet
            pokemonNameText.text = if (raid.pokemon_id > 0) {
                "Pokemon ID: ${raid.pokemon_id}"
            } else {
                "Raid Egg"
            }

            raidLevelText.text = "Level: ${raid.level}"

            // Format timestamps
            raidSpawnText.text = "Spawned: ${formatTimestamp(raid.raid_spawn)}"
            raidStartText.text = "Starts: ${formatTimestamp(raid.raid_start)}"
            raidEndText.text = "Ends: ${formatTimestamp(raid.raid_end)}"

            coordinatesText.text = "${raid.lat}, ${raid.lng}"

            teleportButton.setOnClickListener {
                val url = "https://ipogo.app/?coords=${raid.lat},${raid.lng}"
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                itemView.context.startActivity(intent)
            }

            copyButton.setOnClickListener {
                val clipboard = itemView.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val raidInfo = "${raid.gym_name}\nLevel: ${raid.level}\nCoords: ${raid.lat},${raid.lng}\nSource: ${raid.source}"
                val clip = ClipData.newPlainText("RaidInfo", raidInfo)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(itemView.context, "Raid info copied", Toast.LENGTH_SHORT).show()
            }

            deleteButton.setOnClickListener {
                onRaidActionCallback(raid)
            }
        }

        private fun formatTimestamp(timestamp: Int): String {
            return if (timestamp > 0) {
                dateFormat.format(Date(timestamp * 1000L))
            } else {
                "Unknown"
            }
        }
    }

    class RaidDiffCallback : DiffUtil.ItemCallback<Raid>() {
        override fun areItemsTheSame(oldItem: Raid, newItem: Raid): Boolean {
            return oldItem.gym_name == newItem.gym_name && oldItem.lat == newItem.lat && oldItem.lng == newItem.lng
        }

        override fun areContentsTheSame(oldItem: Raid, newItem: Raid): Boolean {
            return oldItem == newItem
        }
    }
}
