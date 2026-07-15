package com.example.hoshipost

import com.example.hoshipost.domain.logic.BfsPathFinder
import com.example.hoshipost.domain.logic.BoardGenerator
import com.example.hoshipost.domain.model.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardGeneratorTest {

    private val generator = BoardGenerator()
    private val bfs = BfsPathFinder()

    @Test
    fun generate_normalCreatesValidBoard() {
        val board = generator.generate(seed = 1L, difficulty = Difficulty.NORMAL)

        assertEquals(7, board.width)
        assertEquals(7, board.height)
        assertEquals(3, board.deliveryPoints.size)
        assertTrue(board.optimalSteps in 14..30)
    }

    @Test
    fun generate_normalCreatesReachableBoard() {
        val board = generator.generate(seed = 1L, difficulty = Difficulty.NORMAL)

        for (deliveryPoint in board.deliveryPoints) {
            assertTrue(bfs.canReach(board, board.start, deliveryPoint.position))
            assertTrue(bfs.canReach(board, deliveryPoint.position, board.goal))
        }
    }

    @Test
    fun generate_sameSeedReturnsSameBoard() {
        val first = generator.generate(seed = 1L, difficulty = Difficulty.NORMAL)
        val second = generator.generate(seed = 1L, difficulty = Difficulty.NORMAL)

        assertEquals(first, second)
    }

    @Test
    fun generate_differentSeedsUsuallyReturnDifferentBoards() {
        val first = generator.generate(seed = 1L, difficulty = Difficulty.NORMAL)
        val second = generator.generate(seed = 2L, difficulty = Difficulty.NORMAL)

        assertNotEquals(first, second)
    }

    @Test
    fun generate_easyCreatesValidBoard() {
        val board = generator.generate(seed = 1L, difficulty = Difficulty.EASY)

        assertTrue(board.width in listOf(5, 7))
        assertEquals(board.width, board.height)
        assertEquals(2, board.deliveryPoints.size)
        assertTrue(board.optimalSteps in 8..18)
    }
}
