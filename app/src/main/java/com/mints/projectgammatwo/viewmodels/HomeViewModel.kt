package com.mints.projectgammatwo.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.mints.projectgammatwo.data.ApiClient
import com.mints.projectgammatwo.data.FilterPreferences
import com.mints.projectgammatwo.data.Invasion
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val filterPreferences = FilterPreferences(application)

    private val _invasions = MutableLiveData<List<Invasion>>()
    val invasions: LiveData<List<Invasion>> get() = _invasions

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> get() = _error

    companion object {
        private const val TAG = "HomeViewModel"
    }

    fun fetchInvasions() {
        Log.d(TAG, "Starting to fetch invasions...")

        viewModelScope.launch {
            try {
                val response = ApiClient.api.getInvasions()
                val filteredAndSortedInvasions = response.invasions
                    .filter { invasion ->
                        invasion.character in filterPreferences.getEnabledCharacters()
                    }
                    .sortedBy { it.invasion_start }
                    .reversed() // Most recent first

                _invasions.value = filteredAndSortedInvasions
                Log.d(TAG, "API call successful. Filtered data size: ${filteredAndSortedInvasions.size}")
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching invasions: ${e.message}", e)
                _error.value = "Failed to fetch invasions: ${e.message}"
            }
        }
    }

    fun deleteInvasion(invasion: Invasion) {
        val currentList = _invasions.value?.toMutableList() ?: mutableListOf()
        currentList.remove(invasion)
        _invasions.value = currentList
    }
}