package com.example.idlegame.ui.minigame

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val GAME_DURATION = 10
private const val GEM_PER_TAPS = 10

data class TapGameState(
    val timeLeftSeconds: Int = GAME_DURATION,
    val tapCount: Int = 0,
    val isRunning: Boolean = false,
    val isFinished: Boolean = false,
    val gemsEarned: Int = 0
)

class TapGameViewModel : ViewModel() {

    private val _state = MutableStateFlow(TapGameState())
    val state: StateFlow<TapGameState> = _state

    private var timerJob: Job? = null

    fun startGame() {
        if (_state.value.isRunning) return
        _state.value = TapGameState(isRunning = true)
        timerJob = viewModelScope.launch {
            repeat(GAME_DURATION) {
                delay(1000L)
                if (!isActive) return@launch
                val remaining = _state.value.timeLeftSeconds - 1
                if (remaining <= 0) {
                    finishGame()
                } else {
                    _state.value = _state.value.copy(timeLeftSeconds = remaining)
                }
            }
        }
    }

    fun onTap() {
        if (!_state.value.isRunning) return
        _state.value = _state.value.copy(tapCount = _state.value.tapCount + 1)
    }

    private fun finishGame() {
        timerJob?.cancel()
        val taps = _state.value.tapCount
        val gems = taps / GEM_PER_TAPS
        _state.value = _state.value.copy(
            timeLeftSeconds = 0,
            isRunning = false,
            isFinished = true,
            gemsEarned = gems
        )
    }

    override fun onCleared() {
        timerJob?.cancel()
        super.onCleared()
    }

}
