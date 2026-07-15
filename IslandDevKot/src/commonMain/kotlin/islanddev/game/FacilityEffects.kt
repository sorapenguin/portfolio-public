package islanddev.game

import islanddev.data.GameData

object FacilityEffects {
    fun ideaMultiplier(builtFacilityIds: Set<Int>): Float =
        if (GameData.FAC_FURNACE in builtFacilityIds) 1.2f else 1.0f

    fun respawnMultiplier(resourceId: Int, builtFacilityIds: Set<Int>): Float {
        var multiplier = 1.0f
        if (GameData.FAC_WATCHTOWER in builtFacilityIds) {
            multiplier *= 0.85f
        }
        if (GameData.FAC_FRUIT_SHELF in builtFacilityIds && resourceId == GameData.RES_FRUIT) {
            multiplier *= 0.70f
        }
        if (
            GameData.FAC_KILN in builtFacilityIds &&
            resourceId in setOf(GameData.RES_CLAY, GameData.RES_ORE)
        ) {
            multiplier *= 0.80f
        }
        return multiplier
    }

    fun resourceAmountMultiplier(resourceId: Int, builtFacilityIds: Set<Int>): Int =
        when {
            resourceId == GameData.RES_WOOD && GameData.FAC_LUMBER in builtFacilityIds -> 2
            resourceId == GameData.RES_ORE && GameData.FAC_MINE in builtFacilityIds -> 2
            else -> 1
        }

    fun dropRateBonus(builtFacilityIds: Set<Int>): Float =
        if (GameData.FAC_BASE in builtFacilityIds) 1.30f else 1.0f
}
