package com.example.pixelart.data.remote.dto

data class ProgressRequest(
    val userId: String,
    val puzzleId: Int,
    val paintedJson: String,
    val isCleared: Boolean,
)

data class ProgressResponse(
    val id: Int,
    val userId: String,
    val puzzleId: Int,
    val paintedJson: String,
    val isCleared: Boolean,
    val updatedAt: String,
)
