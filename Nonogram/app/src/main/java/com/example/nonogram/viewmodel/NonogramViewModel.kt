package com.example.nonogram.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.example.nonogram.data.local.AdSkipTicketPreferences
import com.example.nonogram.data.local.HintPreferences
import com.example.nonogram.data.model.CellState
import com.example.nonogram.data.model.Puzzle
import com.example.nonogram.data.model.decodeGrid
import com.example.nonogram.data.model.toHints
import com.example.nonogram.data.repository.PuzzleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NonogramUiState(
    val puzzle: Puzzle? = null,
    val rowHints: List<List<Int>> = emptyList(),
    val colHints: List<List<Int>> = emptyList(),
    val savedGrid: List<List<CellState>>? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val hintCount: Int = HintPreferences.INITIAL_COUNT,
    val skipTicketCount: Int = AdSkipTicketPreferences.INITIAL_COUNT,
    /** true の間はヒントボタンを無効化して広告の連打を防ぐ */
    val isAdPending: Boolean = false,
    /** クリア後に戻る→スキップ券なし→広告再生中 */
    val isBackAdPending: Boolean = false,
    /** クリア後に戻る→スキップ券を消費した直後のメッセージ表示中 */
    val ticketUsedForBack: Boolean = false,
    /** true になったら画面が onNavigateBack() を呼ぶ */
    val navigateBack: Boolean = false,
)

@HiltViewModel
class NonogramViewModel @Inject constructor(
    private val repository: PuzzleRepository,
    private val hintPrefs: HintPreferences,
    private val skipTicketPrefs: AdSkipTicketPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NonogramUiState())
    val uiState: StateFlow<NonogramUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            hintPrefs.flow.collect { count ->
                _uiState.update { it.copy(hintCount = count) }
            }
        }
        viewModelScope.launch {
            skipTicketPrefs.flow.collect { count ->
                _uiState.update { it.copy(skipTicketCount = count) }
            }
        }
    }

    fun loadPuzzle(id: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val puzzle = repository.getPuzzleForGame(id)
                val (rowHints, colHints) = puzzle.toHints()
                val savedGrid = if (puzzle.progressJson.isNotEmpty())
                    decodeGrid(puzzle.progressJson, puzzle.rows, puzzle.cols)
                else null
                _uiState.update {
                    it.copy(
                        puzzle = puzzle,
                        rowHints = rowHints,
                        colHints = colHints,
                        savedGrid = savedGrid,
                        isLoading = false,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "読み込み失敗: サーバーに接続できません") }
            }
        }
    }

    fun saveProgress(id: Int, grid: List<List<CellState>>) {
        viewModelScope.launch { repository.saveProgress(id, grid) }
    }

    fun consumeHint() {
        viewModelScope.launch { hintPrefs.consume() }
    }

    fun requestAd() {
        if (_uiState.value.isAdPending) return
        _uiState.update { it.copy(isAdPending = true) }
    }

    fun onAdRewarded() {
        viewModelScope.launch { hintPrefs.addHints() }
        _uiState.update { it.copy(isAdPending = false) }
    }

    fun onAdDismissed() {
        _uiState.update { it.copy(isAdPending = false) }
    }

    fun useSkipTicketForHint() {
        viewModelScope.launch {
            if (skipTicketPrefs.use()) {
                hintPrefs.addHints()
            }
        }
    }

    fun onPuzzleCleared(id: Int) {
        viewModelScope.launch {
            repository.markCleared(id)
        }
    }

    /** クリア後の戻るボタン：スキップ券があれば消費、なければ広告 */
    fun requestBackNavigation() {
        viewModelScope.launch {
            if (skipTicketPrefs.use()) {
                _uiState.update { it.copy(ticketUsedForBack = true) }
            } else {
                _uiState.update { it.copy(isBackAdPending = true) }
            }
        }
    }

    fun onBackAdCompleted() {
        _uiState.update { it.copy(isBackAdPending = false, navigateBack = true) }
    }

    fun onTicketUsedMessageShown() {
        _uiState.update { it.copy(ticketUsedForBack = false, navigateBack = true) }
    }

    fun consumeNavigateBack() {
        _uiState.update { it.copy(navigateBack = false) }
    }

}
