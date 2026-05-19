package com.example.idlegame.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.idlegame.IdleGameApp
import com.example.idlegame.network.ApiResult
import com.example.idlegame.network.TokenManager
import com.example.idlegame.settings.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class DeleteAccountState {
    object Idle : DeleteAccountState()
    object Loading : DeleteAccountState()
    object Success : DeleteAccountState()
    data class Error(val message: String) : DeleteAccountState()
}

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val idleApp = app as IdleGameApp
    private val settingsRepo = idleApp.settingsRepository
    private val apiRepo = idleApp.apiRepository

    val soundEffects: StateFlow<Boolean> = settingsRepo.soundEffects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings.DEFAULT_SOUND_EFFECTS)

    val vibration: StateFlow<Boolean> = settingsRepo.vibration
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings.DEFAULT_VIBRATION)

    private val _deleteState = MutableStateFlow<DeleteAccountState>(DeleteAccountState.Idle)
    val deleteState: StateFlow<DeleteAccountState> = _deleteState.asStateFlow()

    fun setSoundEffects(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setSoundEffects(enabled) }
    }

    fun setVibration(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setVibration(enabled) }
    }

    fun deleteAccount(token: String) {
        if (_deleteState.value is DeleteAccountState.Loading) return
        _deleteState.value = DeleteAccountState.Loading
        viewModelScope.launch {
            _deleteState.value = when (val result = apiRepo.deleteAccount(token)) {
                is ApiResult.Success<*> -> DeleteAccountState.Success
                is ApiResult.Offline   -> DeleteAccountState.Error("ネットワークに接続できません")
                is ApiResult.Failure   -> DeleteAccountState.Error(result.message)
            }
        }
    }

    fun resetDeleteState() {
        _deleteState.value = DeleteAccountState.Idle
    }
}
