package islanddev.game

import islanddev.data.GameData
import islanddev.model.ResourceCellState
import islanddev.model.SaveData
import kotlin.math.roundToLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FacilityEffectsResourceTickerTest {
    @Test
    fun watchtowerShortensWoodRespawnThroughResourceTicker() {
        val wood = GameData.resourceById(GameData.RES_WOOD)!!
        val effectiveRespawn = (
            wood.respawnSeconds *
                FacilityEffects.respawnMultiplier(
                    GameData.RES_WOOD,
                    setOf(GameData.FAC_WATCHTOWER)
                )
            ).roundToLong()
        val save = depletedResourceSave(
            resourceId = GameData.RES_WOOD,
            builtFacilityIds = setOf(GameData.FAC_WATCHTOWER)
        )

        assertEquals(60L, wood.respawnSeconds)
        assertEquals(51L, effectiveRespawn)
        assertTrue(ResourceTicker.tickRespawn(save, nowSec = 50L).resourceCells.single().depleted)
        assertFalse(ResourceTicker.tickRespawn(save, nowSec = 51L).resourceCells.single().depleted)
    }

    @Test
    fun watchtowerAndKilnShortenOreRespawnThroughResourceTicker() {
        val facilities = setOf(GameData.FAC_WATCHTOWER, GameData.FAC_KILN)
        val ore = GameData.resourceById(GameData.RES_ORE)!!
        val effectiveRespawn = (
            ore.respawnSeconds *
                FacilityEffects.respawnMultiplier(GameData.RES_ORE, facilities)
            ).roundToLong()
        val save = depletedResourceSave(
            resourceId = GameData.RES_ORE,
            builtFacilityIds = facilities
        )

        assertEquals(200L, ore.respawnSeconds)
        assertEquals(136L, effectiveRespawn)
        assertTrue(
            ResourceTicker.tickRespawn(
                save,
                nowSec = effectiveRespawn - 1
            ).resourceCells.single().depleted
        )
        assertFalse(
            ResourceTicker.tickRespawn(
                save,
                nowSec = effectiveRespawn
            ).resourceCells.single().depleted
        )
    }

    private fun depletedResourceSave(
        resourceId: Int,
        builtFacilityIds: Set<Int>
    ): SaveData =
        SaveData(
            builtFacilityIds = builtFacilityIds,
            resourceCells = listOf(
                ResourceCellState(
                    id = 1,
                    resourceId = resourceId,
                    col = 1,
                    row = 1,
                    depleted = true,
                    depletedAtSec = 0L
                )
            )
        )
}
