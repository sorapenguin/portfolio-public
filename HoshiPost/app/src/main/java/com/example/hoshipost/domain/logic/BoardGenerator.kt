package com.example.hoshipost.domain.logic

import com.example.hoshipost.domain.model.Board
import com.example.hoshipost.domain.model.Cell
import com.example.hoshipost.domain.model.CellType
import com.example.hoshipost.domain.model.DeliveryPoint
import com.example.hoshipost.domain.model.Difficulty
import com.example.hoshipost.domain.model.Position
import kotlin.random.Random

class BoardGenerator(
    private val bfs: BfsPathFinder = BfsPathFinder(),
    private val optimizer: RouteOptimizer = RouteOptimizer(bfs),
) {
    companion object {
        private const val MAX_ATTEMPTS = 1000
        private val DELIVERY_LABELS = listOf("家", "店", "猫", "花", "公")
    }

    fun generate(seed: Long, difficulty: Difficulty): Board {
        val rng = Random(seed)
        var wallRatio = difficulty.wallRatioRange.start

        for (attempt in 0 until MAX_ATTEMPTS) {
            if (attempt > 0 && attempt % 100 == 0) {
                wallRatio = maxOf(0.0, wallRatio - 0.02)
            }

            val board = tryGenerate(rng, seed, difficulty, wallRatio)
            if (board != null) return board
        }

        return tryGenerate(rng, seed, difficulty, 0.0)
            ?: generateMinimal(seed, difficulty)
    }

    private fun tryGenerate(
        rng: Random,
        seed: Long,
        difficulty: Difficulty,
        wallRatio: Double,
    ): Board? {
        val size = difficulty.boardSize
        val totalCells = size * size
        val wallCount = (totalCells * wallRatio).toInt()
        val requiredOpenCells = 2 + difficulty.deliveryCount

        val allPositions = (0 until size).flatMap { row ->
            (0 until size).map { col -> Position(row, col) }
        }.shuffled(rng)

        if (allPositions.size < wallCount + requiredOpenCells) return null

        val wallPositions = allPositions.take(wallCount).toSet()
        val openPositions = allPositions.drop(wallCount)

        if (openPositions.size < requiredOpenCells) return null

        val start = openPositions[0]
        val goal = openPositions[1]

        if (start.isAdjacentTo(goal)) return null

        val deliveries = openPositions.drop(2)
            .take(difficulty.deliveryCount)
            .mapIndexed { index, position ->
                DeliveryPoint(
                    id = index + 1,
                    position = position,
                    label = DELIVERY_LABELS[index % DELIVERY_LABELS.size],
                )
            }

        val cells = buildCells(size, wallPositions, start, goal, deliveries)
        val partialBoard = Board(
            width = size,
            height = size,
            cells = cells,
            start = start,
            goal = goal,
            deliveryPoints = deliveries,
            optimalSteps = 0,
            seed = seed,
            difficulty = difficulty,
        )

        val targets = deliveries.map { it.position } + goal
        for (target in targets) {
            if (!bfs.canReach(partialBoard, start, target)) return null
        }

        val optimalSteps = optimizer.findOptimalSteps(partialBoard, start, deliveries, goal)
            ?: return null

        if (optimalSteps !in difficulty.optimalStepsRange) return null

        return partialBoard.copy(optimalSteps = optimalSteps)
    }

    private fun buildCells(
        size: Int,
        wallPositions: Set<Position>,
        start: Position,
        goal: Position,
        deliveries: List<DeliveryPoint>,
    ): List<List<Cell>> {
        val deliveryMap = deliveries.associateBy { it.position }

        return (0 until size).map { row ->
            (0 until size).map { col ->
                val position = Position(row, col)
                val type: CellType = when {
                    position == start -> CellType.Start
                    position == goal -> CellType.Goal
                    position in wallPositions -> CellType.Wall
                    position in deliveryMap -> {
                        val delivery = deliveryMap.getValue(position)
                        CellType.DeliveryPoint(delivery.id, delivery.label)
                    }
                    else -> CellType.Road
                }

                Cell(position, type)
            }
        }
    }

    private fun generateMinimal(seed: Long, difficulty: Difficulty): Board {
        val size = difficulty.boardSize
        val start = Position(0, 0)
        val goal = Position(size - 1, size - 1)
        val deliveries = listOf(
            DeliveryPoint(1, Position(0, size - 1), "家"),
            DeliveryPoint(2, Position(size - 1, 0), "店"),
            DeliveryPoint(3, Position(size / 2, size / 2), "猫"),
            DeliveryPoint(4, Position(size / 2, size - 1), "花"),
        ).take(difficulty.deliveryCount)

        val cells = buildCells(size, emptySet(), start, goal, deliveries)
        val board = Board(
            width = size,
            height = size,
            cells = cells,
            start = start,
            goal = goal,
            deliveryPoints = deliveries,
            optimalSteps = 0,
            seed = seed,
            difficulty = difficulty,
        )
        val optimalSteps = optimizer.findOptimalSteps(board, start, deliveries, goal) ?: 0

        return board.copy(optimalSteps = optimalSteps)
    }
}
