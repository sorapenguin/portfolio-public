package starterra.world

data class StarShard(val id: String, val cell: GridCell)

object FirstChapterContent {
    val startCell = GridCell(2, 2)
    val starShards = listOf(
        StarShard("shard_1", GridCell(6, 8)),
        StarShard("shard_2", GridCell(11, 13)),
        StarShard("shard_3", GridCell(6, 17)),
    )
}
