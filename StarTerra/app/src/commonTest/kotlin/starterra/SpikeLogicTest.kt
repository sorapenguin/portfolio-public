package starterra

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import starterra.camera.FollowCamera
import starterra.entity.ActorState
import starterra.entity.PlayerController
import starterra.game.CoreActivationResult
import starterra.game.CoreState
import starterra.game.OutpostProgress
import starterra.world.FirstChapterContent
import starterra.save.KeyValueStore
import starterra.save.OutpostSaveCodec
import starterra.save.OutpostSaveService
import starterra.save.OutpostSaveV1
import starterra.save.StarTerraSaveCodec
import starterra.save.StarTerraSaveService
import starterra.save.StarTerraSaveV2
import starterra.area.AreaId
import starterra.area.Areas
import starterra.game.BeaconId
import starterra.game.BeaconResult
import starterra.game.SignalLinkState
import starterra.game.SignalPuzzleProgress
import starterra.world.GridCell
import starterra.world.SpikeMap

class SpikeLogicTest {
    private val map = SpikeMap.createDebugMap()

    @Test fun floorIsPassableAndWallAndBoundsAreNot() {
        assertTrue(map.isPassable(GridCell(2, 2)))
        assertFalse(map.isPassable(GridCell(0, 0)))
        assertFalse(map.isPassable(GridCell(-1, 2)))
    }

    @Test fun playerStopsAtWallAndMovesOneCellOnFloor() {
        val player = PlayerController(GridCell(1, 1))
        assertFalse(player.tryMove(GridCell(-1, 0), map))
        assertEquals(GridCell(1, 1), player.cell)
        assertTrue(player.tryMove(GridCell(1, 0), map))
        assertEquals(GridCell(2, 1), player.cell)
    }

    @Test fun cellCoordinatesUseThirtyTwoPixels() {
        assertEquals(96.0, map.cellLeft(GridCell(3, 5)))
        assertEquals(160.0, map.cellTop(GridCell(3, 5)))
    }

    @Test fun cameraClampsToMapEdges() {
        val camera = FollowCamera(360.0, 640.0)
        assertEquals(0.0, camera.follow(0.0, 0.0, map).left)
        assertEquals(0.0, camera.follow(0.0, 0.0, map).top)
        val bottomRight = camera.follow(9999.0, 9999.0, map)
        assertEquals(152.0, bottomRight.left)
        assertEquals(128.0, bottomRight.top)
    }

    @Test fun actorDepthUsesFootY() {
        assertTrue(ActorState("lower", GridCell(1, 8)).footY > ActorState("upper", GridCell(1, 7)).footY)
    }

    @Test fun footPositionUsesTheBottomCenterOfTheLogicalCell() {
        assertEquals(Pair(112.0, 192.0), map.footPosition(GridCell(3, 5)))
    }

    @Test fun starCoreHasAnOpenRingForCirculation() {
        listOf(GridCell(6, 10), GridCell(6, 11), GridCell(7, 9), GridCell(8, 9), GridCell(9, 10), GridCell(9, 11), GridCell(7, 12), GridCell(8, 12)).forEach {
            assertTrue(map.isPassable(it), "core circulation cell $it should remain open")
        }
        assertFalse(map.isPassable(GridCell(7, 10)))
        assertFalse(map.isPassable(GridCell(8, 11)))
    }

    @Test fun shardCollectionIsUniqueAndMakesCoreReadyAtThree() {
        var progress = OutpostProgress()
        progress = progress.collectShard("a").collectShard("a").collectShard("b")
        assertEquals(2, progress.shardCount)
        assertEquals(CoreState.DORMANT, progress.coreState)
        progress = progress.collectShard("c")
        assertEquals(3, progress.shardCount)
        assertEquals(CoreState.READY, progress.coreState)
        assertEquals(progress, progress.collectShard("d"))
    }

    @Test fun coreOnlyActivatesWhenReadyAndStaysActive() {
        val dormant = OutpostProgress()
        assertFalse(dormant.canActivateCore())
        assertEquals(CoreActivationResult.INSUFFICIENT_SHARDS, dormant.activateCore().second)
        val ready = dormant.collectShard("a").collectShard("b").collectShard("c")
        val (active, result) = ready.activateCore()
        assertEquals(CoreActivationResult.SUCCESS, result)
        assertEquals(CoreState.ACTIVE, active.coreState)
        assertTrue(active.completed)
        assertEquals(CoreActivationResult.ALREADY_ACTIVE, active.activateCore().second)
        assertEquals(3, active.shardCount)
    }

