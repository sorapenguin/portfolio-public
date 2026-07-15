package islanddev.game

import islanddev.data.GameData
import islanddev.model.EnemyCellState
import islanddev.model.SaveData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BeachVerticalSliceTest {
    @Test
    fun beachProgressionReachesForestUnlockWithoutRpgStats() {
        val enemies = listOf(
            EnemyCellState(id = 0, enemyId = 0, col = 8, row = 4),
            EnemyCellState(id = 1, enemyId = 1, col = 12, row = 6)
        )
        var save = SaveData(enemyCells = enemies)

        save = CraftManager.autoEquip(
            save.copy(inventory = mapOf(GameData.RES_WOOD to 3))
        )
        assertEquals(setOf(0, 1), save.craftedWeaponIds)
        assertEquals(5, save.currentAtk)

        val (afterJellyfish, jellyfishResult) =
            BattleResolver.resolveEnemy(save, enemyId = 0, nowSec = 10L)
        save = afterJellyfish
        assertTrue(jellyfishResult.won)

        save = CraftManager.autoEquip(
            save.copy(
                inventory = save.inventory +
                    mapOf(
                        GameData.RES_SHELL to 3,
                        GameData.RES_FIBER to 2
                    )
            )
        )
        assertEquals(setOf(0, 1, 2), save.craftedWeaponIds)
        assertEquals(8, save.currentAtk)

        val (afterCrab, crabResult) =
            BattleResolver.resolveEnemy(save, enemyId = 1, nowSec = 20L)
        save = afterCrab
        assertTrue(crabResult.won)
        assertEquals(
            "短期: 施設を1つ建てよう\n最終目標: 島の主を倒す / 砂浜 1/5",
            ObjectiveGuide.currentObjective(save)
        )

        save = save.copy(builtFacilityIds = setOf(GameData.FAC_WATCHTOWER))
        assertEquals(
            "短期: 砂浜の主・大ガメに挑もう\n最終目標: 島の主を倒す / 砂浜 1/5",
            ObjectiveGuide.currentObjective(save)
        )

        val (afterTurtle, turtleResult) =
            BattleResolver.resolveBoss(save, bossId = 0)
        assertTrue(turtleResult.won)
        assertTrue(GameData.ZONE_FOREST in afterTurtle.unlockedZoneIds)
        assertTrue(0 in afterTurtle.defeatedBossIds)
        assertFalse(afterTurtle.gameCleared)
        assertEquals(
            "短期: ATK 18を目指そう\n最終目標: 島の主を倒す / 森 2/5",
            ObjectiveGuide.currentObjective(afterTurtle)
        )
    }
}
