package starsaga.battle

import starsaga.data.CreatureRole

object BattleBalance {
    const val ATCK_CRITICAL_CHANCE = 0.20
    const val ATCK_CRITICAL_MULTIPLIER = 1.5
    const val DEFN_DAMAGE_TAKEN_RATE = 0.80
    const val HEAL_ROLE_RECOVERY_RATE = 0.05
    const val HEAL_ROLE_MIN_RECOVERY = 1
    const val AREA_ATTACK_SKILL_BONUS = 3
    const val LUCK_RECRUIT_BONUS_CHANCE = 0.35
    const val BOSS_ENRAGE_HP_RATE = 0.50
    const val BOSS_ENRAGE_ATTACK_MULTIPLIER = 1.20
    const val BOSS_POWER_ATTACK_MULTIPLIER = 1.60
    const val BOSS_POWER_ATTACK_CYCLE = 3

    fun roleEffectText(role: CreatureRole): String = when (role) {
        CreatureRole.ATCK -> "ATCK: ${(ATCK_CRITICAL_CHANCE * 100).toInt()}%でクリティカル"
        CreatureRole.DEFN -> "DEFN: 被ダメージ${((1.0 - DEFN_DAMAGE_TAKEN_RATE) * 100).toInt()}%軽減"
        CreatureRole.HEAL -> "HEAL: 行動後にHP回復"
        CreatureRole.AREA -> "AREA: 攻撃スキル+${AREA_ATTACK_SKILL_BONUS}"
        CreatureRole.LUCK -> "LUCK: 仲間化進行にボーナス"
    }
}
