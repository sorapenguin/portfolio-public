package com.example.nonogram.data.remote.dto

data class PuzzleDetailDto(
    val id: Int,
    val title: String,
    val rows: Int,
    val cols: Int,
    val difficulty: String,
    val stageNumber: Int,
    val rowHints: List<List<Int>>,
    val colHints: List<List<Int>>,
    val solution: List<List<Int>>,
)
