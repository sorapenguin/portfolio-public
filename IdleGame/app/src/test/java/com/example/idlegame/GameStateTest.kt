package com.example.idlegame

import com.example.idlegame.data.GameState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameStateTest {

    // ── starAttack ────────────────────────────────────────────────────────────

    @Test fun `star 1 attack is 10`() {
        assertEquals(10L, GameState.starAttack(1))
    }

    @Test fun `star attack increases with level`() {
        val lv1 = GameState.starAttack(1)
        val lv2 = GameState.starAttack(2)
        val lv5 = GameState.starAttack(5)
        assertTrue(lv2 > lv1)
        assertTrue(lv5 > lv2)
    }

    // ── coinAttackBonus ───────────────────────────────────────────────────────

    @Test fun `level 0 coin attack bonus is 0`() {
        val state = GameState(coinAttackLevel = 0)
        assertEquals(0L, state.coinAttackBonus())
    }

    @Test fun `level 1 coin attack bonus is 1`() {
        val state = GameState(coinAttackLevel = 1)
        assertEquals(1L, state.coinAttackBonus())  // 2^1 - 1 = 1
    }

    @Test fun `level 3 coin attack bonus is 7`() {
        val state = GameState(coinAttackLevel = 3)
        assertEquals(7L, state.coinAttackBonus())  // 2^3 - 1 = 7
    }

    @Test fun `coin attack next cost equals current level power of 2`() {
        val state = GameState(coinAttackLevel = 4)
        assertEquals(16L, state.coinAttackNextCost())  // 2^4 = 16
    }

    // ── weaponSlotExpandCost ──────────────────────────────────────────────────

    @Test fun `slot expand cost increases with slots`() {
        val cost5  = GameState(weaponSlots = 5).weaponSlotExpandCost()
        val cost15 = GameState(weaponSlots = 15).weaponSlotExpandCost()
        val cost25 = GameState(weaponSlots = 25).weaponSlotExpandCost()
        assertTrue(cost15 > cost5)
        assertTrue(cost25 > cost15)
    }

    @Test fun `slot 5 cost is 200`() {
        assertEquals(200L, GameState(weaponSlots = 5).weaponSlotExpandCost())
    }

    // ── enemyHp ───────────────────────────────────────────────────────────────

    @Test fun `normal stage enemy hp equals stage`() {
        val state = GameState(stage = 50L)
        assertEquals(50L, state.enemyHp())
    }

    @Test fun `boss stage 100 has 2x multiplier`() {
        val state = GameState(stage = 100L)
        assertEquals(200L, state.enemyHp())
        assertTrue(state.isBossStage())
    }

    @Test fun `boss stage 300 has 3x multiplier`() {
        val state = GameState(stage = 300L)
        assertEquals(900L, state.enemyHp())
    }

    // ── totalAttack ───────────────────────────────────────────────────────────

    @Test fun `attack with no weapons and no coin upgrade is 0`() {
        val state = GameState(weapons = emptyMap(), coinAttackLevel = 0)
        assertEquals(0L, state.totalAttack())
    }

    @Test fun `attack boost doubles totalAttack when active`() {
        val state = GameState(
            weapons = mapOf(1 to 1),
            attackBoostEndTime = System.currentTimeMillis() + 60_000L
        )
        val boosted   = state.totalAttack()
        val unboosted = state.totalAttackBase()
        assertEquals(boosted, unboosted * 2)
    }

    @Test fun `prestige attack multiplier stacks correctly`() {
        val base  = GameState(weapons = mapOf(1 to 1))
        val prest = GameState(
            weapons = mapOf(1 to 1),
            prestigeUpgrades = mapOf(GameState.PRESTIGE_ATTACK to 4)
        )
        val expected = (base.totalAttack() * 1.20).toLong()
        assertEquals(expected, prest.totalAttack())
    }

    // ── prestigeOfflineHours ──────────────────────────────────────────────────

    @Test fun `default offline hours is 8`() {
        assertEquals(8, GameState().prestigeOfflineHours())
    }

    @Test fun `each prestige offline level adds 1 hour`() {
        val state = GameState(prestigeUpgrades = mapOf(GameState.PRESTIGE_OFFLINE to 3))
        assertEquals(11, state.prestigeOfflineHours())
    }

    // ── achievementTimesEarned ────────────────────────────────────────────────

    @Test fun `kill achievement triggers every 1000 enemies`() {
        val def = GameState.ACHIEVEMENTS.first { it.id == "kill_1k" }
        val state = GameState(totalEnemiesDefeated = 3500L)
        assertEquals(3, state.achievementTimesEarned(def))
    }

    @Test fun `kill achievement not triggered before 1000 enemies`() {
        val def = GameState.ACHIEVEMENTS.first { it.id == "kill_1k" }
        val state = GameState(totalEnemiesDefeated = 999L)
        assertEquals(0, state.achievementTimesEarned(def))
    }

    // ── isBossStage ───────────────────────────────────────────────────────────

    @Test fun `non-boss stages return false`() {
        listOf(1L, 50L, 101L, 200L, 999L).forEach { s ->
            assertFalse("stage $s should not be boss", GameState(stage = s).isBossStage())
        }
    }

    @Test fun `boss stages return true`() {
        listOf(100L, 300L, 500L, 1000L, 10000L).forEach { s ->
            assertTrue("stage $s should be boss", GameState(stage = s).isBossStage())
        }
    }
}
