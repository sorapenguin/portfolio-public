package islanddev.game

import islanddev.data.GameData
import islanddev.model.SaveData

object SubZoneManager {
    fun startDevelopment(save: SaveData, subZoneId: Int, nowSec: Long): SaveData? {
        val subZone = GameData.subZoneById(subZoneId) ?: return null
        if (subZone.order == 0) return null
        if (subZoneId in save.unlockedSubZoneIds || subZoneId in save.developingSubZones) {
            return null
        }

        val previous = GameData.SUB_ZONES.find {
            it.parentZoneId == subZone.parentZoneId && it.order == subZone.order - 1
        } ?: return null
        if (previous.id !in save.unlockedSubZoneIds) return null
        if (save.idea < subZone.ideaCost) return null

        return save.copy(
            idea = save.idea - subZone.ideaCost,
            developingSubZones = save.developingSubZones + (
                subZoneId to (nowSec + subZone.waitSeconds)
                )
        )
    }

    fun tickUnlock(save: SaveData, nowSec: Long): SaveData {
        val completed = save.developingSubZones
            .filterValues { completionSec -> completionSec <= nowSec }
            .keys
        if (completed.isEmpty()) return save

        return save.copy(
            unlockedSubZoneIds = save.unlockedSubZoneIds + completed,
            developingSubZones = save.developingSubZones - completed
        )
    }
}
