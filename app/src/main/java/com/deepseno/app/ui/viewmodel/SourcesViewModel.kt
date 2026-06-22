package com.enmooy.deepseno.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enmooy.deepseno.data.remote.model.Recording
import com.enmooy.deepseno.data.remote.model.SearchResult
import com.enmooy.deepseno.service.ApiClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SourcesViewModel @Inject constructor(
    val apiClient: ApiClient,
) : ViewModel() {

    private val _recordings = MutableStateFlow<List<Recording>>(emptyList())
    val recordings: StateFlow<List<Recording>> = _recordings

    private val _searchResults = MutableStateFlow<List<SearchResult>?>(null)
    val searchResults: StateFlow<List<SearchResult>?> = _searchResults

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _selectedFilter = MutableStateFlow("all")
    val selectedFilter: StateFlow<String> = _selectedFilter

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    val filteredRecordings: List<Recording>
        get() {
            val filter = _selectedFilter.value
            val list = _recordings.value
            if (filter == "all") return list
            return list.filter { it.mediaType == filter }
        }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedFilter(filter: String) {
        _selectedFilter.value = filter
    }

    fun loadRecordings() {
        val api = apiClient.api ?: return
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                _recordings.value = api.getRecordings()
            } catch (e: Exception) {
                _errorMessage.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun search() {
        val query = _searchQuery.value.trim()
        if (query.isEmpty()) {
            _searchResults.value = null
            return
        }
        val api = apiClient.api ?: return
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                _searchResults.value = api.search(query)
            } catch (e: Exception) {
                _errorMessage.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }
}
