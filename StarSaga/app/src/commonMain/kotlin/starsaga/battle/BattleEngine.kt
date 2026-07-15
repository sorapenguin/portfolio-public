package starsaga.battle

import starsaga.data.SkillData
import starsaga.data.SkillKind
import starsaga.data.CreatureRole
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

object BattleEngine {
    fun playerAttack(state: BattleState, random: Random = Random.Default): BattleState {
        if (state.phase != BattlePhase.PlayerTurn) return state

        var enemyHp = state.enemyCurrentHp
        var totalDamage = 0
        val logs = mutableListOf<String>()
        val events = mutableSetOf<BattleEvent>()
        val attackers = state.activeCompanions.filter { it.currentHp > 0 }
        for (attacker in attackers) {
            val result = playerDamage(
                attacker = attacker,
                baseDamage = max(1, attacker.attack - state.enemy.defense / 2),
                isAttackSkill = false,
                random = random,
            )
            val damage = result.damage
            enemyHp = max(0, enemyHp - damage)
            totalDamage += damage
            logs += "${attacker.name}の攻撃！ ${state.enemy.name}に${damage}ダメージ"
            logs += result.logLines
            events += result.events
            if (enemyHp <= 0) {
                return state.afterPlayerDamage(
                    enemyCurrentHp = 0,
                    message = "${state.enemy.name}を倒した！",
                    phase = BattlePhase.Victory,
                    lastDamage = totalDamage,
                    logLines = (logs + "${state.enemy.name}を倒した！").takeLast(MAX_LOG_LINES),
                    baseEvents = events,
                )
            }
        }
        val healed = applyHealRole(attackers, state.activeCompanions)
        logs += healed.logLines
        events += healed.events

        return state.afterPlayerDamage(
            enemyCurrentHp = enemyHp,
            activeCompanions = healed.companions,
            message = logs.lastOrNull() ?: "味方の攻撃！",
            phase = BattlePhase.EnemyTurn,
            lastDamage = totalDamage,
            logLines = logs.takeLast(MAX_LOG_LINES),
            baseEvents = events,
        )
    }

    fun enemyAttack(state: BattleState): BattleState {
        if (state.phase != BattlePhase.EnemyTurn) return state
        if (state.activeCompanions.none { it.currentHp > 0 }) {
            return state.copy(
                message = "全滅した…拠点へ戻った",
                phase = BattlePhase.Defeat,
                lastDamage = 0,
                lastEvents = emptySet(),
            )
        }
        if (state.isBossBattle && state.bossPowerCharging) {
            return bossPowerAttack(state)
        }
        if (state.isBossBattle && state.bossTurnsUntilPowerWarn <= 1) {
            val log = "${state.enemy.name}が力をためている！"
            return state.copy(
                message = log,
                phase = BattlePhase.PlayerTurn,
                lastDamage = 0,
                bossPowerCharging = true,
                bossTurnsUntilPowerWarn = BattleBalance.BOSS_POWER_ATTACK_CYCLE,
                logLines = (state.logLines + log).takeLast(MAX_LOG_LINES),
                lastEvents = setOf(BattleEvent.BossPowerWarned),
            )
        }
        val target = state.activeCompanions.firstOrNull { it.currentHp > 0 }
            ?: return state.copy(
                message = "全滅した…拠点へ戻った",
                phase = BattlePhase.Defeat,
                lastDamage = 0,
                lastEvents = emptySet(),
            )

        val rawDamage = rawEnemyDamage(state, target, powerAttack = false)
        val damage = incomingDamage(target, rawDamage)
        val attackLog = "${state.enemy.name}の反撃！ ${target.name}に${damage}ダメージ"
        val guardLog = if (target.role == CreatureRole.DEFN && damage < rawDamage) {
            "${target.name}が守りを固めた"
        } else {
            null
        }
        val events = if (guardLog != null) setOf(BattleEvent.DefnReduced) else emptySet()
        val updated = state.activeCompanions.map {
            if (it.instanceId == target.instanceId) {
                it.copy(currentHp = max(0, it.currentHp - damage))
            } else {
                it
            }
        }
        val defeated = updated.none { it.currentHp > 0 }
        return state.copy(
            activeCompanions = updated,
            message = if (defeated) {
                "全滅した…拠点へ戻った"
            } else {
                attackLog
            },
            phase = if (defeated) BattlePhase.Defeat else BattlePhase.PlayerTurn,
            lastDamage = damage,
            bossTurnsUntilPowerWarn = if (state.isBossBattle) {
                (state.bossTurnsUntilPowerWarn - 1).coerceAtLeast(1)
            } else {
                state.bossTurnsUntilPowerWarn
            },
            logLines = (state.logLines + attackLog + guardLog + if (defeated) "全滅した…拠点へ戻った" else "").filterNotNull().filter {
                it.isNotBlank()
            }.takeLast(MAX_LOG_LINES),
            lastEvents = events,
        )
    }

