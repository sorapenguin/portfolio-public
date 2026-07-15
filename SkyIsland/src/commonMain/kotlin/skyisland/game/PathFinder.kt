package skyisland.game

object PathFinder {
    private const val MAX_SEARCH = 500

    fun find(start: Cell, target: Cell, passable: (Cell) -> Boolean): List<Cell> {
        if (start == target) return emptyList()
        val queue = ArrayDeque<Cell>()
        val from = mutableMapOf<Cell, Cell?>()
        queue += start
        from[start] = null
        while (queue.isNotEmpty()) {
            if (from.size > MAX_SEARCH) return emptyList()
            val current = queue.removeFirst()
            for (next in current.neighbors()) {
                if (next !in from && passable(next)) {
                    from[next] = current
                    if (next == target) return buildPath(from, target).drop(1)
                    queue += next
                }
            }
        }
        return emptyList()
    }

    private fun buildPath(from: Map<Cell, Cell?>, target: Cell): List<Cell> {
        val path = mutableListOf<Cell>()
        var current: Cell? = target
        while (current != null) {
            path += current
            current = from[current]
        }
        return path.reversed()
    }
}
