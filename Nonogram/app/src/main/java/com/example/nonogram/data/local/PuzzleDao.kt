package com.example.nonogram.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.example.nonogram.data.model.Puzzle
import kotlinx.coroutines.flow.Flow

@Dao
interface PuzzleDao {
    /** 表示中（isVisible=1）の未クリアパズルを observe（全サイズ） */
    @Query("SELECT * FROM puzzles WHERE isCleared = 0 AND isVisible = 1 ORDER BY id ASC")
    fun observeActive(): Flow<List<Puzzle>>

    /** 表示中（isVisible=1）の未クリアパズルを observe（サイズ別） */
    @Query("SELECT * FROM puzzles WHERE isCleared = 0 AND isVisible = 1 AND rows = :rows ORDER BY id ASC")
    fun observeActive(rows: Int): Flow<List<Puzzle>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertStubs(puzzles: List<Puzzle>)

    @Query("SELECT * FROM puzzles WHERE id = :id")
    suspend fun findById(id: Int): Puzzle?

    @Upsert
    suspend fun upsert(puzzle: Puzzle)

    @Query("UPDATE puzzles SET isCleared = 1, progressJson = '' WHERE id = :id")
    suspend fun markCleared(id: Int)

    @Query("UPDATE puzzles SET isUnlocked = 1 WHERE id = :id")
    suspend fun unlockPuzzle(id: Int)

    @Query("UPDATE puzzles SET progressJson = :progressJson WHERE id = :id")
    suspend fun saveProgress(id: Int, progressJson: String)

    @Query("UPDATE puzzles SET isVisible = 1 WHERE id IN (:ids)")
    suspend fun markVisible(ids: List<Int>)

    /** 表示中の未クリア数（サイズ別） */
    @Query("SELECT COUNT(*) FROM puzzles WHERE isCleared = 0 AND isVisible = 1 AND rows = :rows")
    suspend fun countActive(rows: Int): Int

    /** バッファ（非表示・未クリア）数（サイズ別） */
    @Query("SELECT COUNT(*) FROM puzzles WHERE isCleared = 0 AND isVisible = 0 AND rows = :rows")
    suspend fun countBuffer(rows: Int): Int

    /** 未クリア総数（表示中＋バッファ）（サイズ別） */
    @Query("SELECT COUNT(*) FROM puzzles WHERE isCleared = 0 AND rows = :rows")
    suspend fun countUncleared(rows: Int): Int

    /** 全パズル数（ページ計算用）（サイズ別） */
    @Query("SELECT COUNT(*) FROM puzzles WHERE rows = :rows")
    suspend fun countAll(rows: Int): Int

    /** バッファから n 件の id を取得（昇格候補）（サイズ別） */
    @Query("SELECT id FROM puzzles WHERE isVisible = 0 AND isCleared = 0 AND rows = :rows ORDER BY id ASC LIMIT :n")
    suspend fun getBufferIds(n: Int, rows: Int): List<Int>

    /** クリア済みパズルを observe */
    @Query("SELECT * FROM puzzles WHERE isCleared = 1 ORDER BY id ASC")
    fun observeCleared(): Flow<List<Puzzle>>

    /** クリア済み件数を observe */
    @Query("SELECT COUNT(*) FROM puzzles WHERE isCleared = 1")
    fun observeClearedCount(): Flow<Int>

    @Query("DELETE FROM puzzles")
    suspend fun deleteAll()
}
