package com.mints.projectgammatwo.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class SharedFilterViewModel : ViewModel() {
    // LiveData holding the current set of quest filters.
    private val _questFilters = MutableLiveData<Set<String>>(setOf())
    val questFilters: LiveData<Set<String>> = _questFilters

    // Function to update filters
    fun updateFilters(newFilters: Set<String>) {
        _questFilters.value = newFilters
    }
}
