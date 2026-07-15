package com.example.pixelart.data.remote.dto

data class PuzzleListItemDto(
    val id: Int,
    val title: String,
    val theme: String,
    val width: Int,
    val height: Int,
    val difficulty: String,
    val difficultyLevel: Int,
    val stageNumber: Int,
    val paletteSize: Int,
)
