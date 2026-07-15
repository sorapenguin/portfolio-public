package starsaga.battle

import starsaga.data.CreatureData
import starsaga.data.CreatureDatabase
import starsaga.data.CreatureRole
import starsaga.map.T1MapProgress
import kotlin.random.Random

data class EncounterResult(
    val creature: CreatureData?,
    val roll: Double,
)

object EncounterResolver {
    const val ENCOUNTER_RATE = 0.12

    fun roll(mapId: String, random: Random = Random.Default): EncounterResult {
        val roll = random.nextDouble()
        if (roll >= ENCOUNTER_RATE) return EncounterResult(creature = null, roll = roll)
        return EncounterResult(creature = selectCreature(mapId, random), roll = roll)
    }

    fun selectCreature(mapId: String, random: Random = Random.Default): CreatureData {
        val table = weightedTableFor(mapId)
        val totalWeight = table.sumOf { it.weight.coerceAtLeast(0) }
        if (totalWeight <= 0) return CreatureDatabase.t1Creatures.random(random)
        var cursor = random.nextInt(totalWeight)
        table.forEach { entry ->
            val weight = entry.weight.coerceAtLeast(0)
            if (weight <= 0) return@forEach
            if (cursor < weight) return entry.creature
            cursor -= weight
        }
        return table.lastOrNull { it.weight > 0 }?.creature ?: CreatureDatabase.t1Creatures.random(random)
    }

    fun roleWeightsFor(mapId: String): Map<CreatureRole, Int> =
        roleWeightsByMap[mapId] ?: defaultRoleWeights

    fun weightedTableFor(mapId: String): List<WeightedEncounter> {
        val roleWeights = roleWeightsFor(mapId)
        return CreatureDatabase.t1Creatures.map { creature ->
            WeightedEncounter(creature, roleWeights.getOrDefault(creature.role, 1).coerceAtLeast(0))
        }
    }

    private val defaultRoleWeights = mapOf(
        CreatureRole.ATCK to 1,
        CreatureRole.DEFN to 1,
        CreatureRole.AREA to 1,
        CreatureRole.HEAL to 1,
        CreatureRole.LUCK to 1,
    )

    private val roleWeightsByMap = mapOf(
        T1MapProgress.SETTLEMENT_OUTSKIRTS_MAP_ID to mapOf(
            CreatureRole.ATCK to 30,
            CreatureRole.DEFN to 30,
            CreatureRole.AREA to 15,
            CreatureRole.HEAL to 15,
            CreatureRole.LUCK to 10,
        ),
        T1MapProgress.STARGRASS_FORK_MAP_ID to mapOf(
            CreatureRole.ATCK to 15,
            CreatureRole.DEFN to 15,
            CreatureRole.AREA to 30,
            CreatureRole.HEAL to 30,
            CreatureRole.LUCK to 10,
        ),
        T1MapProgress.DEEP_GATE_ROAD_MAP_ID to mapOf(
            CreatureRole.ATCK to 12,
            CreatureRole.DEFN to 22,
            CreatureRole.AREA to 14,
            CreatureRole.HEAL to 22,
            CreatureRole.LUCK to 30,
        ),
    )
}

data class WeightedEncounter(
    val creature: CreatureData,
    val weight: Int,
)
