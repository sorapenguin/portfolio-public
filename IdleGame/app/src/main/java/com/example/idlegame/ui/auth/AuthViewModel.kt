package com.example.idlegame.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.idlegame.network.ApiRepository
import com.example.idlegame.network.ApiResult
import com.example.idlegame.network.dto.AuthResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AuthViewModel : ViewModel() {
    private val repository = ApiRepository()

    private val _state = MutableStateFlow<AuthState>(AuthState.Idle)
    val state: StateFlow<AuthState> = _state

    fun login(username: String, password: String) {
        viewModelScope.launch {
            _state.value = AuthState.Loading
            _state.value = when (val result = repository.login(username, password)) {
                is ApiResult.Success -> AuthState.Success(result.data, isNewAccount = false)
                is ApiResult.Offline -> AuthState.Error("オフラインのためログインできません")
                is ApiResult.Failure -> AuthState.Error(result.message)
            }
        }
    }

    fun cloudSave(password: String) {
        viewModelScope.launch {
            _state.value = AuthState.Loading
            _state.value = when (val result = repository.cloudSave(password)) {
                is ApiResult.Success -> AuthState.Success(result.data, isNewAccount = true)
                is ApiResult.Offline -> AuthState.Error("オフラインのためクラウドセーブできません")
                is ApiResult.Failure -> AuthState.Error(result.message)
            }
        }
    }

    fun reset() { _state.value = AuthState.Idle }
}

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Success(val auth: AuthResponse, val isNewAccount: Boolean) : AuthState()
    data class Error(val message: String) : AuthState()
}
