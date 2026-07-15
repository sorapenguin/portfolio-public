package starsaga.battle

import starsaga.data.CreatureData
import starsaga.data.CreatureRole

enum class BattlePhase {
    PlayerTurn,
    EnemyTurn,
    Victory,
    Defeat,
    Escaped,
}

enum class BattleEvent {
    Critical,
    DefnReduced,
    HealRecovered,
    AreaBonus,
    BossEnraged,
    BossPowerWarned,
    BossPowerAttacked,
}

data class BattleCompanionState(
    val instanceId: String,
    val name: String,
    val role: CreatureRole,
    val attack: Int,
    val defense: Int,
    val currentHp: Int,
    val maxHp: Int,
    val currentMp: Int,
    val maxMp: Int,
    val skillIds: List<Int>,
)

data class BattleState(
    val enemy: CreatureData,
    val enemyCurrentHp: Int,
    val enemyMaxHp: Int,
    val activeCompanions: List<BattleCompanionState>,
    val message: String,
    val phase: BattlePhase,
    val lastDamage: Int = 0,
    val logLines: List<String> = emptyList(),
    val bossEnraged: Boolean = false,
    val bossPowerCharging: Boolean = false,
    val bossTurnsUntilPowerWarn: Int = BattleBalance.BOSS_POWER_ATTACK_CYCLE,
    val lastEvents: Set<BattleEvent> = emptySet(),
)
