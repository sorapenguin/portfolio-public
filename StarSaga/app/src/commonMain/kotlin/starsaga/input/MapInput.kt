package starsaga.input

import starsaga.map.GridCell
import starsaga.map.MapData
import kotlin.math.floor

object MapInput {
    fun cellAt(worldX: Double, worldY: Double, map: MapData): GridCell? {
        if (worldX < 0.0 || worldY < 0.0) return null
        val col = floor(worldX / MapData.TILE_SIZE).toInt()
        val row = floor(worldY / MapData.TILE_SIZE).toInt()
        return GridCell(col, row).takeIf {
            it.col in 0 until map.columns && it.row in 0 until map.rows
        }
    }
}
