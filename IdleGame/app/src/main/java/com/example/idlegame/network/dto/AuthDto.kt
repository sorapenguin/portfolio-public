package com.example.idlegame.network.dto

data class LoginRequest(val username: String, val password: String)

data class CloudSaveRequest(val password: String)

data class AuthResponse(val token: String, val userId: Long, val username: String)

data class ApiResponse<T>(val success: Boolean, val message: String, val data: T?)
