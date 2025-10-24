package com.mints.projectgammatwo.recyclerviews

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import android.text.format.DateFormat
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
import java.util.Date

class DeletedQuestsAdapter : ListAdapter<DeletedQuestsAdapter.UIModel, DeletedQuestsAdapter.VH>(Diff()) {

    data class UIModel(
        val name: String,
        val lat: Double,
        val lng: Double,
        val timestamp: Long,
        val source: String = "",
        val rewards: String = "",
        val conditions: String = "",
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_deleted_quest, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val name: TextView = itemView.findViewById(R.id.nameText)
        private val coords: TextView = itemView.findViewById(R.id.coordsText)
        private val time: TextView = itemView.findViewById(R.id.timeText)
        private val source: TextView = itemView.findViewById(R.id.sourceText)
        private val rewards: TextView = itemView.findViewById(R.id.rewardsText)
        private val conditions: TextView = itemView.findViewById(R.id.conditionsText)
        private val btnCopy: Button = itemView.findViewById(R.id.copyButton)
        private val btnTeleport: Button = itemView.findViewById(R.id.teleportButton)

        fun bind(m: UIModel) {
            val ctx = itemView.context
            name.text = ctx.getString(R.string.visited_item_name, m.name)
            coords.text = ctx.getString(R.string.visited_item_coords, m.lat, m.lng)

            val timeStr = DateFormat.getMediumDateFormat(ctx).format(Date(m.timestamp)) + " " +
                    DateFormat.getTimeFormat(ctx).format(Date(m.timestamp))
            time.text = ctx.getString(R.string.visited_item_time, timeStr)

            // Bind optional fields; hide when empty
            if (m.source.isNullOrBlank()) {
                source.visibility = View.GONE
            } else {
                source.visibility = View.VISIBLE
                source.text = ctx.getString(R.string.visited_item_source, m.source)
            }

            if (m.rewards.isNullOrBlank()) {
                rewards.visibility = View.GONE
            } else {
                rewards.visibility = View.VISIBLE
                rewards.text = ctx.getString(R.string.visited_item_rewards, m.rewards)
            }

            if (m.conditions.isNullOrBlank()) {
                conditions.visibility = View.GONE
            } else {
                conditions.visibility = View.VISIBLE
                conditions.text = ctx.getString(R.string.visited_item_conditions, m.conditions)
            }

            btnCopy.setOnClickListener {
                val c = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val text = "${m.lat},${m.lng}"
                c.setPrimaryClip(ClipData.newPlainText("Coordinates", text))
                Toast.makeText(ctx, R.string.coords_copied, Toast.LENGTH_SHORT).show()
            }
            btnTeleport.setOnClickListener {
                val url = "https://ipogo.app/?coords=${m.lat},${m.lng}"
                val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                ctx.startActivity(intent)
            }
        }
    }

    class Diff : DiffUtil.ItemCallback<UIModel>() {
        override fun areItemsTheSame(oldItem: UIModel, newItem: UIModel): Boolean =
            oldItem.lat == newItem.lat && oldItem.lng == newItem.lng && oldItem.timestamp == newItem.timestamp

        override fun areContentsTheSame(oldItem: UIModel, newItem: UIModel): Boolean = oldItem == newItem
    }
}
