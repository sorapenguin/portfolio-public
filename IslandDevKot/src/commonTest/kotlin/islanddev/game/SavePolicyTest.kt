package islanddev.game

import islanddev.data.GameData
import islanddev.model.EnemyCellState
import islanddev.model.ResourceCellState
import islanddev.model.SaveData
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SavePolicyTest {
    @Test
    fun progressionChangeRequiresImmediateSave() {
        val save = SaveData()

        assertTrue(
            SavePolicy.requiresImmediateSave(
                save,
                save.copy(inventory = mapOf(0 to 1), idea = 1)
            )
        )
        assertTrue(
            SavePolicy.requiresImmediateSave(
                save,
                save.copy(defeatedBossIds = setOf(0), unlockedZoneIds = setOf(0, 1))
            )
        )
    }

    @Test
    fun inventoryAndIdeaChangesRequireImmediateSave() {
        val save = SaveData()

        assertRequiresImmediateSave(save.copy(inventory = mapOf(GameData.RES_WOOD to 1)))
        assertRequiresImmediateSave(save.copy(idea = 1))
    }

    @Test
    fun equipmentAndCraftingChangesRequireImmediateSave() {
        val save = SaveData()

        assertRequiresImmediateSave(save.copy(equippedWeaponId = 1))
        assertRequiresImmediateSave(save.copy(craftedWeaponIds = setOf(0, 1)))
    }

    @Test
    fun zoneBossFacilityAndSubZoneProgressChangesRequireImmediateSave() {
        val save = SaveData()

        assertRequiresImmediateSave(
            save.copy(unlockedZoneIds = setOf(GameData.ZONE_BEACH, GameData.ZONE_FOREST))
        )
        assertRequiresImmediateSave(save.copy(defeatedBossIds = setOf(0)))
        assertRequiresImmediateSave(save.copy(builtFacilityIds = setOf(GameData.FAC_WATCHTOWER)))
        assertRequiresImmediateSave(save.copy(unlockedSubZoneIds = save.unlockedSubZoneIds + 1))
        assertRequiresImmediateSave(save.copy(developingSubZones = mapOf(1 to 100L)))
    }

    @Test
    fun mapCellAndClearStateChangesRequireImmediateSave() {
        val save = SaveData()

        assertRequiresImmediateSave(
            save.copy(
                resourceCells = listOf(
                    ResourceCellState(
                        id = 1,
                        resourceId = GameData.RES_WOOD,
                        col = 1,
                        row = 1
                    )
                )
            )
        )
        assertRequiresImmediateSave(
            save.copy(
                enemyCells = listOf(
                    EnemyCellState(
                        id = 1,
                        enemyId = 0,
                        col = 8,
                        row = 4
                    )
                )
            )
        )
        assertRequiresImmediateSave(save.copy(gameCleared = true))
    }

    @Test
    fun playerPositionAloneUsesPeriodicOrLifecycleSave() {
        val save = SaveData()

        assertFalse(
            SavePolicy.requiresImmediateSave(
                save,
                save.copy(playerCol = 3, playerRow = 8)
            )
        )
    }

    @Test
    fun lastSavedTimestampAloneDoesNotRequireImmediateSave() {
        val save = SaveData()

        assertFalse(
            SavePolicy.requiresImmediateSave(
                save,
                save.copy(lastSavedSec = 100L)
            )
        )
    }

    @Test
    fun identicalSaveDataDoesNotRequireImmediateSave() {
        val save = SaveData()

        assertFalse(SavePolicy.requiresImmediateSave(save, save))
    }

    private fun assertRequiresImmediateSave(current: SaveData) {
        assertTrue(SavePolicy.requiresImmediateSave(SaveData(), current))
    }
}
