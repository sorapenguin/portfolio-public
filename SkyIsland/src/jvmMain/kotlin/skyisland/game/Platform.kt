package skyisland.game

actual fun currentEpochSeconds(): Long = System.currentTimeMillis() / 1000L

