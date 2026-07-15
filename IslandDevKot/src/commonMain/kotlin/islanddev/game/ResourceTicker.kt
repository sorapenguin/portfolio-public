package islanddev.game

import islanddev.data.GameData
import islanddev.model.SaveData
import kotlin.math.roundToInt
import kotlin.math.roundToLong

object ResourceTicker {
    fun tickRespawn(save: SaveData, nowSec: Long): SaveData {
        val cells = save.resourceCells.map { cell ->
            if (!cell.depleted) return@map cell
            val resource = GameData.resourceById(cell.resourceId) ?: return@map cell
            val effectiveRespawn = (
                resource.respawnSeconds *
                    FacilityEffects.respawnMultiplier(cell.resourceId, save.builtFacilityIds)
                ).roundToLong()

            if (cell.depletedAtSec + effectiveRespawn <= nowSec) {
                cell.copy(depleted = false, depletedAtSec = 0L)
            } else {
                cell
            }
        }
        return if (cells == save.resourceCells) save else save.copy(resourceCells = cells)
    }

    fun collectCurrentCell(
        save: SaveData,
        playerCol: Int,
        playerRow: Int,
        nowSec: Long
    ): SaveData {
        var inventory = save.inventory
        var ideaGained = 0
        var changed = false

        val cells = save.resourceCells.map { cell ->
            val onPlayerCell = cell.col == playerCol && cell.row == playerRow
            val resource = GameData.resourceById(cell.resourceId)
            if (cell.depleted || !onPlayerCell || resource == null) return@map cell

            val amount = FacilityEffects.resourceAmountMultiplier(
                cell.resourceId,
                save.builtFacilityIds
            )
            inventory = inventory + (
                cell.resourceId to (inventory.getOrDefault(cell.resourceId, 0) + amount)
                )
            ideaGained += (
                resource.ideaValue * FacilityEffects.ideaMultiplier(save.builtFacilityIds)
                ).roundToInt()
            changed = true
            cell.copy(depleted = true, depletedAtSec = nowSec)
        }

        return if (changed) {
            save.copy(
                inventory = inventory,
                idea = save.idea + ideaGained,
                resourceCells = cells
            )
        } else {
            save
        }
    }
}
