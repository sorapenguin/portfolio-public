package com.example.pixelart.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pixelart_puzzles")
data class PuzzleEntity(
    @PrimaryKey val id: Int,
    val title: String,
    val theme: String,
    val width: Int,
    val height: Int,
    val difficulty: String,
    val difficultyLevel: Int,
    val stageNumber: Int,
    val pixelsJson: String,
    val paletteJson: String,
    val paintedJson: String = "",
    val isCleared: Boolean = false,
    val isVisible: Boolean = false,
)