    @Test fun shardsAreUniquePassableAndReachableFromStart() {
        val shards = FirstChapterContent.starShards
        assertEquals(3, shards.size)
        assertEquals(3, shards.map { it.id }.toSet().size)
        assertEquals(3, shards.map { it.cell }.toSet().size)
        val reachable = breadthFirstReachable(FirstChapterContent.startCell)
        shards.forEach { shard ->
            assertTrue(map.isPassable(shard.cell))
            assertFalse(shard.cell == FirstChapterContent.startCell)
            assertFalse(shard.cell in SpikeMap.CORE_CELLS)
            assertTrue(shard.cell in reachable, "${shard.id} must be reachable")
        }
    }

    @Test fun coreActivationRequiresOrthogonalAdjacency() {
        assertTrue(map.isCoreActAdjacent(GridCell(6, 10)))
        assertTrue(map.isCoreActAdjacent(GridCell(7, 9)))
        assertTrue(map.isCoreActAdjacent(GridCell(9, 11)))
        assertFalse(map.isCoreActAdjacent(GridCell(6, 9)))
        assertFalse(map.isCoreActAdjacent(GridCell(7, 10)))
        assertFalse(map.isCoreActAdjacent(GridCell(2, 2)))
    }

    @Test fun saveCodecRoundTripsDormantReadyAndActiveStates() {
        val ids = FirstChapterContent.starShards.map { it.id }.toSet()
        listOf(
            OutpostSaveV1(collectedShardIds = emptySet(), coreActivated = false) to CoreState.DORMANT,
            OutpostSaveV1(collectedShardIds = setOf("shard_1"), coreActivated = false) to CoreState.DORMANT,
            OutpostSaveV1(collectedShardIds = ids, coreActivated = false) to CoreState.READY,
            OutpostSaveV1(collectedShardIds = ids, coreActivated = true) to CoreState.ACTIVE,
        ).forEach { (save, expectedState) ->
            val decoded = OutpostSaveCodec.decode(OutpostSaveCodec.encode(save), ids)
            assertEquals(save, decoded)
            assertEquals(expectedState, decoded!!.toProgress().coreState)
        }
    }

    @Test fun saveCodecNormalizesOrderDuplicatesAndUnknownIds() {
        val known = FirstChapterContent.starShards.map { it.id }.toSet()
        val decoded = OutpostSaveCodec.decode("active=false|shards=unknown,shard_2,shard_1,shard_2|version=1", known)
        assertEquals(setOf("shard_1", "shard_2"), decoded!!.collectedShardIds)
    }

    @Test fun saveCodecFallsBackForMalformedVersionAndInvalidActiveData() {
        val known = FirstChapterContent.starShards.map { it.id }.toSet()
        assertEquals(null, OutpostSaveCodec.decode("not a save", known))
        assertEquals(null, OutpostSaveCodec.decode("version=2|shards=|active=false", known))
        assertEquals(null, OutpostSaveCodec.decode("version=1|shards=shard_1|active=true", known))
    }

    @Test fun fakeStorePersistsProgressUnderStarTerraKeyAndBadDataDoesNotEscape() {
        val memory = mutableMapOf<String, String>()
        val store = object : KeyValueStore {
            override fun read(key: String): String? = memory[key]
            override fun write(key: String, value: String) { memory[key] = value }
        }
        val service = OutpostSaveService(store, FirstChapterContent.starShards.map { it.id }.toSet())
        assertEquals(OutpostProgress(), service.load())
        val active = OutpostProgress().collectShard("shard_1").collectShard("shard_2").collectShard("shard_3").activateCore().first
        assertTrue(service.save(active))
        assertTrue(memory.containsKey(OutpostSaveService.SAVE_KEY))
        assertEquals(active, service.load())
        memory[OutpostSaveService.SAVE_KEY] = "broken"
        assertEquals(OutpostProgress(), service.load())
    }

    @Test fun outpostGateRequiresActiveCoreAndUsesOrthogonalAdjacency() {
        val gate = Areas.starOutpost
        assertFalse(OutpostProgress().coreState == CoreState.ACTIVE)
        assertFalse(Areas.isGateAdjacent(gate, GridCell(6, 1)))
        assertTrue(Areas.isGateAdjacent(gate, GridCell(6, 2)))
        assertTrue(Areas.isGateAdjacent(gate, GridCell(7, 3)))
        assertFalse(Areas.isGateAdjacent(gate, GridCell(6, 3)))
    }

