package starsaga.map

import korlibs.image.color.RGBA
import korlibs.korge.view.Container
import korlibs.korge.view.position
import korlibs.korge.view.solidRect
import korlibs.korge.view.text
import starsaga.ui.StarSagaFonts

class TileRenderer(
    private val layer: Container,
) {
    fun draw(map: MapData) {
        layer.removeChildren()
        for (row in 0 until map.rows) {
            for (col in 0 until map.columns) {
                val tile = map.tileAt(col, row) ?: continue
                drawTile(map, col, row, tile)
            }
        }
    }

    private fun drawTile(map: MapData, col: Int, row: Int, tile: TileType) {
        val x = col * MapData.TILE_SIZE
        val y = row * MapData.TILE_SIZE
        layer.solidRect(MapData.TILE_SIZE, MapData.TILE_SIZE, colorFor(tile)) {
            position(x, y)
        }
        layer.solidRect(MapData.TILE_SIZE, 1, edgeFor(tile)) { position(x, y) }
        layer.solidRect(1, MapData.TILE_SIZE, edgeFor(tile)) { position(x, y) }

        when (tile) {
            TileType.Floor -> drawFloorPattern(x, y, col, row)
            TileType.TownFloor -> drawTownFloorPattern(x, y, col, row)
            TileType.Road -> drawRoadPattern(x, y, col, row)
            TileType.Grass -> drawGrassPattern(x, y, col, row)
            TileType.Wall -> drawWallPattern(x, y, col, row)
            TileType.Exit -> drawExitPattern(x, y)
            TileType.Heal -> drawHealFacility(map, col, row, x, y)
            TileType.Shop -> drawShopFacility(map, col, row, x, y)
            TileType.Ranch -> drawRanchFacility(map, col, row, x, y)
            TileType.TrainingPad -> drawTrainingPad(x, y, col, row)
            TileType.StarLamp -> drawStarLamp(x, y)
            TileType.Sign -> drawSign(x, y)
            TileType.Planned -> drawPlannedSite(x, y)
            TileType.DeepGate -> drawDeepGate(map, col, row, x, y)
            TileType.MeteorRock -> drawMeteorRock(x, y, col, row)
            TileType.Crystal -> drawCrystal(x, y)
            TileType.EnergyFence -> drawEnergyFence(x, y, col, row)
        }
    }

    private fun drawFloorPattern(x: Int, y: Int, col: Int, row: Int) {
        val dot = if ((col + row) % 2 == 0) RGBA(66, 94, 92, 100) else RGBA(52, 78, 76, 90)
        layer.solidRect(3, 3, dot) { position(x + 6, y + 7) }
        layer.solidRect(2, 2, RGBA(90, 128, 104, 90)) { position(x + 23, y + 21) }
    }

    private fun drawGrassPattern(x: Int, y: Int, col: Int, row: Int) {
        val blade = if ((col + row) % 2 == 0) RGBA(96, 186, 132, 220) else RGBA(62, 158, 122, 225)
        layer.solidRect(3, 8, blade) { position(x + 7, y + 17) }
        layer.solidRect(2, 6, RGBA(130, 214, 136, 190)) { position(x + 15, y + 10) }
        layer.solidRect(3, 7, RGBA(50, 138, 118, 215)) { position(x + 24, y + 19) }
        layer.solidRect(2, 2, RGBA(218, 255, 198, 190)) { position(x + 21, y + 8) }
        if ((col + row) % 4 == 0) {
            layer.solidRect(2, 2, RGBA(245, 255, 176, 170)) { position(x + 11, y + 24) }
        }
    }

    private fun drawTownFloorPattern(x: Int, y: Int, col: Int, row: Int) {
        layer.solidRect(24, 1, RGBA(170, 228, 220, 90)) { position(x + 4, y + 8) }
        layer.solidRect(1, 24, RGBA(170, 228, 220, 70)) { position(x + 8, y + 4) }
        val glow = if ((col + row) % 2 == 0) RGBA(214, 250, 236, 140) else RGBA(138, 218, 210, 110)
        layer.solidRect(3, 3, glow) { position(x + 22, y + 22) }
    }

    private fun drawRoadPattern(x: Int, y: Int, col: Int, row: Int) {
        layer.solidRect(22, 2, RGBA(112, 104, 86, 120)) { position(x + 5, y + 10) }
        layer.solidRect(18, 2, RGBA(132, 124, 96, 100)) { position(x + 8, y + 21) }
        if ((col + row) % 3 == 0) {
            layer.solidRect(4, 3, RGBA(152, 142, 104, 130)) { position(x + 19, y + 15) }
        }
    }

    private fun drawWallPattern(x: Int, y: Int, col: Int, row: Int) {
        layer.solidRect(24, 3, RGBA(108, 118, 133, 150)) { position(x + 4, y + 7) }
        layer.solidRect(18, 3, RGBA(64, 70, 86, 190)) { position(x + 9, y + 20) }
        if ((col + row) % 3 == 0) {
            layer.solidRect(5, 5, RGBA(126, 136, 151, 160)) { position(x + 18, y + 12) }
        }
    }

    private fun drawExitPattern(x: Int, y: Int) {
        layer.solidRect(22, 22, RGBA(94, 78, 158, 210)) { position(x + 5, y + 5) }
        layer.solidRect(12, 12, RGBA(160, 140, 230, 220)) { position(x + 10, y + 10) }
        layer.solidRect(4, 18, RGBA(210, 190, 250, 170)) { position(x + 14, y + 7) }
        layer.solidRect(18, 4, RGBA(210, 190, 250, 130)) { position(x + 7, y + 14) }
    }

    private fun drawHealFacility(map: MapData, col: Int, row: Int, x: Int, y: Int) {
        val leftSame = map.tileAt(col - 1, row) == TileType.Heal
        val rightSame = map.tileAt(col + 1, row) == TileType.Heal
        val bodyX = if (leftSame) x else x + 4
        val bodyW = when {
            leftSame && rightSame -> MapData.TILE_SIZE
            leftSame -> 28
            rightSame -> 28
            else -> 24
        }
        layer.solidRect(bodyW, 20, RGBA(38, 116, 122, 255)) { position(bodyX, y + 8) }
        layer.solidRect(bodyW + 4, 6, RGBA(120, 218, 202, 255)) { position(bodyX - 2, y + 5) }
        if (!leftSame) {
            layer.solidRect(8, 14, RGBA(17, 42, 54, 255)) { position(x + 12, y + 14) }
        }
        if (!rightSame) {
            layer.solidRect(10, 8, RGBA(178, 235, 222, 255)) { position(x + 8, y + 14) }
        }
        val crossX = if (rightSame) x + 22 else x + 10
        layer.solidRect(14, 4, RGBA(230, 248, 220, 255)) { position(crossX - 5, y + 11) }
        layer.solidRect(4, 14, RGBA(230, 248, 220, 255)) { position(crossX, y + 6) }
    }

    private fun drawShopFacility(map: MapData, col: Int, row: Int, x: Int, y: Int) {
        val leftSame = map.tileAt(col - 1, row) == TileType.Shop
        val rightSame = map.tileAt(col + 1, row) == TileType.Shop
        val bodyX = if (leftSame) x else x + 4
        val bodyW = when {
            leftSame && rightSame -> MapData.TILE_SIZE
            leftSame -> 28
            rightSame -> 28
            else -> 24
        }
        layer.solidRect(bodyW, 20, RGBA(126, 88, 47, 255)) { position(bodyX, y + 8) }
        layer.solidRect(bodyW + 4, 7, RGBA(214, 156, 76, 255)) { position(bodyX - 2, y + 5) }
        if (!leftSame) {
            layer.solidRect(9, 14, RGBA(36, 30, 34, 255)) { position(x + 12, y + 14) }
        }
        if (!rightSame) {
            layer.solidRect(12, 8, RGBA(238, 192, 104, 255)) { position(x + 7, y + 15) }
        }
        if (rightSame) {
            layer.text("店", textSize = 14.0, color = RGBA(255, 238, 180, 255), font = StarSagaFonts.font) {
                position(x + 10, y + 8)
            }
        }
    }

    private fun drawRanchFacility(map: MapData, col: Int, row: Int, x: Int, y: Int) {
        val leftSame = map.tileAt(col - 1, row) == TileType.Ranch
        val rightSame = map.tileAt(col + 1, row) == TileType.Ranch
        val bodyX = if (leftSame) x else x + 4
        val bodyW = when {
            leftSame && rightSame -> MapData.TILE_SIZE
            leftSame -> 28
            rightSame -> 28
            else -> 24
        }
        layer.solidRect(bodyW, 20, RGBA(64, 96, 82, 255)) { position(bodyX, y + 8) }
        layer.solidRect(bodyW + 4, 7, RGBA(138, 172, 108, 255)) { position(bodyX - 2, y + 5) }
        if (!leftSame) {
            layer.solidRect(9, 14, RGBA(26, 42, 34, 255)) { position(x + 12, y + 14) }
        }
        if (rightSame) {
            layer.text("牧", textSize = 14.0, color = RGBA(236, 242, 190, 255), font = StarSagaFonts.font) {
                position(x + 10, y + 8)
            }
        }
        layer.solidRect(4, 4, RGBA(212, 228, 172, 230)) { position(x + 6, y + 18) }
        layer.solidRect(4, 4, RGBA(212, 228, 172, 210)) { position(x + 22, y + 18) }
    }

    private fun drawTrainingPad(x: Int, y: Int, col: Int, row: Int) {
        val pulse = if ((col + row) % 2 == 0) RGBA(116, 228, 224, 170) else RGBA(164, 136, 232, 170)
        layer.solidRect(26, 22, RGBA(34, 72, 86, 255)) { position(x + 3, y + 6) }
        layer.solidRect(22, 18, RGBA(62, 102, 122, 255)) { position(x + 5, y + 8) }
        layer.solidRect(18, 2, pulse) { position(x + 7, y + 12) }
        layer.solidRect(18, 2, pulse) { position(x + 7, y + 22) }
        layer.solidRect(2, 14, pulse) { position(x + 9, y + 11) }
        layer.solidRect(2, 14, pulse) { position(x + 21, y + 11) }
        layer.text("育", textSize = 11.0, color = RGBA(226, 255, 242, 245), font = StarSagaFonts.font) {
            position(x + 10, y + 8)
        }
    }

    private fun drawStarLamp(x: Int, y: Int) {
        layer.solidRect(8, 18, RGBA(70, 116, 132, 255)) { position(x + 12, y + 10) }
        layer.solidRect(14, 5, RGBA(168, 238, 226, 230)) { position(x + 9, y + 7) }
        layer.solidRect(4, 22, RGBA(222, 255, 238, 110)) { position(x + 14, y + 5) }
        layer.solidRect(18, 4, RGBA(222, 255, 238, 90)) { position(x + 7, y + 12) }
    }

    private fun drawSign(x: Int, y: Int) {
        layer.solidRect(18, 12, RGBA(92, 76, 54, 255)) { position(x + 7, y + 7) }
        layer.solidRect(14, 2, RGBA(226, 198, 124, 180)) { position(x + 9, y + 11) }
        layer.solidRect(4, 13, RGBA(74, 58, 44, 255)) { position(x + 14, y + 19) }
    }

    private fun drawPlannedSite(x: Int, y: Int) {
        layer.solidRect(24, 18, RGBA(72, 88, 104, 255)) { position(x + 4, y + 10) }
        layer.solidRect(28, 5, RGBA(118, 150, 158, 255)) { position(x + 2, y + 7) }
        layer.solidRect(10, 12, RGBA(30, 38, 50, 255)) { position(x + 11, y + 16) }
        layer.text("予", textSize = 12.0, color = RGBA(220, 234, 226, 255), font = StarSagaFonts.font) {
            position(x + 10, y + 8)
        }
    }

    private fun drawDeepGate(map: MapData, col: Int, row: Int, x: Int, y: Int) {
        val leftSame = map.tileAt(col - 1, row) == TileType.DeepGate
        val rightSame = map.tileAt(col + 1, row) == TileType.DeepGate
        val archX = if (leftSame) x else x + 2
        val archW = when {
            leftSame && rightSame -> MapData.TILE_SIZE
            leftSame -> 30
            rightSame -> 30
            else -> 28
        }
        layer.solidRect(archW, 24, RGBA(66, 58, 126, 255)) { position(archX, y + 6) }
        layer.solidRect(archW - 8, 16, RGBA(18, 24, 52, 255)) { position(archX + 4, y + 12) }
        layer.solidRect(4, 20, RGBA(196, 178, 255, 150)) { position(x + 14, y + 8) }
        if (rightSame) {
            layer.text("封", textSize = 13.0, color = RGBA(232, 222, 255, 255), font = StarSagaFonts.font) {
                position(x + 9, y + 8)
            }
        }
    }

    private fun drawMeteorRock(x: Int, y: Int, col: Int, row: Int) {
        val body = if ((col + row) % 2 == 0) RGBA(92, 102, 118, 255) else RGBA(74, 88, 106, 255)
        layer.solidRect(22, 18, body) { position(x + 5, y + 10) }
        layer.solidRect(16, 6, RGBA(124, 136, 152, 210)) { position(x + 8, y + 7) }
        layer.solidRect(7, 5, RGBA(48, 58, 74, 190)) { position(x + 16, y + 18) }
        layer.solidRect(3, 3, RGBA(180, 214, 216, 150)) { position(x + 9, y + 14) }
    }

    private fun drawCrystal(x: Int, y: Int) {
        layer.solidRect(8, 22, RGBA(128, 220, 232, 240)) { position(x + 12, y + 6) }
        layer.solidRect(4, 16, RGBA(205, 252, 244, 220)) { position(x + 14, y + 9) }
        layer.solidRect(14, 6, RGBA(82, 156, 176, 210)) { position(x + 9, y + 24) }
        layer.solidRect(2, 18, RGBA(230, 255, 250, 120)) { position(x + 8, y + 8) }
        layer.solidRect(18, 2, RGBA(230, 255, 250, 100)) { position(x + 7, y + 14) }
    }

    private fun drawEnergyFence(x: Int, y: Int, col: Int, row: Int) {
        layer.solidRect(4, 20, RGBA(72, 118, 134, 255)) { position(x + 5, y + 8) }
        layer.solidRect(4, 20, RGBA(72, 118, 134, 255)) { position(x + 23, y + 8) }
        layer.solidRect(22, 3, RGBA(168, 246, 226, 190)) { position(x + 5, y + 12) }
        layer.solidRect(22, 3, RGBA(168, 246, 226, 145)) { position(x + 5, y + 22) }
        if ((col + row) % 3 == 0) {
            layer.solidRect(2, 18, RGBA(236, 255, 230, 105)) { position(x + 15, y + 9) }
        }
    }

    private fun colorFor(tile: TileType): RGBA = when (tile) {
        TileType.Floor -> RGBA(54, 92, 88, 255)
        TileType.TownFloor -> RGBA(154, 222, 216, 255)
        TileType.Road -> RGBA(132, 138, 118, 255)
        TileType.Wall -> RGBA(82, 90, 108, 255)
        TileType.Grass -> RGBA(58, 146, 116, 255)
        TileType.Exit -> RGBA(78, 62, 142, 255)
        TileType.Heal -> RGBA(48, 128, 132, 255)
        TileType.Shop -> RGBA(134, 95, 52, 255)
        TileType.Ranch -> RGBA(72, 112, 88, 255)
        TileType.TrainingPad -> RGBA(42, 92, 112, 255)
        TileType.StarLamp -> RGBA(54, 116, 132, 255)
        TileType.Sign -> RGBA(74, 58, 42, 255)
        TileType.Planned -> RGBA(62, 76, 94, 255)
        TileType.DeepGate -> RGBA(56, 46, 112, 255)
        TileType.MeteorRock -> RGBA(80, 92, 108, 255)
        TileType.Crystal -> RGBA(88, 178, 198, 255)
        TileType.EnergyFence -> RGBA(54, 112, 124, 255)
    }

    private fun edgeFor(tile: TileType): RGBA = when (tile) {
        TileType.Floor -> RGBA(64, 126, 110, 105)
        TileType.TownFloor -> RGBA(198, 248, 238, 130)
        TileType.Road -> RGBA(188, 190, 154, 145)
        TileType.Wall -> RGBA(46, 52, 66, 190)
        TileType.Grass -> RGBA(110, 210, 138, 150)
        TileType.Exit -> RGBA(170, 140, 230, 180)
        TileType.Heal -> RGBA(145, 226, 214, 180)
        TileType.Shop -> RGBA(224, 172, 92, 180)
        TileType.Ranch -> RGBA(166, 205, 126, 170)
        TileType.TrainingPad -> RGBA(174, 242, 238, 190)
        TileType.StarLamp -> RGBA(210, 255, 238, 180)
        TileType.Sign -> RGBA(210, 180, 110, 160)
        TileType.Planned -> RGBA(148, 178, 188, 140)
        TileType.DeepGate -> RGBA(190, 170, 245, 190)
        TileType.MeteorRock -> RGBA(156, 170, 184, 170)
        TileType.Crystal -> RGBA(220, 255, 248, 180)
        TileType.EnergyFence -> RGBA(190, 255, 232, 170)
    }
}
