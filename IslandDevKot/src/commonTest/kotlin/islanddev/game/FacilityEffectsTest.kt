package islanddev.game

import islanddev.data.GameData
import kotlin.test.Test
import kotlin.test.assertEquals

class FacilityEffectsTest {
    @Test
    fun noFacilitiesUseDefaultMultipliers() {
        assertEquals(1.0f, FacilityEffects.ideaMultiplier(emptySet()))
        assertEquals(1.0f, FacilityEffects.respawnMultiplier(GameData.RES_WOOD, emptySet()))
        assertEquals(1, FacilityEffects.resourceAmountMultiplier(GameData.RES_WOOD, emptySet()))
        assertEquals(1.0f, FacilityEffects.dropRateBonus(emptySet()))
    }

    @Test
    fun eachFacilityAppliesItsEffect() {
        assertEquals(
            1.2f,
            FacilityEffects.ideaMultiplier(setOf(GameData.FAC_FURNACE))
        )
        assertEquals(
            0.85f,
            FacilityEffects.respawnMultiplier(
                GameData.RES_WOOD,
                setOf(GameData.FAC_WATCHTOWER)
            )
        )
        assertEquals(
            0.70f,
            FacilityEffects.respawnMultiplier(
                GameData.RES_FRUIT,
                setOf(GameData.FAC_FRUIT_SHELF)
            )
        )
        assertEquals(
            0.80f,
            FacilityEffects.respawnMultiplier(
                GameData.RES_CLAY,
                setOf(GameData.FAC_KILN)
            )
        )
        assertEquals(
            2,
            FacilityEffects.resourceAmountMultiplier(
                GameData.RES_WOOD,
                setOf(GameData.FAC_LUMBER)
            )
        )
        assertEquals(
            2,
            FacilityEffects.resourceAmountMultiplier(
                GameData.RES_ORE,
                setOf(GameData.FAC_MINE)
            )
        )
        assertEquals(1.30f, FacilityEffects.dropRateBonus(setOf(GameData.FAC_BASE)))
    }

    @Test
    fun watchtowerAndKilnEffectsMultiplyForOre() {
        assertEquals(
            0.68f,
            FacilityEffects.respawnMultiplier(
                GameData.RES_ORE,
                setOf(GameData.FAC_WATCHTOWER, GameData.FAC_KILN)
            ),
            absoluteTolerance = 0.0001f
        )
    }
}
