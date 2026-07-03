package com.example

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.SavedReply
import com.example.data.SavedReplyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(private val repository: SavedReplyRepository) : ViewModel() {
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory

    val uiState: StateFlow<List<SavedReply>> = combine(
        repository.allReplies,
        _searchQuery,
        _selectedCategory
    ) { replies, query, category ->
        replies.filter { item ->
            val matchesCategory = category == "All" || item.category == category
            val matchesQuery = query.isBlank() || 
                item.title.contains(query, ignoreCase = true) ||
                item.content.contains(query, ignoreCase = true)
            matchesCategory && matchesQuery
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectCategory(category: String) {
        _selectedCategory.value = category
    }

    fun addReply(reply: SavedReply) {
        viewModelScope.launch {
            repository.insertReply(reply)
        }
    }

    fun updateReply(reply: SavedReply) {
        viewModelScope.launch {
            repository.updateReply(reply)
        }
    }

    fun deleteReply(reply: SavedReply) {
        viewModelScope.launch {
            repository.deleteReply(reply)
        }
    }
}
