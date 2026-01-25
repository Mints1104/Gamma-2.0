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
import com.mints.projectgammatwo.data.DeeplinkManager
import java.util.Date

class DeletedInvasionsAdapter : ListAdapter<DeletedInvasionsAdapter.UIModel, DeletedInvasionsAdapter.VH>(Diff()) {

    data class UIModel(
        val name: String,
        val source: String,
        val characterName: String,
        val typeDescription: String,
        val lat: Double,
        val lng: Double,
        val timestamp: Long,
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_deleted_invasion, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val name: TextView = itemView.findViewById(R.id.nameText)
        private val source: TextView = itemView.findViewById(R.id.sourceText)
        private val character: TextView = itemView.findViewById(R.id.characterText)
        private val type: TextView = itemView.findViewById(R.id.typeText)
        private val coords: TextView = itemView.findViewById(R.id.coordsText)
        private val time: TextView = itemView.findViewById(R.id.timeText)
        private val btnCopy: Button = itemView.findViewById(R.id.copyButton)
        private val btnTeleport: Button = itemView.findViewById(R.id.teleportButton)

        // Cache date/time formatters per ViewHolder to avoid repeated lookups
        private val dateFormatter = DateFormat.getMediumDateFormat(itemView.context)
        private val timeFormatter = DateFormat.getTimeFormat(itemView.context)

        fun bind(m: UIModel) {
            val ctx = itemView.context
            name.text = ctx.getString(R.string.deleted_item_name, m.name)
            source.text = ctx.getString(R.string.deleted_item_source, m.source.replaceFirstChar { it.uppercase() })
            character.text = ctx.getString(R.string.deleted_item_character, m.characterName)
            type.text = ctx.getString(R.string.deleted_item_type, m.typeDescription)
            coords.text = ctx.getString(R.string.deleted_item_coords, m.lat, m.lng)

            val date = Date(m.timestamp)
            val timeStr = dateFormatter.format(date) + " " + timeFormatter.format(date)
            time.text = ctx.getString(R.string.deleted_item_time, timeStr)

            btnCopy.setOnClickListener {
                val c = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val text = "${m.lat},${m.lng}"
                c.setPrimaryClip(ClipData.newPlainText("Coordinates", text))
                val toast = Toast.makeText(ctx, R.string.coords_copied, Toast.LENGTH_SHORT)
                toast.show()
            }
            btnTeleport.setOnClickListener {
                val deeplinkManager = DeeplinkManager.getInstance(ctx)
                val url = deeplinkManager.generateDeeplink(m.lat, m.lng)
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
