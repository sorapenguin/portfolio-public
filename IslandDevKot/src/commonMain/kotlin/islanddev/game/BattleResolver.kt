package islanddev.game

import islanddev.data.GameData
import islanddev.model.SaveData
import kotlin.math.ceil

data class BattleResult(
    val won: Boolean,
    val drops: Map<Int, Int>
)

object BattleResolver {
    fun resolveEnemy(
        save: SaveData,
        enemyId: Int,
        nowSec: Long
    ): Pair<SaveData, BattleResult> =
        resolveEnemyAt(save, enemyId, enemyCellId = null, nowSec = nowSec)

    fun resolveEnemyAt(
        save: SaveData,
        enemyId: Int,
        enemyCellId: Int?,
        nowSec: Long
    ): Pair<SaveData, BattleResult> {
        val enemy = GameData.enemyById(enemyId)
            ?: return save to BattleResult(won = false, drops = emptyMap())
        if (save.currentAtk < enemy.requiredAtk) {
            return save to BattleResult(won = false, drops = emptyMap())
        }

        val drops = enemy.dropResourceId?.let {
            mapOf(it to boostedEnemyDropAmount(enemy.dropAmount, save.builtFacilityIds))
        } ?: emptyMap()
        var updated = drops.entries.fold(save) { current, (resourceId, amount) ->
            current.addInventory(resourceId, amount)
        }
        val targetIndex = updated.enemyCells.indexOfFirst {
            it.enemyId == enemyId &&
                !it.defeated &&
                (enemyCellId == null || it.id == enemyCellId)
        }
        if (targetIndex >= 0) {
            val cells = updated.enemyCells.toMutableList()
            cells[targetIndex] = cells[targetIndex].copy(
                defeated = true,
                defeatedAtSec = nowSec
            )
            updated = updated.copy(enemyCells = cells)
        }

        return updated to BattleResult(won = true, drops = drops)
    }

    private fun boostedEnemyDropAmount(baseAmount: Int, builtFacilityIds: Set<Int>): Int {
        val bonus = FacilityEffects.dropRateBonus(builtFacilityIds)
        return ceil(baseAmount * bonus).toInt().coerceAtLeast(baseAmount)
    }

    fun resolveBoss(save: SaveData, bossId: Int): Pair<SaveData, BattleResult> {
        val boss = GameData.bossById(bossId)
            ?: return save to BattleResult(won = false, drops = emptyMap())
        if (save.currentAtk < boss.requiredAtk) {
            return save to BattleResult(won = false, drops = emptyMap())
        }

        val drops = boss.dropResourceId?.let { mapOf(it to boss.dropAmount) } ?: emptyMap()
        var updated = drops.entries.fold(save) { current, (resourceId, amount) ->
            current.addInventory(resourceId, amount)
        }
        updated = updated.copy(
            defeatedBossIds = updated.defeatedBossIds + bossId,
            unlockedZoneIds = if (boss.toZoneId >= 0) {
                updated.unlockedZoneIds + boss.toZoneId
            } else {
                updated.unlockedZoneIds
            },
            gameCleared = updated.gameCleared || boss.toZoneId == -1
        )

        return updated to BattleResult(won = true, drops = drops)
    }
}
