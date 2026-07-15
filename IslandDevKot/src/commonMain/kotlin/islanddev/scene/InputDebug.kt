package islanddev.scene

import islanddev.game.GridPoint

object InputDebug {
    const val ENABLED = false
}

data class MovementDebugState(
    val player: GridPoint,
    val target: GridPoint?,
    val path: List<GridPoint>
)
