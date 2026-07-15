package islanddev.game

import islanddev.data.GameData
import islanddev.model.SaveData
import islanddev.scene.GridScene
import islanddev.scene.StepMove
import kotlin.math.abs

enum class AutoTargetKind {
    ENEMY,
    RESOURCE
}

enum class AutoStopReason(val label: String) {
    NONE("-"),
    USER_OFF("userOff"),
    NO_TARGET("noTarget"),
    NO_DIRECTION("noDir"),
    COLLECT_FAILED("collectFailed"),
    MODAL("modal"),
    GAME_CLEARED("gameCleared")
}

data class AutoTarget(
    val kind: AutoTargetKind,
    val id: Int,
    val objectCell: GridPoint,
    val destination: GridPoint
)

data class StepDirection(val dx: Int, val dy: Int)

data class AutoCollectionState(
    val enabled: Boolean = false,
    val zoneId: Int? = null
) {
    fun toggle(playerCol: Int): AutoCollectionState =
        if (enabled) {
            AutoCollectionState()
        } else {
            if (playerCol !in 0 until GridScene.COLUMNS) {
                AutoCollectionState()
            } else {
                AutoCollectionState(
                    enabled = true,
                    zoneId = GameData.columnToZone(playerCol)
                )
            }
        }

    fun stop(): AutoCollectionState = AutoCollectionState()
}

object AutoNavigator {
    fun candidates(save: SaveData, autoZoneId: Int): List<AutoTarget> {
        if (save.gameCleared) return emptyList()
        if (autoZoneId !in save.unlockedZoneIds) return emptyList()
        val player = GridPoint(save.playerCol, save.playerRow)
        val enemies = save.enemyCells
            .asSequence()
            .filter { !it.defeated }
            .filter { GameData.columnToZone(it.col) == autoZoneId }
            .mapNotNull { cell ->
                val enemy = GameData.enemyById(cell.enemyId) ?: return@mapNotNull null
                if (save.currentAtk < enemy.requiredAtk) return@mapNotNull null
                val objectCell = GridPoint(cell.col, cell.row)
                AutoTarget(
                    AutoTargetKind.ENEMY,
                    cell.enemyId,
                    objectCell,
                    objectCell
                )
            }
        val resources = save.resourceCells
            .asSequence()
            .filter { !it.depleted }
            .filter { GameData.columnToZone(it.col) == autoZoneId }
            .map { cell ->
                val objectCell = GridPoint(cell.col, cell.row)
                AutoTarget(
                    AutoTargetKind.RESOURCE,
                    cell.resourceId,
                    objectCell,
                    objectCell
                )
            }
        return (enemies + resources)
            .sortedWith(
                compareBy<AutoTarget>(
                    { if (it.kind == AutoTargetKind.ENEMY) 0 else 1 },
                    { distance(player, it.destination) },
                    { it.id },
                    { it.objectCell.col },
                    { it.objectCell.row }
                )
            )
            .toList()
    }

    fun findAutoTarget(save: SaveData, autoZoneId: Int): AutoTarget? {
        return candidates(save, autoZoneId).firstOrNull()
    }

    fun chooseDirection(
        save: SaveData,
        target: AutoTarget,
        autoZoneId: Int
    ): StepDirection? {
        val current = GridPoint(save.playerCol, save.playerRow)
        val destination = target.destination
        if (current == destination) return null

        val horizontal = when {
            destination.col > current.col -> StepDirection(1, 0)
            destination.col < current.col -> StepDirection(-1, 0)
            else -> null
        }
        if (horizontal != null &&
            canStep(save, current, horizontal, autoZoneId)
        ) {
            return horizontal
        }

        val vertical = when {
            destination.row > current.row -> StepDirection(0, 1)
            destination.row < current.row -> StepDirection(0, -1)
            else -> null
        }
        return vertical?.takeIf {
            canStep(save, current, it, autoZoneId)
        }
    }

    private fun canStep(
        save: SaveData,
        current: GridPoint,
        direction: StepDirection,
        autoZoneId: Int
    ): Boolean {
        val destination = StepMove.destination(current, direction.dx, direction.dy)
        return GameData.columnToZone(destination.col) == autoZoneId &&
            StepMove.canMoveTo(
                destination,
                save.unlockedZoneIds,
                GridScene.COLUMNS,
                GridScene.ROWS
            )
    }

    private fun distance(a: GridPoint, b: GridPoint): Int =
        abs(a.col - b.col) + abs(a.row - b.row)
}

object AutoInputPolicy {
    const val THINK_INTERVAL_SECONDS = 0.15

    fun canAdvance(
        enabled: Boolean,
        isModalOpen: Boolean,
        playerIdle: Boolean,
        thinkCooldownSeconds: Double = 0.0
    ): Boolean =
        enabled && !isModalOpen && playerIdle && thinkCooldownSeconds <= 0.0

    fun updateThinkCooldown(
        currentSeconds: Double,
        deltaSeconds: Double,
        enabled: Boolean,
        isModalOpen: Boolean,
        playerIdle: Boolean
    ): Double = when {
        !enabled -> 0.0
        isModalOpen || !playerIdle -> THINK_INTERVAL_SECONDS
        else -> (currentSeconds - deltaSeconds).coerceAtLeast(0.0)
    }
}
