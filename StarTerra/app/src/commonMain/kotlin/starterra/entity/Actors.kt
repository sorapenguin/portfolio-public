package starterra.entity

import starterra.world.GridCell
import starterra.world.SpikeMap

data class ActorState(
    val name: String,
    var cell: GridCell,
    val footOffsetY: Double = SpikeMap.TILE_SIZE.toDouble(),
) {
    val footY: Double get() = cell.row * SpikeMap.TILE_SIZE + footOffsetY
}

class PlayerController(start: GridCell) {
    var cell: GridCell = start
        private set
    var isMoving: Boolean = false
        private set

    /** One input is one atomic grid step; inputs during a step are ignored. */
    fun tryMove(direction: GridCell, map: SpikeMap): Boolean {
        if (isMoving) return false
        val next = cell + direction
        if (!map.isPassable(next)) return false
        isMoving = true
        cell = next
        isMoving = false
        return true
    }

    fun place(cell: GridCell) {
        isMoving = false
        this.cell = cell
    }
}
