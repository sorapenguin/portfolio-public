package com.example.hoshipost.domain.logic

import com.example.hoshipost.domain.model.Board
import com.example.hoshipost.domain.model.Position

class BfsPathFinder {

    fun shortestDistance(board: Board, from: Position, to: Position): Int? {
        if (from == to) return 0
        if (!board.isPassable(from) || !board.isPassable(to)) return null

        val visited = mutableSetOf(from)
        val queue = ArrayDeque<Pair<Position, Int>>()
        queue.add(from to 0)

        while (queue.isNotEmpty()) {
            val (current, distance) = queue.removeFirst()

            for (neighbor in current.neighbors()) {
                if (neighbor in visited) continue
                if (!board.isPassable(neighbor)) continue
                if (neighbor == to) return distance + 1

                visited.add(neighbor)
                queue.add(neighbor to distance + 1)
            }
        }

        return null
    }

    fun canReach(board: Board, from: Position, to: Position): Boolean =
        shortestDistance(board, from, to) != null
}
