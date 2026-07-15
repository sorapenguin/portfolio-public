package com.example.hoshipost.domain.model

data class StageProgress(
    val stageId: String,
    val bestStars: Int,
    val bestSteps: Int,
    val clearedCount: Int,
    val updatedAt: Long,
)
