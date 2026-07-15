package starsaga.map

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class T1MapDataTest {
    @Test
    fun firstTownHasReachableFacilitiesAndExits() {
        val map = MapData.get(T1MapProgress.FIRST_TOWN_MAP_ID)

        assertEquals(24, map.columns)
        assertEquals(20, map.rows)
        assertPassable(map, T1MapProgress.DEFAULT_SPAWN)
        listOf(
            GridCell(5, 5),
            GridCell(9, 5),
            GridCell(14, 5),
            GridCell(5, 14),
            T1MapProgress.FIRST_TOWN_ROAD_SPAWN,
        ).forEach { target ->
        assertReachable(map, T1MapProgress.DEFAULT_SPAWN, target)
        }
        assertReachable(map, T1MapProgress.DEFAULT_SPAWN, T1MapProgress.FIRST_TOWN_WARP)

        assertNotNull(map.exits.firstOrNull { it.id == "first_town_to_outskirts" })
        assertTrue(map.exits.none { it.id == "first_town_to_legacy_deep_gate" })
        assertEquals(TileType.Planned, map.tileAt(2, 15))
    }

    @Test
    fun settlementOutskirtsHasReachableReturnAndClosedFutureExit() {
        val map = MapData.get(T1MapProgress.SETTLEMENT_OUTSKIRTS_MAP_ID)

        assertEquals(28, map.columns)
        assertEquals(22, map.rows)
        assertPassable(map, T1MapProgress.SETTLEMENT_OUTSKIRTS_SPAWN)
        assertReachable(map, T1MapProgress.SETTLEMENT_OUTSKIRTS_SPAWN, GridCell(2, 10))
        assertReachable(map, T1MapProgress.SETTLEMENT_OUTSKIRTS_SPAWN, T1MapProgress.SETTLEMENT_OUTSKIRTS_FUTURE_EXIT_APPROACH)
        assertTrue(map.isPassable(25, 10))
        assertEquals(TileType.Exit, map.tileAt(25, 10))
        assertTrue(countTiles(map, TileType.Grass) > 0)
        assertTrue(countTiles(map, TileType.Road) > 0)
    }

    @Test
    fun stargrassForkConnectsOutskirtsDeepGateRoadAndHeal() {
        val map = MapData.get(T1MapProgress.STARGRASS_FORK_MAP_ID)

        assertEquals(34, map.columns)
        assertEquals(24, map.rows)
        assertPassable(map, T1MapProgress.STARGRASS_FORK_WEST_SPAWN)
        assertReachable(map, T1MapProgress.STARGRASS_FORK_WEST_SPAWN, GridCell(2, 12))
        assertReachable(map, T1MapProgress.STARGRASS_FORK_WEST_SPAWN, T1MapProgress.STARGRASS_FORK_HEAL)
        assertReachable(map, T1MapProgress.STARGRASS_FORK_WEST_SPAWN, T1MapProgress.STARGRASS_FORK_EAST_SPAWN)
        assertEquals(TileType.Heal, map.tileAt(T1MapProgress.STARGRASS_FORK_HEAL.col, T1MapProgress.STARGRASS_FORK_HEAL.row))
        assertNotNull(map.exits.firstOrNull { it.id == "stargrass_fork_to_outskirts" })
        assertNotNull(map.exits.firstOrNull { it.id == "stargrass_fork_to_deep_gate_road" })
    }

    @Test
    fun deepGateRoadConnectsForkAndLegacyDeepGate() {
        val map = MapData.get(T1MapProgress.DEEP_GATE_ROAD_MAP_ID)

        assertEquals(30, map.columns)
        assertEquals(24, map.rows)
        assertPassable(map, T1MapProgress.DEEP_GATE_ROAD_WEST_SPAWN)
        assertReachable(map, T1MapProgress.DEEP_GATE_ROAD_WEST_SPAWN, GridCell(2, 12))
        assertReachable(map, T1MapProgress.DEEP_GATE_ROAD_WEST_SPAWN, T1MapProgress.DEEP_GATE_ROAD_GATE_SPAWN)
        assertNotNull(map.exits.firstOrNull { it.id == "deep_gate_road_to_stargrass_fork" })
        val outpostExit = map.exits.firstOrNull { it.id == "deep_gate_road_to_outpost" }
        assertNotNull(outpostExit)
        assertEquals(T1MapProgress.OUTPOST_MAP_ID, outpostExit.targetMapId)
    }

    @Test
    fun outpostHasReachableBossPrepFacilities() {
        val map = MapData.get(T1MapProgress.OUTPOST_MAP_ID)

        assertEquals(22, map.columns)
        assertEquals(18, map.rows)
        assertPassable(map, T1MapProgress.OUTPOST_ENTRANCE_SPAWN)
        assertReachable(map, T1MapProgress.OUTPOST_ENTRANCE_SPAWN, GridCell(4, 5))
        assertReachable(map, T1MapProgress.OUTPOST_ENTRANCE_SPAWN, GridCell(10, 5))
        assertReachable(map, T1MapProgress.OUTPOST_ENTRANCE_SPAWN, GridCell(10, 12))
        assertReachable(map, T1MapProgress.OUTPOST_ENTRANCE_SPAWN, T1MapProgress.OUTPOST_WARP)
        assertReachable(map, T1MapProgress.OUTPOST_ENTRANCE_SPAWN, T1MapProgress.OUTPOST_DEEP_GATE_SPAWN)
        assertReachable(map, T1MapProgress.OUTPOST_ENTRANCE_SPAWN, GridCell(2, 9))
        assertEquals(TileType.Heal, map.tileAt(4, 5))
        assertEquals(TileType.Ranch, map.tileAt(10, 5))
        assertEquals(TileType.Sign, map.tileAt(10, 13))
        assertEquals(TileType.Exit, map.tileAt(T1MapProgress.OUTPOST_WARP.col, T1MapProgress.OUTPOST_WARP.row))
        assertEquals(TileType.DeepGate, map.tileAt(15, 9))
    }

    @Test
    fun legacyDeepGateCanReturnToDeepGateRoad() {
        val map = MapData.get(T1MapProgress.LEGACY_PLANET_MAP_ID)
        val exit = map.exits.firstOrNull { it.id == "legacy_deep_gate_to_deep_gate_road" }

        assertNotNull(exit)
        assertReachable(map, T1MapProgress.LEGACY_DEEP_GATE_APPROACH_SPAWN, GridCell(45, 22))
        val target = MapData.get(exit.targetMapId)
        assertPassable(target, exit.targetSpawn)
    }

    @Test
    fun allM2SpawnsAndTransitionTargetsArePassable() {
        val firstTown = MapData.get(T1MapProgress.FIRST_TOWN_MAP_ID)
        val outskirts = MapData.get(T1MapProgress.SETTLEMENT_OUTSKIRTS_MAP_ID)
        val ranch = MapData.get(T1MapProgress.RANCH_MAP_ID)

        assertPassable(firstTown, T1MapProgress.DEFAULT_SPAWN)
        assertPassable(firstTown, T1MapProgress.FIRST_TOWN_ROAD_SPAWN)
        assertPassable(outskirts, T1MapProgress.SETTLEMENT_OUTSKIRTS_SPAWN)
        assertPassable(outskirts, T1MapProgress.SETTLEMENT_OUTSKIRTS_FUTURE_EXIT_APPROACH)
        assertPassable(ranch, T1MapProgress.RANCH_SPAWN)
        assertPassable(MapData.get(T1MapProgress.STARGRASS_FORK_MAP_ID), T1MapProgress.STARGRASS_FORK_WEST_SPAWN)
        assertPassable(MapData.get(T1MapProgress.DEEP_GATE_ROAD_MAP_ID), T1MapProgress.DEEP_GATE_ROAD_WEST_SPAWN)
        assertPassable(MapData.get(T1MapProgress.OUTPOST_MAP_ID), T1MapProgress.OUTPOST_ENTRANCE_SPAWN)
        assertPassable(MapData.get(T1MapProgress.OUTPOST_MAP_ID), T1MapProgress.OUTPOST_WARP_SPAWN)

        listOf(
            firstTown,
            outskirts,
            MapData.get(T1MapProgress.STARGRASS_FORK_MAP_ID),
            MapData.get(T1MapProgress.DEEP_GATE_ROAD_MAP_ID),
            MapData.get(T1MapProgress.OUTPOST_MAP_ID),
            MapData.get(T1MapProgress.LEGACY_PLANET_MAP_ID),
        ).flatMap { it.exits }.forEach { exit ->
            val targetMap = MapData.get(exit.targetMapId)
            assertPassable(targetMap, exit.targetSpawn)
        }
    }

    @Test
    fun m2AreaIdsMatchPhysicalMaps() {
        assertEquals(T1Area.FIRST_TOWN.id, T1MapProgress.areaIdFor(T1MapProgress.FIRST_TOWN_MAP_ID, T1MapProgress.DEFAULT_SPAWN))
        assertEquals(
            T1Area.SETTLEMENT_OUTSKIRTS.id,
            T1MapProgress.areaIdFor(T1MapProgress.SETTLEMENT_OUTSKIRTS_MAP_ID, T1MapProgress.SETTLEMENT_OUTSKIRTS_SPAWN),
        )
        assertEquals(
            T1Area.STARGRASS_FORK.id,
            T1MapProgress.areaIdFor(T1MapProgress.STARGRASS_FORK_MAP_ID, T1MapProgress.STARGRASS_FORK_WEST_SPAWN),
        )
        assertEquals(
            T1Area.DEEP_GATE_ROAD.id,
            T1MapProgress.areaIdFor(T1MapProgress.DEEP_GATE_ROAD_MAP_ID, T1MapProgress.DEEP_GATE_ROAD_WEST_SPAWN),
        )
        assertEquals(
            T1Area.T1_OUTPOST.id,
            T1MapProgress.areaIdFor(T1MapProgress.OUTPOST_MAP_ID, T1MapProgress.OUTPOST_ENTRANCE_SPAWN),
        )
        assertEquals(
            T1Area.DEEP_GATE.id,
            T1MapProgress.areaIdFor(T1MapProgress.LEGACY_PLANET_MAP_ID, T1MapProgress.LEGACY_DEEP_GATE_APPROACH_SPAWN),
        )
    }

    @Test
    fun outpostWarpPolicyRequiresUnlockAndIsBidirectional() {
        assertEquals(null, T1WarpPolicy.targetFor(T1MapProgress.FIRST_TOWN_MAP_ID, unlocked = false))
        assertEquals(null, T1WarpPolicy.targetFor(T1MapProgress.OUTPOST_MAP_ID, unlocked = false))

        val toOutpost = T1WarpPolicy.targetFor(T1MapProgress.FIRST_TOWN_MAP_ID, unlocked = true)
        val toTown = T1WarpPolicy.targetFor(T1MapProgress.OUTPOST_MAP_ID, unlocked = true)

        assertNotNull(toOutpost)
        assertNotNull(toTown)
        assertEquals(T1MapProgress.OUTPOST_MAP_ID, toOutpost.mapId)
        assertEquals(T1MapProgress.OUTPOST_WARP_SPAWN, toOutpost.cell)
        assertEquals(T1MapProgress.FIRST_TOWN_MAP_ID, toTown.mapId)
        assertEquals(T1MapProgress.DEFAULT_SPAWN, toTown.cell)
        assertPassable(MapData.get(toOutpost.mapId), toOutpost.cell)
        assertPassable(MapData.get(toTown.mapId), toTown.cell)
    }

    @Test
    fun outpostDeepGateTileIsReachableByInteraction() {
        val map = MapData.get(T1MapProgress.OUTPOST_MAP_ID)

        assertEquals(TileType.DeepGate, map.tileAt(15, 9))
        assertReachable(map, T1MapProgress.OUTPOST_ENTRANCE_SPAWN, T1MapProgress.OUTPOST_DEEP_GATE_SPAWN)
    }

    @Test
    fun m2ExplorationCountsAreAvailableForSimulationReports() {
        val firstTown = MapData.get(T1MapProgress.FIRST_TOWN_MAP_ID)
        val outskirts = MapData.get(T1MapProgress.SETTLEMENT_OUTSKIRTS_MAP_ID)

        val townToOutskirtsExit = PathFinder.findPath(firstTown, T1MapProgress.DEFAULT_SPAWN, T1MapProgress.FIRST_TOWN_ROAD_SPAWN)
        val outskirtsToFutureExit = PathFinder.findPath(
            outskirts,
            T1MapProgress.SETTLEMENT_OUTSKIRTS_SPAWN,
            T1MapProgress.SETTLEMENT_OUTSKIRTS_FUTURE_EXIT_APPROACH,
        )

        assertEquals(15, townToOutskirtsExit.size)
        assertEquals(21, outskirtsToFutureExit.size)
        assertEquals(514, countPassableTiles(outskirts))
        assertEquals(114, countTiles(outskirts, TileType.Grass))
    }

    private fun assertReachable(map: MapData, start: GridCell, target: GridCell) {
        val path = PathFinder.findPath(map, start, target)
        assertTrue(path.isNotEmpty() || start == target, "Expected $target to be reachable from $start on ${map.id}")
    }

    private fun assertPassable(map: MapData, cell: GridCell) {
        assertTrue(map.isPassable(cell.col, cell.row), "Expected $cell to be passable on ${map.id}")
    }

    private fun countPassableTiles(map: MapData): Int {
        var count = 0
        for (row in 0 until map.rows) {
            for (col in 0 until map.columns) {
                if (map.isPassable(col, row)) count += 1
            }
        }
        return count
    }

    private fun countTiles(map: MapData, tile: TileType): Int {
        var count = 0
        for (row in 0 until map.rows) {
            for (col in 0 until map.columns) {
                if (map.tileAt(col, row) == tile) count += 1
            }
        }
        return count
    }
}
