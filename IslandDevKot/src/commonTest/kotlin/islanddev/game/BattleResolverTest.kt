package islanddev.game

import islanddev.data.GameData
import islanddev.model.EnemyCellState
import islanddev.model.SaveData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BattleResolverTest {
    @Test
    fun sufficientAttackWinsEnemyBattleAndAddsDrop() {
        val save = SaveData(
            equippedWeaponId = 1,
            craftedWeaponIds = setOf(0, 1),
            enemyCells = listOf(EnemyCellState(10, enemyId = 0, col = 3, row = 4))
        )

        val (updated, result) = BattleResolver.resolveEnemy(save, enemyId = 0, nowSec = 900L)

        assertTrue(result.won)
        assertEquals(mapOf(GameData.RES_JELLY_MEMBRANE to 1), result.drops)
        assertEquals(1, updated.inventory[GameData.RES_JELLY_MEMBRANE])
        assertTrue(updated.enemyCells.single().defeated)
        assertEquals(900L, updated.enemyCells.single().defeatedAtSec)
    }

    @Test
    fun enemyDropUsesBaseFacilityBonusWhenBuilt() {
        val save = SaveData(
            equippedWeaponId = 1,
            craftedWeaponIds = setOf(0, 1),
            builtFacilityIds = setOf(GameData.FAC_BASE),
            enemyCells = listOf(EnemyCellState(10, enemyId = 0, col = 3, row = 4))
        )

        val (updated, result) = BattleResolver.resolveEnemy(save, enemyId = 0, nowSec = 900L)

        assertTrue(result.won)
        assertEquals(mapOf(GameData.RES_JELLY_MEMBRANE to 2), result.drops)
        assertEquals(2, updated.inventory[GameData.RES_JELLY_MEMBRANE])
    }

    @Test
    fun insufficientAttackLosesWithoutChanges() {
        val save = SaveData()

        val (updated, result) = BattleResolver.resolveEnemy(save, enemyId = 1, nowSec = 1L)

        assertFalse(result.won)
        assertEquals(save, updated)
    }

    @Test
    fun bossVictoryUnlocksNextZone() {
        val save = SaveData(
            equippedWeaponId = 2,
            craftedWeaponIds = setOf(0, 2)
        )

        val (updated, result) = BattleResolver.resolveBoss(save, bossId = 0)

        assertTrue(result.won)
        assertTrue(0 in updated.defeatedBossIds)
        assertTrue(GameData.ZONE_FOREST in updated.unlockedZoneIds)
    }

    @Test
    fun finalBossVictoryClearsGame() {
        val save = SaveData(
            equippedWeaponId = 10,
            craftedWeaponIds = setOf(0, 10)
        )

        val (updated, result) = BattleResolver.resolveBoss(save, bossId = 4)

        assertTrue(result.won)
        assertTrue(updated.gameCleared)
    }
}
