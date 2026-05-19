package com.example.nonogram.domain

import com.example.nonogram.data.model.CellState
import com.example.nonogram.data.model.computeHint

internal fun verifySolution(
    grid: List<List<CellState>>,
    rowHints: List<List<Int>>,
    colHints: List<List<Int>>,
): Boolean {
    if (rowHints.isEmpty() || colHints.isEmpty()) return false
    grid.forEachIndexed { r, row ->
        if (computeHint(row.map { if (it == CellState.FILLED) 1 else 0 }) != rowHints[r]) return false
    }
    (0 until (grid.firstOrNull()?.size ?: 0)).forEach { c ->
        if (computeHint(grid.map { if (it[c] == CellState.FILLED) 1 else 0 }) != colHints[c]) return false
    }
    return true
}
