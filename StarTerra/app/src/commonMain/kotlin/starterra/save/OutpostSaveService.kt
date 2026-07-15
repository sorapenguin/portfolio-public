package starterra.save

import starterra.game.CoreState
import starterra.game.OutpostProgress

interface KeyValueStore {
    fun read(key: String): String?
    fun write(key: String, value: String)
}

class OutpostSaveService(private val store: KeyValueStore, private val knownIds: Set<String>) {
    fun load(): OutpostProgress {
        val raw = try { store.read(SAVE_KEY) } catch (_: Throwable) { return OutpostProgress() }
        return raw?.let { OutpostSaveCodec.decode(it, knownIds)?.toProgress() } ?: OutpostProgress()
    }

    fun save(progress: OutpostProgress): Boolean = try {
        val data = OutpostSaveV1(
            collectedShardIds = progress.collectedShardIds.intersect(knownIds),
            coreActivated = progress.coreState == CoreState.ACTIVE,
        )
        store.write(SAVE_KEY, OutpostSaveCodec.encode(data))
        true
    } catch (_: Throwable) {
        false
    }

    companion object { const val SAVE_KEY = "starterra.outpost.save.v1" }
}

expect object PlatformOutpostSave {
    fun load(): Pair<OutpostProgress, Boolean>
    fun save(progress: OutpostProgress, signalLinked: Boolean)
}
