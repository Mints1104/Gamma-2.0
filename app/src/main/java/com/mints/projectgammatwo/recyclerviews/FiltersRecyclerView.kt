package com.mints.projectgammatwo.recyclerviews

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mints.projectgammatwo.R

/**
 * Adapter for displaying filters in the overlay
 */
class FiltersRecyclerView(
    private val onFilterSelected: (String) -> Unit
) : ListAdapter<String, FiltersRecyclerView.FilterViewHolder>(FilterDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FilterViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.filter_items, parent, false)
        return FilterViewHolder(view)
    }

    override fun onBindViewHolder(holder: FilterViewHolder, position: Int) {
        val filter = getItem(position)
        holder.bind(filter)
    }

    inner class FilterViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val filterNameTextView: TextView = itemView.findViewById(R.id.filterName)

        fun bind(filterName: String) {
            filterNameTextView.text = filterName

            itemView.setOnClickListener {
                onFilterSelected(filterName)
            }
        }
    }

    private class FilterDiffCallback : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(oldItem: String, newItem: String): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: String, newItem: String): Boolean {
            return oldItem == newItem
        }
    }
}