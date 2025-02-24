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
import com.mints.projectgammatwo.data.Quests
import com.mints.projectgammatwo.data.Quests.Quest


class QuestsAdapter(
    private val onDeleteQuest: (Quest) -> Unit
) : ListAdapter<Quest, QuestsAdapter.QuestViewHolder>(QuestDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QuestViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_quest, parent, false)
        return QuestViewHolder(view, onDeleteQuest)
    }

    override fun onBindViewHolder(holder: QuestViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class QuestViewHolder(
        itemView: View,
        private val onDeleteQuest: (Quest) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val questNameText: TextView = itemView.findViewById(R.id.questNameText)
        private val rewardText: TextView = itemView.findViewById(R.id.rewardText)
        private val conditionsText: TextView = itemView.findViewById(R.id.conditionsText)
        private val coordinatesText: TextView = itemView.findViewById(R.id.coordinatesText)
        private val sourceText: TextView = itemView.findViewById(R.id.sourceText) // New TextView for source
        private val teleportButton: Button = itemView.findViewById(R.id.teleportButton)
        private val copyButton: Button = itemView.findViewById(R.id.copyButton)
        private val deleteButton: Button = itemView.findViewById(R.id.deleteButton)

        fun bind(quest: Quest) {
            questNameText.text = quest.name
            rewardText.text = "Reward: ${quest.rewardsString}"
            conditionsText.text = "Condition: ${quest.conditionsString}"
            val coordsFormatted = String.format("%.5f, %.5f", quest.lat, quest.lng)
            coordinatesText.text = "Location: $coordsFormatted"
            sourceText.text = "Source: ${quest.source}"  // Display the source

            teleportButton.setOnClickListener {
                val url = "https://ipogo.app/?coords=${quest.lat},${quest.lng}"
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                itemView.context.startActivity(intent)
                onDeleteQuest(quest)
            }

            copyButton.setOnClickListener {
                val textToCopy = "Quest: ${quest.name}\nReward: ${quest.rewardsString}\nLocation: $coordsFormatted"
                val clipboard = itemView.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Quest Info", textToCopy)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(itemView.context, "Quest info copied", Toast.LENGTH_SHORT).show()
            }

            deleteButton.setOnClickListener {
                onDeleteQuest(quest)
            }
        }
    }

    class QuestDiffCallback : DiffUtil.ItemCallback<Quest>() {
        override fun areItemsTheSame(oldItem: Quest, newItem: Quest): Boolean {
            return oldItem.name == newItem.name && oldItem.lat == newItem.lat && oldItem.lng == newItem.lng
        }
        override fun areContentsTheSame(oldItem: Quest, newItem: Quest): Boolean {
            return oldItem == newItem
        }
    }
}