    fun useSkill(state: BattleState, casterInstanceId: String, skill: SkillData, random: Random = Random.Default): BattleState {
        if (state.phase != BattlePhase.PlayerTurn) return state
        val caster = state.activeCompanions.firstOrNull {
            it.instanceId == casterInstanceId && it.currentHp > 0
        }
            ?: return state.copy(
                message = "行動できる仲間がいない",
                logLines = (state.logLines + "行動できる仲間がいない").takeLast(MAX_LOG_LINES),
                lastEvents = emptySet(),
            )
        if (caster.currentMp < skill.mpCost) {
            val log = "${caster.name}はMPが足りない！"
            return state.copy(
                message = log,
                logLines = (state.logLines + log).takeLast(MAX_LOG_LINES),
                lastEvents = emptySet(),
            )
        }

        return when (skill.kind) {
            SkillKind.Attack -> useAttackSkill(state, caster, skill, random)
            SkillKind.Heal -> useHealSkill(state, caster, skill)
        }
    }

    fun escape(state: BattleState): BattleState =
        state.copy(
            message = "うまく逃げ切った！",
            phase = BattlePhase.Escaped,
            lastDamage = 0,
            logLines = (state.logLines + "うまく逃げ切った！").takeLast(MAX_LOG_LINES),
            lastEvents = emptySet(),
        )

    private fun useAttackSkill(
        state: BattleState,
        caster: BattleCompanionState,
        skill: SkillData,
        random: Random,
    ): BattleState {
        val baseDamage = max(1, caster.attack + skill.power - state.enemy.defense / 2)
        val result = playerDamage(
            attacker = caster,
            baseDamage = baseDamage,
            isAttackSkill = true,
            random = random,
        )
        val damage = result.damage
        val enemyHp = max(0, state.enemyCurrentHp - damage)
        val updatedCompanions = consumeMp(state, caster, skill.mpCost)
        val skillLog = "${caster.name}は${skill.name}を使った！ ${state.enemy.name}に${damage}ダメージ"
        if (enemyHp <= 0) {
            return state.afterPlayerDamage(
                enemyCurrentHp = 0,
                activeCompanions = updatedCompanions,
                message = "${state.enemy.name}を倒した！",
                phase = BattlePhase.Victory,
                lastDamage = damage,
                logLines = (state.logLines + skillLog + result.logLines + "${state.enemy.name}を倒した！").takeLast(MAX_LOG_LINES),
                baseEvents = result.events,
            )
        }
        val healed = applyHealRole(listOf(caster), updatedCompanions)
        val events = result.events + healed.events
        return state.afterPlayerDamage(
            enemyCurrentHp = enemyHp,
            activeCompanions = healed.companions,
            message = (result.logLines + healed.logLines).lastOrNull() ?: skillLog,
            phase = BattlePhase.EnemyTurn,
            lastDamage = damage,
            logLines = (state.logLines + skillLog + result.logLines + healed.logLines).takeLast(MAX_LOG_LINES),
            baseEvents = events,
        )
    }

