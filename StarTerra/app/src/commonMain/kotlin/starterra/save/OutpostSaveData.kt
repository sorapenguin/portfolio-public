package starterra.save

import starterra.game.CoreState
import starterra.game.OutpostProgress

data class OutpostSaveV1(
    val version: Int = VERSION,
    val collectedShardIds: Set<String>,
    val coreActivated: Boolean,
) {
    fun toProgress(): OutpostProgress = when {
        coreActivated && collectedShardIds.size == OutpostProgress.REQUIRED_SHARDS ->
            OutpostProgress(collectedShardIds, CoreState.ACTIVE, completed = true)
        coreActivated -> OutpostProgress()
        collectedShardIds.size == OutpostProgress.REQUIRED_SHARDS ->
            OutpostProgress(collectedShardIds, CoreState.READY, completed = false)
        else -> OutpostProgress(collectedShardIds, CoreState.DORMANT, completed = false)
    }

    companion object { const val VERSION = 1 }
}
