package starterra.debug

/** DEBUG-only platform log sink for physical-device input verification. */
expect object DebugInputLog {
    fun record(message: String)
}
