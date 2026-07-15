package starterra.visual

import korlibs.image.color.RGBA
import korlibs.korge.view.Container
import korlibs.korge.view.position
import korlibs.korge.view.solidRect
import starterra.world.GridCell
import starterra.world.SpikeMap
import starterra.world.TileType
import starterra.game.CoreState

/** Fixed Batch C scenery. It deliberately contains no gameplay behaviour. */
object OutpostVisuals {
    private val pavedCells = buildSet {
        for (row in 7..14) for (column in 4..11) add(GridCell(column, row))
        add(GridCell(3, 10)); add(GridCell(12, 10)); add(GridCell(12, 11)); add(GridCell(12, 12))
    }

    fun drawGround(layer: Container, map: SpikeMap) {
        for (row in 0 until map.rows) for (column in 0 until map.columns) {
            val cell = GridCell(column, row)
            val x = map.cellLeft(cell); val y = map.cellTop(cell)
            val grass = if ((column + row) % 5 == 0) OutpostPalette.grassVariant else OutpostPalette.grass
            layer.solidRect(SpikeMap.TILE_SIZE, SpikeMap.TILE_SIZE, grass) { position(x, y) }
            if (cell in pavedCells) {
                layer.solidRect(30, 30, OutpostPalette.paving) { position(x + 1, y + 1) }
                layer.solidRect(24, 2, OutpostPalette.pavingInset) { position(x + 4, y + 27) }
                if ((column + row) % 3 == 0) layer.solidRect(2, 12, OutpostPalette.metalHighlight.withAd(75.0)) { position(x + 6, y + 7) }
            } else {
                if ((column * 3 + row) % 7 == 0) layer.solidRect(3, 3, OutpostPalette.grassLight) { position(x + 7, y + 10) }
                if ((column + row * 2) % 11 == 0) layer.solidRect(5, 2, OutpostPalette.grassDark) { position(x + 20, y + 22) }
            }
        }
        // A broken paved approach leading toward the central device.
        for (column in 5..10) layer.solidRect(20, 3, OutpostPalette.pavingInset.withAd(110.0)) {
            position(column * SpikeMap.TILE_SIZE.toDouble() + 6, 15 * SpikeMap.TILE_SIZE.toDouble() + 9)
        }
    }

    fun drawTerrain(layer: Container, map: SpikeMap) {
        for (row in 0 until map.rows) for (column in 0 until map.columns) {
            if (map.tileAt(GridCell(column, row)) == TileType.WALL) drawWallCell(layer, map.cellLeft(GridCell(column, row)), map.cellTop(GridCell(column, row)))
        }
        // A visual-only stepped approach on the east of the core; it adds no height rule.
        drawSlope(layer, 10 * SpikeMap.TILE_SIZE.toDouble(), 12 * SpikeMap.TILE_SIZE.toDouble())
        // Broken low background bulkhead, deliberately with a gap rather than a wall across the screen.
        drawWallCell(layer, 9 * 32.0, 3 * 32.0)
        drawWallCell(layer, 10 * 32.0, 3 * 32.0)
    }

    private fun drawWallCell(layer: Container, x: Double, y: Double) {
        layer.solidRect(32, 13, OutpostPalette.metalTop) { position(x, y) }
        layer.solidRect(32, 19, OutpostPalette.metalFront) { position(x, y + 13) }
        layer.solidRect(32, 3, OutpostPalette.metalHighlight) { position(x, y + 13) }
        layer.solidRect(3, 16, OutpostPalette.metalSide) { position(x + 29, y + 16) }
    }

    private fun drawSlope(layer: Container, x: Double, y: Double) {
        layer.solidRect(62, 8, OutpostPalette.metalTop) { position(x, y + 4) }
        layer.solidRect(54, 7, OutpostPalette.metalSide) { position(x + 4, y + 12) }
        layer.solidRect(46, 7, OutpostPalette.metalFront) { position(x + 8, y + 19) }
    }

    fun Container.drawActor(name: String, coreState: CoreState = CoreState.DORMANT, signalLinked: Boolean = false) {
        when (name) {
            "starCore" -> drawStarCore(coreState)
            "antenna" -> drawAntenna(coreState == CoreState.ACTIVE, signalLinked)
            "crateA", "crateB" -> drawCrate(name == "crateB")
            "treeA", "treeB" -> drawTree(name == "treeB")
        }
    }

