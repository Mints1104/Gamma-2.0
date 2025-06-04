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
import com.mints.projectgammatwo.data.Invasion
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class InvasionsAdapter(
    private val onDeleteInvasion: (Invasion) -> Unit
) : ListAdapter<Invasion, InvasionsAdapter.InvasionViewHolder>(InvasionDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InvasionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_invasion, parent, false)
        return InvasionViewHolder(view, onDeleteInvasion)
    }

    override fun onBindViewHolder(holder: InvasionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class InvasionViewHolder(
        itemView: View,
        private val onDeleteInvasion: (Invasion) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val characterNameText: TextView = itemView.findViewById(R.id.characterNameText)
        private val typeText: TextView = itemView.findViewById(R.id.typeText)
        private val locationNameText: TextView = itemView.findViewById(R.id.locationText)
        private val sourceText: TextView = itemView.findViewById(R.id.sourceText)  // New TextView for source
        private val coordinatesText: TextView = itemView.findViewById(R.id.coordinatesText)
        private val timeText: TextView = itemView.findViewById(R.id.timeText)
        private val teleportButton: Button = itemView.findViewById(R.id.teleportButton)
        private val copyButton: Button = itemView.findViewById(R.id.copyButton)
        private val deleteButton: Button = itemView.findViewById(R.id.deleteButton)
        private val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        fun bind(invasion: Invasion) {
            characterNameText.text = invasion.characterName
            typeText.text = invasion.typeDescription
            locationNameText.text = "Pokestop: ${invasion.name}"
            val invasionText = invasion.source.lowercase().replaceFirstChar { it.uppercase() }

            // Bind the source. (Assumes your Invasion data class has a "source" property.)
            sourceText.text = "Source: $invasionText"

            val coordsFormatted = String.format("%.5f, %.5f", invasion.lat, invasion.lng)
            coordinatesText.text = "Location: $coordsFormatted"
            val startTime = dateFormat.format(Date(invasion.invasion_start * 1000))
            val endTime = dateFormat.format(Date(invasion.invasion_end * 1000))
            timeText.text = "Time: $startTime - $endTime"

            // Teleport button
            teleportButton.setOnClickListener {
                val url = "https://ipogo.app/?coords=${invasion.lat},${invasion.lng}"
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                itemView.context.startActivity(intent)
                onDeleteInvasion(invasion)
            }

            // Copy button
            copyButton.setOnClickListener {
                val coordsText = "${invasion.lat},${invasion.lng}"
                val clipboard = itemView.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Coordinates", coordsText)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(itemView.context, "Coordinates copied to clipboard", Toast.LENGTH_SHORT).show()
            }

            // Delete button
            deleteButton.setOnClickListener {
                onDeleteInvasion(invasion)
            }
        }
    }

    class InvasionDiffCallback : DiffUtil.ItemCallback<Invasion>() {
        override fun areItemsTheSame(oldItem: Invasion, newItem: Invasion): Boolean {
            return oldItem.lat == newItem.lat && oldItem.lng == newItem.lng
        }

        override fun areContentsTheSame(oldItem: Invasion, newItem: Invasion): Boolean {
            return oldItem == newItem
        }
    }
}
