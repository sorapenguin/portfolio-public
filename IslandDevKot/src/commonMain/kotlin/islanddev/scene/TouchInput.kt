package islanddev.scene

data class TouchPoint(
    val x: Float,
    val y: Float,
    val rawX: Float = x,
    val rawY: Float = y
)

expect object TouchInput {
    fun install(context: Any?)
    fun consumeTap(): TouchPoint?
}
