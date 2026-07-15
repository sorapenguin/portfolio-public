package com.example.hoshipost

import com.example.hoshipost.domain.model.Position
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PositionTest {

    @Test
    fun isAdjacentTo_returnsTrueForOrthogonalNeighbors() {
        assertTrue(Position(0, 0).isAdjacentTo(Position(0, 1)))
        assertTrue(Position(0, 0).isAdjacentTo(Position(1, 0)))
    }

    @Test
    fun isAdjacentTo_returnsFalseForDiagonalOrSamePosition() {
        assertFalse(Position(0, 0).isAdjacentTo(Position(1, 1)))
        assertFalse(Position(0, 0).isAdjacentTo(Position(0, 0)))
    }

    @Test
    fun neighbors_returnsFourPositionsIncludingOutOfBounds() {
        val neighbors = Position(1, 1).neighbors()

        assertEquals(4, neighbors.size)
        assertTrue(Position(0, 0).neighbors().contains(Position(-1, 0)))
    }
}