    @Test fun signalFieldSpawnsGatesAndLandmarksAreReachable() {
        val area = Areas.signalField
        assertEquals(AreaId.SIGNAL_FIELD, area.id)
        assertTrue(area.map.isPassable(area.defaultSpawn))
        assertTrue(Areas.starOutpost.map.isPassable(GridCell(7, 4)))
        val reachable = breadthFirstReachable(area.map, area.defaultSpawn)
        listOf(GridCell(6, 10), GridCell(9, 11), GridCell(5, 6), GridCell(11, 11), GridCell(6, 16), GridCell(7, 20)).forEach {
            assertTrue(it in reachable, "Signal Field landmark approach $it must be reachable")
        }
        assertTrue(Areas.isGateAdjacent(area, GridCell(7, 20)))
        assertFalse(Areas.isGateAdjacent(area, GridCell(6, 20)))
    }

    @Test fun signalPuzzleUsesFixedRouteAndOnlineIsStable() {
        var puzzle = SignalPuzzleProgress()
        assertEquals(SignalLinkState.IDLE, puzzle.state)
        puzzle = puzzle.startRouting()
        assertEquals(SignalLinkState.ROUTING, puzzle.state)
        assertEquals(BeaconId.A, puzzle.expectedNext())
        puzzle = puzzle.activate(BeaconId.A).first
        assertEquals(listOf(BeaconId.A), puzzle.acceptedBeaconIds)
        assertEquals(BeaconId.C, puzzle.expectedNext())
        puzzle = puzzle.activate(BeaconId.C).first
        assertEquals(listOf(BeaconId.A, BeaconId.C), puzzle.acceptedBeaconIds)
        assertEquals(BeaconId.B, puzzle.expectedNext())
        val (online, result) = puzzle.activate(BeaconId.B)
        assertEquals(BeaconResult.COMPLETED, result)
        assertEquals(SignalLinkState.ONLINE, online.state)
        assertEquals(online, online.activate(BeaconId.A).first)
        assertEquals(BeaconResult.IGNORED, online.activate(BeaconId.A).second)
    }

    @Test fun signalPuzzleRejectsWrongOrdersAndCanRecover() {
        fun routed() = SignalPuzzleProgress().startRouting()
        listOf(BeaconId.B, BeaconId.C).forEach { first ->
            val (reset, result) = routed().activate(first)
            assertEquals(BeaconResult.REJECTED, result)
            assertEquals(SignalLinkState.ROUTING, reset.state)
            assertTrue(reset.acceptedBeaconIds.isEmpty())
        }
        listOf(listOf(BeaconId.A, BeaconId.B), listOf(BeaconId.A, BeaconId.C, BeaconId.A)).forEach { input ->
            var puzzle = routed()
            input.forEach { puzzle = puzzle.activate(it).first }
            assertTrue(puzzle.acceptedBeaconIds.isEmpty())
        }
        var puzzle = routed()
        puzzle = puzzle.activate(BeaconId.B).first
        listOf(BeaconId.A, BeaconId.C, BeaconId.B).forEach { puzzle = puzzle.activate(it).first }
        assertEquals(SignalLinkState.ONLINE, puzzle.state)
    }

    @Test fun signalPuzzleIgnoresIdleBeaconAndResetsOnlyRoutingOnAreaExit() {
        val idle = SignalPuzzleProgress()
        assertEquals(BeaconResult.NEED_TERMINAL, idle.activate(BeaconId.A).second)
        assertEquals(idle, idle.activate(BeaconId.A).first)
        val routing = idle.startRouting().activate(BeaconId.A).first.leaveArea()
        assertEquals(SignalLinkState.IDLE, routing.state)
        assertTrue(routing.acceptedBeaconIds.isEmpty())
        var online = idle.startRouting()
        listOf(BeaconId.A, BeaconId.C, BeaconId.B).forEach { online = online.activate(it).first }
        assertEquals(online, online.leaveArea())
    }

