package com.example.hoshipost

import com.example.hoshipost.domain.logic.RouteOptimizer
import com.example.hoshipost.domain.model.Board
import com.example.hoshipost.domain.model.Cell
import com.example.hoshipost.domain.model.CellType
import com.example.hoshipost.domain.model.DeliveryPoint
import com.example.hoshipost.domain.model.Difficulty
import com.example.hoshipost.domain.model.Position
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouteOptimizerTest {

    private val optimizer = RouteOptimizer()

    @Test
    fun findOptimalSteps_returnsShortestRouteThroughAllDeliveryPoints() {
        val start = Position(0, 0)
        val goal = Position(0, 6)
        val deliveries = listOf(
            DeliveryPoint(1, Position(0, 1), "A"),
            DeliveryPoint(2, Position(0, 3), "B"),
            DeliveryPoint(3, Position(0, 5), "C"),
        )
        val board = buildBoard(7, start, goal, deliveries)

        assertEquals(6, optimizer.findOptimalSteps(board, start, deliveries, goal))
    }

    @Test
    fun findOptimalSteps_returnsNullWhenDeliveryPointIsUnreachable() {
        val start = Position(0, 0)
        val goal = Position(2, 2)
        val deliveries = listOf(DeliveryPoint(1, Position(1, 1), "A"))
        val board = buildBoard(
            size = 3,
            start = start,
            goal = goal,
            deliveries = deliveries,
            wallPositions = setOf(Position(0, 1), Position(1, 0), Position(1, 2), Position(2, 1)),
        )

        assertNull(optimizer.findOptimalSteps(board, start, deliveries, goal))
    }

    @Test
    fun findOptimalSteps_withNoDeliveryPointsReturnsStartToGoalDistance() {
        val start = Position(0, 0)
        val goal = Position(6, 6)
        val board = buildBoard(7, start, goal, emptyList())

        assertEquals(12, optimizer.findOptimalSteps(board, start, emptyList(), goal))
    }

    private fun buildBoard(
        size: Int,
        start: Position,
        goal: Position,
        deliveries: List<DeliveryPoint>,
        wallPositions: Set<Position> = emptySet(),
    ): Board {
        val deliveriesByPosition = deliveries.associateBy { it.position }
        val cells = (0 until size).map { row ->
            (0 until size).map { col ->
                val position = Position(row, col)
                val type = when {
                    position == start -> CellType.Start
                    position == goal -> CellType.Goal
                    position in wallPositions -> CellType.Wall
                    position in deliveriesByPosition -> {
                        val delivery = deliveriesByPosition.getValue(position)
                        CellType.DeliveryPoint(delivery.id, delivery.label)
                    }
                    else -> CellType.Road
                }
                Cell(position, type)
            }
        }
        return Board(size, size, cells, start, goal, deliveries, 0, 0L, Difficulty.NORMAL)
    }
}
