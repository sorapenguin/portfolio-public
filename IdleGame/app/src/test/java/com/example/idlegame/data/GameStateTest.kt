package com.example.idlegame.data

import org.junit.Assert.*
import org.junit.Test

class GameStateTest {

    // ─── coinAttackBonus ─────────────────────────────────────────────────────

    @Test
    fun `coinAttackBonus is 0 at level 0`() {
        assertEquals(0L, GameState(coinAttackLevel = 0).coinAttackBonus())
    }

    @Test
    fun `coinAttackBonus at level 1 equals 1`() {
        // 2^1 - 1 = 1
        assertEquals(1L, GameState(coinAttackLevel = 1).coinAttackBonus())
    }

    @Test
    fun `coinAttackBonus at level 3 equals 7`() {
        // 2^3 - 1 = 7
        assertEquals(7L, GameState(coinAttackLevel = 3).coinAttackBonus())
    }

    @Test
    fun `coinAttackBonus at level 10 equals 1023`() {
        // 2^10 - 1 = 1023
        assertEquals(1023L, GameState(coinAttackLevel = 10).coinAttackBonus())
    }

    // ─── coinAttackNextCost ───────────────────────────────────────────────────

    @Test
    fun `coinAttackNextCost at level 0 is 1`() {
        assertEquals(1L, GameState(coinAttackLevel = 0).coinAttackNextCost())
    }

    @Test
    fun `coinAttackNextCost doubles each level`() {
        for (lv in 0..5) {
            assertEquals(1L shl lv, GameState(coinAttackLevel = lv).coinAttackNextCost())
        }
    }

    // ─── weaponSlotExpandCost ─────────────────────────────────────────────────

    @Test
    fun `weaponSlotExpandCost tier1 n=5 returns 200`() {
        assertEquals(200L, GameState(weaponSlots = 5).weaponSlotExpandCost())
    }

    @Test
    fun `weaponSlotExpandCost tier1 n=9 returns 600`() {
        assertEquals(600L, GameState(weaponSlots = 9).weaponSlotExpandCost())
    }

    @Test
    fun `weaponSlotExpandCost tier2 n=10 returns 3000`() {
        assertEquals(3_000L, GameState(weaponSlots = 10).weaponSlotExpandCost())
    }

    @Test
    fun `weaponSlotExpandCost tier3 n=20 returns 100000`() {
        assertEquals(100_000L, GameState(weaponSlots = 20).weaponSlotExpandCost())
    }

    @Test
    fun `weaponSlotExpandCost tier4 n=35 returns 5000000`() {
        assertEquals(5_000_000L, GameState(weaponSlots = 35).weaponSlotExpandCost())
    }

    // ─── starAttack ───────────────────────────────────────────────────────────

    @Test
    fun `starAttack level 1 is 10`() {
        assertEquals(10L, GameState.starAttack(1))
    }

    @Test
    fun `starAttack level 2 is 22`() {
        // (10 * 2.2).toLong() = 22
        assertEquals(22L, GameState.starAttack(2))
    }

    @Test
    fun `starAttack grows strictly monotonically`() {
        var prev = GameState.starAttack(1)
        for (lv in 2..10) {
            val cur = GameState.starAttack(lv)
            assertTrue("Level $lv should be greater than level ${lv - 1}", cur > prev)
            prev = cur
        }
    }

    // ─── bossMultiplier / isBossStage ─────────────────────────────────────────

    @Test
    fun `bossMultiplier is 1 on non-boss stages`() {
        listOf(1L, 50L, 99L, 101L, 200L, 999L).forEach { stage ->
            assertEquals("stage $stage", 1, GameState(stage = stage).bossMultiplier())
        }
    }

    @Test
    fun `bossMultiplier returns correct values for all boss stages`() {
        assertEquals(2,  GameState(stage = 100L).bossMultiplier())
        assertEquals(3,  GameState(stage = 300L).bossMultiplier())
        assertEquals(5,  GameState(stage = 500L).bossMultiplier())
        assertEquals(7,  GameState(stage = 1000L).bossMultiplier())
        assertEquals(10, GameState(stage = 10000L).bossMultiplier())
    }

