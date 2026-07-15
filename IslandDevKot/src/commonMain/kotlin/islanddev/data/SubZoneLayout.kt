package islanddev.data

data class SubZoneColumnRange(
    val subZoneId: Int,
    val name: String,
    val parentZoneId: Int,
    val order: Int,
    val startCol: Int,
    val endColExclusive: Int
) {
    operator fun contains(col: Int): Boolean = col in startCol until endColExclusive
}

object SubZoneLayout {
    private const val ZONE_COLUMNS = 20

    val ranges: List<SubZoneColumnRange> = GameData.SUB_ZONES
        .groupBy { it.parentZoneId }
        .toSortedMap()
        .flatMap { (zoneId, definitions) ->
            val ordered = definitions.sortedBy { it.order }
            ordered.map { definition ->
                val start = firstColumnForOrder(definition.order, ordered.size)
                val end = firstColumnForOrder(definition.order + 1, ordered.size)
                SubZoneColumnRange(
                    subZoneId = definition.id,
                    name = definition.name,
                    parentZoneId = zoneId,
                    order = definition.order,
                    startCol = zoneId * ZONE_COLUMNS + start,
                    endColExclusive = zoneId * ZONE_COLUMNS + end
                )
            }
        }

    fun atColumn(col: Int): SubZoneColumnRange? =
        ranges.firstOrNull { col in it }

    fun byId(subZoneId: Int): SubZoneColumnRange? =
        ranges.firstOrNull { it.subZoneId == subZoneId }

    private fun firstColumnForOrder(order: Int, count: Int): Int {
        if (order <= 0) return 0
        if (order >= count) return ZONE_COLUMNS
        return (0 until ZONE_COLUMNS).first { localCol ->
            localCol * count / ZONE_COLUMNS >= order
        }
    }
}
