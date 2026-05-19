package com.example.nonogram.data.remote.dto

data class PuzzleListItemDto(
    val id: Int,
    val title: String,
    val rows: Int,
    val cols: Int,
    val difficulty: String,
    val stageNumber: Int,
)