    @Test
    fun `isBossStage is false on normal stages`() {
        assertFalse(GameState(stage = 50L).isBossStage())
    }

    @Test
    fun `isBossStage is true for all boss stages`() {
        GameState.BOSS_STAGES.forEach { stage ->
            assertTrue("Stage $stage should be a boss stage", GameState(stage = stage).isBossStage())
        }
    }

    // ─── enemyHp ──────────────────────────────────────────────────────────────

    @Test
    fun `enemyHp equals stage on normal stages`() {
        assertEquals(50L, GameState(stage = 50L).enemyHp())
    }

    @Test
    fun `enemyHp equals stage times bossMultiplier on boss stages`() {
        assertEquals(200L,   GameState(stage = 100L).enemyHp())   // 100 × 2
        assertEquals(900L,   GameState(stage = 300L).enemyHp())   // 300 × 3
        assertEquals(2500L,  GameState(stage = 500L).enemyHp())   // 500 × 5
        assertEquals(7000L,  GameState(stage = 1000L).enemyHp())  // 1000 × 7
        assertEquals(100000L, GameState(stage = 10000L).enemyHp()) // 10000 × 10
    }

    // ─── totalWeapons ─────────────────────────────────────────────────────────

    @Test
    fun `totalWeapons is 0 with no weapons`() {
        assertEquals(0, GameState().totalWeapons())
    }

    @Test
    fun `totalWeapons sums all counts across stars`() {
        assertEquals(6, GameState(weapons = mapOf(1 to 3, 2 to 2, 3 to 1)).totalWeapons())
    }

    // ─── starUpgradeCost ──────────────────────────────────────────────────────

    @Test
    fun `starUpgradeCost below level 10 uses half multiplier`() {
        // star=2, level=5: (5+1) * 2 * 1 = 12
        assertEquals(12, GameState().starUpgradeCost(star = 2, currentLevel = 5))
    }

    @Test
    fun `starUpgradeCost at level 10 uses double multiplier`() {
        // star=2, level=10: (10+1) * 2 * 2 = 44
        assertEquals(44, GameState().starUpgradeCost(star = 2, currentLevel = 10))
    }

    @Test
    fun `starUpgradeCost discount boundary between level 9 and 10`() {
        // star=3, level=9: (9+1)*3*1 = 30
        assertEquals(30, GameState().starUpgradeCost(star = 3, currentLevel = 9))
        // star=3, level=10: (10+1)*3*2 = 66
        assertEquals(66, GameState().starUpgradeCost(star = 3, currentLevel = 10))
    }

    // ─── starUnlockCost ───────────────────────────────────────────────────────

    @Test
    fun `starUnlockCost is star times 100`() {
        assertEquals(200, GameState().starUnlockCost(2))
        assertEquals(300, GameState().starUnlockCost(3))
        assertEquals(500, GameState().starUnlockCost(5))
    }

    // ─── canUnlockStar ────────────────────────────────────────────────────────

    @Test
    fun `star 2 can be unlocked when locked`() {
        assertTrue(GameState(starGenLevels = emptyMap()).canUnlockStar(2))
    }

    @Test
    fun `star 2 cannot be unlocked when already unlocked`() {
        assertFalse(GameState(starGenLevels = mapOf(2 to 1)).canUnlockStar(2))
    }

    @Test
    fun `star 3 cannot be unlocked when star 2 is below level 10`() {
        assertFalse(GameState(starGenLevels = mapOf(2 to 9)).canUnlockStar(3))
    }

    @Test
    fun `star 3 can be unlocked when star 2 is at level 10`() {
        assertTrue(GameState(starGenLevels = mapOf(2 to 10)).canUnlockStar(3))
    }

    // ─── prestige multipliers ─────────────────────────────────────────────────

