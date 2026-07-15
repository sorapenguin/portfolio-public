package islanddev.scene

import islanddev.data.GameData
import islanddev.game.GridPoint

object StepMove {
    fun destination(origin: GridPoint, dx: Int, dy: Int): GridPoint =
        GridPoint(origin.col + dx, origin.row + dy)

    fun canMoveTo(
        destination: GridPoint,
        unlockedZoneIds: Set<Int>,
        columns: Int = GridScene.COLUMNS,
        rows: Int = GridScene.ROWS
    ): Boolean =
        destination.col in 0 until columns &&
            destination.row in 0 until rows &&
            GameData.columnToZone(destination.col) in unlockedZoneIds
}

object MovementInputConfig {
    const val ENABLE_TAP_MOVE = true
}

object StepMoveInputPolicy {
    fun acceptsInput(isModalOpen: Boolean): Boolean = !isModalOpen
}
