package starsaga.battle

import kotlin.random.Random

data class BefriendResult(
    val offered: Boolean,
    val chance: Double,
    val roll: Double,
)

object BefriendResolver {
    fun chanceForDefeatCount(defeatCount: Int): Double = when (defeatCount) {
        1 -> 0.10
        2 -> 0.25
        3 -> 0.50
        4 -> 0.75
        else -> 1.00
    }

    fun roll(defeatCount: Int, random: Random = Random.Default): BefriendResult {
        val chance = chanceForDefeatCount(defeatCount)
        val roll = random.nextDouble()
        return BefriendResult(
            offered = roll < chance,
            chance = chance,
            roll = roll,
        )
    }
}