    @Test
    fun `prestigeAttackMultiplier is 1_0 with no upgrades`() {
        assertEquals(1.0, GameState().prestigeAttackMultiplier(), 0.001)
    }

    @Test
    fun `prestigeAttackMultiplier adds 5 percent per level`() {
        val state = GameState(prestigeUpgrades = mapOf(GameState.PRESTIGE_ATTACK to 4))
        assertEquals(1.20, state.prestigeAttackMultiplier(), 0.001)
    }

    @Test
    fun `prestigeCoinMultiplier adds 10 percent per level`() {
        val state = GameState(prestigeUpgrades = mapOf(GameState.PRESTIGE_COIN to 3))
        assertEquals(1.30, state.prestigeCoinMultiplier(), 0.001)
    }

    @Test
    fun `prestigeOfflineHours base is 8`() {
        assertEquals(8, GameState().prestigeOfflineHours())
    }

    @Test
    fun `prestigeOfflineHours adds 1 per level`() {
        val state = GameState(prestigeUpgrades = mapOf(GameState.PRESTIGE_OFFLINE to 3))
        assertEquals(11, state.prestigeOfflineHours())
    }

    @Test
    fun `prestigeGemDropRate base is 5 percent`() {
        assertEquals(0.05f, GameState().prestigeGemDropRate(), 0.001f)
    }

    @Test
    fun `prestigeGemDropRate adds 1 percent per level`() {
        val state = GameState(prestigeUpgrades = mapOf(GameState.PRESTIGE_GEM_DROP to 5))
        assertEquals(0.10f, state.prestigeGemDropRate(), 0.001f)
    }

    // ─── totalAttack (boost off) ──────────────────────────────────────────────

    @Test
    fun `totalAttack is 0 with no weapons and no coinAttack`() {
        val state = GameState(
            weapons = emptyMap(),
            coinAttackLevel = 0,
            attackBoostEndTime = 0L
        )
        assertEquals(0L, state.totalAttack())
    }

    @Test
    fun `totalAttack includes coinAttackBonus`() {
        // coinAttackLevel=3 → bonus=7
        val state = GameState(
            weapons = emptyMap(),
            coinAttackLevel = 3,
            attackBoostEndTime = 0L
        )
        assertEquals(7L, state.totalAttack())
    }

    @Test
    fun `totalAttack scales with prestige attack multiplier`() {
        val base = GameState(weapons = mapOf(1 to 10), attackBoostEndTime = 0L)
        val buffed = base.copy(prestigeUpgrades = mapOf(GameState.PRESTIGE_ATTACK to 4))
        assertTrue(buffed.totalAttack() > base.totalAttack())
    }

    // ─── coinAdReward ─────────────────────────────────────────────────────────

    @Test
    fun `coinAdReward minimum is 1000`() {
        assertEquals(1000L, GameState(maxMilestoneReached = 0).coinAdReward())
    }

    @Test
    fun `coinAdReward scales with maxMilestoneReached`() {
        // 5 * 100 * 10 = 5000
        assertEquals(5_000L, GameState(maxMilestoneReached = 5).coinAdReward())
    }

    // ─── autoMergeFreeRemainingToday ──────────────────────────────────────────

    @Test
    fun `autoMergeFreeRemainingToday returns max when date differs`() {
        val state = GameState(autoMergeFreeLastDate = "2024-01-01", autoMergeFreeUsesToday = 2)
        assertEquals(GameState.AUTO_MERGE_DAILY_FREE, state.autoMergeFreeRemainingToday("2024-01-02"))
    }

    @Test
    fun `autoMergeFreeRemainingToday decrements for same day`() {
        val state = GameState(autoMergeFreeLastDate = "2024-01-01", autoMergeFreeUsesToday = 2)
        assertEquals(1, state.autoMergeFreeRemainingToday("2024-01-01"))
    }

    @Test
    fun `autoMergeFreeRemainingToday is 0 when all used`() {
        val state = GameState(
            autoMergeFreeLastDate = "2024-01-01",
            autoMergeFreeUsesToday = GameState.AUTO_MERGE_DAILY_FREE
        )
        assertEquals(0, state.autoMergeFreeRemainingToday("2024-01-01"))
    }

