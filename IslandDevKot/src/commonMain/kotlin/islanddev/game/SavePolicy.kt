package islanddev.game

import islanddev.model.SaveData

object SavePolicy {
    fun requiresImmediateSave(previous: SaveData, current: SaveData): Boolean =
        previous.inventory != current.inventory ||
            previous.idea != current.idea ||
            previous.equippedWeaponId != current.equippedWeaponId ||
            previous.craftedWeaponIds != current.craftedWeaponIds ||
            previous.unlockedZoneIds != current.unlockedZoneIds ||
            previous.defeatedBossIds != current.defeatedBossIds ||
            previous.builtFacilityIds != current.builtFacilityIds ||
            previous.unlockedSubZoneIds != current.unlockedSubZoneIds ||
            previous.developingSubZones != current.developingSubZones ||
            previous.resourceCells != current.resourceCells ||
            previous.enemyCells != current.enemyCells ||
            previous.gameCleared != current.gameCleared
}
