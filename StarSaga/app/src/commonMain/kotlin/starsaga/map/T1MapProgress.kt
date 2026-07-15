package starsaga.map

import starsaga.data.RpgSaveData

enum class T1Area(
    val id: String,
    val displayName: String,
) {
    FIRST_TOWN("first_town", "星降りの集落"),
    SETTLEMENT_OUTSKIRTS("settlement_outskirts", "集落近郊"),
    STARGRASS_FORK("stargrass_fork", "星草の分かれ道"),
    DEEP_GATE_ROAD("deep_gate_road", "深門への道"),
    T1_OUTPOST("t1_outpost", "第2前哨地"),
    DEEP_GATE("deep_gate", "DeepGate"),
}

data class MapSpawn(
    val mapId: String,
    val cell: GridCell,
)

object T1MapProgress {
    const val CURRENT_REVISION = 4
    const val FIRST_TOWN_MAP_ID = "t1_first_town"
    const val SETTLEMENT_OUTSKIRTS_MAP_ID = "t1_settlement_outskirts"
    const val STARGRASS_FORK_MAP_ID = "t1_stargrass_fork"
    const val DEEP_GATE_ROAD_MAP_ID = "t1_deep_gate_road"
    const val OUTPOST_MAP_ID = "t1_outpost"
    const val DEFAULT_MAP_ID = FIRST_TOWN_MAP_ID
    const val LEGACY_PLANET_MAP_ID = "planet_t1"
    const val LEGACY_EAST_FIELD_MAP_ID = "planet_t1_east"
    const val RANCH_MAP_ID = "ranch"
    const val DEFAULT_AREA_ID = "first_town"

    val DEFAULT_SPAWN = GridCell(6, 10)
    val FIRST_TOWN_ROAD_SPAWN = GridCell(21, 10)
    val SETTLEMENT_OUTSKIRTS_SPAWN = GridCell(3, 10)
    val SETTLEMENT_OUTSKIRTS_FUTURE_EXIT_APPROACH = GridCell(24, 10)
    val STARGRASS_FORK_WEST_SPAWN = GridCell(3, 12)
    val STARGRASS_FORK_EAST_SPAWN = GridCell(30, 12)
    val STARGRASS_FORK_HEAL = GridCell(16, 6)
    val DEEP_GATE_ROAD_WEST_SPAWN = GridCell(3, 12)
    val DEEP_GATE_ROAD_GATE_SPAWN = GridCell(27, 12)
    val OUTPOST_ENTRANCE_SPAWN = GridCell(3, 9)
    val OUTPOST_DEEP_GATE_SPAWN = GridCell(13, 9)
    val OUTPOST_WARP_SPAWN = GridCell(6, 13)
    val FIRST_TOWN_WARP = GridCell(12, 16)
    val OUTPOST_WARP = GridCell(6, 13)
    val LEGACY_DEEP_GATE_APPROACH_SPAWN = GridCell(47, 22)
    val RANCH_SPAWN = GridCell(10, 9)

    fun knownMapIds(): Set<String> = setOf(
        FIRST_TOWN_MAP_ID,
        SETTLEMENT_OUTSKIRTS_MAP_ID,
        STARGRASS_FORK_MAP_ID,
        DEEP_GATE_ROAD_MAP_ID,
        OUTPOST_MAP_ID,
        LEGACY_PLANET_MAP_ID,
        LEGACY_EAST_FIELD_MAP_ID,
        RANCH_MAP_ID,
    )

    fun isKnownMapId(mapId: String): Boolean = mapId in knownMapIds()

    fun isLegacyFieldMap(mapId: String): Boolean =
        mapId == LEGACY_PLANET_MAP_ID || mapId == LEGACY_EAST_FIELD_MAP_ID

    fun safeSpawnFor(mapId: String, t1BossCleared: Boolean): MapSpawn {
        return when (mapId) {
            FIRST_TOWN_MAP_ID -> MapSpawn(FIRST_TOWN_MAP_ID, DEFAULT_SPAWN)
            SETTLEMENT_OUTSKIRTS_MAP_ID -> MapSpawn(SETTLEMENT_OUTSKIRTS_MAP_ID, SETTLEMENT_OUTSKIRTS_SPAWN)
            STARGRASS_FORK_MAP_ID -> MapSpawn(STARGRASS_FORK_MAP_ID, STARGRASS_FORK_WEST_SPAWN)
            DEEP_GATE_ROAD_MAP_ID -> MapSpawn(DEEP_GATE_ROAD_MAP_ID, DEEP_GATE_ROAD_WEST_SPAWN)
            OUTPOST_MAP_ID -> MapSpawn(OUTPOST_MAP_ID, OUTPOST_ENTRANCE_SPAWN)
            LEGACY_PLANET_MAP_ID -> if (t1BossCleared) {
                MapSpawn(FIRST_TOWN_MAP_ID, DEFAULT_SPAWN)
            } else {
                MapSpawn(FIRST_TOWN_MAP_ID, DEFAULT_SPAWN)
            }
            LEGACY_EAST_FIELD_MAP_ID -> MapSpawn(FIRST_TOWN_MAP_ID, DEFAULT_SPAWN)
            RANCH_MAP_ID -> MapSpawn(RANCH_MAP_ID, RANCH_SPAWN)
            else -> MapSpawn(DEFAULT_MAP_ID, DEFAULT_SPAWN)
        }
    }

