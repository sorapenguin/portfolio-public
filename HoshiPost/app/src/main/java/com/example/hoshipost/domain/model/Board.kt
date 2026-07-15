package com.example.hoshipost.domain.model

data class Board(
    val width: Int,
    val height: Int,
    val cells: List<List<Cell>>,
    val start: Position,
    val goal: Position,
    val deliveryPoints: List<DeliveryPoint>,
    val optimalSteps: Int,
    val seed: Long,
    val difficulty: Difficulty,
) {
    fun cellAt(pos: Position): Cell? =
        if (pos.isInBounds(width, height)) cells[pos.row][pos.col] else null

    fun isPassable(pos: Position): Boolean {
        val cell = cellAt(pos) ?: return false
        return cell.type !is CellType.Wall
    }
}
