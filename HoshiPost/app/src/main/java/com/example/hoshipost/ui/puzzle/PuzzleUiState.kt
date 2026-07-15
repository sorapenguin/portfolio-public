package com.example.hoshipost.ui.puzzle

import com.example.hoshipost.domain.model.Board
import com.example.hoshipost.domain.model.Position

data class PuzzleUiState(
    val board: Board,
    val route: List<Position> = emptyList(),
    val visitedDeliveryIds: Set<Int> = emptySet(),
    val isCleared: Boolean = false,
    val stars: Int? = null,
) {
    val playerSteps: Int get() = maxOf(0, route.size - 1)
    val currentPosition: Position? get() = route.lastOrNull()
    val deliveredCount: Int get() = visitedDeliveryIds.size
    val totalDeliveries: Int get() = board.deliveryPoints.size
}
