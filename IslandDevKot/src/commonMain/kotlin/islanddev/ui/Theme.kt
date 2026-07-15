package islanddev.ui

import islanddev.data.GameData
import korlibs.image.color.RGBA
import korlibs.korge.view.Container
import korlibs.korge.view.container
import korlibs.korge.view.position
import korlibs.korge.view.solidRect

internal object IslandTheme {
    object Color {
        val Background = RGBA(7, 16, 24, 255)
        val Hud = RGBA(19, 32, 51, 255)
        val HudBorder = RGBA(44, 66, 95, 255)
        val Panel = RGBA(20, 28, 42, 255)
        val PanelBorder = RGBA(64, 86, 116, 255)
        val Text = RGBA(234, 242, 255, 255)
        val MutedText = RGBA(159, 178, 199, 255)
        val Accent = RGBA(244, 196, 48, 255)
        val Button = RGBA(36, 93, 143, 255)
        val ButtonImportant = RGBA(201, 130, 30, 255)
        val ButtonDisabled = RGBA(57, 70, 84, 255)
        val ButtonBorder = RGBA(104, 135, 168, 255)
        val ModalScrim = RGBA(0, 0, 0, 178)
        val Enemy = RGBA(217, 74, 74, 255)
        val Boss = RGBA(243, 154, 46, 255)
        val Player = RGBA(248, 250, 255, 255)
        val PlayerBorder = RGBA(12, 16, 22, 255)
        val Fog = RGBA(125, 135, 148, 176)
        val Facility = RGBA(92, 61, 34, 255)
        val FacilityCore = RGBA(46, 86, 111, 255)
    }

    object Size {
        const val StageWidth = 360.0
        const val StageHeight = 640.0
        const val HudHeight = 128.0
        const val TileSize = 32.0
        const val MapTop = HudHeight
        const val AvailableMapHeight = 376.0
        const val VisibleMapRows = 11
        const val MapHeight = VisibleMapRows * TileSize
        const val BottomPanelTop = MapTop + MapHeight
        const val BottomPanelHeight = StageHeight - BottomPanelTop
        const val ButtonHeight = 42.0
        const val RowHeight = 50.0
    }

    fun zoneColor(zoneId: Int, alt: Boolean = false): RGBA {
        val base = when (zoneId) {
            GameData.ZONE_BEACH -> if (alt) RGBA(202, 160, 87, 255) else RGBA(216, 179, 106, 255)
            GameData.ZONE_FOREST -> if (alt) RGBA(39, 104, 52, 255) else RGBA(47, 125, 61, 255)
            GameData.ZONE_REEF -> if (alt) RGBA(91, 96, 106, 255) else RGBA(111, 116, 128, 255)
            GameData.ZONE_DEPTHS -> if (alt) RGBA(52, 88, 65, 255) else RGBA(63, 106, 77, 255)
            GameData.ZONE_SUMMIT -> if (alt) RGBA(100, 108, 120, 255) else RGBA(119, 127, 138, 255)
            else -> Color.Background
        }
        return base
    }
}

internal fun Container.panelRect(
    width: Double,
    height: Double,
    fill: RGBA = IslandTheme.Color.Hud,
    border: RGBA = IslandTheme.Color.HudBorder,
    borderSize: Double = 2.0
): Container = container {
    solidRect(width, height, border)
    solidRect(
        (width - borderSize * 2).coerceAtLeast(0.0),
        (height - borderSize * 2).coerceAtLeast(0.0),
        fill
    ) {
        position(borderSize, borderSize)
    }
}
