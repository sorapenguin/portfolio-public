package starterra.save

/** Stable small key-value wire format; IDs are known fixed identifiers, not user input. */
object OutpostSaveCodec {
    fun encode(save: OutpostSaveV1): String =
        "version=${save.version}|shards=${save.collectedShardIds.sorted().joinToString(",")}|active=${save.coreActivated}"

    fun decode(raw: String, knownIds: Set<String>): OutpostSaveV1? {
        val fields = raw.split('|').mapNotNull {
            val parts = it.split('=', limit = 2)
            if (parts.size == 2) parts[0] to parts[1] else null
        }.toMap()
        val version = fields["version"]?.toIntOrNull() ?: return null
        if (version != OutpostSaveV1.VERSION) return null
        val active = when (fields["active"]) { "true" -> true; "false" -> false; else -> return null }
        val shardText = fields["shards"] ?: return null
        val ids = shardText.split(',').filter { it.isNotBlank() }.filter { it in knownIds }.toSet()
        // A non-empty shard field containing only unknown identifiers is safe: normalize it to empty.
        return OutpostSaveV1(version, ids, active).takeIf { !(it.coreActivated && it.collectedShardIds.size != knownIds.size) }
    }
}
