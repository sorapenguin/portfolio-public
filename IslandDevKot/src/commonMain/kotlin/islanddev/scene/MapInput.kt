package islanddev.scene

import islanddev.game.GridPoint
import kotlin.math.floor

object MapInput {
    fun cellAt(
        mapLocalX: Double,
        mapLocalY: Double,
        columns: Int = GridScene.COLUMNS,
        rows: Int = GridScene.ROWS,
        cellSize: Double = GridScene.CELL_SIZE
    ): GridPoint? {
        if (mapLocalX < 0.0 || mapLocalY < 0.0) return null

        val col = floor(mapLocalX / cellSize).toInt()
        val row = floor(mapLocalY / cellSize).toInt()
        return GridPoint(col, row).takeIf {
            it.col in 0 until columns && it.row in 0 until rows
        }
    }
}
