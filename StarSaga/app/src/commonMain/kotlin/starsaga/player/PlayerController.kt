package starsaga.player

import starsaga.map.GridCell
import starsaga.map.MapData
import starsaga.map.PathFinder

class PlayerController(
    startCell: GridCell,
) {
    var currentCell: GridCell = startCell
        private set
    var targetCell: GridCell? = null
        private set
    var path: List<GridCell> = emptyList()
        private set

    private var stepElapsedSeconds = 0.0

    fun moveTo(map: MapData, destination: GridCell) {
        if (!map.isPassable(destination.col, destination.row)) return
        val nextPath = PathFinder.findPath(map, currentCell, destination)
        if (nextPath.isEmpty() && destination != currentCell) return
        targetCell = destination
        path = nextPath
        stepElapsedSeconds = 0.0
    }

    fun warpTo(cell: GridCell) {
        currentCell = cell
        clearMovement()
    }

    fun clearMovement() {
        targetCell = null
        path = emptyList()
        stepElapsedSeconds = 0.0
    }

    fun update(deltaSeconds: Double) {
        if (path.isEmpty()) {
            targetCell = null
            return
        }
        stepElapsedSeconds += deltaSeconds
        if (stepElapsedSeconds < STEP_SECONDS) return

        stepElapsedSeconds = 0.0
        currentCell = path.first()
        path = path.drop(1)
        if (path.isEmpty()) targetCell = null
    }

    companion object {
        private const val STEP_SECONDS = 0.12
    }
}