    private fun Container.drawStarCore(state: CoreState) {
        val glow = when (state) {
            CoreState.DORMANT -> OutpostPalette.coreGlow.withAd(45.0)
            CoreState.READY -> OutpostPalette.coreGlow.withAd(110.0)
            CoreState.ACTIVE -> OutpostPalette.coreGlow.withAd(175.0)
        }
        val inner = if (state == CoreState.DORMANT) OutpostPalette.coreBlue else OutpostPalette.coreInner
        solidRect(58, 8, OutpostPalette.shadow) { position(-13, 29) }
        solidRect(56, 14, OutpostPalette.metalTop) { position(-12, 15) }
        solidRect(48, 13, OutpostPalette.metalFront) { position(-8, 29) }
        solidRect(40, 3, OutpostPalette.metalHighlight) { position(-4, 29) }
        // Static two-step glow and a framed core; no animation, particles, or shader.
        solidRect(34, 34, glow) { position(-1, -22) }
        solidRect(22, 22, OutpostPalette.coreBlue) { position(5, -16) }
        solidRect(12, 12, inner) { position(10, -11) }
        solidRect(5, 35, OutpostPalette.metalSide) { position(1, -20) }
        solidRect(5, 35, OutpostPalette.metalFront) { position(26, -20) }
        solidRect(28, 4, OutpostPalette.metalTop) { position(2, -21) }
    }

    private fun Container.drawAntenna(active: Boolean, signalLinked: Boolean) {
        solidRect(28, 5, OutpostPalette.shadow) { position(2, 28) }
        solidRect(20, 8, OutpostPalette.metalTop) { position(6, 20) }
        solidRect(20, 7, OutpostPalette.metalFront) { position(6, 28) }
        solidRect(6, 42, OutpostPalette.metalSide) { position(13, -17) }
        solidRect(24, 5, OutpostPalette.metalTop) { position(4, -18) }
        solidRect(5, 15, if (active) OutpostPalette.coreBlue else OutpostPalette.metalSide) { position(22, -28) }
        solidRect(5, 10, if (active) OutpostPalette.coreInner else OutpostPalette.metalTop) { position(22, -25) }
        if (signalLinked) {
            // Static online signal: brighter lamp, receiver trace, mast trace, and compact bars.
            solidRect(13, 18, OutpostPalette.coreGlow.withAd(190.0)) { position(18, -31) }
            solidRect(5, 10, OutpostPalette.coreInner) { position(22, -25) }
            solidRect(2, 35, OutpostPalette.coreInner.withAd(210.0)) { position(15, -15) }
            solidRect(15, 2, OutpostPalette.coreInner.withAd(210.0)) { position(22, -18) }
            solidRect(3, 4, OutpostPalette.coreInner) { position(30, -29) }
            solidRect(3, 7, OutpostPalette.coreInner) { position(35, -32) }
            solidRect(3, 10, OutpostPalette.coreInner) { position(40, -35) }
        }
    }

    private fun Container.drawCrate(offset: Boolean) {
        val shift = if (offset) 3 else 0
        solidRect(27, 5, OutpostPalette.shadow) { position(3 + shift, 28) }
        solidRect(25, 9, OutpostPalette.crateTop) { position(3 + shift, 14) }
        solidRect(25, 11, OutpostPalette.crateFront) { position(3 + shift, 23) }
        solidRect(4, 11, OutpostPalette.crateSide) { position(24 + shift, 23) }
        solidRect(18, 2, RGBA(206, 166, 101, 160)) { position(6 + shift, 16) }
    }

    private fun Container.drawTree(offset: Boolean) {
        val shift = if (offset) 2 else 0
        solidRect(27, 5, OutpostPalette.shadow) { position(3 + shift, 28) }
        solidRect(8, 30, OutpostPalette.trunk) { position(12 + shift, -1) }
        solidRect(28, 20, OutpostPalette.foliage) { position(2 + shift, -28) }
        solidRect(20, 15, OutpostPalette.foliageLight) { position(6 + shift, -40) }
        solidRect(12, 8, RGBA(109, 169, 90)) { position(10 + shift, -47) }
    }

    fun Container.drawShard() {
        solidRect(17, 4, OutpostPalette.shadow) { position(7, 28) }
        solidRect(20, 20, OutpostPalette.coreGlow.withAd(120.0)) { position(6, 4) }
        solidRect(10, 18, OutpostPalette.coreBlue) { position(11, 5) }
        solidRect(18, 10, OutpostPalette.coreBlue) { position(7, 9) }
        solidRect(6, 6, OutpostPalette.coreInner) { position(13, 11) }
    }
}
