package com.example.pixelart.data.remote.dto

data class PuzzleDetailDto(
    val id: Int,
    val title: String,
    val theme: String,
    val width: Int,
    val height: Int,
    val difficulty: String,
    val difficultyLevel: Int,
    val stageNumber: Int,
    val pixels: List<List<Int>>,
    val palette: List<String>,
)
