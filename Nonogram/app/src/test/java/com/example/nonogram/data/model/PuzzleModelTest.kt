package com.example.nonogram.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PuzzleModelTest {

    // ── computeHint ───────────────────────────────────────────────────────────

    @Test
    fun `all zeros returns zero sentinel`() {
        assertEquals(listOf(0), computeHint(listOf(0, 0, 0)))
    }

    @Test
    fun `all ones returns full length as single hint`() {
        assertEquals(listOf(5), computeHint(listOf(1, 1, 1, 1, 1)))
    }

    @Test
    fun `alternating 1-0 returns individual counts`() {
        assertEquals(listOf(1, 1, 1), computeHint(listOf(1, 0, 1, 0, 1)))
    }

    @Test
    fun `two groups returns two counts`() {
        assertEquals(listOf(2, 3), computeHint(listOf(1, 1, 0, 1, 1, 1)))
    }

    @Test
    fun `trailing zeros do not add extra hint`() {
        assertEquals(listOf(2), computeHint(listOf(1, 1, 0, 0)))
    }

    @Test
    fun `leading zeros are ignored`() {
        assertEquals(listOf(3), computeHint(listOf(0, 0, 1, 1, 1)))
    }

    @Test
    fun `single filled cell returns single count`() {
        assertEquals(listOf(1), computeHint(listOf(1)))
    }

    @Test
    fun `single empty cell returns zero sentinel`() {
        assertEquals(listOf(0), computeHint(listOf(0)))
    }

    @Test
    fun `empty list returns zero sentinel`() {
        assertEquals(listOf(0), computeHint(emptyList()))
    }

    @Test
    fun `Heart row 0 hint is computed correctly`() {
        // row: 0 1 0 1 0 → groups of size 1, 1
        assertEquals(listOf(1, 1), computeHint(listOf(0, 1, 0, 1, 0)))
    }

    @Test
    fun `Heart col 1 hint is computed correctly`() {
        // col 1: 1 1 1 1 0 → group of size 4
        assertEquals(listOf(4), computeHint(listOf(1, 1, 1, 1, 0)))
    }

    // ── PuzzleCategory ────────────────────────────────────────────────────────

    @Test
    fun `MINI has rows 5`() {
        assertEquals(5, PuzzleCategory.MINI.rows)
    }

    @Test
    fun `NORMAL has rows 10`() {
        assertEquals(10, PuzzleCategory.NORMAL.rows)
    }

    @Test
    fun `LARGE has rows 15`() {
        assertEquals(15, PuzzleCategory.LARGE.rows)
    }

    @Test
    fun `all categories have distinct rows values`() {
        val rowValues = PuzzleCategory.entries.map { it.rows }
        assertEquals(rowValues.size, rowValues.toSet().size)
    }

    @Test
    fun `MINI label contains 5`() {
        assertTrue(PuzzleCategory.MINI.label.contains("5"))
    }

    @Test
    fun `NORMAL label contains 10`() {
        assertTrue(PuzzleCategory.NORMAL.label.contains("10"))
    }

    @Test
    fun `LARGE label contains 15`() {
        assertTrue(PuzzleCategory.LARGE.label.contains("15"))
    }

    @Test
    fun `entries returns all three categories`() {
        assertEquals(3, PuzzleCategory.entries.size)
    }

    // ── encodeGrid / decodeGrid ───────────────────────────────────────────────

    @Test
    fun `encode then decode round-trips for all CellState values`() {
        val grid = listOf(
            listOf(CellState.FILLED, CellState.EMPTY, CellState.MARKED),
            listOf(CellState.MARKED, CellState.FILLED, CellState.EMPTY),
        )
        assertEquals(grid, decodeGrid(encodeGrid(grid), 2, 3))
    }

    @Test
    fun `blank progressJson decodes to all-EMPTY grid`() {
        val decoded = decodeGrid("", 3, 3)
        val expected = List(3) { List(3) { CellState.EMPTY } }
        assertEquals(expected, decoded)
    }

    @Test
    fun `FILLED encodes as 1`() {
        assertTrue(encodeGrid(listOf(listOf(CellState.FILLED))).contains("1"))
    }

    @Test
    fun `MARKED encodes as 2`() {
        assertTrue(encodeGrid(listOf(listOf(CellState.MARKED))).contains("2"))
    }

    @Test
    fun `EMPTY encodes as 0`() {
        assertTrue(encodeGrid(listOf(listOf(CellState.EMPTY))).contains("0"))
    }

    @Test
    fun `encoded output is valid JSON array structure`() {
        val grid = listOf(listOf(CellState.FILLED, CellState.EMPTY))
        val encoded = encodeGrid(grid)
        assertTrue(encoded.startsWith("[["))
        assertTrue(encoded.endsWith("]]"))
    }
}
