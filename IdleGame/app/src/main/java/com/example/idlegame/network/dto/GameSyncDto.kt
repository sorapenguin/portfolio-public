package com.example.idlegame.network.dto

data class GameSaveRequest(
    val stage: Long,
    val coins: Long,
    val gems: Int,
    val totalAttack: Long,
    val weaponSlots: Int,
    val maxMilestoneReached: Int,
    val totalEnemiesDefeated: Long,
    val totalCoinsEarned: Long,
    val stateJson: String? = null,
)

data class GameStateResponse(
    val id: Long,
    val userId: Long,
    val stage: Long,
    val coins: Long,
    val gems: Int,
    val totalAttack: Long,
    val weaponSlots: Int,
    val stateJson: String? = null,
    val lastSavedAt: String,
)
