package starsaga.sim

import kotlin.math.roundToInt
import kotlin.random.Random
import starsaga.battle.AutoBattlePolicy
import starsaga.battle.BattleCompanionState
import starsaga.battle.BattleEngine
import starsaga.battle.BattleEvent
import starsaga.battle.BattlePhase
import starsaga.battle.BattleState
import starsaga.battle.Leveling
import starsaga.battle.RecruitmentProgress
import starsaga.data.CreatureData
import starsaga.data.CreatureDatabase
import starsaga.data.SkillDatabase
import starsaga.data.SkillKind

enum class SimStrategy(val label: String) {
    AttackFirst("攻撃優先"),
    SurvivalFirst("生存優先"),
    CurrentAuto("現行AUTO"),
}

data class PartyTemplate(
    val name: String,
    val creatureIds: List<Int>,
    val level: Int,
)

data class BattleSimulationResult(
    val partyName: String,
    val strategy: SimStrategy,
    val enemyName: String,
    val seed: Int,
    val victory: Boolean,
    val timeout: Boolean,
    val turns: Int,
    val remainingHp: Int,
    val defeatedCompanions: Int,
    val criticalCount: Int,
    val defnReducedCount: Int,
    val healRecoveredCount: Int,
    val areaBonusCount: Int,
    val skillUseCount: Int,
    val mpSpent: Int,
    val bossEnrageCount: Int,
    val bossEnrageTurn: Int?,
    val bossPowerWarnCount: Int,
    val bossPowerAttackCount: Int,
    val bossPowerDownCount: Int,
    val anomalies: List<String>,
)

data class BattleAggregate(
    val label: String,
    val battles: Int,
    val winRate: Double,
    val averageTurns: Double,
    val minTurns: Int,
    val maxTurns: Int,
    val averageRemainingHp: Double,
    val defeatRate: Double,
    val criticalCount: Int,
    val defnReducedCount: Int,
    val healRecoveredCount: Int,
    val areaBonusCount: Int,
    val skillUseCount: Int,
    val mpSpent: Int,
    val timeoutCount: Int,
    val anomalyCount: Int,
    val bossEnrageRate: Double,
    val averageBossEnrageTurn: Double?,
    val bossPowerWarnCount: Int,
    val bossPowerAttackCount: Int,
    val bossPowerDownCount: Int,
)

data class RecruitmentAggregate(
    val label: String,
    val runs: Int,
    val averageBattles: Double,
    val minBattles: Int,
    val maxBattles: Int,
    val within3Rate: Double,
    val within4Rate: Double,
    val exactly5Rate: Double,
    val averageLuckBonuses: Double,
    val overflowCount: Int,
)

object T1BattleSimulator {
    const val DEFAULT_MAX_TURNS = 80
    const val RECRUIT_THRESHOLD = 5

    val parties = listOf(
        PartyTemplate("ATCK中心", listOf(1, 1, 3), level = 2),
        PartyTemplate("DEFN含む", listOf(2, 1, 3), level = 2),
        PartyTemplate("HEAL含む", listOf(4, 1, 2), level = 2),
        PartyTemplate("AREA含む", listOf(3, 1, 4), level = 2),
        PartyTemplate("LUCK含む", listOf(5, 1, 4), level = 2),
        PartyTemplate("バランス", listOf(1, 2, 4), level = 2),
    )

    val bossParties = parties.map { it.copy(level = 3) }

