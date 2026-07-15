package com.example.hoshipost.domain.model

data class StageResult(
    val stageId: String,
    val cleared: Boolean,
    val stars: Int,
    val playerSteps: Int,
    val optimalSteps: Int,
    val clearedAt: Long,
)
