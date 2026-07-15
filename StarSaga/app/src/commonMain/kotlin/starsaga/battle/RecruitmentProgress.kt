package starsaga.battle

import kotlin.random.Random

data class RecruitmentProgressResult(
    val before: Int,
    val after: Int,
    val amount: Int,
    val luckBonus: Boolean,
    val completed: Boolean,
)

object RecruitmentProgress {
    fun advance(
        currentProgress: Int,
        threshold: Int,
        hasLuckRole: Boolean,
        random: Random = Random.Default,
    ): RecruitmentProgressResult {
        val before = currentProgress.coerceIn(0, threshold)
        val luckBonus = hasLuckRole && random.nextDouble() < BattleBalance.LUCK_RECRUIT_BONUS_CHANCE
        val amount = if (luckBonus) 2 else 1
        val after = (before + amount).coerceAtMost(threshold)
        return RecruitmentProgressResult(
            before = before,
            after = after,
            amount = after - before,
            luckBonus = luckBonus,
            completed = after >= threshold,
        )
    }
}
