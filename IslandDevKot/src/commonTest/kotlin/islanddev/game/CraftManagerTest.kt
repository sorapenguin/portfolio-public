package islanddev.game

import islanddev.data.GameData
import islanddev.model.SaveData
import kotlin.test.Test
import kotlin.test.assertEquals

class CraftManagerTest {
    @Test
    fun woodStickIsCraftedAndEquipped() {
        val result = requireNotNull(
            CraftManager.craftWeapon(
                SaveData(inventory = mapOf(GameData.RES_WOOD to 3)),
                weaponId = 1
            )
        )

        assertEquals(setOf(0, 1), result.craftedWeaponIds)
        assertEquals(1, result.equippedWeaponId)
        assertEquals(0, result.inventory[GameData.RES_WOOD])
    }

    @Test
    fun insufficientMaterialsDoNotChangeSave() {
        val save = SaveData(inventory = mapOf(GameData.RES_WOOD to 2))

        assertEquals(null, CraftManager.craftWeapon(save, weaponId = 1))
    }

    @Test
    fun strongestCraftedWeaponIsEquipped() {
        val afterStick = requireNotNull(
            CraftManager.craftWeapon(
                SaveData(
                    inventory = mapOf(
                        GameData.RES_WOOD to 3,
                        GameData.RES_SHELL to 3,
                        GameData.RES_FIBER to 2
                    )
                ),
                weaponId = 1
            )
        )
        val result = requireNotNull(
            CraftManager.craftWeapon(
                afterStick,
                weaponId = 2
            )
        )

        assertEquals(setOf(0, 1, 2), result.craftedWeaponIds)
        assertEquals(2, result.equippedWeaponId)
    }

    @Test
    fun autoEquipCraftsAffordableBeachWeapons() {
        val result = CraftManager.autoEquip(
            SaveData(
                inventory = mapOf(
                    GameData.RES_WOOD to 3,
                    GameData.RES_SHELL to 3,
                    GameData.RES_FIBER to 2
                )
            )
        )

        assertEquals(setOf(0, 1, 2), result.craftedWeaponIds)
        assertEquals(2, result.equippedWeaponId)
        assertEquals(0, result.inventory[GameData.RES_WOOD])
        assertEquals(0, result.inventory[GameData.RES_SHELL])
        assertEquals(0, result.inventory[GameData.RES_FIBER])
    }
}
