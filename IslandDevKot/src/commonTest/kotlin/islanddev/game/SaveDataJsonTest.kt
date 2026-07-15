package islanddev.game

import islanddev.model.EnemyCellState
import islanddev.model.ResourceCellState
import islanddev.model.SaveData
import kotlin.test.Test
import kotlin.test.assertEquals

class SaveDataJsonTest {
    @Test
    fun roundTripPreservesCompleteProgress() {
        val save = SaveData(
            inventory = mapOf(0 to 12, 3 to 5),
            idea = 88,
            equippedWeaponId = 2,
            craftedWeaponIds = setOf(0, 1, 2),
            unlockedZoneIds = setOf(0, 1),
            defeatedBossIds = setOf(0),
            builtFacilityIds = setOf(0, 2),
            unlockedSubZoneIds = setOf(0, 1, 4),
            developingSubZones = mapOf(2 to 9999L),
            resourceCells = listOf(
                ResourceCellState(1, 0, 3, 4, depleted = true, depletedAtSec = 100L)
            ),
            enemyCells = listOf(
                EnemyCellState(2, 0, 8, 4, defeated = true, defeatedAtSec = 200L)
            ),
            playerCol = 9,
            playerRow = 7,
            gameCleared = false
        )

        assertEquals(save, SaveDataJson.decode(SaveDataJson.encode(save)))
        assertEquals(8, save.currentAtk)
    }
}