    fun simulateBattle(
        party: PartyTemplate,
        enemy: CreatureData,
        strategy: SimStrategy,
        seed: Int,
        maxTurns: Int = DEFAULT_MAX_TURNS,
    ): BattleSimulationResult {
        val random = Random(seed)
        var state = initialState(party, enemy)
        var turns = 0
        var critical = 0
        var defn = 0
        var heal = 0
        var area = 0
        var skillUse = 0
        var mpSpent = 0
        var enrage = 0
        var enrageTurn: Int? = null
        var warn = 0
        var power = 0
        var powerDown = 0
        val anomalies = mutableListOf<String>()

        while (state.phase == BattlePhase.PlayerTurn && turns < maxTurns) {
            turns += 1
            val playerAction = playerAction(state, strategy, random)
            val afterPlayer = playerAction.state
            skillUse += playerAction.skillUsed
            mpSpent += playerAction.mpSpent
            countEvents(afterPlayer.lastEvents).also {
                critical += it.critical
                defn += it.defn
                heal += it.heal
                area += it.area
                if (it.enrage > 0) {
                    enrage += it.enrage
                    if (enrageTurn == null) enrageTurn = turns
                    if (enrage > 1) anomalies += "boss enraged multiple times"
                }
            }
            validateState(afterPlayer)?.let { anomalies += it }
            state = when (afterPlayer.phase) {
                BattlePhase.EnemyTurn -> {
                    val beforeEnemy = afterPlayer
                    val afterEnemy = BattleEngine.enemyAttack(beforeEnemy)
                    val enemyEvents = countEvents(afterEnemy.lastEvents)
                    defn += enemyEvents.defn
                    warn += enemyEvents.warn
                    power += enemyEvents.power
                    if (enemyEvents.power > 0 && !beforeEnemy.bossPowerCharging) {
                        anomalies += "boss power attack without warning"
                    }
                    if (enemyEvents.power > 0) {
                        powerDown += downedByEnemyAction(beforeEnemy, afterEnemy)
                    }
                    if (enemyEvents.warn > 0 && afterEnemy.phase != BattlePhase.PlayerTurn) {
                        anomalies += "boss warning did not return to player"
                    }
                    validateState(afterEnemy)?.let { anomalies += it }
                    afterEnemy
                }
                else -> afterPlayer
            }
            if (state.bossPowerCharging && state.phase !in setOf(BattlePhase.PlayerTurn, BattlePhase.Victory, BattlePhase.Defeat)) {
                anomalies += "boss warning stalled outside player turn"
            }
        }

        val timeout = state.phase == BattlePhase.PlayerTurn && turns >= maxTurns
        if (timeout) anomalies += "battle exceeded $maxTurns turns"
        return BattleSimulationResult(
            partyName = party.name,
            strategy = strategy,
            enemyName = enemy.name,
            seed = seed,
            victory = state.phase == BattlePhase.Victory,
            timeout = timeout,
            turns = turns,
            remainingHp = state.activeCompanions.sumOf { it.currentHp },
            defeatedCompanions = state.activeCompanions.count { it.currentHp <= 0 },
            criticalCount = critical,
            defnReducedCount = defn,
            healRecoveredCount = heal,
            areaBonusCount = area,
            skillUseCount = skillUse,
            mpSpent = mpSpent,
            bossEnrageCount = enrage,
            bossEnrageTurn = enrageTurn,
            bossPowerWarnCount = warn,
            bossPowerAttackCount = power,
            bossPowerDownCount = powerDown,
            anomalies = anomalies,
        )
    }

    fun aggregate(label: String, results: List<BattleSimulationResult>): BattleAggregate {
        val battles = results.size.coerceAtLeast(1)
        val enrageTurns = results.mapNotNull { it.bossEnrageTurn }
        return BattleAggregate(
            label = label,
            battles = results.size,
            winRate = results.count { it.victory }.toDouble() / battles,
            averageTurns = results.sumOf { it.turns }.toDouble() / battles,
            minTurns = results.minOfOrNull { it.turns } ?: 0,
            maxTurns = results.maxOfOrNull { it.turns } ?: 0,
            averageRemainingHp = results.sumOf { it.remainingHp }.toDouble() / battles,
            defeatRate = results.count { it.defeatedCompanions > 0 }.toDouble() / battles,
            criticalCount = results.sumOf { it.criticalCount },
            defnReducedCount = results.sumOf { it.defnReducedCount },
            healRecoveredCount = results.sumOf { it.healRecoveredCount },
            areaBonusCount = results.sumOf { it.areaBonusCount },
            skillUseCount = results.sumOf { it.skillUseCount },
            mpSpent = results.sumOf { it.mpSpent },
            timeoutCount = results.count { it.timeout },
            anomalyCount = results.sumOf { it.anomalies.size },
            bossEnrageRate = results.count { it.bossEnrageCount > 0 }.toDouble() / battles,
            averageBossEnrageTurn = enrageTurns.takeIf { it.isNotEmpty() }?.let { values ->
                values.sum().toDouble() / values.size
            },
            bossPowerWarnCount = results.sumOf { it.bossPowerWarnCount },
            bossPowerAttackCount = results.sumOf { it.bossPowerAttackCount },
            bossPowerDownCount = results.sumOf { it.bossPowerDownCount },
        )
    }

