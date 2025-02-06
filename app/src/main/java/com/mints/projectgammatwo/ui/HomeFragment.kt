package com.mints.projectgammatwo.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.recyclerview.widget.RecyclerView
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

        // Observe LiveData
        viewModel.invasions.observe(viewLifecycleOwner) { invasions ->
            adapter.submitList(invasions)
            swipeRefresh.isRefreshing = false
        }

        viewModel.error.observe(viewLifecycleOwner) { errorMessage ->
            Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
            swipeRefresh.isRefreshing = false
        }

        // Initial data fetch
        viewModel.fetchInvasions()
    }
}