    fun areaIdFor(mapId: String, cell: GridCell): String =
        areaFor(mapId, cell).id

    fun areaFor(mapId: String, cell: GridCell): T1Area =
        when (mapId) {
            RANCH_MAP_ID -> T1Area.FIRST_TOWN
            FIRST_TOWN_MAP_ID -> T1Area.FIRST_TOWN
            SETTLEMENT_OUTSKIRTS_MAP_ID -> T1Area.SETTLEMENT_OUTSKIRTS
            STARGRASS_FORK_MAP_ID -> T1Area.STARGRASS_FORK
            DEEP_GATE_ROAD_MAP_ID -> T1Area.DEEP_GATE_ROAD
            OUTPOST_MAP_ID -> T1Area.T1_OUTPOST
            LEGACY_PLANET_MAP_ID -> when {
                cell.col in 47..53 && cell.row in 20..24 -> T1Area.DEEP_GATE
                cell.col in 2..18 && cell.row in 2..12 -> T1Area.FIRST_TOWN
                else -> T1Area.SETTLEMENT_OUTSKIRTS
            }
            LEGACY_EAST_FIELD_MAP_ID -> T1Area.SETTLEMENT_OUTSKIRTS
            else -> T1Area.FIRST_TOWN
        }
}

object SaveMigration {
    fun migrate(save: RpgSaveData): RpgSaveData {
        val normalizedLocation = normalizeLocation(save)
        val normalizedAreaId = T1MapProgress.areaIdFor(normalizedLocation.mapId, normalizedLocation.cell)
        return save.copy(
            currentMapId = normalizedLocation.mapId,
            playerCol = normalizedLocation.cell.col,
            playerRow = normalizedLocation.cell.row,
            t1MapRevision = T1MapProgress.CURRENT_REVISION,
            currentT1AreaId = normalizedAreaId,
            reachedT1Outpost = save.reachedT1Outpost || normalizedAreaId == T1Area.T1_OUTPOST.id,
        )
    }

    fun normalizeLocation(save: RpgSaveData): MapSpawn {
        val requestedMapId = save.currentMapId
        if (save.t1MapRevision < T1MapProgress.CURRENT_REVISION &&
            requestedMapId == T1MapProgress.LEGACY_PLANET_MAP_ID &&
            T1MapProgress.areaFor(requestedMapId, GridCell(save.playerCol, save.playerRow)) == T1Area.DEEP_GATE
        ) {
            return MapSpawn(T1MapProgress.OUTPOST_MAP_ID, T1MapProgress.OUTPOST_DEEP_GATE_SPAWN)
        }

        if (save.t1MapRevision < T1MapProgress.CURRENT_REVISION && T1MapProgress.isLegacyFieldMap(requestedMapId)) {
            return T1MapProgress.safeSpawnFor(requestedMapId, save.t1BossCleared)
        }

        if (!T1MapProgress.isKnownMapId(requestedMapId)) {
            return T1MapProgress.safeSpawnFor(requestedMapId, save.t1BossCleared)
        }

        val requestedMap = MapData.get(requestedMapId)
        if (requestedMap.isPassable(save.playerCol, save.playerRow)) {
            return MapSpawn(requestedMapId, GridCell(save.playerCol, save.playerRow))
        }

        val safe = T1MapProgress.safeSpawnFor(requestedMapId, save.t1BossCleared)
        val safeMap = MapData.get(safe.mapId)
        if (safeMap.isPassable(safe.cell.col, safe.cell.row)) return safe

        return MapSpawn(T1MapProgress.DEFAULT_MAP_ID, T1MapProgress.DEFAULT_SPAWN)
    }
}

object T1WarpPolicy {
    fun targetFor(mapId: String, unlocked: Boolean): MapSpawn? {
        if (!unlocked) return null
        return when (mapId) {
            T1MapProgress.FIRST_TOWN_MAP_ID -> MapSpawn(T1MapProgress.OUTPOST_MAP_ID, T1MapProgress.OUTPOST_WARP_SPAWN)
            T1MapProgress.OUTPOST_MAP_ID -> MapSpawn(T1MapProgress.FIRST_TOWN_MAP_ID, T1MapProgress.DEFAULT_SPAWN)
            else -> null
        }
    }
}
