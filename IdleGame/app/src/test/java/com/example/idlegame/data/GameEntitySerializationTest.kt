package com.example.idlegame.data

import com.example.idlegame.data.local.GameStateEntity
import org.junit.Assert.*
import org.junit.Test

class GameEntitySerializationTest {

    // ─── Map roundtrip via toEntity / toDomain ────────────────────────────────

    @Test
    fun `weapons map survives toEntity toDomain roundtrip`() {
        val weapons = mapOf(1 to 3, 2 to 5, 3 to 1)
        val restored = GameState(weapons = weapons).toEntity().toDomain()
        assertEquals(weapons, restored.weapons)
    }

    @Test
    fun `empty weapons map survives roundtrip`() {
        val restored = GameState(weapons = emptyMap()).toEntity().toDomain()
        assertEquals(emptyMap<Int, Int>(), restored.weapons)
    }

    @Test
    fun `starGenLevels map survives roundtrip`() {
        val levels = mapOf(2 to 15, 3 to 5)
        val restored = GameState(starGenLevels = levels).toEntity().toDomain()
        assertEquals(levels, restored.starGenLevels)
    }

    @Test
    fun `prestigeUpgrades map survives roundtrip`() {
        val upgrades = mapOf(
            GameState.PRESTIGE_ATTACK   to 3,
            GameState.PRESTIGE_COIN     to 1,
            GameState.PRESTIGE_OFFLINE  to 2,
            GameState.PRESTIGE_GEM_DROP to 4
        )
        val restored = GameState(prestigeUpgrades = upgrades).toEntity().toDomain()
        assertEquals(upgrades, restored.prestigeUpgrades)
    }

    @Test
    fun `achievementsClaimed map survives roundtrip`() {
        val claimed = mapOf("kill_1k" to 2, "stage_ms" to 5)
        val restored = GameState(achievementsClaimed = claimed).toEntity().toDomain()
        assertEquals(claimed, restored.achievementsClaimed)
    }

    // ─── Field preservation via toDomain ─────────────────────────────────────

    @Test
    fun `toDomain preserves all scalar fields`() {
        val entity = buildFullEntity()
        val state = entity.toDomain()

        assertEquals(1000L, state.coins)
        assertEquals(50, state.gems)
        assertEquals(5, state.weaponSlots)
        assertEquals(10L, state.stage)
        assertEquals(2, state.autoDeleteLevel)
        assertEquals(3, state.coinAttackLevel)
        assertEquals(7, state.prestigeStones)
        assertEquals(2, state.maxMilestoneReached)
        assertEquals(1500L, state.totalEnemiesDefeated)
        assertEquals(9999L, state.totalCoinsEarned)
        assertEquals(2, state.gemAdWatchedToday)
        assertEquals("2024-01-01", state.gemAdLastDate)
        assertEquals(true, state.penaltyShieldActive)
        assertEquals(true, state.tutorialShown)
    }

    @Test
    fun `toDomain with empty json strings produces empty maps`() {
        val entity = buildFullEntity(
            weaponsJson           = "",
            starGenLevelsJson     = "",
            prestigeUpgradesJson  = "",
            achievementsJson      = ""
        )
        val state = entity.toDomain()
        assertEquals(emptyMap<Int, Int>(), state.weapons)
        assertEquals(emptyMap<Int, Int>(), state.starGenLevels)
        assertEquals(emptyMap<Int, Int>(), state.prestigeUpgrades)
        assertEquals(emptyMap<String, Int>(), state.achievementsClaimed)
    }

    @Test
    fun `weapons with zero count are not serialized`() {
        // serializeIntMap filters out value==0, so level with 0 weapons should be dropped
        val state = GameState(weapons = mapOf(1 to 3, 2 to 0, 3 to 2))
        val restored = state.toEntity().toDomain()
        // key 2 should be absent (value 0 is filtered out on serialize)
        assertFalse(restored.weapons.containsKey(2))
        assertEquals(3, restored.weapons[1])
        assertEquals(2, restored.weapons[3])
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private fun buildFullEntity(
        weaponsJson: String = "1:3,2:5",
        starGenLevelsJson: String = "2:10,3:5",
        prestigeUpgradesJson: String = "1:2",
        achievementsJson: String = "kill_1k=1"
    ) = GameStateEntity(
        id                    = 1,
        coins                 = 1000L,
        gems                  = 50,
        lastSaveTime          = 0L,
        weaponsJson           = weaponsJson,
        weaponSlots           = 5,
        stage                 = 10L,
        autoDeleteLevel       = 2,
        starGenLevelsJson     = starGenLevelsJson,
        coinAttackLevel       = 3,
        prestigeStones        = 7,
        prestigeUpgradesJson  = prestigeUpgradesJson,
        maxMilestoneReached   = 2,
        achievementsClaimedJson = achievementsJson,
        totalEnemiesDefeated  = 1500L,
        totalCoinsEarned      = 9999L,
        gemAdWatchedToday     = 2,
        gemAdLastDate         = "2024-01-01",
        lastGemAdTime         = 1000L,
        lastCoinAdTime        = 2000L,
        attackBoostEndTime    = 0L,
        penaltyShieldActive   = true,
        lastAttackBoostAdTime = 0L,
        lastShieldAdTime      = 0L,
        tutorialShown         = true
    )
}
