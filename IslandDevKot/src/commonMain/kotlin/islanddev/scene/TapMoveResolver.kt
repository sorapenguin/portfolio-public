package islanddev.scene

import islanddev.data.GameData
import islanddev.game.GridPoint
import islanddev.game.PathFinder
import islanddev.model.SaveData

enum class TapMoveRejectReason {
    OUTSIDE_MAP,
    LOCKED_OR_BLOCKED,
    NO_ROUTE
}

data class TapMoveRequest(
    val tappedCell: GridPoint,
    val destination: GridPoint,
    val path: List<GridPoint>
)

data class TapMoveResolution(
    val request: TapMoveRequest?,
    val rejectReason: TapMoveRejectReason?
)

data class TapMoveCoordinateTrace(
    val stageX: Float,
    val stageY: Float,
    val tileOriginLeft: Double,
    val tileOriginTop: Double,
    val localX: Double,
    val localY: Double,
    val worldX: Double,
    val worldY: Double,
    val scrollLeft: Double,
    val scrollTop: Double,
    val cellSize: Double,
    val cell: GridPoint?,
    val selectedCellRect: DrawnRect?,
    val rawMotionX: Float? = null,
    val rawMotionY: Float? = null
)

object TapMoveResolver {
    const val DEBUG_TAP_MOVE = false

    fun screenToGridCell(
        stageX: Float,
        stageY: Float,
        viewport: MapViewport
    ): GridPoint? = coordinateTrace(
        stageX = stageX,
        stageY = stageY,
        viewport = viewport
    ).cell

    fun screenToGridCell(
        stageX: Float,
        stageY: Float,
        mapViewLeft: Double,
        mapViewTop: Double,
        viewportWidth: Double,
        viewportHeight: Double,
        scrollLeft: Double,
        scrollTop: Double,
        cellSize: Double = GridScene.CELL_SIZE
    ): GridPoint? = screenToGridCell(
        stageX = stageX,
        stageY = stageY,
        viewport = MapViewport(
            viewportLeft = mapViewLeft,
            viewportTop = mapViewTop,
            tileOriginLeft = mapViewLeft,
            tileOriginTop = mapViewTop,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
            scrollLeft = scrollLeft,
            scrollTop = scrollTop,
            cellSize = cellSize
        )
    )

    fun coordinateTrace(
        stageX: Float,
        stageY: Float,
        viewport: MapViewport
    ): TapMoveCoordinateTrace {
        val localX = stageX.toDouble() - viewport.tileOriginLeft
        val localY = stageY.toDouble() - viewport.tileOriginTop
        val worldX = localX + viewport.scrollLeft
        val worldY = localY + viewport.scrollTop
        val cell = if (
            localX < 0.0 ||
            localY < 0.0 ||
            localX >= viewport.tileDrawWidth ||
            localY >= viewport.tileDrawHeight
        ) {
            null
        } else {
            MapInput.cellAt(
                mapLocalX = worldX,
                mapLocalY = worldY,
                cellSize = viewport.cellSize
            )
        }
        return TapMoveCoordinateTrace(
            stageX = stageX,
            stageY = stageY,
            tileOriginLeft = viewport.tileOriginLeft,
            tileOriginTop = viewport.tileOriginTop,
            localX = localX,
            localY = localY,
            worldX = worldX,
            worldY = worldY,
            scrollLeft = viewport.scrollLeft,
            scrollTop = viewport.scrollTop,
            cellSize = viewport.cellSize,
            cell = cell,
            selectedCellRect = cell?.let(viewport::drawnRect)
        )
    }

    fun resolve(
        save: SaveData,
        start: GridPoint,
        tappedCell: GridPoint?
    ): TapMoveResolution {
        val tapped = tappedCell
            ?: return TapMoveResolution(null, TapMoveRejectReason.OUTSIDE_MAP)
        if (!canMoveTo(save, tapped)) {
            return TapMoveResolution(null, TapMoveRejectReason.LOCKED_OR_BLOCKED)
        }

        val boss = GameData.BOSSES.firstOrNull {
            it.id !in save.defeatedBossIds &&
                tapped.col == it.fromZoneId * 20 + 19 &&
                tapped.row == 8
        }
        val destinations = if (boss != null) {
            bossAdjacentCells(tapped).filter { canMoveTo(save, it) }
        } else {
            listOf(tapped)
        }

        val best = destinations
            .mapNotNull { destination ->
                val path = pathTo(save, start, destination) ?: return@mapNotNull null
                TapMoveRequest(tapped, destination, path)
            }
            .minWithOrNull(
                compareBy<TapMoveRequest>(
                    { it.path.size },
                    { kotlin.math.abs(it.destination.col - start.col) + kotlin.math.abs(it.destination.row - start.row) },
                    { it.destination.col },
                    { it.destination.row }
                )
            )

        return if (best != null) {
            TapMoveResolution(best, null)
        } else {
            TapMoveResolution(null, TapMoveRejectReason.NO_ROUTE)
        }
    }

    private fun bossAdjacentCells(bossCell: GridPoint): List<GridPoint> = listOf(
        GridPoint(bossCell.col - 1, bossCell.row),
        GridPoint(bossCell.col + 1, bossCell.row),
        GridPoint(bossCell.col, bossCell.row - 1),
        GridPoint(bossCell.col, bossCell.row + 1)
    )

    private fun pathTo(
        save: SaveData,
        start: GridPoint,
        destination: GridPoint
    ): List<GridPoint>? {
        if (start == destination) return emptyList()
        val path = PathFinder.findPath(
            start = start,
            goal = destination,
            width = GridScene.COLUMNS,
            height = GridScene.ROWS,
            isPassable = { canMoveTo(save, it) }
        )
        return path.takeIf { it.isNotEmpty() }
    }

    private fun canMoveTo(save: SaveData, cell: GridPoint): Boolean =
        StepMove.canMoveTo(
            destination = cell,
            unlockedZoneIds = save.unlockedZoneIds,
            columns = GridScene.COLUMNS,
            rows = GridScene.ROWS
        )
}