    private fun useHealSkill(
        state: BattleState,
        caster: BattleCompanionState,
        skill: SkillData,
    ): BattleState {
        val target = state.activeCompanions
            .filter { it.currentHp > 0 }
            .maxByOrNull { it.maxHp - it.currentHp }
            ?: caster
        var healed = 0
        val updatedCompanions = state.activeCompanions.map {
            val afterMp = if (it.instanceId == caster.instanceId) {
                max(0, it.currentMp - skill.mpCost)
            } else {
                it.currentMp
            }
            if (it.instanceId == target.instanceId) {
                val afterHp = min(it.maxHp, it.currentHp + skill.power)
                healed = afterHp - it.currentHp
                it.copy(currentHp = afterHp, currentMp = afterMp)
            } else {
                it.copy(currentMp = afterMp)
            }
        }
        val log = "${caster.name}は${skill.name}を使った！ ${target.name}が${healed}回復"
        val roleHeal = applyHealRole(listOf(caster), updatedCompanions)
        return state.copy(
            activeCompanions = roleHeal.companions,
            message = roleHeal.logLines.lastOrNull() ?: log,
            phase = BattlePhase.EnemyTurn,
            lastDamage = 0,
            logLines = (state.logLines + log + roleHeal.logLines).takeLast(MAX_LOG_LINES),
            lastEvents = roleHeal.events,
        )
    }

    private fun consumeMp(
        state: BattleState,
        caster: BattleCompanionState,
        mpCost: Int,
    ): List<BattleCompanionState> =
        state.activeCompanions.map {
            if (it.instanceId == caster.instanceId) {
                it.copy(currentMp = max(0, it.currentMp - mpCost))
            } else {
                it
            }
        }

    private data class DamageResult(
        val damage: Int,
        val logLines: List<String>,
        val events: Set<BattleEvent>,
    )

    private data class HealRoleResult(
        val companions: List<BattleCompanionState>,
        val logLines: List<String>,
        val events: Set<BattleEvent>,
    )

    private fun playerDamage(
        attacker: BattleCompanionState,
        baseDamage: Int,
        isAttackSkill: Boolean,
        random: Random,
    ): DamageResult {
        var damage = baseDamage
        val logs = mutableListOf<String>()
        val events = mutableSetOf<BattleEvent>()
        if (attacker.role == CreatureRole.AREA && isAttackSkill) {
            damage += BattleBalance.AREA_ATTACK_SKILL_BONUS
            logs += "${attacker.name}の範囲術式が広がった"
            events += BattleEvent.AreaBonus
        }
        if (attacker.role == CreatureRole.ATCK && random.nextDouble() < BattleBalance.ATCK_CRITICAL_CHANCE) {
            damage = max(1, ceil(damage * BattleBalance.ATCK_CRITICAL_MULTIPLIER).toInt())
            logs += "${attacker.name}のクリティカル！"
            events += BattleEvent.Critical
        }
        return DamageResult(damage, logs, events)
    }

    private fun incomingDamage(target: BattleCompanionState, rawDamage: Int): Int =
        if (target.role == CreatureRole.DEFN) {
            max(1, (rawDamage * BattleBalance.DEFN_DAMAGE_TAKEN_RATE).toInt())
        } else {
            rawDamage
        }

    private fun applyHealRole(
        actors: List<BattleCompanionState>,
        companions: List<BattleCompanionState>,
    ): HealRoleResult {
        var updated = companions
        val logs = mutableListOf<String>()
        val events = mutableSetOf<BattleEvent>()
        actors.filter { it.role == CreatureRole.HEAL && it.currentHp > 0 }.forEach { healer ->
            val index = updated.indexOfFirst { it.instanceId == healer.instanceId && it.currentHp > 0 }
            if (index < 0) return@forEach
            val current = updated[index]
            val amount = max(BattleBalance.HEAL_ROLE_MIN_RECOVERY, (current.maxHp * BattleBalance.HEAL_ROLE_RECOVERY_RATE).toInt())
            val nextHp = min(current.maxHp, current.currentHp + amount)
            val healed = nextHp - current.currentHp
            if (healed > 0) {
                updated = updated.toMutableList().also { list ->
                    list[index] = current.copy(currentHp = nextHp)
                }
                logs += "${current.name}が星光で${healed}回復"
                events += BattleEvent.HealRecovered
            }
        }
        return HealRoleResult(updated, logs, events)
    }

