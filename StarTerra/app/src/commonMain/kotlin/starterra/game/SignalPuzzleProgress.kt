package starterra.game

enum class BeaconId { A, B, C }
enum class SignalLinkState { IDLE, ROUTING, ONLINE }
enum class BeaconResult { NEED_TERMINAL, ACCEPTED, REJECTED, COMPLETED, IGNORED }

data class SignalPuzzleProgress(val state: SignalLinkState = SignalLinkState.IDLE, val acceptedBeaconIds: List<BeaconId> = emptyList()) {
    fun startRouting() = if (state == SignalLinkState.ONLINE) this else SignalPuzzleProgress(SignalLinkState.ROUTING)
    fun leaveArea() = if (state == SignalLinkState.ROUTING) SignalPuzzleProgress() else this
    fun activate(id: BeaconId): Pair<SignalPuzzleProgress, BeaconResult> = when (state) {
        SignalLinkState.IDLE -> this to BeaconResult.NEED_TERMINAL
        SignalLinkState.ONLINE -> this to BeaconResult.IGNORED
        SignalLinkState.ROUTING -> {
            val order = listOf(BeaconId.A, BeaconId.C, BeaconId.B)
            if (id != order[acceptedBeaconIds.size]) SignalPuzzleProgress(SignalLinkState.ROUTING) to BeaconResult.REJECTED
            else if (acceptedBeaconIds.size == 2) SignalPuzzleProgress(SignalLinkState.ONLINE, order) to BeaconResult.COMPLETED
            else copy(acceptedBeaconIds = acceptedBeaconIds + id) to BeaconResult.ACCEPTED
        }
    }
    fun expectedNext(): BeaconId? = if (state == SignalLinkState.ROUTING) listOf(BeaconId.A, BeaconId.C, BeaconId.B).getOrNull(acceptedBeaconIds.size) else null
}
