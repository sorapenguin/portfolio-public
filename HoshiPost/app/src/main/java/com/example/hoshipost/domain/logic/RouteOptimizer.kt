package com.example.hoshipost.domain.logic

import com.example.hoshipost.domain.model.Board
import com.example.hoshipost.domain.model.DeliveryPoint
import com.example.hoshipost.domain.model.Position

class RouteOptimizer(private val bfs: BfsPathFinder = BfsPathFinder()) {

    fun findOptimalSteps(
        board: Board,
        start: Position,
        deliveryPoints: List<DeliveryPoint>,
        goal: Position,
    ): Int? {
        val keyPositions = listOf(start) + deliveryPoints.map { it.position } + listOf(goal)
        val distMap = buildDistMap(board, keyPositions)

        return deliveryPoints.map { it.position }
            .permutations()
            .mapNotNull { permutation ->
                val route = listOf(start) + permutation + listOf(goal)
                var total = 0

                for (i in 0 until route.lastIndex) {
                    val distance = distMap[route[i] to route[i + 1]] ?: return@mapNotNull null
                    total += distance
                }

                total
            }
            .minOrNull()
    }

    private fun buildDistMap(
        board: Board,
        positions: List<Position>,
    ): Map<Pair<Position, Position>, Int?> {
        val map = mutableMapOf<Pair<Position, Position>, Int?>()

        for (from in positions) {
            for (to in positions) {
                if (from != to) {
                    map[from to to] = bfs.shortestDistance(board, from, to)
                }
            }
        }

        return map
    }
}

private fun <T> List<T>.permutations(): List<List<T>> {
    if (size <= 1) return listOf(this)

    val result = mutableListOf<List<T>>()
    forEachIndexed { index, element ->
        val rest = toMutableList().also { it.removeAt(index) }
        rest.permutations().forEach { permutation ->
            result.add(listOf(element) + permutation)
        }
    }

    return result
}
