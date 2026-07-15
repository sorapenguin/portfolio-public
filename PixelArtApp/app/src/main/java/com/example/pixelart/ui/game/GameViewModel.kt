package com.example.pixelart.ui.game

import android.graphics.Color.parseColor
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pixelart.data.local.PuzzleEntity
import com.example.pixelart.data.repository.PixelArtRepository
import com.example.pixelart.domain.PaintLogic
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GameUiState(
    val puzzle: PuzzleEntity? = null,
    val pixels: List<List<Int>> = emptyList(),
    val palette: List<Color> = emptyList(),
    val painted: List<List<Int>> = emptyList(),
    val usedColorIndices: List<Int> = emptyList(),
    val selectedColorIndex: Int = 0,
    val isCleared: Boolean = false,
    val completionRate: Float = 0f,
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class GameViewModel @Inject constructor(
    private val repository: PixelArtRepository,
) : ViewModel() {
    private val gson = Gson()
    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    fun loadPuzzle(id: Int) {
        if (_uiState.value.puzzle?.id == id) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { repository.getPuzzleForGame(id) }
                .onSuccess { puzzle ->
                    val pixels = parseIntGrid(puzzle.pixelsJson)
                    val palette = parsePalette(puzzle.paletteJson)
                    val painted = parseIntGrid(puzzle.paintedJson).ifEmpty {
                        PaintLogic.emptyPainted(puzzle.width, puzzle.height)
                    }
                    val usedColorIndices = pixels.flatten()
                        .filter { it != 0 }.distinct().sorted()
                    val cleared = puzzle.isCleared || PaintLogic.isCleared(pixels, painted)
                    _uiState.update {
                        it.copy(
                            puzzle = puzzle,
                            pixels = pixels,
                            palette = palette,
                            painted = painted,
                            usedColorIndices = usedColorIndices,
                            selectedColorIndex = usedColorIndices.firstOrNull() ?: 0,
                            isCleared = cleared,
                            completionRate = if (cleared) 1f else PaintLogic.completionRate(pixels, painted),
                            isLoading = false,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isLoading = false, error = error.message ?: "読み込みに失敗しました")
                    }
                }
        }
    }

    fun selectColor(index: Int) {
        _uiState.update { it.copy(selectedColorIndex = index) }
    }

    fun paintCell(row: Int, col: Int) {
        val state = _uiState.value
        val puzzleId = state.puzzle?.id ?: return
        if (state.isCleared || row !in state.painted.indices || col !in state.painted[row].indices) return
        val target = state.pixels.getOrNull(row)?.getOrNull(col) ?: 0
        // 背景セル or 選択色がセルの番号と一致しない場合は無視
        if (target == 0 || state.selectedColorIndex != target) return

        val newPainted = state.painted.mapIndexed { r, cols ->
            if (r != row) cols else cols.mapIndexed { c, value ->
                if (c == col) state.selectedColorIndex else value
            }
        }
        val cleared = PaintLogic.isCleared(state.pixels, newPainted)
        _uiState.update {
            it.copy(
                painted = newPainted,
                isCleared = cleared,
                completionRate = if (cleared) 1f else PaintLogic.completionRate(state.pixels, newPainted),
            )
        }
        if (cleared) {
            viewModelScope.launch { repository.markCleared(puzzleId, newPainted) }
        }
    }

    fun saveProgress(id: Int) {
        val painted = _uiState.value.painted
        if (painted.isEmpty()) return
        viewModelScope.launch { repository.savePainted(id, painted) }
    }

    private fun parseIntGrid(json: String): List<List<Int>> {
        if (json.isBlank()) return emptyList()
        val type = object : TypeToken<List<List<Int>>>() {}.type
        return runCatching { gson.fromJson<List<List<Int>>>(json, type) }.getOrDefault(emptyList())
    }

    private fun parsePalette(json: String): List<Color> {
        if (json.isBlank()) return emptyList()
        val type = object : TypeToken<List<String>>() {}.type
        return runCatching {
            gson.fromJson<List<String>>(json, type).map { Color(parseColor(it)) }
        }.getOrDefault(emptyList())
    }
}
