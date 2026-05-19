package com.example.idlegame.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface GameStateDao {
    @Query("SELECT * FROM game_state WHERE id = 1")
    suspend fun get(): GameStateEntity?

    @Upsert
    suspend fun upsert(entity: GameStateEntity)
}
