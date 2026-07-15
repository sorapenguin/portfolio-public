package islanddev.game

import islanddev.data.GameData
import islanddev.model.ResourceCellState
import islanddev.model.SaveData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResourceTickerTest {
    @Test
    fun elapsedResourceRespawns() {
        val save = SaveData(
            resourceCells = listOf(
                ResourceCellState(1, GameData.RES_WOOD, 1, 0, true, 100L)
            )
        )

        val result = ResourceTicker.tickRespawn(save, nowSec = 160L)

        assertFalse(result.resourceCells.single().depleted)
        assertEquals(0L, result.resourceCells.single().depletedAtSec)
    }

    @Test
    fun resourceDoesNotRespawnBeforeDuration() {
        val save = SaveData(
            resourceCells = listOf(
                ResourceCellState(1, GameData.RES_WOOD, 1, 0, true, 100L)
            )
        )

        assertEquals(save, ResourceTicker.tickRespawn(save, nowSec = 159L))
    }

    @Test
    fun resourceOnPlayerCellIsCollected() {
        val save = SaveData(
            resourceCells = listOf(
                ResourceCellState(1, GameData.RES_FRUIT, 1, 0)
            )
        )

        val result = ResourceTicker.collectCurrentCell(
            save,
            playerCol = 1,
            playerRow = 0,
            nowSec = 500L
        )

        assertEquals(1, result.inventory[GameData.RES_FRUIT])
        assertEquals(GameData.resourceById(GameData.RES_FRUIT)!!.ideaValue, result.idea)
        assertTrue(result.resourceCells.single().depleted)
        assertEquals(500L, result.resourceCells.single().depletedAtSec)
    }

    @Test
    fun adjacentResourceIsNotCollected() {
        val save = SaveData(
            resourceCells = listOf(
                ResourceCellState(1, GameData.RES_WOOD, 1, 0)
            )
        )

        val result = ResourceTicker.collectCurrentCell(
            save,
            playerCol = 0,
            playerRow = 0,
            nowSec = 500L
        )

        assertEquals(save, result)
    }

    @Test
    fun collectingDoesNotCraftOrConsumeInventory() {
        val save = SaveData(
            inventory = mapOf(GameData.RES_WOOD to 2),
            resourceCells = listOf(
                ResourceCellState(1, GameData.RES_WOOD, 1, 0)
            )
        )

        val result = ResourceTicker.collectCurrentCell(
            save,
            playerCol = 1,
            playerRow = 0,
            nowSec = 500L
        )

        assertEquals(3, result.inventory[GameData.RES_WOOD])
        assertEquals(setOf(0), result.craftedWeaponIds)
        assertEquals(0, result.equippedWeaponId)
    }
}
