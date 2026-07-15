package starsaga.battle

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import starsaga.data.CreatureDatabase
import starsaga.data.CreatureRole
import starsaga.data.SkillData
import starsaga.data.SkillKind

class BattleEngineRoleEffectsTest {
    @Test
    fun atckCriticalIncreasesNormalAttackDamage() {
        val state = battleState(
            companion = companion(role = CreatureRole.ATCK, attack = 10),
            enemyDefense = 0,
        )

        val result = BattleEngine.playerAttack(state, ZeroRandom)

        assertEquals(15, result.lastDamage)
        assertTrue(result.logLines.any { it.contains("クリティカル") })
    }

    @Test
    fun defnReducesIncomingDamageButKeepsMinimumOne() {
        val state = battleState(
            companion = companion(role = CreatureRole.DEFN, defense = 0),
            enemyAttack = 10,
            phase = BattlePhase.EnemyTurn,
        )

        val result = BattleEngine.enemyAttack(state)

        assertEquals(8, result.lastDamage)
        assertEquals(22, result.activeCompanions.first().currentHp)
    }

    @Test
    fun healRoleRecoversAfterActingWithoutExceedingMaxHp() {
        val state = battleState(
            companion = companion(role = CreatureRole.HEAL, currentHp = 20, maxHp = 30),
        )

        val result = BattleEngine.playerAttack(state, HighRandom)

        assertEquals(21, result.activeCompanions.first().currentHp)
        assertTrue(result.logLines.any { it.contains("星光") })
    }

    @Test
    fun areaAttackSkillAddsSmallDamageBonus() {
        val state = battleState(
            companion = companion(role = CreatureRole.AREA, attack = 10, currentMp = 10),
        )
        val skill = SkillData(99, "test", mpCost = 1, power = 5, kind = SkillKind.Attack)

        val result = BattleEngine.useSkill(state, "c1", skill, HighRandom)

        assertEquals(18, result.lastDamage)
        assertTrue(result.logLines.any { it.contains("範囲") })
    }

    @Test
    fun bossEnragesOnceWhenHpFallsBelowHalf() {
        val state = battleState(
            companion = companion(role = CreatureRole.ATCK, attack = 15),
            enemy = CreatureDatabase.t1Boss,
            enemyCurrentHp = 50,
        )

        val result = BattleEngine.playerAttack(state, HighRandom)

        assertTrue(result.bossEnraged)
        assertTrue(result.logLines.any { it.contains("ざわめいた") })
    }

    @Test
    fun bossEnrageMultiplierAppliesToEnemyDamage() {
        val state = battleState(
            companion = companion(role = CreatureRole.ATCK, defense = 0),
            enemy = CreatureDatabase.t1Boss,
            enemyAttack = CreatureDatabase.t1Boss.attack,
            phase = BattlePhase.EnemyTurn,
        ).copy(bossEnraged = true)

        val result = BattleEngine.enemyAttack(state)

        assertEquals(18, result.lastDamage)
    }

    @Test
    fun bossPowerAttackWarnsBeforeStrongAttack() {
        val warningState = battleState(
            companion = companion(role = CreatureRole.ATCK, defense = 0),
            enemy = CreatureDatabase.t1Boss,
            enemyAttack = CreatureDatabase.t1Boss.attack,
            phase = BattlePhase.EnemyTurn,
        ).copy(bossTurnsUntilPowerWarn = 1)

        val warned = BattleEngine.enemyAttack(warningState)

        assertEquals(BattlePhase.PlayerTurn, warned.phase)
        assertEquals(0, warned.lastDamage)
        assertTrue(warned.bossPowerCharging)
        assertTrue(warned.logLines.any { it.contains("力をためている") })

        val strong = BattleEngine.enemyAttack(warned.copy(phase = BattlePhase.EnemyTurn))

        assertEquals(24, strong.lastDamage)
        assertTrue(strong.logLines.any { it.contains("強攻撃") })
    }

    @Test
    fun powerAttackCycleDoesNotApplyToNormalEnemies() {
        val state = battleState(
            companion = companion(role = CreatureRole.ATCK, defense = 0),
            phase = BattlePhase.EnemyTurn,
        ).copy(bossTurnsUntilPowerWarn = 1)

        val result = BattleEngine.enemyAttack(state)

        assertEquals(5, result.lastDamage)
        assertTrue(result.logLines.none { it.contains("力をためている") })
    }

    @Test
    fun autoBattleCanContinueAfterBossWarningState() {
        val state = battleState(
            companion = companion(role = CreatureRole.ATCK),
            enemy = CreatureDatabase.t1Boss,
            enemyAttack = CreatureDatabase.t1Boss.attack,
            phase = BattlePhase.EnemyTurn,
        ).copy(bossTurnsUntilPowerWarn = 1)

        val warned = BattleEngine.enemyAttack(state)
        val afterAutoStep = BattleEngine.playerAttack(warned, HighRandom)

        assertEquals(BattlePhase.EnemyTurn, afterAutoStep.phase)
    }

    private fun battleState(
        companion: BattleCompanionState,
        enemyAttack: Int = 5,
        enemyDefense: Int = 0,
        phase: BattlePhase = BattlePhase.PlayerTurn,
        enemy: starsaga.data.CreatureData = CreatureDatabase.t1Creatures.first(),
        enemyCurrentHp: Int? = null,
    ): BattleState {
        val battleEnemy = enemy.copy(
            attack = enemyAttack,
            defense = enemyDefense,
            hp = if (enemy.id == CreatureDatabase.t1Boss.id) enemy.hp else 100,
        )
        return BattleState(
            enemy = battleEnemy,
            enemyCurrentHp = enemyCurrentHp ?: battleEnemy.hp,
            enemyMaxHp = battleEnemy.hp,
            activeCompanions = listOf(companion),
            message = "",
            phase = phase,
        )
    }

    private fun companion(
        role: CreatureRole,
        attack: Int = 10,
        defense: Int = 0,
        currentHp: Int = 30,
        maxHp: Int = 30,
        currentMp: Int = 0,
    ): BattleCompanionState =
        BattleCompanionState(
            instanceId = "c1",
            name = "test",
            role = role,
            attack = attack,
            defense = defense,
            currentHp = currentHp,
            maxHp = maxHp,
            currentMp = currentMp,
            maxMp = 10,
            skillIds = emptyList(),
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
