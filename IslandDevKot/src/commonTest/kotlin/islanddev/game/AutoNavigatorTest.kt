package islanddev.game

import islanddev.data.GameData
import islanddev.model.EnemyCellState
import islanddev.model.ResourceCellState
import islanddev.model.SaveData
import islanddev.game.GridPoint
import islanddev.scene.MovementInputConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AutoNavigatorTest {
    @Test
    fun autoStartFixesCurrentLargeZone() {
        val beach = AutoCollectionState().toggle(playerCol = 2)
        val forest = AutoCollectionState().toggle(playerCol = 25)

        assertEquals(GameData.ZONE_BEACH, beach.zoneId)
        assertEquals(GameData.ZONE_FOREST, forest.zoneId)
        assertEquals(AutoCollectionState(), beach.stop())
    }

    @Test
    fun allAvailableResourcesInFixedZoneAreCandidates() {
        val save = SaveData(
            playerCol = 2,
            playerRow = 8,
            resourceCells = listOf(
                ResourceCellState(1, GameData.RES_WOOD, 3, 8),
                ResourceCellState(2, GameData.RES_FIBER, 8, 9),
                ResourceCellState(3, GameData.RES_SHELL, 18, 8),
                ResourceCellState(4, GameData.RES_FRUIT, 23, 8)
            )
        )

        val candidates = AutoNavigator.candidates(save, GameData.ZONE_BEACH)

        assertEquals(setOf(3, 8, 18), candidates.map { it.objectCell.col }.toSet())
        assertTrue(candidates.all { it.destination == it.objectCell })
    }

    @Test
    fun resourcesAcrossBeachSubZonesRemainCandidates() {
        val save = SaveData(
            resourceCells = listOf(
                ResourceCellState(1, GameData.RES_WOOD, 4, 8),
                ResourceCellState(2, GameData.RES_FIBER, 5, 8),
                ResourceCellState(3, GameData.RES_SHELL, 14, 8),
                ResourceCellState(4, GameData.RES_WOOD, 19, 8)
            )
        )

        assertEquals(
            4,
            AutoNavigator.candidates(save, GameData.ZONE_BEACH).size
        )
    }

    @Test
    fun fixedZoneExcludesOtherZonesAndDepletedResources() {
        val save = SaveData(
            unlockedZoneIds = setOf(GameData.ZONE_BEACH, GameData.ZONE_FOREST),
            resourceCells = listOf(
                ResourceCellState(
                    1,
                    GameData.RES_WOOD,
                    3,
                    8,
                    depleted = true
                ),
                ResourceCellState(2, GameData.RES_FRUIT, 23, 8)
            )
        )

        assertTrue(
            AutoNavigator.candidates(save, GameData.ZONE_BEACH).isEmpty()
        )
    }

    @Test
    fun lockedLargeZoneHasNoCandidates() {
        val save = SaveData(
            resourceCells = listOf(
                ResourceCellState(1, GameData.RES_FRUIT, 23, 8)
            )
        )

        assertTrue(
            AutoNavigator.candidates(save, GameData.ZONE_FOREST).isEmpty()
        )
    }

    @Test
    fun oneDepletedResourceDoesNotHideRemainingZoneResource() {
        val save = SaveData(
            resourceCells = listOf(
                ResourceCellState(
                    1,
                    GameData.RES_WOOD,
                    3,
                    8,
                    depleted = true
                ),
                ResourceCellState(2, GameData.RES_FIBER, 18, 9)
            )
        )

        val target = assertNotNull(
            AutoNavigator.findAutoTarget(save, GameData.ZONE_BEACH)
        )

        assertEquals(GameData.RES_FIBER, target.id)
        assertEquals(18, target.objectCell.col)
    }

    @Test
    fun noTargetOnlyAfterAllFixedZoneResourcesAreDepleted() {
        val save = SaveData(
            resourceCells = listOf(
                ResourceCellState(
                    1,
                    GameData.RES_WOOD,
                    3,
                    8,
                    depleted = true
                ),
                ResourceCellState(
                    2,
                    GameData.RES_FIBER,
                    18,
                    9,
                    depleted = true
                )
            )
        )

        assertNull(AutoNavigator.findAutoTarget(save, GameData.ZONE_BEACH))
    }

    @Test
    fun directionStaysInsideFixedLargeZone() {
        val save = SaveData(playerCol = 19, playerRow = 8)
        val outsideTarget = AutoTarget(
            AutoTargetKind.RESOURCE,
            GameData.RES_FRUIT,
            GridPoint(20, 8),
            GridPoint(20, 8)
        )

        assertNull(
            AutoNavigator.chooseDirection(
                save,
                outsideTarget,
                GameData.ZONE_BEACH
            )
        )
    }

    @Test
    fun attackShortageEnemiesAndBossProgressNeverCreateTargets() {
        val save = SaveData(
            enemyCells = listOf(EnemyCellState(1, 0, 8, 4)),
            resourceCells = emptyList(),
            defeatedBossIds = emptySet()
        )

        assertNull(AutoNavigator.findAutoTarget(save, GameData.ZONE_BEACH))
    }

    @Test
    fun beatableEnemyIsAutoCandidateBeforeResources() {
        val save = SaveData(
            equippedWeaponId = 1,
            craftedWeaponIds = setOf(0, 1),
            playerCol = 2,
            playerRow = 8,
            enemyCells = listOf(EnemyCellState(1, enemyId = 0, col = 8, row = 4)),
            resourceCells = listOf(ResourceCellState(2, GameData.RES_WOOD, 3, 8))
        )

        val target = assertNotNull(AutoNavigator.findAutoTarget(save, GameData.ZONE_BEACH))

        assertEquals(AutoTargetKind.ENEMY, target.kind)
        assertEquals(0, target.id)
        assertEquals(GridPoint(8, 4), target.destination)
    }

    @Test
    fun beatableEnemyIsFirstCandidateEvenWhenResourceIsCloser() {
        val save = SaveData(
            equippedWeaponId = 1,
            craftedWeaponIds = setOf(0, 1),
            playerCol = 2,
            playerRow = 8,
            enemyCells = listOf(EnemyCellState(1, enemyId = 0, col = 18, row = 8)),
            resourceCells = listOf(ResourceCellState(2, GameData.RES_WOOD, 3, 8))
        )

        val candidates = AutoNavigator.candidates(save, GameData.ZONE_BEACH)

        assertEquals(AutoTargetKind.ENEMY, candidates.first().kind)
        assertEquals(0, candidates.first().id)
    }

    @Test
    fun enemyWithInsufficientAtkIsNotAutoCandidate() {
        val save = SaveData(
            equippedWeaponId = 1,
            craftedWeaponIds = setOf(0, 1),
            enemyCells = listOf(EnemyCellState(1, enemyId = 1, col = 8, row = 4)),
            resourceCells = emptyList()
        )

        assertNull(AutoNavigator.findAutoTarget(save, GameData.ZONE_BEACH))
    }

    @Test
    fun enemyWithInsufficientAtkIsExcludedAndResourceCanStillBeTargeted() {
        val save = SaveData(
            equippedWeaponId = 1,
            craftedWeaponIds = setOf(0, 1),
            playerCol = 2,
            playerRow = 8,
            enemyCells = listOf(EnemyCellState(1, enemyId = 1, col = 8, row = 4)),
            resourceCells = listOf(ResourceCellState(2, GameData.RES_WOOD, 3, 8))
        )

        val candidates = AutoNavigator.candidates(save, GameData.ZONE_BEACH)
        val target = assertNotNull(AutoNavigator.findAutoTarget(save, GameData.ZONE_BEACH))

        assertTrue(candidates.none { it.kind == AutoTargetKind.ENEMY && it.id == 1 })
        assertEquals(AutoTargetKind.RESOURCE, target.kind)
        assertEquals(GameData.RES_WOOD, target.id)
    }

    @Test
    fun nearestCandidateWinsWithinSameKind() {
        val save = SaveData(
            equippedWeaponId = 2,
            craftedWeaponIds = setOf(0, 1, 2),
            playerCol = 2,
            playerRow = 8,
            enemyCells = listOf(
                EnemyCellState(1, enemyId = 0, col = 18, row = 8),
                EnemyCellState(2, enemyId = 1, col = 3, row = 8)
            )
        )

        val target = assertNotNull(AutoNavigator.findAutoTarget(save, GameData.ZONE_BEACH))

        assertEquals(AutoTargetKind.ENEMY, target.kind)
        assertEquals(1, target.id)
        assertEquals(GridPoint(3, 8), target.destination)
    }

    @Test
    fun lockedZoneEnemiesAndResourcesAreExcluded() {
        val save = SaveData(
            unlockedZoneIds = setOf(GameData.ZONE_BEACH),
            equippedWeaponId = 4,
            craftedWeaponIds = setOf(0, 4),
            enemyCells = listOf(EnemyCellState(1, enemyId = 3, col = 24, row = 8)),
            resourceCells = listOf(ResourceCellState(2, GameData.RES_FRUIT, 25, 8))
        )

        assertTrue(AutoNavigator.candidates(save, GameData.ZONE_FOREST).isEmpty())
        assertNull(AutoNavigator.findAutoTarget(save, GameData.ZONE_FOREST))
    }

    @Test
    fun gameClearedHasNoAutoCandidates() {
        val save = SaveData(
            gameCleared = true,
            equippedWeaponId = 1,
            craftedWeaponIds = setOf(0, 1),
            enemyCells = listOf(EnemyCellState(1, enemyId = 0, col = 8, row = 4)),
            resourceCells = listOf(ResourceCellState(2, GameData.RES_WOOD, 3, 8))
        )

        assertTrue(AutoNavigator.candidates(save, GameData.ZONE_BEACH).isEmpty())
        assertNull(AutoNavigator.findAutoTarget(save, GameData.ZONE_BEACH))
    }

    @Test
    fun lateZoneBeatableEnemyIsAutoCandidateWhenUnlocked() {
        val save = SaveData(
            equippedWeaponId = 7,
            craftedWeaponIds = setOf(0, 7),
            unlockedZoneIds = setOf(
                GameData.ZONE_BEACH,
                GameData.ZONE_FOREST,
                GameData.ZONE_REEF,
                GameData.ZONE_DEPTHS
            ),
            playerCol = 62,
            playerRow = 8,
            enemyCells = listOf(EnemyCellState(1, enemyId = 6, col = 68, row = 4)),
            resourceCells = emptyList()
        )

        val target = assertNotNull(AutoNavigator.findAutoTarget(save, GameData.ZONE_DEPTHS))

        assertEquals(AutoTargetKind.ENEMY, target.kind)
        assertEquals(6, target.id)
        assertEquals(GridPoint(68, 4), target.destination)
    }

    @Test
    fun autoStateDoesNotRestartWithoutUserToggle() {
        assertFalse(AutoInputPolicy.canAdvance(false, false, true))
        assertTrue(AutoInputPolicy.canAdvance(true, false, true))
    }

    @Test
    fun modalPausesWithoutDisablingAutoState() {
        assertFalse(AutoInputPolicy.canAdvance(true, true, true))
        assertTrue(AutoInputPolicy.canAdvance(true, false, true))
    }

    @Test
    fun autoThinksOnlyWhenIdleAndCooldownHasElapsed() {
        assertFalse(
            AutoInputPolicy.canAdvance(
                enabled = true,
                isModalOpen = false,
                playerIdle = false,
                thinkCooldownSeconds = 0.0
            )
        )
        assertFalse(
            AutoInputPolicy.canAdvance(
                enabled = true,
                isModalOpen = false,
                playerIdle = true,
                thinkCooldownSeconds = 0.01
            )
        )
        assertTrue(
            AutoInputPolicy.canAdvance(
                enabled = true,
                isModalOpen = false,
                playerIdle = true,
                thinkCooldownSeconds = 0.0
            )
        )
    }

    @Test
    fun autoCooldownWaitsAfterMovementAndCountsDownWhileIdle() {
        val whileMoving = AutoInputPolicy.updateThinkCooldown(
            currentSeconds = 0.0,
            deltaSeconds = 0.1,
            enabled = true,
            isModalOpen = false,
            playerIdle = false
        )
        assertEquals(AutoInputPolicy.THINK_INTERVAL_SECONDS, whileMoving)

        val whileIdle = AutoInputPolicy.updateThinkCooldown(
            currentSeconds = whileMoving,
            deltaSeconds = AutoInputPolicy.THINK_INTERVAL_SECONDS,
            enabled = true,
            isModalOpen = false,
            playerIdle = true
        )
        assertEquals(0.0, whileIdle)
    }

    @Test
    fun tapMoveIsEnabledWithoutChangingAutoPolicy() {
        assertTrue(MovementInputConfig.ENABLE_TAP_MOVE)
    }
}
