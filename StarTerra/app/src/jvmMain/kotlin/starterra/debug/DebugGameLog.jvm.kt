package starterra.debug

actual object DebugGameLog {
    actual fun record(message: String) = println("StarTerraGame $message")
}
