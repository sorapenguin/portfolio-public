package com.example.idlegame

import com.example.idlegame.data.GameRepository
import com.example.idlegame.data.GameState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * GameRepository.calculateOfflineResult のユニットテスト。
 * DAO への依存がないため null を渡してインスタンス化してテストする。
 */
class GameRepositoryOfflineTest {

    // DAO 不要なメソッドだけテストするため stub を使う
    private val repo = GameRepository(dao = object : com.example.idlegame.data.local.GameStateDao {
        override suspend fun upsert(entity: com.example.idlegame.data.local.GameStateEntity) {}
        override suspend fun get(): com.example.idlegame.data.local.GameStateEntity? = null
    })

    private fun stateWithAttack(atk: Long, stage: Long = 1L): GameState {
        // ★1 武器 1本で atk が star1Attack(=10) になるよう調整
        // テスト用: coinAttackLevel で擬似的に攻撃力を積む
        // coinAttackBonus = 2^n - 1 → coinAttackLevel で目標攻撃力を設定
        // ここでは直接 weapons で絶対値を設定しづらいので、
        // coinAttackLevel で 2^n-1 ≈ atk になるレベルを使う
        val level = (0..62).firstOrNull { (1L shl it) - 1 >= atk } ?: 62
        return GameState(
            stage = stage,
            coinAttackLevel = level,
            lastSaveTime = System.currentTimeMillis() - 3 * 60 * 60 * 1000L  // 3時間前
        )
    }

    @Test fun `returns null when elapsed is less than 30 minutes`() {
        val state = GameState(
            stage = 1L,
            coinAttackLevel = 10,
            lastSaveTime = System.currentTimeMillis() - 20 * 60 * 1000L  // 20分前
        )
        val serverTime = System.currentTimeMillis()
        assertNull(repo.calculateOfflineResult(state, serverTime))
    }

    @Test fun `returns result when elapsed is over 30 minutes and attack wins`() {
        // ★1 武器でステージ1（HP=1）に絶対勝てる攻撃力を確保
        val state = GameState(
            stage = 1L,
            coinAttackLevel = 5,  // bonus = 31 > hp=1
            lastSaveTime = System.currentTimeMillis() - 60 * 60 * 1000L  // 1時間前
        )
        val serverTime = System.currentTimeMillis()
        val result = repo.calculateOfflineResult(state, serverTime)
        assertNotNull(result)
        assert(result!!.minutesWon > 0)
        assert(result.coins > 0)
    }

    @Test fun `caps offline minutes at prestige offline hours`() {
        val maxHours = 8  // デフォルト最大8時間
        val state = GameState(
            stage = 1L,
            coinAttackLevel = 10,
            lastSaveTime = System.currentTimeMillis() - 12 * 60 * 60 * 1000L  // 12時間前
        )
        val serverTime = System.currentTimeMillis()
        val result = repo.calculateOfflineResult(state, serverTime)
        assertNotNull(result)
        // minutesWon は最大 8 * 60 = 480 分（ステージが上がっても勝ち続ける場合）
        assert(result!!.minutes <= maxHours * 60L) {
            "minutes=${result.minutes} should be <= ${maxHours * 60}"
        }
    }

    @Test fun `returns null when attack cannot beat enemy`() {
        // 攻撃力0（武器なし・コインアップグレードなし）でステージ1（HP=1）に負ける
        val state = GameState(
            stage = 1L,
            coinAttackLevel = 0,
            weapons = emptyMap(),
            lastSaveTime = System.currentTimeMillis() - 60 * 60 * 1000L
        )
        val serverTime = System.currentTimeMillis()
        val result = repo.calculateOfflineResult(state, serverTime)
        assertNull(result)  // 1分も勝てないので null
    }

    @Test fun `stageAfter is greater than or equal to stageBefore on win`() {
        val state = GameState(
            stage = 10L,
            coinAttackLevel = 10,
            lastSaveTime = System.currentTimeMillis() - 60 * 60 * 1000L
        )
        val serverTime = System.currentTimeMillis()
        val result = repo.calculateOfflineResult(state, serverTime)
        assertNotNull(result)
        assert(result!!.stageAfter >= result.stageBefore)
    }
}
