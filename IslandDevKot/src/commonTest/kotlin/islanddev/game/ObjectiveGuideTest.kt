package islanddev.game

import islanddev.data.GameData
import islanddev.model.EnemyCellState
import islanddev.model.SaveData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ObjectiveGuideTest {
    @Test
    fun guidesBeachOpeningInOrder() {
        assertEquals(
            "短期: 木材で木の棒を作ろう\n最終目標: 島の主を倒す / 砂浜 1/5",
            ObjectiveGuide.currentObjective(SaveData())
        )

        val withWoodenStick = SaveData(craftedWeaponIds = setOf(0, 1))
        assertEquals(
            "短期: クラゲを倒そう\n最終目標: 島の主を倒す / 砂浜 1/5",
            ObjectiveGuide.currentObjective(withWoodenStick)
        )

        val afterJellyfish = withWoodenStick.copy(
            enemyCells = listOf(defeatedEnemy(cellId = 0, enemyId = 0))
        )
        assertEquals(
            "短期: 貝殻ナイフを作ろう\n最終目標: 島の主を倒す / 砂浜 1/5",
            ObjectiveGuide.currentObjective(afterJellyfish)
        )

        val withShellKnife = afterJellyfish.copy(craftedWeaponIds = setOf(0, 1, 2))
        assertEquals(
            "短期: 大ガニを倒そう\n最終目標: 島の主を倒す / 砂浜 1/5",
            ObjectiveGuide.currentObjective(withShellKnife)
        )

        val afterCrab = withShellKnife.copy(
            enemyCells = withShellKnife.enemyCells + defeatedEnemy(cellId = 1, enemyId = 1)
        )
        assertEquals(
            "短期: 施設を1つ建てよう\n最終目標: 島の主を倒す / 砂浜 1/5",
            ObjectiveGuide.currentObjective(afterCrab)
        )

        val afterFacility = afterCrab.copy(
            builtFacilityIds = setOf(GameData.FAC_WATCHTOWER)
        )
        assertEquals(
            "短期: 砂浜の主・大ガメに挑もう\n最終目標: 島の主を倒す / 砂浜 1/5",
            ObjectiveGuide.currentObjective(afterFacility)
        )

        val forestUnlocked = afterFacility.copy(
            equippedWeaponId = 4,
            craftedWeaponIds = afterFacility.craftedWeaponIds + 4,
            defeatedBossIds = setOf(0),
            unlockedZoneIds = setOf(GameData.ZONE_BEACH, GameData.ZONE_FOREST)
        )
        assertEquals(
            "短期: 森の主・大イノシシに挑もう\n最終目標: 島の主を倒す / 森 2/5",
            ObjectiveGuide.currentObjective(forestUnlocked)
        )
    }

    @Test
    fun defeatedTimestampKeepsCompletedEnemyObjectiveAfterRespawn() {
        val save = SaveData(
            craftedWeaponIds = setOf(0, 1),
            enemyCells = listOf(
                EnemyCellState(
                    id = 0,
                    enemyId = 0,
                    col = 8,
                    row = 4,
                    defeated = false,
                    defeatedAtSec = 100L
                )
            )
        )

        assertEquals(
            "短期: 貝殻ナイフを作ろう\n最終目標: 島の主を倒す / 砂浜 1/5",
            ObjectiveGuide.currentObjective(save)
        )
    }

    @Test
    fun lateZoneDoesNotReturnBeachEnemyObjective() {
        val save = SaveData(
            equippedWeaponId = 8,
            craftedWeaponIds = setOf(0, 8),
            defeatedBossIds = setOf(0, 1, 2),
            unlockedZoneIds = setOf(
                GameData.ZONE_BEACH,
                GameData.ZONE_FOREST,
                GameData.ZONE_REEF,
                GameData.ZONE_DEPTHS
            )
        )

        assertEquals(
            "短期: 奥地の主・大ヒョウに挑もう\n最終目標: 島の主を倒す / 奥地 4/5",
            ObjectiveGuide.currentObjective(save)
        )
    }

    @Test
    fun lateZoneWithLowAttackTargetsBeatableLocalEnemyInsteadOfBeachEnemy() {
        val save = SaveData(
            equippedWeaponId = 7,
            craftedWeaponIds = setOf(0, 7),
            defeatedBossIds = setOf(0, 1, 2),
            unlockedZoneIds = setOf(
                GameData.ZONE_BEACH,
                GameData.ZONE_FOREST,
                GameData.ZONE_REEF,
                GameData.ZONE_DEPTHS
            ),
            enemyCells = listOf(EnemyCellState(id = 30, enemyId = 6, col = 68, row = 4))
        )

        assertEquals(
            "短期: 大サルを倒そう\n最終目標: 島の主を倒す / 奥地 4/5",
            ObjectiveGuide.currentObjective(save)
        )
    }

    @Test
    fun afterBeachBossWithLowAttackTargetsForestBossAttackRequirement() {
        val save = SaveData(
            equippedWeaponId = 2,
            craftedWeaponIds = setOf(0, 1, 2),
            defeatedBossIds = setOf(0),
            unlockedZoneIds = setOf(GameData.ZONE_BEACH, GameData.ZONE_FOREST)
        )

        assertEquals(
            "短期: ATK 18を目指そう",
            ObjectiveGuide.shortTermObjective(save)
        )
    }

    @Test
    fun afterBeachBossWithEnoughAttackTargetsForestBossChallenge() {
        val save = SaveData(
            equippedWeaponId = 4,
            craftedWeaponIds = setOf(0, 1, 2, 4),
            defeatedBossIds = setOf(0),
            unlockedZoneIds = setOf(GameData.ZONE_BEACH, GameData.ZONE_FOREST)
        )

        assertEquals(
            "短期: 森の主・大イノシシに挑もう",
            ObjectiveGuide.shortTermObjective(save)
        )
    }

    @Test
    fun afterForestBossTargetsBeatableReefEnemyBeforeReefBossAttackRequirement() {
        val save = SaveData(
            equippedWeaponId = 4,
            craftedWeaponIds = setOf(0, 1, 2, 4),
            defeatedBossIds = setOf(0, 1),
            unlockedZoneIds = setOf(
                GameData.ZONE_BEACH,
                GameData.ZONE_FOREST,
                GameData.ZONE_REEF
            ),
            enemyCells = listOf(
                EnemyCellState(id = 20, enemyId = 4, col = 48, row = 5),
                EnemyCellState(id = 21, enemyId = 5, col = 52, row = 7)
            )
        )

        assertEquals(
            "短期: 大ウニを倒そう",
            ObjectiveGuide.shortTermObjective(save)
        )
    }

    @Test
    fun summitProgressDoesNotReturnBeachEnemyObjective() {
        val save = SaveData(
            equippedWeaponId = 9,
            craftedWeaponIds = setOf(0, 9),
            defeatedBossIds = setOf(0, 1, 2, 3),
            unlockedZoneIds = setOf(
                GameData.ZONE_BEACH,
                GameData.ZONE_FOREST,
                GameData.ZONE_REEF,
                GameData.ZONE_DEPTHS,
                GameData.ZONE_SUMMIT
            ),
            enemyCells = listOf(
                EnemyCellState(id = 40, enemyId = 0, col = 8, row = 4),
                EnemyCellState(id = 41, enemyId = 8, col = 88, row = 6)
            )
        )

        val objective = ObjectiveGuide.shortTermObjective(save)

        assertFalse(objective.contains("クラゲを倒そう"))
        assertEquals("短期: 岩オオカミを倒そう", objective)
    }

    @Test
    fun gameClearedShowsFreePlayShortTermObjective() {
        val save = SaveData(gameCleared = true)

        assertEquals(
            "短期: 自由に島づくり",
            ObjectiveGuide.shortTermObjective(save)
        )
    }

    @Test
    fun clearObjectiveShowsClearedWhenAllBossesDefeatedOrGameCleared() {
        val allBossesDefeated = SaveData(
            defeatedBossIds = GameData.BOSSES.map { it.id }.toSet()
        )
        val gameCleared = SaveData(gameCleared = true)

        assertEquals(
            "最終目標: 島の主を倒した",
            ObjectiveGuide.clearObjective(allBossesDefeated)
        )
        assertEquals(
            "最終目標: 島の主を倒した",
            ObjectiveGuide.clearObjective(gameCleared)
        )
    }

    @Test
    fun allBossesDefeatedShowsFreePlayObjective() {
        val save = SaveData(
            defeatedBossIds = setOf(0, 1, 2, 3, 4),
            unlockedZoneIds = setOf(
                GameData.ZONE_BEACH,
                GameData.ZONE_FOREST,
                GameData.ZONE_REEF,
                GameData.ZONE_DEPTHS,
                GameData.ZONE_SUMMIT
            ),
            gameCleared = true
        )

        assertEquals(
            "短期: 自由に島づくり\n最終目標: 島の主を倒した",
            ObjectiveGuide.currentObjective(save)
        )
    }

    private fun defeatedEnemy(cellId: Int, enemyId: Int) = EnemyCellState(
        id = cellId,
        enemyId = enemyId,
        col = 8,
        row = 4,
        defeated = true,
        defeatedAtSec = 100L
    )
}
