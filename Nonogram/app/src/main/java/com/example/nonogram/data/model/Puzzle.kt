package com.example.nonogram.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "puzzles")
data class Puzzle(
    @PrimaryKey val id: Int,
    val title: String,
    val rows: Int,
    val cols: Int,
    /** JSON-encoded 2D array: [[1,0,1],[0,1,0],...] */
    val solutionJson: String,
    val isCleared: Boolean = false,
    val isUnlocked: Boolean = false,
    /** true = 画面に表示中、false = バッファ（非表示） */
    val isVisible: Boolean = false,
    /** JSON-encoded grid progress: [[0,1,2],...] where 0=EMPTY,1=FILLED,2=MARKED */
    val progressJson: String = "",
)

fun Puzzle.toHints(): Pair<List<List<Int>>, List<List<Int>>> {
    val solution = parseSolution(solutionJson)
    val rowHints = solution.map { computeHint(it) }
    val colHints = (0 until cols).map { c -> computeHint(solution.map { it[c] }) }
    return rowHints to colHints
}

fun encodeGrid(grid: List<List<CellState>>): String =
    "[" + grid.joinToString(",") { row ->
        "[" + row.joinToString(",") { cell ->
            when (cell) { CellState.FILLED -> "1"; CellState.MARKED -> "2"; else -> "0" }
        } + "]"
    } + "]"

fun decodeGrid(json: String, rows: Int, cols: Int): List<List<CellState>> {
    if (json.isBlank()) return List(rows) { List(cols) { CellState.EMPTY } }
    return json.trim().removePrefix("[[").removeSuffix("]]")
        .split("],[")
        .map { row ->
            row.split(",").map { cell ->
                when (cell.trim()) { "1" -> CellState.FILLED; "2" -> CellState.MARKED; else -> CellState.EMPTY }
            }
        }
}

internal fun parseSolution(json: String): List<List<Int>> =
    json.trim().removePrefix("[[").removeSuffix("]]")
        .split("],[")
        .map { row -> row.split(",").map { it.trim().toInt() } }

fun computeHint(line: List<Int>): List<Int> {
    val hints = mutableListOf<Int>()
    var count = 0
    for (v in line) {
        if (v == 1) count++
        else if (count > 0) { hints.add(count); count = 0 }
    }
    if (count > 0) hints.add(count)
    return hints.ifEmpty { listOf(0) }
}
