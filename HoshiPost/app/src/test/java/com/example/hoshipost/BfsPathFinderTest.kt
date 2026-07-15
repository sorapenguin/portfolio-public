package com.example.hoshipost

import com.example.hoshipost.domain.logic.BfsPathFinder
import com.example.hoshipost.domain.model.Board
import com.example.hoshipost.domain.model.Cell
import com.example.hoshipost.domain.model.CellType
import com.example.hoshipost.domain.model.Difficulty
import com.example.hoshipost.domain.model.Position
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BfsPathFinderTest {

    private val bfs = BfsPathFinder()

    @Test
    fun shortestDistance_returnsManhattanDistanceWithoutWalls() {
        val board = buildBoard(size = 7)

        assertEquals(0, bfs.shortestDistance(board, Position(0, 0), Position(0, 0)))
        assertEquals(6, bfs.shortestDistance(board, Position(0, 0), Position(0, 6)))
        assertEquals(12, bfs.shortestDistance(board, Position(0, 0), Position(6, 6)))
    }

    @Test
    fun shortestDistance_returnsDetourDistanceAroundWall() {
        val board = buildBoard(
            size = 3,
            wallPositions = setOf(Position(1, 1)),
        )

        assertEquals(4, bfs.shortestDistance(board, Position(1, 0), Position(1, 2)))
    }

    @Test
    fun shortestDistance_returnsNullWhenTargetIsUnreachable() {
        val board = buildBoard(
            size = 3,
            wallPositions = setOf(Position(0, 1), Position(1, 0), Position(1, 2), Position(2, 1)),
        )

        assertNull(bfs.shortestDistance(board, Position(0, 0), Position(1, 1)))
    }

    @Test
    fun shortestDistance_returnsNullWhenFromOrToIsWall() {
        val wall = Position(0, 1)
        val board = buildBoard(size = 3, wallPositions = setOf(wall))

        assertNull(bfs.shortestDistance(board, wall, Position(0, 2)))
        assertNull(bfs.shortestDistance(board, Position(0, 0), wall))
    }

    private fun buildBoard(
        size: Int,
        wallRows: List<Int> = emptyList(),
        wallPositions: Set<Position> = emptySet(),
    ): Board {
        val walls = wallPositions + wallRows.flatMap { row ->
            (0 until size).map { col -> Position(row, col) }
        }
        val cells = (0 until size).map { row ->
            (0 until size).map { col ->
                val position = Position(row, col)
                Cell(
                    position = position,
                    type = if (position in walls) CellType.Wall else CellType.Road,
                )
            }
        }
        return Board(
            width = size,
            height = size,
            cells = cells,
            start = Position(0, 0),
            goal = Position(size - 1, size - 1),
            deliveryPoints = emptyList(),
            optimalSteps = 0,
            seed = 0L,
            difficulty = Difficulty.NORMAL,
        )
    }
}
