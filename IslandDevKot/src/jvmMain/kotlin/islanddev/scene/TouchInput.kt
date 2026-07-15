package islanddev.scene

actual object TouchInput {
    actual fun install(context: Any?) = Unit
    actual fun consumeTap(): TouchPoint? = null
}