    // ─── achievementTimesEarned ───────────────────────────────────────────────

    @Test
    fun `kill achievement triggers at multiples of 1000 enemies`() {
        val def = GameState.ACHIEVEMENTS.first { it.id == "kill_1k" }
        assertEquals(0, GameState(totalEnemiesDefeated = 999L).achievementTimesEarned(def))
        assertEquals(1, GameState(totalEnemiesDefeated = 1000L).achievementTimesEarned(def))
        assertEquals(3, GameState(totalEnemiesDefeated = 3500L).achievementTimesEarned(def))
    }

    @Test
    fun `stage milestone achievement triggers per maxMilestoneReached`() {
        val def = GameState.ACHIEVEMENTS.first { it.id == "stage_ms" }
        assertEquals(0, GameState(maxMilestoneReached = 0).achievementTimesEarned(def))
        assertEquals(1, GameState(maxMilestoneReached = 1).achievementTimesEarned(def))
        assertEquals(5, GameState(maxMilestoneReached = 5).achievementTimesEarned(def))
    }

    // ─── dailyMissions ────────────────────────────────────────────────────────

    @Test
    fun `dailyMissions resets progress when date differs`() {
        val state = GameState(
            dailyDate = "2024-01-01",
            dailyMergeCount = 10,
            dailyPlaySeconds = 300,
            dailyAdWatchCount = 5
        )
        val missions = state.dailyMissions("2024-01-02")
        missions.forEach { assertEquals("${it.id} should be 0", 0, it.progress) }
    }

    @Test
    fun `dailyMissions reflects today merge progress`() {
        val state = GameState(dailyDate = "2024-01-01", dailyMergeCount = 7)
        val merge = state.dailyMissions("2024-01-01").first { it.id == "merge10" }
        assertEquals(7, merge.progress)
        assertFalse(merge.completed)
    }

    @Test
    fun `merge mission completes and is claimable at count 10`() {
        val state = GameState(dailyDate = "2024-01-01", dailyMergeCount = 10)
        val merge = state.dailyMissions("2024-01-01").first { it.id == "merge10" }
        assertTrue(merge.completed)
        assertTrue(merge.canClaim)
    }

    @Test
    fun `play5m mission completes at 300 seconds`() {
        val state = GameState(dailyDate = "2024-01-01", dailyPlaySeconds = 300)
        val play = state.dailyMissions("2024-01-01").first { it.id == "play5m" }
        assertTrue(play.completed)
    }

    @Test
    fun `play5m mission is not complete at 299 seconds`() {
        val state = GameState(dailyDate = "2024-01-01", dailyPlaySeconds = 299)
        val play = state.dailyMissions("2024-01-01").first { it.id == "play5m" }
        assertFalse(play.completed)
    }

    @Test
    fun `claimed mission shows canClaim as false`() {
        val state = GameState(
            dailyDate = "2024-01-01",
            dailyMergeCount = 10,
            dailyMissionsClaimed = "merge10"
        )
        val merge = state.dailyMissions("2024-01-01").first { it.id == "merge10" }
        assertTrue(merge.completed)
        assertFalse(merge.canClaim)
    }

    @Test
    fun `ad mission starts at ad1 tier`() {
        val state = GameState(dailyDate = "2024-01-01")
        val adMission = state.dailyMissions("2024-01-01").first { it.id.startsWith("ad") }
        assertEquals("ad1", adMission.id)
    }

    @Test
    fun `ad mission advances to ad5 after ad1 is claimed`() {
        val state = GameState(
            dailyDate = "2024-01-01",
            dailyAdWatchCount = 3,
            dailyMissionsClaimed = "ad1"
        )
        val adMission = state.dailyMissions("2024-01-01").first { it.id.startsWith("ad") }
        assertEquals("ad5", adMission.id)
    }
}
