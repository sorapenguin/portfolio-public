package com.example.hoshipost.ui.puzzle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.hoshipost.data.ProgressRepository
import com.example.hoshipost.domain.logic.RouteValidator
import com.example.hoshipost.domain.logic.ScoreCalculator
import com.example.hoshipost.domain.model.Board
import com.example.hoshipost.domain.model.Position
import com.example.hoshipost.domain.model.StageResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PuzzleViewModel(
    private val board: Board,
    private val progressRepository: ProgressRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PuzzleUiState(board))
    val uiState: StateFlow<PuzzleUiState> = _uiState.asStateFlow()

    private var lastTouched: Position? = null

    fun onDragStarted(position: Position) {
        lastTouched = null
        onCellTouched(position)
        lastTouched = position
    }

    fun onDragMoved(position: Position) {
        if (position == lastTouched) return
        lastTouched = position
        onCellTouched(position)
    }

    fun onDragEnded() {
        lastTouched = null
    }

    fun onResetRoute() {
        _uiState.update {
            it.copy(
                route = emptyList(),
                visitedDeliveryIds = emptySet(),
                isCleared = false,
                stars = null,
            )
        }
    }

    fun onUndoRoute() {
        val state = _uiState.value
        if (state.route.isEmpty()) return

        val newRoute = state.route.dropLast(1)
        val visited = RouteValidator.computeVisitedIds(newRoute, state.board)

        _uiState.update {
            it.copy(
                route = newRoute,
                visitedDeliveryIds = visited,
                isCleared = false,
                stars = null,
            )
        }
    }

    fun onCellTouched(position: Position) {
        val state = _uiState.value
        if (state.isCleared) return

        val newRoute = RouteValidator.nextRoute(state.route, position, state.board)
            ?: return

        val visited = RouteValidator.computeVisitedIds(newRoute, state.board)
        val cleared = RouteValidator.isCleared(newRoute, visited, state.board)
        val stars = if (cleared) {
            ScoreCalculator.calculate(newRoute.size - 1, state.board.optimalSteps)
        } else {
            null
        }

        _uiState.update {
            it.copy(
                route = newRoute,
                visitedDeliveryIds = visited,
                isCleared = cleared,
                stars = stars,
            )
        }

        if (cleared && stars != null) {
            saveProgress(stars, newRoute.size - 1)
        }
    }

    private fun saveProgress(stars: Int, steps: Int) {
        val stageId = board.seed.toString()
        val result = StageResult(
            stageId = stageId,
            cleared = true,
            stars = stars,
            playerSteps = steps,
            optimalSteps = board.optimalSteps,
            clearedAt = System.currentTimeMillis(),
        )

        viewModelScope.launch {
            progressRepository.save(result)
            progressRepository.saveLastStageId(stageId)
        }
    }
}

class PuzzleViewModelFactory(
    private val board: Board,
    private val progressRepository: ProgressRepository,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PuzzleViewModel::class.java)) {
            return PuzzleViewModel(board, progressRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
