package com.example.nonogram.data.repository

import com.example.nonogram.data.local.PuzzleDao
import com.example.nonogram.data.model.CellState
import com.example.nonogram.data.model.Puzzle
import com.example.nonogram.data.model.PuzzleCategory
import com.example.nonogram.data.model.encodeGrid
import com.example.nonogram.data.remote.NonogramApiService
import kotlinx.coroutines.flow.Flow

private const val VISIBLE_SIZE = 5
private const val TARGET_UNCLEARED = 10

class PuzzleRepository(
    private val dao: PuzzleDao,
    private val api: NonogramApiService,
    private val builtinLoader: com.example.nonogram.data.local.BuiltinPuzzleLoader,
) {
    fun observeActive(category: PuzzleCategory): Flow<List<Puzzle>> =
        dao.observeActive(category.rows)

    fun observeCleared(): Flow<List<Puzzle>> = dao.observeCleared()

    fun observeClearedCount(): Flow<Int> = dao.observeClearedCount()

    suspend fun refreshList(category: PuzzleCategory) {
        val rows = category.rows
        repeat(2) {
            if (dao.countUncleared(rows) < TARGET_UNCLEARED) {
                runCatching { fetchAndInsert(rows) }
            }
        }
        if (dao.countAll(rows) == 0) seedBuiltins(rows)
        fillVisibleSlots(rows)
    }

    suspend fun getPuzzleForGame(id: Int): Puzzle {
        val cached = dao.findById(id)
        if (id < 0) return cached ?: error("Builtin puzzle $id not found")
        if (cached != null && cached.solutionJson.isNotEmpty()) return cached

        val dto = api.getPuzzle(id)
        val puzzle = Puzzle(
            id = dto.id,
            title = dto.title,
            rows = dto.rows,
            cols = dto.cols,
            solutionJson = encodeSolution(dto.solution),
            isCleared = cached?.isCleared ?: false,
            isUnlocked = cached?.isUnlocked ?: false,
            isVisible = cached?.isVisible ?: false,
            progressJson = cached?.progressJson ?: "",
        )
        dao.upsert(puzzle)
        return puzzle
    }

    suspend fun markCleared(id: Int) {
        val rows = dao.findById(id)?.rows ?: run {
            dao.markCleared(id)
            return
        }
        dao.markCleared(id)
        if (dao.countBuffer(rows) < VISIBLE_SIZE) runCatching { fetchAndInsert(rows) }
        fillVisibleSlots(rows)
    }

    suspend fun resetCache() = dao.deleteAll()

    suspend fun unlockPuzzle(id: Int) = dao.unlockPuzzle(id)

    suspend fun saveProgress(id: Int, grid: List<List<CellState>>) =
        dao.saveProgress(id, encodeGrid(grid))

    private suspend fun fillVisibleSlots(rows: Int) {
        val needed = VISIBLE_SIZE - dao.countActive(rows)
        if (needed > 0) {
            val ids = dao.getBufferIds(needed, rows)
            if (ids.isNotEmpty()) dao.markVisible(ids)
        }
    }

    private suspend fun fetchAndInsert(rows: Int) {
        val page = dao.countAll(rows) / VISIBLE_SIZE
        val stubs = api.getPuzzles(rows = rows, page = page, size = VISIBLE_SIZE).map { dto ->
            Puzzle(
                id = dto.id, title = dto.title, rows = dto.rows, cols = dto.cols,
                solutionJson = "", isVisible = false,
            )
        }
        dao.insertStubs(stubs)
    }

    private suspend fun seedBuiltins(rows: Int) {
        builtinLoader.loadByRows(rows).forEach { dao.upsert(it) }
    }

    private fun encodeSolution(solution: List<List<Int>>): String =
        "[" + solution.joinToString(",") { row -> "[" + row.joinToString(",") + "]" } + "]"
}
