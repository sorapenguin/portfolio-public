package islanddev.game

import islanddev.model.SaveData
import islanddev.model.EnemyCellState
import islanddev.model.ResourceCellState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorldFactoryTest {
    @Test
    fun initializesResourcesAndEnemiesOnce() {
        val initialized = WorldFactory.ensureInitialized(SaveData())
        val second = WorldFactory.ensureInitialized(initialized)

        assertTrue(initialized.resourceCells.isNotEmpty())
        assertTrue(initialized.enemyCells.isNotEmpty())
        assertEquals(initialized, second)
    }

    @Test
    fun fillsMissingEnemiesWithoutChangingProgress() {
        val resource = ResourceCellState(10, 0, 3, 4, depleted = true, depletedAtSec = 50)
        val save = SaveData(
            inventory = mapOf(0 to 12),
            idea = 34,
            defeatedBossIds = setOf(0),
            unlockedZoneIds = setOf(0, 1),
            resourceCells = listOf(resource)
        )

        val initialized = WorldFactory.ensureInitialized(save)

        assertEquals(save.inventory, initialized.inventory)
        assertEquals(save.idea, initialized.idea)
        assertEquals(save.defeatedBossIds, initialized.defeatedBossIds)
        assertEquals(save.unlockedZoneIds, initialized.unlockedZoneIds)
        assertEquals(listOf(resource), initialized.resourceCells)
        assertTrue(initialized.enemyCells.isNotEmpty())
    }

    @Test
    fun fillsMissingResourcesWithoutChangingEnemies() {
        val enemy = EnemyCellState(20, 0, 8, 4, defeated = true, defeatedAtSec = 60)
        val save = SaveData(enemyCells = listOf(enemy))

        val initialized = WorldFactory.ensureInitialized(save)

        assertEquals(listOf(enemy), initialized.enemyCells)
        assertTrue(initialized.resourceCells.isNotEmpty())
    }
}
