package com.example.pixelart.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PuzzleDao {
    // 表示対象（未クリア・visible）を最大5件ウォッチ
    @Query("SELECT * FROM pixelart_puzzles WHERE width = :width AND isVisible = 1 AND isCleared = 0 ORDER BY stageNumber ASC LIMIT 5")
    fun observeActive(width: Int): Flow<List<PuzzleEntity>>

    @Query("SELECT * FROM pixelart_puzzles WHERE id = :id")
    suspend fun findById(id: Int): PuzzleEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertStubs(puzzles: List<PuzzleEntity>)

    @Upsert
    suspend fun upsert(puzzle: PuzzleEntity)

    @Query("UPDATE pixelart_puzzles SET isCleared = 1 WHERE id = :id")
    suspend fun markCleared(id: Int)

    @Query("UPDATE pixelart_puzzles SET paintedJson = :json WHERE id = :id")
    suspend fun savePainted(id: Int, json: String)

    // 表示スロット管理
    @Query("SELECT COUNT(*) FROM pixelart_puzzles WHERE width = :width AND isVisible = 1 AND isCleared = 0")
    suspend fun countActive(width: Int): Int

    @Query("SELECT COUNT(*) FROM pixelart_puzzles WHERE width = :width")
    suspend fun countAll(width: Int): Int

    @Query("SELECT id FROM pixelart_puzzles WHERE width = :width AND isVisible = 0 AND isCleared = 0 ORDER BY stageNumber ASC LIMIT :n")
    suspend fun getBufferIds(n: Int, width: Int): List<Int>

    @Query("UPDATE pixelart_puzzles SET isVisible = 1 WHERE id IN (:ids)")
    suspend fun markVisible(ids: List<Int>)
}
