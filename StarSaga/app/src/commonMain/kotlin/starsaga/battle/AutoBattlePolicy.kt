package starsaga.battle

import starsaga.data.CreatureRole
import starsaga.data.SkillData
import starsaga.data.SkillDatabase
import starsaga.data.SkillKind
import kotlin.math.ceil
import kotlin.math.max

data class AutoSkillChoice(
    val casterInstanceId: String,
    val skill: SkillData,
)

object AutoBattlePolicy {
    fun chooseSkill(state: BattleState): AutoSkillChoice? {
        if (state.phase != BattlePhase.PlayerTurn || state.enemyCurrentHp <= 0) return null
        return chooseHealSkill(state) ?: chooseAttackSkill(state)
    }

    private fun chooseHealSkill(state: BattleState): AutoSkillChoice? {
        val needsHeal = state.activeCompanions.any {
            it.currentHp > 0 && it.currentHp * 2 <= it.maxHp
        }
        if (!needsHeal) return null
        return skillCandidates(state, SkillKind.Heal).firstOrNull()
    }

    private fun chooseAttackSkill(state: BattleState): AutoSkillChoice? {
        val candidates = skillCandidates(state, SkillKind.Attack)
        if (candidates.isEmpty()) return null
        val shouldSpendMp = state.isBossBattle || estimatedPartyNormalDamage(state) < state.enemyCurrentHp
        if (!shouldSpendMp) return null
        val areaCandidates = candidates.filter { choice ->
            state.activeCompanions.firstOrNull { it.instanceId == choice.casterInstanceId }?.role == CreatureRole.AREA
        }
        return (areaCandidates.ifEmpty { candidates }).maxByOrNull { estimatedAttackSkillDamage(state, it) }
    }

    private fun skillCandidates(state: BattleState, kind: SkillKind): List<AutoSkillChoice> =
        state.activeCompanions
            .filter { it.currentHp > 0 }
            .flatMap { companion ->
                companion.skillIds.mapNotNull { skillId ->
                    val skill = SkillDatabase.get(skillId) ?: return@mapNotNull null
                    if (skill.kind == kind && companion.currentMp >= skill.mpCost) {
                        AutoSkillChoice(companion.instanceId, skill)
                    } else {
                        null
                    }
                }
            }

    private fun estimatedPartyNormalDamage(state: BattleState): Int =
        state.activeCompanions
            .filter { it.currentHp > 0 }
            .sumOf { max(1, it.attack - state.enemy.defense / 2) }

    private fun estimatedAttackSkillDamage(state: BattleState, choice: AutoSkillChoice): Int {
        val caster = state.activeCompanions.firstOrNull { it.instanceId == choice.casterInstanceId } ?: return 0
        var damage = max(1, caster.attack + choice.skill.power - state.enemy.defense / 2)
        if (caster.role == CreatureRole.AREA) {
            damage += BattleBalance.AREA_ATTACK_SKILL_BONUS
        }
        if (caster.role == CreatureRole.ATCK) {
            damage = ceil(damage * (1.0 + BattleBalance.ATCK_CRITICAL_CHANCE * (BattleBalance.ATCK_CRITICAL_MULTIPLIER - 1.0))).toInt()
        }
        return damage
    }

    private val BattleState.isBossBattle: Boolean
        get() = enemy.id == starsaga.data.CreatureDatabase.t1Boss.id
}
