package starterra.visual

import korlibs.image.color.RGBA
import korlibs.korge.view.Container
import korlibs.korge.view.position
import korlibs.korge.view.solidRect
import korlibs.korge.view.text
import starterra.world.GridCell
import starterra.world.SpikeMap
import starterra.world.TileType
import starterra.game.SignalPuzzleProgress
import starterra.game.SignalLinkState

/** Batch F's inactive second-area scenery; it has no gameplay behaviour. */
object SignalFieldVisuals {
    private val paving = setOf(GridCell(6, 9), GridCell(9, 9), GridCell(6, 12), GridCell(9, 12), GridCell(7, 12), GridCell(8, 12))
    fun drawGround(layer: Container, map: SpikeMap) {
        for (row in 0 until map.rows) for (column in 0 until map.columns) {
            val cell = GridCell(column, row); val x = map.cellLeft(cell); val y = map.cellTop(cell)
            layer.solidRect(32, 32, if ((row + column) % 4 == 0) RGBA(57, 105, 91) else RGBA(53, 99, 86)) { position(x, y) }
            if (cell in paving) layer.solidRect(30, 30, RGBA(67, 87, 105)) { position(x + 1, y + 1) }
            else if ((column * 5 + row) % 9 == 0) layer.solidRect(4, 2, RGBA(94, 135, 105, 130)) { position(x + 13, y + 17) }
        }
    }
    fun drawTerrain(layer: Container, map: SpikeMap) {
        for (row in 0 until map.rows) for (column in 0 until map.columns) if (map.tileAt(GridCell(column, row)) == TileType.WALL) {
            val x = column * 32.0; val y = row * 32.0
            layer.solidRect(32, 12, RGBA(111, 135, 149)) { position(x, y) }
            layer.solidRect(32, 20, RGBA(53, 72, 88)) { position(x, y + 12) }
            layer.solidRect(32, 3, RGBA(159, 180, 187)) { position(x, y + 12) }
        }
    }
    fun Container.drawActor(name: String, puzzle: SignalPuzzleProgress = SignalPuzzleProgress(), linked: Boolean = false) {
        when (name) {
            "relay" -> { solidRect(64, 7, RGBA(20, 39, 48, 100)) { position(-16, 29) }; solidRect(58, 14, RGBA(130, 151, 160)) { position(-13, 14) }; solidRect(52, 14, RGBA(52, 71, 86)) { position(-10, 28) }; solidRect(8, 45, RGBA(81, 102, 116)) { position(12, -20) }; solidRect(36, 7, if(linked) OutpostPalette.coreInner else RGBA(155,174,181)) { position(-2, -22) } }
            "beaconA", "beaconB", "beaconC" -> { val lit = linked || (puzzle.state == SignalLinkState.ROUTING && name.last().toString() in puzzle.acceptedBeaconIds.map { it.name }); solidRect(25, 5, RGBA(20, 39, 48, 105)) { position(4, 28) }; solidRect(18, 8, RGBA(104, 124, 139)) { position(7, 20) }; solidRect(6, 27, RGBA(65, 83, 100)) { position(13, -6) }; solidRect(if(name=="beaconA") 8 else 6, 8, if(lit) OutpostPalette.coreInner else RGBA(76,71,105)) { position(12, -13) }; text(name.last().toString(), textSize=8.0, color=RGBA(230,240,245)) { position(13,-25) } }
            "terminal" -> { solidRect(29, 5, RGBA(20, 39, 48, 105)) { position(2, 28) }; solidRect(25, 11, RGBA(89, 108, 120)) { position(3, 16) }; solidRect(25, 12, RGBA(48, 67, 81)) { position(3, 27) }; solidRect(16, 7, if(linked) OutpostPalette.coreInner else RGBA(77,71,102)) { position(7, 8) } }
            "rockA", "rockB", "rockC" -> { solidRect(25, 5, RGBA(20, 39, 48, 100)) { position(4, 28) }; solidRect(24, 18, RGBA(79, 101, 118)) { position(4, 10) }; solidRect(16, 7, RGBA(120, 142, 151)) { position(8, 6) } }
            "shrubA" -> { solidRect(24, 5, RGBA(20, 39, 48, 100)) { position(4, 28) }; solidRect(25, 20, RGBA(50, 122, 88)) { position(4, 7) }; solidRect(15, 10, RGBA(84, 148, 99)) { position(9, 0) } }
        }
    }
    fun drawGate(layer: Container, active: Boolean, x: Double, y: Double) {
        val light = if (active) OutpostPalette.coreInner else RGBA(66, 80, 91)
        layer.solidRect(15, 32, RGBA(59, 79, 94)) { position(x, y) }; layer.solidRect(15, 32, RGBA(59, 79, 94)) { position(x + 49, y) }
        layer.solidRect(49, 5, RGBA(133, 156, 165)) { position(x + 8, y) }; layer.solidRect(5, 8, light) { position(x + 5, y + 8) }; layer.solidRect(5, 8, light) { position(x + 54, y + 8) }
    }
}
