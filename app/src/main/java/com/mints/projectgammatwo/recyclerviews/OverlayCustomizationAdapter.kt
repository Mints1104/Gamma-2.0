package com.mints.projectgammatwo.recyclerviews

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.RecyclerView
import com.mints.projectgammatwo.R
import java.util.Collections

data class OverlayButtonItem(
    val id: String,
    val name: String,
    val iconResId: Int,
    var isVisible: Boolean,
    val isRequired: Boolean = false // Some buttons like drag_handle and close_button should always be visible
)

class OverlayCustomizationAdapter(
    private val items: MutableList<OverlayButtonItem>,
    private val onItemChanged: (List<OverlayButtonItem>) -> Unit,
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit
) : RecyclerView.Adapter<OverlayCustomizationAdapter.ButtonViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ButtonViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_overlay_button_customization, parent, false)
        return ButtonViewHolder(view)
    }

    override fun onBindViewHolder(holder: ButtonViewHolder, position: Int) {
        holder.bind(items[position], position)
    }

    override fun getItemCount(): Int = items.size

    fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        // Don't allow moving drag_handle from first position
        if (fromPosition == 0 || toPosition == 0) return false

        Collections.swap(items, fromPosition, toPosition)
        notifyItemMoved(fromPosition, toPosition)
        onItemChanged(items)
        return true
    }

    fun updateItems(newItems: List<OverlayButtonItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    inner class ButtonViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val dragIcon: ImageView = itemView.findViewById(R.id.drag_icon)
        private val buttonIcon: ImageView = itemView.findViewById(R.id.button_icon)
        private val buttonName: TextView = itemView.findViewById(R.id.button_name)
        private val visibilitySwitch: SwitchCompat = itemView.findViewById(R.id.visibility_switch)

        fun bind(item: OverlayButtonItem, position: Int) {
            buttonIcon.setImageResource(item.iconResId)
            buttonName.text = item.name
            visibilitySwitch.isChecked = item.isVisible

            // Disable switch for required buttons
            visibilitySwitch.isEnabled = !item.isRequired

            // Disable dragging for drag_handle (should always be first)
            // Only make the DRAG ICON grey, not the button preview icon
            dragIcon.alpha = if (position == 0) 0.3f else 1.0f

            // Keep button icon at full opacity
            buttonIcon.alpha = 1.0f

            visibilitySwitch.setOnCheckedChangeListener { _, isChecked ->
                item.isVisible = isChecked
                onItemChanged(items)
            }

            dragIcon.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN && position != 0) {
                    onStartDrag(this)
                    true
                } else {
                    false
                }
            }
        }
    }
}
