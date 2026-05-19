package com.example.idlegame.network

import com.example.idlegame.network.dto.ApiResponse
import com.example.idlegame.network.dto.AuthResponse
import com.example.idlegame.network.dto.CloudSaveRequest
import com.example.idlegame.network.dto.GameSaveRequest
import com.example.idlegame.network.dto.GameStateResponse
import com.example.idlegame.network.dto.LoginRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface ApiService {
    @POST("idlegame/auth/login")
    suspend fun login(@Body request: LoginRequest): ApiResponse<AuthResponse>

    @POST("idlegame/auth/register")
    suspend fun cloudSave(@Body request: CloudSaveRequest): ApiResponse<AuthResponse>

    @GET("idlegame/time")
    suspend fun serverTime(): ApiResponse<Long>

    @GET("idlegame/game-data")
    suspend fun loadGameState(
        @Header("Authorization") bearerToken: String,
    ): ApiResponse<GameStateResponse>

    @POST("idlegame/game-data")
    suspend fun saveGameState(
        @Header("Authorization") bearerToken: String,
        @Body request: GameSaveRequest,
    ): ApiResponse<GameStateResponse>

    @DELETE("idlegame/auth/account")
    suspend fun deleteAccount(
        @Header("Authorization") bearerToken: String,
    ): ApiResponse<Unit>
}
