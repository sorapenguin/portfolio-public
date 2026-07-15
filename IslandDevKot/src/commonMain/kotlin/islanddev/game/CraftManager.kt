package islanddev.game

import islanddev.data.GameData
import islanddev.model.SaveData

object CraftManager {
    fun autoEquip(save: SaveData): SaveData {
        var current = equipStrongestCrafted(
            save.copy(craftedWeaponIds = save.craftedWeaponIds + 0)
        )
        GameData.WEAPONS
            .filter { it.id != 0 }
            .sortedByDescending { it.atk }
            .forEach { weapon ->
                if (weapon.id in current.craftedWeaponIds) return@forEach
                val consumed = current.consumeInventory(weapon.cost) ?: return@forEach
                current = consumed.copy(
                    craftedWeaponIds = consumed.craftedWeaponIds + weapon.id
                )
            }
        return equipStrongestCrafted(current)
    }

    fun craftWeapon(save: SaveData, weaponId: Int): SaveData? {
        val weapon = GameData.weaponById(weaponId) ?: return null
        if (weaponId in save.craftedWeaponIds) return save
        val consumed = save.consumeInventory(weapon.cost) ?: return null
        return equipStrongestCrafted(
            consumed.copy(craftedWeaponIds = consumed.craftedWeaponIds + weaponId)
        )
    }

    fun equipStrongestCrafted(save: SaveData): SaveData {
        val craftedIds = save.craftedWeaponIds + 0
        val strongest = craftedIds
            .mapNotNull(GameData::weaponById)
            .maxByOrNull { it.atk }
            ?: GameData.weaponById(0)!!

        return save.copy(
            craftedWeaponIds = craftedIds,
            equippedWeaponId = strongest.id
        )
    }
}