    private fun bossPowerAttack(state: BattleState): BattleState {
        val target = state.activeCompanions.firstOrNull { it.currentHp > 0 }
            ?: return state.copy(
                message = "全滅した…拠点へ戻った",
                phase = BattlePhase.Defeat,
                lastDamage = 0,
                lastEvents = emptySet(),
            )
        val rawDamage = rawEnemyDamage(state, target, powerAttack = true)
        val damage = incomingDamage(target, rawDamage)
        val attackLog = "${state.enemy.name}の強攻撃！ ${target.name}に${damage}ダメージ"
        val guardLog = if (target.role == CreatureRole.DEFN && damage < rawDamage) {
            "${target.name}が守りを固めた"
        } else {
            null
        }
        val events = mutableSetOf(BattleEvent.BossPowerAttacked)
        if (guardLog != null) events += BattleEvent.DefnReduced
        val updated = state.activeCompanions.map {
            if (it.instanceId == target.instanceId) {
                it.copy(currentHp = max(0, it.currentHp - damage))
            } else {
                it
            }
        }
        val defeated = updated.none { it.currentHp > 0 }
        return state.copy(
            activeCompanions = updated,
            message = if (defeated) "全滅した…拠点へ戻った" else attackLog,
            phase = if (defeated) BattlePhase.Defeat else BattlePhase.PlayerTurn,
            lastDamage = damage,
            bossPowerCharging = false,
            bossTurnsUntilPowerWarn = BattleBalance.BOSS_POWER_ATTACK_CYCLE,
            logLines = (state.logLines + attackLog + guardLog + if (defeated) "全滅した…拠点へ戻った" else "").filterNotNull().filter {
                it.isNotBlank()
            }.takeLast(MAX_LOG_LINES),
            lastEvents = events,
        )
    }

    private fun rawEnemyDamage(
        state: BattleState,
        target: BattleCompanionState,
        powerAttack: Boolean,
    ): Int {
        var attack = state.enemy.attack.toDouble()
        if (state.isBossBattle && state.bossEnraged) {
            attack *= BattleBalance.BOSS_ENRAGE_ATTACK_MULTIPLIER
        }
        if (powerAttack) {
            attack *= BattleBalance.BOSS_POWER_ATTACK_MULTIPLIER
        }
        return max(1, ceil(attack).toInt() - target.defense / 2)
    }

    private fun BattleState.afterPlayerDamage(
        enemyCurrentHp: Int,
        activeCompanions: List<BattleCompanionState> = this.activeCompanions,
        message: String,
        phase: BattlePhase,
        lastDamage: Int,
        logLines: List<String>,
        baseEvents: Set<BattleEvent>,
    ): BattleState {
        val shouldEnrage = isBossBattle &&
            !bossEnraged &&
            enemyCurrentHp > 0 &&
            enemyCurrentHp <= ceil(enemyMaxHp * BattleBalance.BOSS_ENRAGE_HP_RATE).toInt()
        val enrageLog = if (shouldEnrage) {
            "${enemy.name}の星草がざわめいた！"
        } else {
            null
        }
        return copy(
            enemyCurrentHp = enemyCurrentHp,
            activeCompanions = activeCompanions,
            message = enrageLog ?: message,
            phase = phase,
            lastDamage = lastDamage,
            logLines = (logLines + enrageLog).filterNotNull().takeLast(MAX_LOG_LINES),
            bossEnraged = bossEnraged || shouldEnrage,
            lastEvents = baseEvents + if (shouldEnrage) setOf(BattleEvent.BossEnraged) else emptySet(),
        )
    }

    private val BattleState.isBossBattle: Boolean
        get() = enemy.id == starsaga.data.CreatureDatabase.t1Boss.id

    private const val MAX_LOG_LINES = 3
}
