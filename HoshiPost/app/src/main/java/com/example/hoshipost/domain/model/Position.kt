package com.example.hoshipost.domain.model

import kotlin.math.abs

data class Position(val row: Int, val col: Int) {

    fun isAdjacentTo(other: Position): Boolean =
        abs(row - other.row) + abs(col - other.col) == 1

    fun neighbors(): List<Position> = listOf(
        Position(row - 1, col),
        Position(row + 1, col),
        Position(row, col - 1),
        Position(row, col + 1),
    )

    fun isInBounds(width: Int, height: Int): Boolean =
        row in 0 until height && col in 0 until width
}