    fun simulateRecruitment(hasLuck: Boolean, seeds: IntRange): RecruitmentAggregate {
        val battles = mutableListOf<Int>()
        val luckBonuses = mutableListOf<Int>()
        var overflow = 0
        seeds.forEach { seed ->
            val random = Random(seed)
            var progress = 0
            var battleCount = 0
            var bonuses = 0
            while (progress < RECRUIT_THRESHOLD && battleCount < 20) {
                battleCount += 1
                val result = RecruitmentProgress.advance(progress, RECRUIT_THRESHOLD, hasLuck, random)
                if (result.after > RECRUIT_THRESHOLD) overflow += 1
                if (result.luckBonus) bonuses += 1
                progress = result.after
            }
            battles += battleCount
            luckBonuses += bonuses
        }
        val runs = battles.size.coerceAtLeast(1)
        return RecruitmentAggregate(
            label = if (hasLuck) "LUCKあり" else "LUCKなし",
            runs = battles.size,
            averageBattles = battles.sum().toDouble() / runs,
            minBattles = battles.minOrNull() ?: 0,
            maxBattles = battles.maxOrNull() ?: 0,
            within3Rate = battles.count { it <= 3 }.toDouble() / runs,
            within4Rate = battles.count { it <= 4 }.toDouble() / runs,
            exactly5Rate = battles.count { it == 5 }.toDouble() / runs,
            averageLuckBonuses = luckBonuses.sum().toDouble() / runs,
            overflowCount = overflow,
        )
    }

    private fun initialState(party: PartyTemplate, enemy: CreatureData): BattleState {
        val companions = party.creatureIds.mapIndexed { index, creatureId ->
            val creature = CreatureDatabase.get(creatureId) ?: error("missing creature $creatureId")
            BattleCompanionState(
                instanceId = "${party.name}-$index",
                name = creature.name,
                role = creature.role,
                attack = Leveling.attackWithLevel(creature.attack, party.level),
                defense = Leveling.defenseWithLevel(creature.defense, party.level),
                currentHp = Leveling.maxHp(creature.hp, party.level),
                maxHp = Leveling.maxHp(creature.hp, party.level),
                currentMp = Leveling.maxMp(creature.mp, party.level),
                maxMp = Leveling.maxMp(creature.mp, party.level),
                skillIds = SkillDatabase.initialSkillIdsFor(creature.id),
            )
        }
        return BattleState(
            enemy = enemy,
            enemyCurrentHp = enemy.hp,
            enemyMaxHp = enemy.hp,
            activeCompanions = companions,
            message = "${enemy.name}が現れた！",
            phase = BattlePhase.PlayerTurn,
            logLines = listOf("${enemy.name}が現れた！"),
        )
    }

    private data class PlayerActionResult(
        val state: BattleState,
        val skillUsed: Int,
        val mpSpent: Int,
    )

    private fun playerAction(state: BattleState, strategy: SimStrategy, random: Random): PlayerActionResult {
        val beforeMp = totalMp(state)
        val after = when (strategy) {
            SimStrategy.AttackFirst -> useAttackSkillIfPossible(state, random) ?: BattleEngine.playerAttack(state, random)
            SimStrategy.SurvivalFirst -> useHealIfNeeded(state) ?: useAttackSkillIfPossible(state, random) ?: BattleEngine.playerAttack(state, random)
            SimStrategy.CurrentAuto -> AutoBattlePolicy.chooseSkill(state)?.let { choice ->
                BattleEngine.useSkill(state, choice.casterInstanceId, choice.skill, random)
            } ?: BattleEngine.playerAttack(state, random)
        }
        val spent = (beforeMp - totalMp(after)).coerceAtLeast(0)
        return PlayerActionResult(
            state = after,
            skillUsed = if (spent > 0) 1 else 0,
            mpSpent = spent,
        )
    }

