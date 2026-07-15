package starsaga.battle

import starsaga.data.CompanionState

data class ExpGainResult(
    val companion: CompanionState,
    val gainedExp: Int,
    val levelsGained: Int,
)

object Leveling {
    fun requiredExpForNextLevel(level: Int): Int =
        20 + (level - 1).coerceAtLeast(0) * 15

    fun maxHp(baseHp: Int, level: Int): Int =
        baseHp + (level - 1).coerceAtLeast(0) * HP_GAIN_PER_LEVEL

    fun maxMp(baseMp: Int, level: Int): Int =
        baseMp + (level - 1).coerceAtLeast(0) * MP_GAIN_PER_LEVEL

    fun grantExp(companion: CompanionState, expReward: Int): ExpGainResult {
        var level = companion.level
        var exp = companion.exp + expReward
        var hp = companion.hp
        var mp = companion.mp
        var levelsGained = 0

        while (exp >= requiredExpForNextLevel(level)) {
            exp -= requiredExpForNextLevel(level)
            level += 1
            levelsGained += 1
            hp += HP_GAIN_PER_LEVEL
            mp += MP_GAIN_PER_LEVEL
        }

        return ExpGainResult(
            companion = companion.copy(
                level = level,
                exp = exp,
                hp = hp,
                mp = mp,
            ),
            gainedExp = expReward,
            levelsGained = levelsGained,
        )
    }

    fun attackWithLevel(baseAttack: Int, level: Int): Int =
        baseAttack + (level - 1).coerceAtLeast(0)

    fun defenseWithLevel(baseDefense: Int, level: Int): Int =
        baseDefense + (level - 1).coerceAtLeast(0)

    private const val HP_GAIN_PER_LEVEL = 4
    private const val MP_GAIN_PER_LEVEL = 2
}
