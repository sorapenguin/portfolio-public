package com.example.nonogram.domain

import com.example.nonogram.data.model.CellState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PuzzleLogicTest {

    // Heart 5×5 — 論理一意解の正しいパズル
    private val heartSolution = listOf(
        listOf(0, 1, 0, 1, 0),
        listOf(1, 1, 1, 1, 1),
        listOf(1, 1, 1, 1, 1),
        listOf(0, 1, 1, 1, 0),
        listOf(0, 0, 1, 0, 0),
    )
    private val heartRowHints = listOf(listOf(1, 1), listOf(5), listOf(5), listOf(3), listOf(1))
    private val heartColHints = listOf(listOf(2), listOf(4), listOf(4), listOf(4), listOf(2))

    private fun solutionToGrid(solution: List<List<Int>>): List<List<CellState>> =
        solution.map { row -> row.map { if (it == 1) CellState.FILLED else CellState.EMPTY } }

    // ── 正解グリッドの検証 ─────────────────────────────────────────────────────

    @Test
    fun `correct Heart grid returns true`() {
        assertTrue(verifySolution(solutionToGrid(heartSolution), heartRowHints, heartColHints))
    }

    @Test
    fun `single 1x1 filled cell is verified correctly`() {
        val grid = listOf(listOf(CellState.FILLED))
        assertTrue(verifySolution(grid, listOf(listOf(1)), listOf(listOf(1))))
    }

    @Test
    fun `single 1x1 empty cell is verified correctly`() {
        val grid = listOf(listOf(CellState.EMPTY))
        assertTrue(verifySolution(grid, listOf(listOf(0)), listOf(listOf(0))))
    }

    // ── 不正グリッドの検出 ─────────────────────────────────────────────────────

    @Test
    fun `grid with one extra FILLED cell returns false`() {
        val wrong = solutionToGrid(heartSolution).mapIndexed { r, row ->
            if (r == 0) row.toMutableList().also { it[0] = CellState.FILLED } else row
        }
        assertFalse(verifySolution(wrong, heartRowHints, heartColHints))
    }

    @Test
    fun `all-empty grid returns false`() {
        val grid = List(5) { List(5) { CellState.EMPTY } }
        assertFalse(verifySolution(grid, heartRowHints, heartColHints))
    }

    @Test
    fun `all-FILLED grid returns false for Heart hints`() {
        val grid = List(5) { List(5) { CellState.FILLED } }
        assertFalse(verifySolution(grid, heartRowHints, heartColHints))
    }

    // ── ヒント不正入力 ────────────────────────────────────────────────────────

    @Test
    fun `empty rowHints returns false`() {
        assertFalse(verifySolution(solutionToGrid(heartSolution), emptyList(), heartColHints))
    }

    @Test
    fun `empty colHints returns false`() {
        assertFalse(verifySolution(solutionToGrid(heartSolution), heartRowHints, emptyList()))
    }

    // ── CellState の区別 ──────────────────────────────────────────────────────

    @Test
    fun `MARKED cells are not counted as FILLED`() {
        // 正解セルをすべて MARKED に変えると検証は失敗するべき
        val marked = solutionToGrid(heartSolution).map { row ->
            row.map { if (it == CellState.FILLED) CellState.MARKED else it }
        }
        assertFalse(verifySolution(marked, heartRowHints, heartColHints))
    }

    @Test
    fun `mixed FILLED and MARKED produces false for Heart`() {
        // row 0 の1つだけ MARKED に変える
        val mixed = solutionToGrid(heartSolution).mapIndexed { r, row ->
            if (r == 0) row.toMutableList().also { it[1] = CellState.MARKED } else row
        }
        assertFalse(verifySolution(mixed, heartRowHints, heartColHints))
    }

    // ── Cross パズル（対称形）────────────────────────────────────────────────

    @Test
    fun `correct Cross grid returns true`() {
        val cross = listOf(
            listOf(0, 0, 1, 0, 0),
            listOf(0, 0, 1, 0, 0),
            listOf(1, 1, 1, 1, 1),
            listOf(0, 0, 1, 0, 0),
            listOf(0, 0, 1, 0, 0),
        )
        val rowHints = listOf(listOf(1), listOf(1), listOf(5), listOf(1), listOf(1))
        val colHints = listOf(listOf(1), listOf(1), listOf(5), listOf(1), listOf(1))
        assertTrue(verifySolution(solutionToGrid(cross), rowHints, colHints))
    }
}
