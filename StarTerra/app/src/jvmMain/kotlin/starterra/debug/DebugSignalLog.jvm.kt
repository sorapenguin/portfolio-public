package starterra.debug
actual object DebugSignalLog { actual fun record(message: String) = println("StarTerraSignal $message") }
