package com.example.nonogram.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.nonogram.worker.StaminaRecoveryWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import com.example.nonogram.data.local.AdSkipTicketPreferences
import com.example.nonogram.data.local.LoginBonusPreferences
import com.example.nonogram.data.local.StaminaPreferences
import com.example.nonogram.data.model.Puzzle
import com.example.nonogram.data.model.PuzzleCategory
import com.example.nonogram.data.repository.PuzzleRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PuzzleListUiState(
    val puzzles: List<Puzzle> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val stamina: Int = StaminaPreferences.MAX,
    val minutesToNextRecovery: Int = 0,
    val isStaminaAdPending: Boolean = false,
    val selectedCategory: PuzzleCategory = PuzzleCategory.MINI,
    val skipTicketCount: Int = AdSkipTicketPreferences.INITIAL_COUNT,
    val clearedCount: Int = 0,
    val isSkipTicketAdPending: Boolean = false,
    val canWatchSkipTicketAd: Boolean = false,
    val skipAdWatchedToday: Int = 0,
    val loginBonusAmount: Int = 0,
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class PuzzleListViewModel @Inject constructor(
    private val repository: PuzzleRepository,
    private val staminaPreferences: StaminaPreferences,
    private val skipTicketPrefs: AdSkipTicketPreferences,
    private val loginBonusPrefs: LoginBonusPreferences,
    private val workManager: WorkManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PuzzleListUiState())
    val uiState: StateFlow<PuzzleListUiState> = _uiState.asStateFlow()

    private val _selectedCategory = MutableStateFlow(PuzzleCategory.MINI)

    init {
        viewModelScope.launch {
            _selectedCategory.flatMapLatest { category ->
                repository.observeActive(category)
            }.collect { list ->
                _uiState.update { it.copy(puzzles = list) }
            }
        }
        viewModelScope.launch {
            repository.observeClearedCount().collect { count ->
                _uiState.update { it.copy(clearedCount = count) }
            }
        }
        viewModelScope.launch {
            skipTicketPrefs.flow.collect { count ->
                val today = todayString()
                _uiState.update {
                    it.copy(
                        skipTicketCount      = count,
                        canWatchSkipTicketAd = false,  // 後続の refreshSkipTicketAdState で更新
                    )
                }
                refreshSkipTicketAdState()
            }
        }
        refresh()
        refreshStamina()
        viewModelScope.launch {
            val today = todayString()
            if (loginBonusPrefs.checkAndClaim(today)) {
                skipTicketPrefs.award(AdSkipTicketPreferences.LOGIN_BONUS_AMOUNT)
                _uiState.update { it.copy(loginBonusAmount = AdSkipTicketPreferences.LOGIN_BONUS_AMOUNT) }
            }
            refreshSkipTicketAdState()
        }
    }

    private fun todayString(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    fun selectCategory(category: PuzzleCategory) {
        _selectedCategory.value = category
        _uiState.update { it.copy(selectedCategory = category) }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                repository.refreshList(_selectedCategory.value)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "読み込みエラー: サーバーに接続できません") }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
            refreshSkipTicketAdState()
        }
    }

    fun refreshStamina() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    stamina              = staminaPreferences.get(),
                    minutesToNextRecovery = staminaPreferences.minutesToNextRecovery(),
                )
            }
        }
    }

    private fun refreshSkipTicketAdState() {
        viewModelScope.launch {
            val today = todayString()
            _uiState.update {
                it.copy(
                    canWatchSkipTicketAd = skipTicketPrefs.canWatchSkipTicketAd(today),
                    skipAdWatchedToday   = skipTicketPrefs.skipAdWatchedToday(today),
                )
            }
        }
    }

    fun unlockPuzzle(id: Int): Boolean {
        if (_uiState.value.stamina <= 0) return false
        _uiState.update { it.copy(stamina = it.stamina - 1) }
        viewModelScope.launch {
            staminaPreferences.consume()
            val newStamina = staminaPreferences.get()
            val minutesToNext = staminaPreferences.minutesToNextRecovery()
            _uiState.update {
                it.copy(
                    stamina              = newStamina,
                    minutesToNextRecovery = minutesToNext,
                )
            }
            repository.unlockPuzzle(id)
            if (newStamina == 0) {
                scheduleStaminaRecoveryNotification(minutesToNext)
            }
        }
        return true
    }

    private fun scheduleStaminaRecoveryNotification(minutesToFirstRecovery: Int) {
        val delayMinutes = minutesToFirstRecovery + (StaminaPreferences.MAX - 1) * 10L
        val request = OneTimeWorkRequestBuilder<StaminaRecoveryWorker>()
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniqueWork(
            StaminaRecoveryWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    // --- スタミナ広告（スタミナ=0 のとき） ---

    fun requestStaminaAd() {
        _uiState.update { it.copy(isStaminaAdPending = true) }
    }

    fun onStaminaAdRewarded() {
        viewModelScope.launch {
            staminaPreferences.addFromAd()
            _uiState.update {
                it.copy(
                    isStaminaAdPending   = false,
                    stamina              = staminaPreferences.get(),
                    minutesToNextRecovery = staminaPreferences.minutesToNextRecovery(),
                )
            }
        }
    }

    fun onStaminaAdDismissed() {
        _uiState.update { it.copy(isStaminaAdPending = false) }
    }

    // --- スキップ券広告（1日3回・10分CT・上限10枚） ---

    fun requestSkipTicketAd() {
        if (!_uiState.value.canWatchSkipTicketAd) return
        _uiState.update { it.copy(isSkipTicketAdPending = true, canWatchSkipTicketAd = false) }
    }

    fun onSkipTicketAdRewarded() {
        viewModelScope.launch {
            skipTicketPrefs.recordSkipTicketAdWatch(todayString())
            _uiState.update { it.copy(isSkipTicketAdPending = false) }
            refreshSkipTicketAdState()
        }
    }

    fun onSkipTicketAdDismissed() {
        _uiState.update { it.copy(isSkipTicketAdPending = false) }
        refreshSkipTicketAdState()
    }

    // --- スキップ券でスタミナ回復 ---

    fun useSkipTicketForStamina() {
        viewModelScope.launch {
            if (skipTicketPrefs.use()) {
                staminaPreferences.addFromAd()
                _uiState.update {
                    it.copy(
                        stamina              = staminaPreferences.get(),
                        minutesToNextRecovery = staminaPreferences.minutesToNextRecovery(),
                    )
                }
            }
        }
    }

    fun dismissLoginBonus() {
        _uiState.update { it.copy(loginBonusAmount = 0) }
    }

    fun resetCache() {
        viewModelScope.launch {
            repository.resetCache()
            refresh()
        }
    }

}
