package starsaga.map

import kotlin.collections.ArrayDeque

object PathFinder {
    fun findPath(map: MapData, start: GridCell, goal: GridCell): List<GridCell> {
        if (start == goal) return emptyList()
        if (!map.isPassable(goal.col, goal.row)) return emptyList()

        val queue = ArrayDeque<GridCell>()
        val visited = mutableSetOf(start)
        val cameFrom = mutableMapOf<GridCell, GridCell>()
        queue.add(start)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            for (next in neighbors(current)) {
                if (next in visited || !map.isPassable(next.col, next.row)) continue
                visited += next
                cameFrom[next] = current
                if (next == goal) return reconstructPath(start, goal, cameFrom)
                queue.add(next)
            }
        }

        return emptyList()
    }

    private fun neighbors(cell: GridCell): List<GridCell> = listOf(
        GridCell(cell.col + 1, cell.row),
        GridCell(cell.col - 1, cell.row),
        GridCell(cell.col, cell.row + 1),
        GridCell(cell.col, cell.row - 1),
    )

    private fun reconstructPath(
        start: GridCell,
        goal: GridCell,
        cameFrom: Map<GridCell, GridCell>,
    ): List<GridCell> {
        val reversed = mutableListOf<GridCell>()
        var current = goal
        while (current != start) {
            reversed += current
            current = cameFrom[current] ?: return emptyList()
        }
        return reversed.asReversed()
    }
}
