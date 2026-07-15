package islanddev.scene

import islanddev.data.GameData
import islanddev.game.BattleResolver
import islanddev.game.GridPoint
import islanddev.game.PathFinder
import islanddev.game.ResourceTicker
import islanddev.model.SaveData
import islanddev.ui.IslandUiFonts
import islanddev.ui.IslandTheme
import korlibs.image.color.RGBA
import korlibs.korge.view.Container
import korlibs.korge.view.position
import korlibs.korge.view.solidRect
import korlibs.korge.view.text
import kotlin.math.abs

class PlayerSprite(
    initialSave: SaveData,
    private val onSaveChanged: (SaveData) -> Unit,
    private val onBossApproach: (Int?) -> Unit
) : Container() {
    companion object {
        const val MOVE_SECONDS_PER_CELL = 0.18
        const val MAX_DELTA_SECONDS = 0.033333333333
        private const val MARKER_SIZE = 28.0
        private const val MARKER_OFFSET = (GridScene.CELL_SIZE - MARKER_SIZE) / 2.0
    }

    private var save = initialSave
    private var currentPath = emptyList<GridPoint>()
    private var pathIndex = 0
    private var segmentElapsed = 0.0
    private var currentSegmentFrom = GridPoint(save.playerCol, save.playerRow)
    private var currentSegmentTo = currentSegmentFrom
    private var targetCell: GridPoint? = null
    private var pendingRoute: ConfirmedRoute? = null
    private var movementState = PlayerMovementState.IDLE
    var visualCol: Double = save.playerCol.toDouble()
        private set
    var visualRow: Double = save.playerRow.toDouble()
        private set
    val isIdle: Boolean
        get() = movementState == PlayerMovementState.IDLE

    fun navigationOrigin(): GridPoint =
        if (movementState == PlayerMovementState.IDLE) {
            GridPoint(save.playerCol, save.playerRow)
        } else {
            currentSegmentTo
        }

    init {
        solidRect(
            MARKER_SIZE,
            MARKER_SIZE,
            IslandTheme.Color.Accent
        )
        solidRect(
            MARKER_SIZE - 4.0,
            MARKER_SIZE - 4.0,
            IslandTheme.Color.PlayerBorder
        ) {
            position(2, 2)
        }
        solidRect(
            MARKER_SIZE - 8.0,
            MARKER_SIZE - 8.0,
            IslandTheme.Color.Player
        ) {
            position(4, 4)
        }
        text(
            MapLabels.player(),
            textSize = 17.0,
            color = RGBA(18, 28, 40, 255),
            font = IslandUiFonts.font
        ) {
            position(8, 2)
        }
        positionForCell(currentSegmentFrom)
    }

    fun updateSave(newSave: SaveData) {
        save = newSave
    }

    fun movementDebugState(): MovementDebugState {
        val pending = pendingRoute
        return MovementDebugState(
            player = GridPoint(save.playerCol, save.playerRow),
            target = pending?.target ?: targetCell,
            path = pending?.let { listOf(currentSegmentTo) + it.path }
                ?: currentPath.drop(pathIndex)
        )
    }

    fun moveTo(col: Int, row: Int) {
        val goal = GridPoint(col, row)
        if (!isValidGoal(goal)) return

        if (movementState != PlayerMovementState.IDLE) {
            pendingRoute = findConfirmedRoute(currentSegmentTo, goal)
            movementState = PlayerMovementState.PENDING_RETARGET
            return
        }
        applyRoute(findConfirmedRoute(GridPoint(save.playerCol, save.playerRow), goal))
    }

    fun requestMoveAlongPath(destination: GridPoint, path: List<GridPoint>): Boolean {
        if (!isValidGoal(destination)) return false
        if (path.isEmpty()) {
            currentPath = emptyList()
            pathIndex = 0
            pendingRoute = null
            targetCell = null
            movementState = PlayerMovementState.IDLE
            checkBossApproach()
            return true
        }
        val start = if (movementState == PlayerMovementState.IDLE) {
            GridPoint(save.playerCol, save.playerRow)
        } else {
            currentSegmentTo
        }
        val route = ConfirmedRoute(
            start = start,
            target = destination,
            path = path
        )
        if (movementState == PlayerMovementState.IDLE) {
            applyRoute(route)
        } else {
            pendingRoute = route
            movementState = PlayerMovementState.PENDING_RETARGET
        }
        return true
    }

    fun requestStepMove(dx: Int, dy: Int): Boolean {
        if (kotlin.math.abs(dx) + kotlin.math.abs(dy) != 1) return false

        val origin = if (movementState == PlayerMovementState.IDLE) {
            GridPoint(save.playerCol, save.playerRow)
        } else {
            currentSegmentTo
        }
        val destination = StepMove.destination(origin, dx, dy)
        if (!canMoveTo(destination.col, destination.row)) return false

        val route = ConfirmedRoute(
            start = origin,
            target = destination,
            path = listOf(destination)
        )
        if (movementState == PlayerMovementState.IDLE) {
            applyRoute(route)
        } else {
            pendingRoute = route
            movementState = PlayerMovementState.PENDING_RETARGET
        }
        return true
    }

    fun cancelPlannedMovement() {
        pendingRoute = null
        targetCell = null
        if (movementState == PlayerMovementState.IDLE) {
            currentPath = emptyList()
            pathIndex = 0
            currentSegmentTo = currentSegmentFrom
            return
        }
        currentPath = listOf(currentSegmentTo)
        pathIndex = 0
    }

    fun canMoveTo(col: Int, row: Int): Boolean =
        StepMove.canMoveTo(
            destination = GridPoint(col, row),
            unlockedZoneIds = save.unlockedZoneIds
        )

    fun collectCurrentCell(nowSec: Long): Boolean {
        val updated = ResourceTicker.collectCurrentCell(
            save,
            save.playerCol,
            save.playerRow,
            nowSec
        )
        if (updated == save) return false
        save = updated
        onSaveChanged(save)
        return true
    }

    private fun findConfirmedRoute(start: GridPoint, goal: GridPoint): ConfirmedRoute {
        val confirmedPath = PathFinder.findPath(
            start = start,
            goal = goal,
            width = GridScene.COLUMNS,
            height = GridScene.ROWS,
            isPassable = { GameData.columnToZone(it.col) in save.unlockedZoneIds }
        )
        return ConfirmedRoute(start, goal, confirmedPath)
    }

    private fun applyRoute(route: ConfirmedRoute) {
        currentPath = route.path
        pathIndex = 0
        segmentElapsed = 0.0
        currentSegmentFrom = route.start
        targetCell = route.target

        if (!route.isMovementRequired) {
            currentSegmentTo = currentSegmentFrom
            targetCell = null
            movementState = PlayerMovementState.IDLE
            return
        }

        currentSegmentTo = currentPath.first()
        movementState = PlayerMovementState.MOVING
    }

    fun update(deltaSeconds: Double, nowSec: Long) {
        if (movementState == PlayerMovementState.IDLE) return

        segmentElapsed += MovementTiming.effectiveDelta(
            deltaSeconds = deltaSeconds,
            maxDeltaSeconds = MAX_DELTA_SECONDS
        )
        if (segmentElapsed < MOVE_SECONDS_PER_CELL) {
            updateVisualPosition()
            return
        }

        setVisualCell(
            currentSegmentTo.col.toDouble(),
            currentSegmentTo.row.toDouble()
        )
        completeCurrentCell(nowSec)
    }

    private fun completeCurrentCell(nowSec: Long) {
        save = save.copy(
            playerCol = currentSegmentTo.col,
            playerRow = currentSegmentTo.row
        )
        save = ResourceTicker.collectCurrentCell(
            save,
            currentSegmentTo.col,
            currentSegmentTo.row,
            nowSec
        )
        val enemyCell = save.enemyCells.firstOrNull {
            !it.defeated &&
                it.col == currentSegmentTo.col &&
                it.row == currentSegmentTo.row
        }
        if (enemyCell != null) {
            save = BattleResolver.resolveEnemyAt(
                save,
                enemyId = enemyCell.enemyId,
                enemyCellId = enemyCell.id,
                nowSec = nowSec
            ).first
        }
        onSaveChanged(save)
        checkBossApproach()

        pendingRoute?.let { route ->
            pendingRoute = null
            applyRoute(route)
            return
        }

        pathIndex++
        segmentElapsed = 0.0
        currentSegmentFrom = currentSegmentTo
        if (pathIndex < currentPath.size) {
            currentSegmentTo = currentPath[pathIndex]
            movementState = PlayerMovementState.MOVING
        } else {
            currentPath = emptyList()
            pathIndex = 0
            currentSegmentTo = currentSegmentFrom
            targetCell = null
            movementState = PlayerMovementState.IDLE
        }
    }

    private fun updateVisualPosition() {
        val ratio = (segmentElapsed / MOVE_SECONDS_PER_CELL).coerceIn(0.0, 1.0)
        setVisualCell(
            currentSegmentFrom.col +
                (currentSegmentTo.col - currentSegmentFrom.col) * ratio,
            currentSegmentFrom.row +
                (currentSegmentTo.row - currentSegmentFrom.row) * ratio
        )
    }

    private fun setVisualCell(col: Double, row: Double) {
        visualCol = col
        visualRow = row
        position(
            col * GridScene.CELL_SIZE + MARKER_OFFSET,
            row * GridScene.CELL_SIZE + MARKER_OFFSET
        )
    }

    private fun isValidGoal(goal: GridPoint): Boolean =
        canMoveTo(goal.col, goal.row)

    private fun checkBossApproach() {
        val boss = GameData.BOSSES.firstOrNull {
            val bossCol = it.fromZoneId * 20 + 19
            val bossRow = 8
            it.id !in save.defeatedBossIds &&
                abs(save.playerCol - bossCol) + abs(save.playerRow - bossRow) <= 1
        }
        onBossApproach(boss?.id)
    }

    private fun positionForCell(point: GridPoint) {
        setVisualCell(point.col.toDouble(), point.row.toDouble())
    }

}
