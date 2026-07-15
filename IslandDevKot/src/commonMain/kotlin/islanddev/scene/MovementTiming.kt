package islanddev.scene

object MovementTiming {
    fun effectiveDelta(deltaSeconds: Double, maxDeltaSeconds: Double): Double =
        if (maxDeltaSeconds <= 0.0) {
            0.0
        } else {
            deltaSeconds.coerceIn(0.0, maxDeltaSeconds)
        }
}
