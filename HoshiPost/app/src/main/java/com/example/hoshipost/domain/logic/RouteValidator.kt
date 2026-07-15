package com.example.hoshipost.domain.logic

import com.example.hoshipost.domain.model.Board
import com.example.hoshipost.domain.model.CellType
import com.example.hoshipost.domain.model.Position

object RouteValidator {

    fun nextRoute(
        route: List<Position>,
        position: Position,
        board: Board,
    ): List<Position>? {
        if (!position.isInBounds(board.width, board.height)) return null
        if (board.cellAt(position)?.type is CellType.Wall) return null

        if (route.isEmpty()) {
            return if (board.cellAt(position)?.type == CellType.Start) {
                listOf(position)
            } else {
                null
            }
        }

        val current = route.last()
        if (position == current) return null
        if (!position.isAdjacentTo(current)) return null

        if (route.size >= 2 && position == route[route.size - 2]) {
            return route.dropLast(1)
        }

        return route + position
    }

    fun computeVisitedIds(route: List<Position>, board: Board): Set<Int> =
        route.mapNotNull { position ->
            (board.cellAt(position)?.type as? CellType.DeliveryPoint)?.id
        }.toSet()

    fun isCleared(
        route: List<Position>,
        visitedIds: Set<Int>,
        board: Board,
    ): Boolean {
        if (route.isEmpty()) return false
        if (board.cellAt(route.last())?.type != CellType.Goal) return false

        return visitedIds.size == board.deliveryPoints.size
    }
}
