package starterra

import korlibs.image.color.Colors
import korlibs.korge.Korge
import korlibs.korge.scene.sceneContainer
import korlibs.math.geom.Size
import starterra.scene.SpikeScene

/** Batch A entry point. The game deliberately contains one debug map only. */
suspend fun main() = Korge(
    windowSize = Size(360, 640),
    title = "StarTerra",
    bgcolor = Colors["#101923"],
) {
    injector.mapPrototype { SpikeScene() }
    sceneContainer().changeTo<SpikeScene>()
}
