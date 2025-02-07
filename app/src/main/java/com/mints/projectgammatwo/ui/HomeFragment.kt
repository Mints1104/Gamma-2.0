package com.mints.projectgammatwo.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.mints.projectgammatwo.R
import com.mints.projectgammatwo.viewmodels.HomeViewModel

class HomeFragment : Fragment() {

    private val viewModel: HomeViewModel by viewModels()
    private lateinit var adapter: InvasionsAdapter
    private lateinit var swipeRefresh: SwipeRefreshLayout

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize RecyclerView with the adapter that handles deletion
        val recyclerView = view.findViewById<RecyclerView>(R.id.invasionsRecyclerView)
        adapter = InvasionsAdapter { invasion ->
            viewModel.deleteInvasion(invasion)
        }
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(context)

        // Initialize SwipeRefreshLayout
        swipeRefresh = view.findViewById(R.id.swipeRefresh)
        swipeRefresh.setOnRefreshListener {
            viewModel.fetchInvasions()
        }

        // Observe invasions LiveData
        viewModel.invasions.observe(viewLifecycleOwner) { invasions ->
            adapter.submitList(invasions)
            swipeRefresh.isRefreshing = false
        }

        // Observe error LiveData
        viewModel.error.observe(viewLifecycleOwner) { errorMessage ->
            Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
            swipeRefresh.isRefreshing = false
        }

        // Observe deletion counter LiveData.
        // Ensure you have a TextView with ID "deletedCountText" in your fragment_home.xml layout.
        val deletedCountTextView = view.findViewById<TextView>(R.id.deletedCountText)
        viewModel.deletedCount.observe(viewLifecycleOwner) { count ->
            deletedCountTextView.text = "Battled in last 24h: $count"
        }

        // Initial data fetch
        viewModel.fetchInvasions()
    }
}
