package com.example.hoshipost.domain.logic

object ScoreCalculator {

    fun calculate(playerSteps: Int, optimalSteps: Int): Int = when {
        playerSteps <= optimalSteps + 2 -> 3
        playerSteps <= optimalSteps + 5 -> 2
        else -> 1
    }
}
