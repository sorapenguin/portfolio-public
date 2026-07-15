package islanddev.model

import islanddev.data.GameData
import kotlinx.serialization.Serializable

@Serializable
data class ResourceCellState(
    val id: Int,
    val resourceId: Int,
    val col: Int,
    val row: Int,
    val depleted: Boolean = false,
    val depletedAtSec: Long = 0L
)

@Serializable
data class EnemyCellState(
    val id: Int,
    val enemyId: Int,
    val col: Int,
    val row: Int,
    val defeated: Boolean = false,
    val defeatedAtSec: Long = 0L
)

@Serializable
data class SaveData(
    val inventory: Map<Int, Int> = emptyMap(),
    val idea: Int = 0,
    val equippedWeaponId: Int = 0,
    val craftedWeaponIds: Set<Int> = setOf(0),
    val unlockedZoneIds: Set<Int> = setOf(GameData.ZONE_BEACH),
    val defeatedBossIds: Set<Int> = emptySet(),
    val builtFacilityIds: Set<Int> = emptySet(),
    val unlockedSubZoneIds: Set<Int> = setOf(0, 4, 8, 12, 15),
    val developingSubZones: Map<Int, Long> = emptyMap(),
    val resourceCells: List<ResourceCellState> = emptyList(),
    val enemyCells: List<EnemyCellState> = emptyList(),
    val playerCol: Int = 2,
    val playerRow: Int = 8,
    val lastSavedSec: Long = 0L,
    val gameCleared: Boolean = false
) {
    val currentAtk: Int
        get() = GameData.weaponById(equippedWeaponId)?.atk ?: 1

    fun addInventory(resourceId: Int, amount: Int): SaveData {
        val current = inventory.getOrDefault(resourceId, 0)
        return copy(inventory = inventory + (resourceId to (current + amount)))
    }

    fun consumeInventory(cost: Map<Int, Int>): SaveData? {
        val newInventory = inventory.toMutableMap()
        for ((id, amount) in cost) {
            val have = newInventory.getOrDefault(id, 0)
            if (have < amount) return null
            newInventory[id] = have - amount
        }
        return copy(inventory = newInventory)
    }

    fun canAfford(cost: Map<Int, Int>): Boolean =
        cost.all { (id, amount) -> (inventory[id] ?: 0) >= amount }
}
