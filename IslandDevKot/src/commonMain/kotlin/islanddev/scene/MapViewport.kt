package islanddev.scene

import islanddev.game.GridPoint
import kotlin.math.floor

data class DrawnRect(
    val left: Double,
    val top: Double,
    val right: Double,
    val bottom: Double
) {
    fun contains(x: Double, y: Double): Boolean =
        x >= left && x < right && y >= top && y < bottom
}

data class MapViewport(
    val viewportLeft: Double,
    val viewportTop: Double,
    val tileOriginLeft: Double,
    val tileOriginTop: Double,
    val viewportWidth: Double,
    val viewportHeight: Double,
    val scrollLeft: Double,
    val scrollTop: Double,
    val cellSize: Double = GridScene.CELL_SIZE
) {
    val visibleCols: Int = floor(viewportWidth / cellSize).toInt()
    val visibleRows: Int = floor(viewportHeight / cellSize).toInt()
    val tileDrawWidth: Double = visibleCols * cellSize
    val tileDrawHeight: Double = visibleRows * cellSize
    val tileDrawBottom: Double = tileOriginTop + tileDrawHeight

    fun drawnRect(cell: GridPoint): DrawnRect {
        val left = tileOriginLeft + cell.col * cellSize - scrollLeft
        val top = tileOriginTop + cell.row * cellSize - scrollTop
        return DrawnRect(left, top, left + cellSize, top + cellSize)
    }

    fun drawnCenter(cell: GridPoint): Pair<Double, Double> {
        val rect = drawnRect(cell)
        return ((rect.left + rect.right) / 2.0) to ((rect.top + rect.bottom) / 2.0)
    }
}
