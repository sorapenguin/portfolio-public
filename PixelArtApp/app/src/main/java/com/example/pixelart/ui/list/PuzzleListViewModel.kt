package com.example.pixelart.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pixelart.data.local.PuzzleEntity
import com.example.pixelart.data.repository.PixelArtRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class PuzzleSize(val gridSize: Int, val label: String) {
    SMALL(8, "8×8"),
    LARGE(16, "16×16"),
}

data class PuzzleListUiState(
    val selectedSize: PuzzleSize = PuzzleSize.SMALL,
    val puzzles: List<PuzzleEntity> = emptyList(),
    val isLoading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PuzzleListViewModel @Inject constructor(
    private val repository: PixelArtRepository,
) : ViewModel() {

    private val _selectedSize = MutableStateFlow(PuzzleSize.SMALL)
    private val _isRefreshing = MutableStateFlow(true)

    val uiState: StateFlow<PuzzleListUiState> = combine(
        _selectedSize.flatMapLatest { size ->
            repository.observeActive(size.gridSize).map { size to it }
        },
        _isRefreshing,
    ) { (size, puzzles), refreshing ->
        PuzzleListUiState(
            selectedSize = size,
            puzzles = puzzles,
            isLoading = refreshing && puzzles.isEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PuzzleListUiState())

    init {
        viewModelScope.launch {
            _isRefreshing.value = true
            PuzzleSize.entries.forEach { repository.refreshBySize(it.gridSize) }
            _isRefreshing.value = false
        }
    }

    fun selectSize(size: PuzzleSize) {
        _selectedSize.value = size
    }
}
