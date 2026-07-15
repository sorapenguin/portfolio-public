package starsaga

import korlibs.korge.Korge
import korlibs.korge.scene.sceneContainer
import korlibs.image.color.Colors
import korlibs.math.geom.Size
import starsaga.scene.GameScene

suspend fun main() = Korge(
    windowSize = Size(360, 640),
    title = "StarSaga",
    bgcolor = Colors.BLACK,
) {
    injector.mapPrototype { GameScene() }
    sceneContainer().changeTo<GameScene>()
}
