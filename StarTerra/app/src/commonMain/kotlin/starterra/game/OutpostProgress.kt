package starterra.game

enum class CoreState { DORMANT, READY, ACTIVE }

enum class CoreActivationResult { SUCCESS, INSUFFICIENT_SHARDS, ALREADY_ACTIVE }

/** Immutable, save-free progress for Batch D's first chapter. */
data class OutpostProgress(
    val collectedShardIds: Set<String> = emptySet(),
    val coreState: CoreState = CoreState.DORMANT,
    val completed: Boolean = false,
) {
    val shardCount: Int get() = collectedShardIds.size

    fun collectShard(id: String): OutpostProgress {
        if (id in collectedShardIds || collectedShardIds.size >= REQUIRED_SHARDS || coreState == CoreState.ACTIVE) return this
        val ids = collectedShardIds + id
        return copy(collectedShardIds = ids, coreState = if (ids.size >= REQUIRED_SHARDS) CoreState.READY else CoreState.DORMANT)
    }

    fun canActivateCore(): Boolean = coreState == CoreState.READY

    fun activateCore(): Pair<OutpostProgress, CoreActivationResult> = when (coreState) {
        CoreState.DORMANT -> this to CoreActivationResult.INSUFFICIENT_SHARDS
        CoreState.READY -> copy(coreState = CoreState.ACTIVE, completed = true) to CoreActivationResult.SUCCESS
        CoreState.ACTIVE -> this to CoreActivationResult.ALREADY_ACTIVE
    }

    companion object { const val REQUIRED_SHARDS = 3 }
}
