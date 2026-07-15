package islanddev.scene

import islanddev.IslandSession
import islanddev.MainScene
import islanddev.data.GameData
import islanddev.ui.IslandTheme
import islanddev.ui.actionButton
import korlibs.image.color.RGBA
import korlibs.korge.scene.Scene
import korlibs.korge.view.Container
import korlibs.korge.view.SContainer
import korlibs.korge.view.addUpdater
import korlibs.korge.view.container
import korlibs.korge.view.position
import korlibs.korge.view.solidRect
import korlibs.korge.view.text
import kotlinx.coroutines.launch

class EndingScene : Scene() {
    override suspend fun SContainer.sceneMain() {
        solidRect(360, 640, RGBA(10, 18, 30, 255))
        val island = container {
            position(0, 160)
            for (zoneId in GameData.ZONE_BEACH..GameData.ZONE_SUMMIT) {
                solidRect(20 * GridScene.CELL_SIZE, 16 * GridScene.CELL_SIZE, IslandTheme.zoneColor(zoneId)) {
                    position(zoneId * 20 * GridScene.CELL_SIZE, 0)
                }
            }
            IslandSession.save.builtFacilityIds.forEach { facilityId ->
                solidRect(18, 18, RGBA(40, 110, 240, 255)) {
                    position(80 + facilityId * 360, 220 + (facilityId % 3) * 45)
                }
            }
        }
        val message = text(
            "この島はあなたのものになった",
            textSize = 22,
            color = RGBA(255, 255, 255, 255)
        ) {
            position(28, 70)
            alpha = 0.0
        }
        val continueButton = actionButton(
            "続けてプレイ",
            100.0,
            565.0,
            160.0,
            44.0
        ) {
            launch { sceneContainer.changeTo<MainScene>() }
        }
        continueButton.visible = false

        var elapsed = 0.0
        addUpdater { delta ->
            elapsed += delta.inWholeMicroseconds / 1_000_000.0
            message.alpha = (elapsed / 2.0).coerceIn(0.0, 1.0)
            val panRatio = (elapsed / 10.0).coerceIn(0.0, 1.0)
            island.x = -(GridScene.COLUMNS * GridScene.CELL_SIZE - 360.0) * panRatio
            if (elapsed >= 4.0) continueButton.visible = true
        }
    }
}
