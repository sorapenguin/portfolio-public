package com.example.hoshipost.domain.model

enum class Difficulty {
    EASY, NORMAL, HARD;

    val boardSize: Int
        get() = when (this) {
            EASY -> 5
            NORMAL -> 7
            HARD -> 9
        }

    val deliveryCount: Int
        get() = when (this) {
            EASY -> 2
            NORMAL -> 3
            HARD -> 4
        }

    val wallRatioRange: ClosedFloatingPointRange<Double>
        get() = when (this) {
            EASY -> 0.05..0.10
            NORMAL -> 0.10..0.18
            HARD -> 0.15..0.25
        }

    val optimalStepsRange: IntRange
        get() = when (this) {
            EASY -> 8..18
            NORMAL -> 14..30
            HARD -> 24..45
        }
}
