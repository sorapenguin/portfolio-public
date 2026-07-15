package islanddev.game

import kotlin.math.abs

data class GridPoint(val col: Int, val row: Int)

object PathFinder {
    private data class Node(
        val point: GridPoint,
        val g: Int,
        val f: Int
    )

    fun findPath(
        start: GridPoint,
        goal: GridPoint,
        width: Int,
        height: Int,
        isPassable: (GridPoint) -> Boolean
    ): List<GridPoint> {
        if (start == goal) return emptyList()
        if (!inside(goal, width, height) || !isPassable(goal)) return emptyList()

        val open = mutableListOf(
            Node(start, g = 0, f = heuristic(start, goal))
        )
        val cameFrom = mutableMapOf<GridPoint, GridPoint>()
        val bestCost = mutableMapOf(start to 0)

        while (open.isNotEmpty()) {
            val currentIndex = open.indices.minBy { open[it].f }
            val current = open.removeAt(currentIndex)
            if (current.point == goal) {
                return reconstruct(cameFrom, start, goal)
            }

            for (next in neighbors(current.point)) {
                if (!inside(next, width, height) || !isPassable(next)) continue
                val nextCost = current.g + 1
                if (nextCost >= bestCost.getOrDefault(next, Int.MAX_VALUE)) continue

                cameFrom[next] = current.point
                bestCost[next] = nextCost
                open += Node(next, nextCost, nextCost + heuristic(next, goal))
            }
        }
        return emptyList()
    }

    private fun reconstruct(
        cameFrom: Map<GridPoint, GridPoint>,
        start: GridPoint,
        goal: GridPoint
    ): List<GridPoint> {
        val reversed = mutableListOf<GridPoint>()
        var current = goal
        while (current != start) {
            reversed += current
            current = cameFrom[current] ?: return emptyList()
        }
        return reversed.asReversed()
    }

    private fun heuristic(a: GridPoint, b: GridPoint): Int =
        abs(a.col - b.col) + abs(a.row - b.row)

    private fun neighbors(point: GridPoint): List<GridPoint> = listOf(
        GridPoint(point.col + 1, point.row),
        GridPoint(point.col - 1, point.row),
        GridPoint(point.col, point.row + 1),
        GridPoint(point.col, point.row - 1)
    )

    private fun inside(point: GridPoint, width: Int, height: Int): Boolean =
        point.col in 0 until width && point.row in 0 until height
}
