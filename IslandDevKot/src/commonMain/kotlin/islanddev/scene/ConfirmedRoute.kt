package islanddev.scene

import islanddev.game.GridPoint

data class ConfirmedRoute(
    val start: GridPoint,
    val target: GridPoint,
    val path: List<GridPoint>
) {
    init {
        require(path.isEmpty() || path.last() == target)
    }

    val isMovementRequired: Boolean
        get() = path.isNotEmpty()
}

enum class PlayerMovementState {
    IDLE,
    MOVING,
    PENDING_RETARGET
}
