package starsaga.camera

import starsaga.map.GridCell
import starsaga.map.MapData

data class CameraState(val left: Double, val top: Double)

data class CameraPageDebug(
    val pageX: Int,
    val pageY: Int,
    val pageLeftCol: Int,
    val pageRightCol: Int,
    val handoffLeftCol: Int,
    val handoffRightCol: Int,
    val lastTransition: String,
)

class CameraController(
    private val viewportWidth: Double,
    private val viewportHeight: Double,
) {
    var state: CameraState = CameraState(0.0, 0.0)
        private set
    var debug: CameraPageDebug = CameraPageDebug(0, 0, 0, PAGE_COLS - 1, HANDOFF_COLS, PAGE_COLS - 1 - HANDOFF_COLS, "-")
        private set

    private var pageLeftCol = 0
    private var pageTopRow = 0
    private var lastPlayerCell: GridCell? = null

    fun reset() {
        state = CameraState(0.0, 0.0)
        pageLeftCol = 0
        pageTopRow = 0
        lastPlayerCell = null
        debug = CameraPageDebug(0, 0, 0, PAGE_COLS - 1, HANDOFF_COLS, PAGE_COLS - 1 - HANDOFF_COLS, "-")
    }

    fun update(map: MapData, playerCell: GridCell) {
        val previous = lastPlayerCell
        val maxPageLeftCol = (map.columns - PAGE_COLS).coerceAtLeast(0)
        val maxPageTopRow = (map.rows - PAGE_ROWS).coerceAtLeast(0)
        var transition = "-"

        if (previous == null || playerCell.col !in pageLeftCol..pageRightCol()) {
            pageLeftCol = (playerCell.col - HANDOFF_COLS).coerceIn(0, maxPageLeftCol)
            transition = "snap-x"
        } else if (playerCell.col > previous.col && playerCell.col >= handoffRightCol()) {
            pageLeftCol = (pageLeftCol + PAGE_STEP_COLS).coerceAtMost(maxPageLeftCol)
            transition = "east"
        } else if (playerCell.col < previous.col && playerCell.col <= handoffLeftCol()) {
            pageLeftCol = (pageLeftCol - PAGE_STEP_COLS).coerceAtLeast(0)
            transition = "west"
        }

        if (previous == null || playerCell.row !in pageTopRow..pageBottomRow()) {
            pageTopRow = (playerCell.row - HANDOFF_ROWS).coerceIn(0, maxPageTopRow)
            transition = appendTransition(transition, "snap-y")
        } else if (playerCell.row > previous.row && playerCell.row >= handoffBottomRow()) {
            pageTopRow = (pageTopRow + PAGE_STEP_ROWS).coerceAtMost(maxPageTopRow)
            transition = appendTransition(transition, "south")
        } else if (playerCell.row < previous.row && playerCell.row <= handoffTopRow()) {
            pageTopRow = (pageTopRow - PAGE_STEP_ROWS).coerceAtLeast(0)
            transition = appendTransition(transition, "north")
        }

        lastPlayerCell = playerCell
        val maxLeft = (map.columns * MapData.TILE_SIZE.toDouble() - viewportWidth).coerceAtLeast(0.0)
        val maxTop = (map.rows * MapData.TILE_SIZE.toDouble() - viewportHeight).coerceAtLeast(0.0)
        state = CameraState(
            left = (pageLeftCol * MapData.TILE_SIZE.toDouble()).coerceAtMost(maxLeft),
            top = (pageTopRow * MapData.TILE_SIZE.toDouble()).coerceAtMost(maxTop),
        )
        debug = CameraPageDebug(
            pageX = pageLeftCol / PAGE_STEP_COLS,
            pageY = pageTopRow / PAGE_STEP_ROWS,
            pageLeftCol = pageLeftCol,
            pageRightCol = pageRightCol(),
            handoffLeftCol = handoffLeftCol(),
            handoffRightCol = handoffRightCol(),
            lastTransition = transition,
        )
    }

    private fun pageRightCol(): Int = pageLeftCol + PAGE_COLS - 1

    private fun pageBottomRow(): Int = pageTopRow + PAGE_ROWS - 1

    private fun handoffLeftCol(): Int = pageLeftCol + HANDOFF_COLS

    private fun handoffRightCol(): Int = pageRightCol() - HANDOFF_COLS

    private fun handoffTopRow(): Int = pageTopRow + HANDOFF_ROWS

    private fun handoffBottomRow(): Int = pageBottomRow() - HANDOFF_ROWS

    private fun appendTransition(current: String, next: String): String =
        if (current == "-") next else "$current,$next"

    companion object {
        const val PAGE_COLS = 11
        const val PAGE_ROWS = 20
        const val HANDOFF_COLS = 2
        const val HANDOFF_ROWS = 2
        private const val PAGE_STEP_COLS = PAGE_COLS - HANDOFF_COLS * 2
        private const val PAGE_STEP_ROWS = PAGE_ROWS - HANDOFF_ROWS * 2
    }
}
