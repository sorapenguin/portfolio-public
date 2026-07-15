package islanddev.game

import islanddev.data.GameData
import islanddev.model.SaveData

object ObjectiveGuide {
    fun currentObjective(save: SaveData): String {
        return "${shortTermObjective(save)}\n${clearObjective(save)}"
    }

    fun shortTermObjective(save: SaveData): String {
        if (save.gameCleared || GameData.BOSSES.all { it.id in save.defeatedBossIds }) {
            return "短期: 自由に島づくり"
        }

        if (0 !in save.defeatedBossIds) {
            return beachOpeningObjective(save)
        }

        nextBoss(save)?.let { boss ->
            if (save.currentAtk >= boss.requiredAtk) {
                return "短期: ${boss.name}に挑もう"
            }
            currentZoneEnemyObjective(save, boss.fromZoneId)?.let { return it }
            return "短期: ATK ${boss.requiredAtk}を目指そう"
        }

        nextDevelopableSubZone(save)?.let { zone ->
            return "短期: ${zone.name}を開拓しよう"
        }

        return "短期: 次の島づくりを進めよう"
    }

    private fun beachOpeningObjective(save: SaveData): String {
        if (1 !in save.craftedWeaponIds) {
            return "短期: 木材で木の棒を作ろう"
        }
        if (!save.hasDefeatedEnemy(0)) {
            return "短期: クラゲを倒そう"
        }
        if (2 !in save.craftedWeaponIds) {
            return "短期: 貝殻ナイフを作ろう"
        }
        if (!save.hasDefeatedEnemy(1)) {
            return "短期: 大ガニを倒そう"
        }
        if (save.builtFacilityIds.isEmpty()) {
            return "短期: 施設を1つ建てよう"
        }
        return "短期: 砂浜の主・大ガメに挑もう"
    }

    fun clearObjective(save: SaveData): String {
        if (save.gameCleared || GameData.BOSSES.all { it.id in save.defeatedBossIds }) {
            return "最終目標: 島の主を倒した"
        }
        val zoneId = currentProgressZone(save)
        val progress = save.unlockedZoneIds.size.coerceIn(1, 5)
        return "最終目標: 島の主を倒す / ${zoneShortName(zoneId)} $progress/5"
    }

    private fun nextBoss(save: SaveData) =
        GameData.BOSSES
            .filter { it.fromZoneId in save.unlockedZoneIds }
            .firstOrNull { it.id !in save.defeatedBossIds }

    private fun currentProgressZone(save: SaveData): Int =
        nextBoss(save)?.fromZoneId
            ?: save.unlockedZoneIds.maxOrNull()
            ?: GameData.ZONE_BEACH

    private fun currentZoneEnemyObjective(save: SaveData, zoneId: Int): String? =
        save.enemyCells
            .filter { !it.defeated }
            .mapNotNull { cell ->
                val enemy = GameData.enemyById(cell.enemyId) ?: return@mapNotNull null
                if (enemy.zoneId != zoneId || save.currentAtk < enemy.requiredAtk) return@mapNotNull null
                enemy
            }
            .minByOrNull { it.requiredAtk }
            ?.let { "短期: ${it.name}を倒そう" }

    private fun nextDevelopableSubZone(save: SaveData) =
        GameData.SUB_ZONES
            .filter { it.parentZoneId in save.unlockedZoneIds }
            .filter { it.id !in save.unlockedSubZoneIds }
            .firstOrNull { zone ->
                val previous = GameData.SUB_ZONES.firstOrNull {
                    it.parentZoneId == zone.parentZoneId && it.order == zone.order - 1
                }
                previous?.id in save.unlockedSubZoneIds
            }

    private fun zoneShortName(zoneId: Int): String = when (zoneId) {
        GameData.ZONE_BEACH -> "砂浜"
        GameData.ZONE_FOREST -> "森"
        GameData.ZONE_REEF -> "岩礁"
        GameData.ZONE_DEPTHS -> "奥地"
        GameData.ZONE_SUMMIT -> "山頂"
        else -> "島"
    }

    private fun SaveData.hasDefeatedEnemy(enemyId: Int): Boolean =
        enemyCells.any {
            it.enemyId == enemyId && (it.defeated || it.defeatedAtSec > 0L)
        }
}
