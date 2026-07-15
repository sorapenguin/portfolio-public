package starterra.debug

actual object DebugInputLog {
    actual fun record(message: String) = println("StarTerraInput $message")
}
