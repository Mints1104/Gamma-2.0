package com.mints.projectgammatwo.recyclerviews

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mints.projectgammatwo.R
import com.mints.projectgammatwo.data.Invasion
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class InvasionsAdapter : ListAdapter<Invasion, InvasionsAdapter.InvasionViewHolder>(
    InvasionDiffCallback()
) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InvasionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_invasion, parent, false)
        return InvasionViewHolder(view)
    }

    override fun onBindViewHolder(holder: InvasionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class InvasionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val characterNameText: TextView = itemView.findViewById(R.id.characterNameText)
        private val typeText: TextView = itemView.findViewById(R.id.typeText)
        private val coordinatesText: TextView = itemView.findViewById(R.id.coordinatesText)
        private val timeText: TextView = itemView.findViewById(R.id.timeText)
        private val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        fun bind(invasion: Invasion) {
            characterNameText.text = invasion.characterName
            typeText.text = invasion.typeDescription
            coordinatesText.text = "Location: ${invasion.lat}, ${invasion.lng}"

            val startTime = dateFormat.format(Date(invasion.invasion_start * 1000))
            val endTime = dateFormat.format(Date(invasion.invasion_end * 1000))
            timeText.text = "Time: $startTime - $endTime"
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