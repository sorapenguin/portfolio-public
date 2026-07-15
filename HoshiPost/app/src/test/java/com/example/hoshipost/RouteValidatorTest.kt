package com.example.hoshipost

import com.example.hoshipost.domain.logic.RouteValidator
import com.example.hoshipost.domain.model.Board
import com.example.hoshipost.domain.model.Cell
import com.example.hoshipost.domain.model.CellType
import com.example.hoshipost.domain.model.DeliveryPoint
import com.example.hoshipost.domain.model.Difficulty
import com.example.hoshipost.domain.model.Position
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteValidatorTest {

    private val start = Position(0, 0)
    private val road = Position(0, 1)
    private val wall = Position(1, 0)
    private val farAway = Position(2, 2)
    private val dp1 = DeliveryPoint(1, Position(0, 2), "A")
    private val dp2 = DeliveryPoint(2, Position(1, 2), "B")
    private val goal = Position(2, 2)
    private val board = buildBoard()

    @Test
    fun nextRoute_cannotStartOutsideStartCell() {
        assertNull(RouteValidator.nextRoute(emptyList(), road, board))
    }

    @Test
    fun nextRoute_canStartFromStartCell() {
        assertEquals(listOf(start), RouteValidator.nextRoute(emptyList(), start, board))
    }

    @Test
    fun nextRoute_cannotMoveToWall() {
        assertNull(RouteValidator.nextRoute(listOf(start), wall, board))
    }

    @Test
    fun nextRoute_cannotMoveToNonAdjacentCell() {
        assertNull(RouteValidator.nextRoute(listOf(start), farAway, board))
    }

    @Test
    fun nextRoute_movingToPreviousCellUndoesLastStep() {
        val route = listOf(start, road, dp1.position)

        assertEquals(listOf(start, road), RouteValidator.nextRoute(route, road, board))
    }

    @Test
    fun isCleared_returnsTrueWhenAllDeliveriesVisitedAndRouteEndsAtGoal() {
        val route = listOf(start, road, dp1.position, dp2.position, goal)
        val visitedIds = RouteValidator.computeVisitedIds(route, board)

        assertTrue(RouteValidator.isCleared(route, visitedIds, board))
    }

    @Test
    fun isCleared_returnsFalseWhenGoalReachedWithoutAllDeliveries() {
        val route = listOf(start, road, goal)

        assertFalse(RouteValidator.isCleared(route, emptySet(), board))
    }

    @Test
    fun computeVisitedIds_recomputesAfterUndo() {
        val fullRoute = listOf(start, road, dp1.position, dp2.position)
        val undoneRoute = fullRoute.dropLast(1)

        assertEquals(setOf(1, 2), RouteValidator.computeVisitedIds(fullRoute, board))
        assertEquals(setOf(1), RouteValidator.computeVisitedIds(undoneRoute, board))
    }

    private fun buildBoard(): Board {
        val deliveries = listOf(dp1, dp2)
        val deliveriesByPosition = deliveries.associateBy { it.position }
        val cells = (0 until 3).map { row ->
            (0 until 3).map { col ->
                val position = Position(row, col)
                val type = when {
                    position == start -> CellType.Start
                    position == goal -> CellType.Goal
                    position == wall -> CellType.Wall
                    position in deliveriesByPosition -> {
                        val delivery = deliveriesByPosition.getValue(position)
                        CellType.DeliveryPoint(delivery.id, delivery.label)
                    }
                    else -> CellType.Road
                }
                Cell(position, type)
            }
        }
        return Board(
            width = 3,
            height = 3,
            cells = cells,
            start = start,
            goal = goal,
            deliveryPoints = deliveries,
            optimalSteps = 0,
            seed = 0L,
            difficulty = Difficulty.NORMAL,
        )
    }
}
