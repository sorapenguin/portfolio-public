package starterra.camera

import starterra.world.SpikeMap
import kotlin.math.max
import kotlin.math.min

data class CameraOffset(val left: Double, val top: Double)

/** Continuous follow camera with independent safe clamping per axis. */
class FollowCamera(
    private val viewportWidth: Double,
    private val viewportHeight: Double,
) {
    fun follow(playerCenterX: Double, playerCenterY: Double, map: SpikeMap): CameraOffset {
        val worldWidth = map.columns * SpikeMap.TILE_SIZE.toDouble()
        val worldHeight = map.rows * SpikeMap.TILE_SIZE.toDouble()
        return CameraOffset(
            left = clamp(playerCenterX - viewportWidth / 2.0, 0.0, max(0.0, worldWidth - viewportWidth)),
            top = clamp(playerCenterY - viewportHeight / 2.0, 0.0, max(0.0, worldHeight - viewportHeight)),
        )
    }

    private fun clamp(value: Double, minimum: Double, maximum: Double): Double = min(max(value, minimum), maximum)
}