    private fun totalMp(state: BattleState): Int =
        state.activeCompanions.sumOf { it.currentMp }

    private fun useAttackSkillIfPossible(state: BattleState, random: Random): BattleState? {
        val caster = state.activeCompanions.firstOrNull { companion ->
            companion.currentHp > 0 &&
                companion.skillIds.any { skillId ->
                    val skill = SkillDatabase.get(skillId)
                    skill?.kind == SkillKind.Attack && companion.currentMp >= skill.mpCost
                }
        } ?: return null
        val skillId = caster.skillIds.firstOrNull { skillId ->
            val skill = SkillDatabase.get(skillId)
            skill?.kind == SkillKind.Attack && caster.currentMp >= skill.mpCost
        } ?: return null
        return BattleEngine.useSkill(state, caster.instanceId, SkillDatabase.get(skillId) ?: return null, random)
    }

    private fun useHealIfNeeded(state: BattleState): BattleState? {
        val needsHeal = state.activeCompanions.any {
            it.currentHp > 0 && it.currentHp * 2 <= it.maxHp
        }
        if (!needsHeal) return null
        val caster = state.activeCompanions.firstOrNull { companion ->
            companion.currentHp > 0 &&
                companion.skillIds.any { skillId ->
                    val skill = SkillDatabase.get(skillId)
                    skill?.kind == SkillKind.Heal && companion.currentMp >= skill.mpCost
                }
        } ?: return null
        val skillId = caster.skillIds.firstOrNull { skillId ->
            val skill = SkillDatabase.get(skillId)
            skill?.kind == SkillKind.Heal && caster.currentMp >= skill.mpCost
        } ?: return null
        return BattleEngine.useSkill(state, caster.instanceId, SkillDatabase.get(skillId) ?: return null)
    }

    private data class EventCounts(
        val critical: Int = 0,
        val defn: Int = 0,
        val heal: Int = 0,
        val area: Int = 0,
        val enrage: Int = 0,
        val warn: Int = 0,
        val power: Int = 0,
    )

    private fun countEvents(events: Set<BattleEvent>): EventCounts =
        EventCounts(
            critical = if (BattleEvent.Critical in events) 1 else 0,
            defn = if (BattleEvent.DefnReduced in events) 1 else 0,
            heal = if (BattleEvent.HealRecovered in events) 1 else 0,
            area = if (BattleEvent.AreaBonus in events) 1 else 0,
            enrage = if (BattleEvent.BossEnraged in events) 1 else 0,
            warn = if (BattleEvent.BossPowerWarned in events) 1 else 0,
            power = if (BattleEvent.BossPowerAttacked in events) 1 else 0,
        )

    private fun downedByEnemyAction(before: BattleState, after: BattleState): Int =
        before.activeCompanions.count { beforeCompanion ->
            beforeCompanion.currentHp > 0 &&
                after.activeCompanions.firstOrNull { it.instanceId == beforeCompanion.instanceId }?.currentHp == 0
        }

    private fun validateState(state: BattleState): String? {
        if (state.enemyCurrentHp !in 0..state.enemyMaxHp) return "enemy hp out of range"
        val invalidCompanion = state.activeCompanions.firstOrNull { it.currentHp !in 0..it.maxHp || it.currentMp !in 0..it.maxMp }
        if (invalidCompanion != null) return "companion hp/mp out of range"
        if (state.phase == BattlePhase.Victory && state.enemyCurrentHp != 0) return "victory with enemy hp > 0"
        if (state.phase == BattlePhase.Defeat && state.activeCompanions.any { it.currentHp > 0 }) return "defeat with living companion"
        return null
    }
}

fun Double.percent(): String = "${(this * 1000).roundToInt() / 10.0}%"
fun Double.oneDecimal(): String = "${(this * 10).roundToInt() / 10.0}"
