package com.example.nonogram.data.remote

import com.example.nonogram.data.remote.dto.PuzzleDetailDto
import com.example.nonogram.data.remote.dto.PuzzleListItemDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface NonogramApiService {
    @GET("puzzles")
    suspend fun getPuzzles(
        @Query("rows") rows: Int,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 100,
    ): List<PuzzleListItemDto>

    @GET("puzzles/{id}")
    suspend fun getPuzzle(@Path("id") id: Int): PuzzleDetailDto
}
