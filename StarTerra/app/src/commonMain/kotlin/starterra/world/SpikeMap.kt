package starterra.world

enum class TileType(val passable: Boolean) {
    FLOOR(true),
    WALL(false),
}

data class GridCell(val column: Int, val row: Int) {
    operator fun plus(other: GridCell) = GridCell(column + other.column, row + other.row)
}

/** Small code-defined map for collision, camera, and depth-order verification. */
class SpikeMap(
    val columns: Int,
    val rows: Int,
    private val tiles: List<TileType>,
) {
    init {
        require(tiles.size == columns * rows)
    }

    fun tileAt(cell: GridCell): TileType? =
        if (cell.column in 0 until columns && cell.row in 0 until rows) tiles[cell.row * columns + cell.column] else null

    fun isPassable(cell: GridCell): Boolean = tileAt(cell)?.passable == true

    fun cellLeft(cell: GridCell): Double = cell.column * TILE_SIZE.toDouble()

    fun cellTop(cell: GridCell): Double = cell.row * TILE_SIZE.toDouble()

    fun footPosition(cell: GridCell): Pair<Double, Double> =
        Pair(cellLeft(cell) + TILE_SIZE / 2.0, cellTop(cell) + TILE_SIZE.toDouble())

    fun isCoreActAdjacent(cell: GridCell): Boolean = cell !in CORE_CELLS && CORE_CELLS.any { core ->
        kotlin.math.abs(cell.column - core.column) + kotlin.math.abs(cell.row - core.row) == 1
    }

    companion object {
        const val TILE_SIZE = 32
        val CORE_CELLS = setOf(GridCell(7, 10), GridCell(8, 10), GridCell(7, 11), GridCell(8, 11))

        fun createDebugMap(): SpikeMap {
            val columns = 16
            val rows = 24
            val tiles = MutableList(columns * rows) { TileType.FLOOR }

            fun set(column: Int, row: Int, type: TileType) {
                tiles[row * columns + column] = type
            }

            for (column in 0 until columns) {
                set(column, 0, TileType.WALL)
                set(column, rows - 1, TileType.WALL)
            }
            for (row in 0 until rows) {
                set(0, row, TileType.WALL)
                set(columns - 1, row, TileType.WALL)
            }
            // Batch C: a broken L-shaped outpost wall, crates, antenna base, and core base.
            for (column in 2..5) set(column, 4, TileType.WALL)
            for (row in 4..7) set(2, row, TileType.WALL)
            for (column in 3..5) set(column, 18, TileType.WALL)
            for (row in 18..20) set(3, row, TileType.WALL)
            (CORE_CELLS + listOf(GridCell(7, 2), GridCell(8, 2), GridCell(12, 6), GridCell(4, 16), GridCell(12, 17))).forEach {
                set(it.column, it.row, TileType.WALL)
            }
            return SpikeMap(columns, rows, tiles)
        }

        fun createSignalFieldMap(): SpikeMap {
            val columns = 16; val rows = 24
            val tiles = MutableList(columns * rows) { TileType.FLOOR }
            fun wall(column: Int, row: Int) { tiles[row * columns + column] = TileType.WALL }
            for (column in 0 until columns) { wall(column, 0); wall(column, rows - 1) }
            for (row in 0 until rows) { wall(0, row); wall(columns - 1, row) }
            listOf(
                GridCell(7, 10), GridCell(8, 10), GridCell(7, 11), GridCell(8, 11),
                GridCell(7, 21), GridCell(8, 21), GridCell(5, 7), GridCell(11, 12), GridCell(6, 17),
                GridCell(12, 14), GridCell(3, 8), GridCell(12, 8), GridCell(4, 15),
            ).forEach { wall(it.column, it.row) }
            for (column in 2..4) wall(column, 4)
            for (row in 4..6) wall(2, row)
            return SpikeMap(columns, rows, tiles)
        }
    }
}
