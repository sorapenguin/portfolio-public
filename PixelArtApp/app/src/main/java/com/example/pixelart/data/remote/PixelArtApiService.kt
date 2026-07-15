package com.example.pixelart.data.remote

import com.example.pixelart.data.remote.dto.ProgressRequest
import com.example.pixelart.data.remote.dto.ProgressResponse
import com.example.pixelart.data.remote.dto.PuzzleDetailDto
import com.example.pixelart.data.remote.dto.PuzzleListItemDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface PixelArtApiService {
    // size: グリッドサイズ (8 or 16)、page: ページ番号、limit: 件数
    // API 未対応の場合は全件返却されるが、クライアント側でフィルタする
    @GET("puzzles")
    suspend fun getPuzzles(
        @Query("size") size: Int? = null,
        @Query("page") page: Int = 0,
        @Query("limit") limit: Int = 20,
    ): List<PuzzleListItemDto>

    @GET("puzzles/{id}")
    suspend fun getPuzzle(@Path("id") id: Int): PuzzleDetailDto

    @POST("progress")
    suspend fun saveProgress(@Body body: ProgressRequest): ProgressResponse

    @GET("progress/{puzzleId}")
    suspend fun getProgress(
        @Path("puzzleId") puzzleId: Int,
        @Query("userId") userId: String,
    ): ProgressResponse
}
