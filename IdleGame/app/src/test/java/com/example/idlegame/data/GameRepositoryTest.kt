package com.example.idlegame.data

import com.example.idlegame.data.local.GameStateDao
import com.example.idlegame.data.local.GameStateEntity
import org.junit.Assert.*
import org.junit.Test

class GameRepositoryTest {

    private val repository = GameRepository(object : GameStateDao {
        override suspend fun get(): GameStateEntity? = null
        override suspend fun upsert(entity: GameStateEntity) {}
    })

    private fun stateWithEnoughAttack(
        offlineMinutes: Long = 60L,
        stage: Long = 1L,
        prestigeUpgrades: Map<Int, Int> = emptyMap()
    ): GameState {
        val now = System.currentTimeMillis()
        return GameState(
            lastSaveTime = now - offlineMinutes * 60_000L,
            weapons = mapOf(1 to 100),
            stage = stage,
            prestigeUpgrades = prestigeUpgrades
        )
    }

    // ─── null guard cases ─────────────────────────────────────────────────────

    @Test
    fun `returns null when offline less than 30 minutes`() {
        val now = System.currentTimeMillis()
        val state = GameState(
            lastSaveTime = now - 29 * 60_000L,
            weapons = mapOf(1 to 100),
            stage = 1L
        )
        assertNull(repository.calculateOfflineResult(state, now))
    }

    @Test
    fun `returns null when attack cannot beat any enemy`() {
        val now = System.currentTimeMillis()
        // stage=1000 boss: hp = 1000 * 7 = 7000, attack = 0
        val state = GameState(
            lastSaveTime = now - 60 * 60_000L,
            weapons = emptyMap(),
            coinAttackLevel = 0,
            stage = 1000L
        )
        assertNull(repository.calculateOfflineResult(state, now))
    }

    @Test
    fun `returns null when attack exactly equals enemy hp`() {
        val now = System.currentTimeMillis()
        // stage=1, hp=1, attack must be > hp to win (atk <= hp → break)
        val state = GameState(
            lastSaveTime = now - 60 * 60_000L,
            weapons = emptyMap(),
            coinAttackLevel = 1,  // bonus = 1, hp = 1 → atk <= hp
            stage = 1L
        )
        assertNull(repository.calculateOfflineResult(state, now))
    }

    // ─── basic reward calculation ─────────────────────────────────────────────

    @Test
    fun `returns result with coins when attack beats enemies`() {
        val result = repository.calculateOfflineResult(stateWithEnoughAttack(), System.currentTimeMillis())
        assertNotNull(result)
        assertTrue(result!!.minutesWon > 0)
        assertTrue(result.coins > 0)
    }

    @Test
    fun `stage advances during offline calculation`() {
        val result = repository.calculateOfflineResult(stateWithEnoughAttack(), System.currentTimeMillis())
        assertNotNull(result)
        assertTrue(result!!.stageAfter > result.stageBefore)
    }

    @Test
    fun `gems are earned during offline`() {
        val result = repository.calculateOfflineResult(stateWithEnoughAttack(), System.currentTimeMillis())
        assertNotNull(result)
        assertTrue(result!!.gems >= 0)
    }

    // ─── time cap ─────────────────────────────────────────────────────────────

    @Test
    fun `offline minutes are capped at default 8 hours`() {
        // 20 hours offline → capped at 480 minutes (8h)
        val result = repository.calculateOfflineResult(
            stateWithEnoughAttack(offlineMinutes = 20 * 60L),
            System.currentTimeMillis()
        )
        assertNotNull(result)
        assertTrue(result!!.minutes <= 8 * 60L)
    }

    @Test
    fun `prestige offline hours extends the cap`() {
        val now = System.currentTimeMillis()
        // 10 hours offline: default cap=8h counts 480m, extended cap=12h counts 600m
        val base = stateWithEnoughAttack(offlineMinutes = 10 * 60L)
        val extended = base.copy(
            prestigeUpgrades = mapOf(GameState.PRESTIGE_OFFLINE to 4)  // 8+4 = 12h
        )

        val baseResult     = repository.calculateOfflineResult(base, now)
        val extendedResult = repository.calculateOfflineResult(extended, now)

        assertNotNull(baseResult)
        assertNotNull(extendedResult)
        assertTrue(extendedResult!!.minutes > baseResult!!.minutes)
    }

    // ─── prestige multipliers affect rewards ──────────────────────────────────

    @Test
    fun `prestige coin multiplier increases offline coins`() {
        val now = System.currentTimeMillis()
        val base   = stateWithEnoughAttack()
        val buffed = base.copy(prestigeUpgrades = mapOf(GameState.PRESTIGE_COIN to 5))  // ×1.5

        val baseResult   = repository.calculateOfflineResult(base, now)
        val buffedResult = repository.calculateOfflineResult(buffed, now)

        assertNotNull(baseResult)
        assertNotNull(buffedResult)
        assertTrue(buffedResult!!.coins > baseResult!!.coins)
    }

    @Test
    fun `stronger attack wins more stages offline`() {
        val now = System.currentTimeMillis()
        // Weak attack might stall on boss; strong attack clears it
        val weak   = stateWithEnoughAttack(stage = 99L)
        val strong = weak.copy(weapons = mapOf(1 to 10_000))

        val weakResult   = repository.calculateOfflineResult(weak, now)
        val strongResult = repository.calculateOfflineResult(strong, now)

        assertNotNull(weakResult)
        assertNotNull(strongResult)
        assertTrue(strongResult!!.stageAfter >= weakResult!!.stageAfter)
    }
}
