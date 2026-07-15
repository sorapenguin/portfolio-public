package skyisland.ui

data class TouchPoint(val x: Float, val y: Float)

expect object TouchState {
    fun install(context: Any?)
    fun consumeTap(): TouchPoint?
}

