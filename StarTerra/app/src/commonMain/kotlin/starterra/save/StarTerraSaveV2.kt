package starterra.save

import starterra.game.CoreState
import starterra.game.OutpostProgress

data class StarTerraSaveV2(val version: Int = 2, val collectedShardIds: Set<String>, val coreActivated: Boolean, val signalLinked: Boolean) {
    fun toProgress(): OutpostProgress = OutpostSaveV1(1, collectedShardIds, coreActivated).toProgress()
    fun valid(known: Set<String>) = !(coreActivated && collectedShardIds.size != known.size) && !(signalLinked && (!coreActivated || collectedShardIds.size != known.size))
}

object StarTerraSaveCodec {
    fun encode(v: StarTerraSaveV2) = "version=2|shards=${v.collectedShardIds.sorted().joinToString(",")}|active=${v.coreActivated}|signal=${v.signalLinked}"
    fun decode(raw: String, known: Set<String>): StarTerraSaveV2? {
        val f = raw.split('|').mapNotNull { it.split('=', limit = 2).takeIf { p -> p.size == 2 }?.let { p -> p[0] to p[1] } }.toMap()
        if (f["version"] != "2") return null
        val active = when (f["active"]) { "true" -> true; "false" -> false; else -> return null }
        val signal = when (f["signal"]) { "true" -> true; "false" -> false; else -> return null }
        val ids = f["shards"]?.split(',')?.filter { it in known }?.toSet() ?: return null
        return StarTerraSaveV2(collectedShardIds = ids, coreActivated = active, signalLinked = signal).takeIf { it.valid(known) }
    }
}

class StarTerraSaveService(private val store: KeyValueStore, private val known: Set<String>) {
    fun load(): StarTerraSaveV2 {
        StarTerraSaveCodec.decode(store.read(V2_KEY) ?: "", known)?.let { return it }
        val legacy = OutpostSaveCodec.decode(store.read(OutpostSaveService.SAVE_KEY) ?: "", known)
        val migrated = legacy?.let { StarTerraSaveV2(collectedShardIds = it.collectedShardIds, coreActivated = it.coreActivated, signalLinked = false) }
        if (migrated != null) store.write(V2_KEY, StarTerraSaveCodec.encode(migrated))
        return migrated ?: StarTerraSaveV2(collectedShardIds = emptySet(), coreActivated = false, signalLinked = false)
    }
    fun save(progress: OutpostProgress, signalLinked: Boolean) = runCatching { store.write(V2_KEY, StarTerraSaveCodec.encode(StarTerraSaveV2(collectedShardIds = progress.collectedShardIds.intersect(known), coreActivated = progress.coreState == CoreState.ACTIVE, signalLinked = signalLinked))) }.isSuccess
    companion object { const val V2_KEY = "starterra.game.save.v2" }
}