    @Test fun terminalAndBeaconActRequireOrthogonalAdjacency() {
        val terminal = Areas.signalField.scenery.first { it.name == "terminal" }.cell
        listOf("beaconA", "beaconB", "beaconC").forEach { name ->
            val beacon = Areas.signalField.scenery.first { it.name == name }.cell
            assertTrue(Areas.isOrthogonallyAdjacent(GridCell(beacon.column - 1, beacon.row), beacon), name)
            assertFalse(Areas.isOrthogonallyAdjacent(GridCell(beacon.column - 1, beacon.row - 1), beacon), name)
            assertFalse(Areas.isOrthogonallyAdjacent(GridCell(beacon.column - 2, beacon.row), beacon), name)
        }
        assertTrue(Areas.isOrthogonallyAdjacent(GridCell(terminal.column, terminal.row - 1), terminal))
        assertFalse(Areas.isOrthogonallyAdjacent(GridCell(terminal.column - 1, terminal.row - 1), terminal))
        assertFalse(Areas.isOrthogonallyAdjacent(GridCell(terminal.column, terminal.row - 2), terminal))
    }

    @Test fun saveV2RoundTripsAndNormalizesKnownShardIds() {
        val known = FirstChapterContent.starShards.map { it.id }.toSet()
        listOf(
            StarTerraSaveV2(collectedShardIds = emptySet(), coreActivated = false, signalLinked = false),
            StarTerraSaveV2(collectedShardIds = known, coreActivated = true, signalLinked = false),
            StarTerraSaveV2(collectedShardIds = known, coreActivated = true, signalLinked = true),
        ).forEach { save -> assertEquals(save, StarTerraSaveCodec.decode(StarTerraSaveCodec.encode(save), known)) }
        val normalized = StarTerraSaveCodec.decode("version=2|shards=unknown,shard_2,shard_1,shard_2|active=false|signal=false", known)
        assertEquals(setOf("shard_1", "shard_2"), normalized!!.collectedShardIds)
    }

    @Test fun saveV2RejectsInvalidPayloads() {
        val known = FirstChapterContent.starShards.map { it.id }.toSet()
        listOf(
            "version=2|shards=shard_1,shard_2,shard_3|active=false|signal=true",
            "version=2|shards=shard_1|active=true|signal=true",
            "broken",
            "version=3|shards=|active=false|signal=false",
        ).forEach { assertEquals(null, StarTerraSaveCodec.decode(it, known)) }
    }

    @Test fun v2LoadMigratesLegacyAndPrefersValidV2WithoutDeletingV1() {
        val known = FirstChapterContent.starShards.map { it.id }.toSet()
        val memory = mutableMapOf<String, String>()
        val store = object : KeyValueStore {
            override fun read(key: String) = memory[key]
            override fun write(key: String, value: String) { memory[key] = value }
        }
        val service = StarTerraSaveService(store, known)
        listOf(emptySet<String>() to false, known to false, known to true).forEach { (ids, active) ->
            memory.clear(); memory[OutpostSaveService.SAVE_KEY] = OutpostSaveCodec.encode(OutpostSaveV1(collectedShardIds = ids, coreActivated = active))
            val migrated = service.load()
            assertEquals(ids, migrated.collectedShardIds); assertEquals(active, migrated.coreActivated); assertFalse(migrated.signalLinked)
            assertTrue(memory.containsKey(OutpostSaveService.SAVE_KEY)); assertTrue(memory.containsKey(StarTerraSaveService.V2_KEY))
        }
        val v2 = StarTerraSaveV2(collectedShardIds = known, coreActivated = true, signalLinked = true)
        memory[StarTerraSaveService.V2_KEY] = StarTerraSaveCodec.encode(v2)
        assertEquals(v2, service.load())
        memory[StarTerraSaveService.V2_KEY] = "broken"
        assertFalse(service.load().signalLinked)
        memory[StarTerraSaveService.V2_KEY] = "broken"
        memory[OutpostSaveService.SAVE_KEY] = "broken"
        assertEquals(StarTerraSaveV2(collectedShardIds = emptySet(), coreActivated = false, signalLinked = false), service.load())
    }

    private fun breadthFirstReachable(start: GridCell): Set<GridCell> {
        return breadthFirstReachable(map, start)
    }

    private fun breadthFirstReachable(targetMap: SpikeMap, start: GridCell): Set<GridCell> {
        val visited = mutableSetOf(start)
        val pending = ArrayDeque<GridCell>().apply { add(start) }
        val directions = listOf(GridCell(1, 0), GridCell(-1, 0), GridCell(0, 1), GridCell(0, -1))
        while (pending.isNotEmpty()) {
            val current = pending.removeFirst()
            directions.map { current + it }.filter { targetMap.isPassable(it) && visited.add(it) }.forEach { pending.add(it) }
        }
        return visited
    }
}
