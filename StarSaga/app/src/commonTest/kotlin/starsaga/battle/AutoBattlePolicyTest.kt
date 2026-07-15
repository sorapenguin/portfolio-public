package starsaga.battle

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import starsaga.data.CreatureDatabase
import starsaga.data.CreatureRole

class AutoBattlePolicyTest {
    @Test
    fun choosesAttackSkillWhenAvailableAndNormalAttackWillNotFinish() {
        val state = battleState(
            companion = companion(role = CreatureRole.AREA, attack = 5, currentMp = 10, skillIds = listOf(1)),
            enemyHp = 30,
        )

        val choice = AutoBattlePolicy.chooseSkill(state)

        assertNotNull(choice)
        assertEquals(1, choice.skill.skillId)
    }

    @Test
    fun fallsBackWhenMpIsInsufficient() {
        val state = battleState(
            companion = companion(role = CreatureRole.AREA, attack = 5, currentMp = 0, skillIds = listOf(1)),
            enemyHp = 30,
        )

        assertNull(AutoBattlePolicy.chooseSkill(state))
    }

    @Test
    fun fallsBackWhenNoAttackSkillExists() {
        val state = battleState(
            companion = companion(role = CreatureRole.DEFN, attack = 5, currentMp = 10, skillIds = emptyList()),
            enemyHp = 30,
        )

        assertNull(AutoBattlePolicy.chooseSkill(state))
    }

    @Test
    fun autoAttackSkillAppliesAreaBonusAndConsumesMp() {
        val state = battleState(
            companion = companion(role = CreatureRole.AREA, attack = 5, currentMp = 10, skillIds = listOf(1)),
            enemyHp = 30,
        )
        val choice = AutoBattlePolicy.chooseSkill(state) ?: error("expected skill")

        val result = BattleEngine.useSkill(state, choice.casterInstanceId, choice.skill, HighRandom)

        assertTrue(BattleEvent.AreaBonus in result.lastEvents)
        assertEquals(7, result.activeCompanions.first().currentMp)
    }

    @Test
    fun autoAttackSkillKeepsAtckCriticalRoll() {
        val state = battleState(
            companion = companion(role = CreatureRole.ATCK, attack = 10, currentMp = 10, skillIds = listOf(1)),
            enemyHp = 100,
        )
        val choice = AutoBattlePolicy.chooseSkill(state) ?: error("expected skill")

        val result = BattleEngine.useSkill(state, choice.casterInstanceId, choice.skill, ZeroRandom)

        assertTrue(BattleEvent.Critical in result.lastEvents)
    }

    @Test
    fun autoCanActAfterBossWarningState() {
        val state = battleState(
            companion = companion(role = CreatureRole.ATCK, attack = 10, currentMp = 10, skillIds = listOf(1)),
            enemy = CreatureDatabase.t1Boss,
            enemyHp = CreatureDatabase.t1Boss.hp,
        ).copy(bossPowerCharging = true)

        val choice = AutoBattlePolicy.chooseSkill(state)
        val afterPlayer = choice?.let {
            BattleEngine.useSkill(state, it.casterInstanceId, it.skill, HighRandom)
        } ?: BattleEngine.playerAttack(state, HighRandom)

        assertEquals(BattlePhase.EnemyTurn, afterPlayer.phase)
    }

    private fun battleState(
        companion: BattleCompanionState,
        enemy: starsaga.data.CreatureData = CreatureDatabase.t1Creatures.first(),
        enemyHp: Int,
    ): BattleState =
        BattleState(
            enemy = enemy,
            enemyCurrentHp = enemyHp,
            enemyMaxHp = enemy.hp.coerceAtLeast(enemyHp),
            activeCompanions = listOf(companion),
            message = "",
            phase = BattlePhase.PlayerTurn,
        )

    private fun companion(
        role: CreatureRole,
        attack: Int,
        currentMp: Int,
        skillIds: List<Int>,
    ): BattleCompanionState =
        BattleCompanionState(
            instanceId = "c1",
            name = "test",
            role = role,
            attack = attack,
            defense = 0,
            currentHp = 30,
            maxHp = 30,
            currentMp = currentMp,
            maxMp = 10,
            skillIds = skillIds,
        )

    private object ZeroRandom : Random() {
        override fun nextBits(bitCount: Int): Int = 0
    }

    private object HighRandom : Random() {
        override fun nextBits(bitCount: Int): Int = when {
            bitCount <= 0 -> 0
            bitCount >= 31 -> Int.MAX_VALUE
            else -> (1 shl bitCount) - 1
        }
    }
}
