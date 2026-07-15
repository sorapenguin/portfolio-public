package starterra.debug

actual object DebugSaveLog {
    actual fun record(message: String) = println("StarTerraSave $message")
}
