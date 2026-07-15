package starsaga.battle

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import starsaga.data.CreatureDatabase
import starsaga.data.CreatureRole
import starsaga.map.T1MapProgress

class EncounterResolverAreaWeightsTest {
    @Test
    fun allAreasCanSelectAllT1Creatures() {
        listOf(
            T1MapProgress.SETTLEMENT_OUTSKIRTS_MAP_ID,
            T1MapProgress.STARGRASS_FORK_MAP_ID,
            T1MapProgress.DEEP_GATE_ROAD_MAP_ID,
            "unknown_area",
        ).forEach { mapId ->
            val table = EncounterResolver.weightedTableFor(mapId)
            assertEquals(CreatureDatabase.t1Creatures.map { it.id }.toSet(), table.map { it.creature.id }.toSet())
            assertTrue(table.all { it.weight > 0 })
        }
    }

    @Test
    fun areaWeightsFavorExpectedRoles() {
        val outskirts = sampleRoles(T1MapProgress.SETTLEMENT_OUTSKIRTS_MAP_ID)
        assertTrue(outskirts.getValue(CreatureRole.ATCK) > outskirts.getValue(CreatureRole.AREA))
        assertTrue(outskirts.getValue(CreatureRole.DEFN) > outskirts.getValue(CreatureRole.LUCK))

        val fork = sampleRoles(T1MapProgress.STARGRASS_FORK_MAP_ID)
        assertTrue(fork.getValue(CreatureRole.HEAL) > fork.getValue(CreatureRole.ATCK))
        assertTrue(fork.getValue(CreatureRole.AREA) > fork.getValue(CreatureRole.LUCK))

        val road = sampleRoles(T1MapProgress.DEEP_GATE_ROAD_MAP_ID)
        assertTrue(road.getValue(CreatureRole.LUCK) > road.getValue(CreatureRole.ATCK))
        assertTrue(road.getValue(CreatureRole.LUCK) > road.getValue(CreatureRole.AREA))
    }

    @Test
    fun weightedSelectionIsDeterministicForSameSeed() {
        val first = sampleIds(T1MapProgress.STARGRASS_FORK_MAP_ID, seed = 2468)
        val second = sampleIds(T1MapProgress.STARGRASS_FORK_MAP_ID, seed = 2468)

        assertEquals(first, second)
    }

    private fun sampleRoles(mapId: String, seed: Int = 1357): Map<CreatureRole, Int> {
        val counts = CreatureRole.entries.associateWith { 0 }.toMutableMap()
        val random = Random(seed)
        repeat(1_000) {
            val role = EncounterResolver.selectCreature(mapId, random).role
            counts[role] = counts.getValue(role) + 1
        }
        return counts
    }

    private fun sampleIds(mapId: String, seed: Int): List<Int> {
        val random = Random(seed)
        return List(200) { EncounterResolver.selectCreature(mapId, random).id }
    }
}
