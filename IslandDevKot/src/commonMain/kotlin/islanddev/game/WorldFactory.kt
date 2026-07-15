package islanddev.game

import islanddev.data.GameData
import islanddev.model.EnemyCellState
import islanddev.model.ResourceCellState
import islanddev.model.SaveData

object WorldFactory {
    fun ensureInitialized(save: SaveData): SaveData {
        val resources = if (save.resourceCells.isEmpty()) createResourceCells() else save.resourceCells
        val enemies = if (save.enemyCells.isEmpty()) createEnemyCells() else save.enemyCells
        if (resources === save.resourceCells && enemies === save.enemyCells) return save
        return save.copy(resourceCells = resources, enemyCells = enemies)
    }

    private fun createResourceCells(): List<ResourceCellState> {
        var resourceCellId = 0
        return buildList {
            for (zoneId in GameData.ZONE_BEACH..GameData.ZONE_SUMMIT) {
                val zoneStart = zoneId * 20
                val available = GameData.RESOURCES.filter { zoneId in it.zoneIds }
                available.forEachIndexed { index, resource ->
                    repeat(4) { offset ->
                        add(
                            ResourceCellState(
                                id = resourceCellId++,
                                resourceId = resource.id,
                                col = zoneStart + 3 + index * 4 + offset,
                                row = 3 + (index * 3 + offset * 2) % 10
                            )
                        )
                    }
                }
            }
        }
    }

    private fun createEnemyCells(): List<EnemyCellState> {
        var enemyCellId = 0
        return buildList {
            GameData.ENEMIES.forEach { enemy ->
                val zoneStart = enemy.zoneId * 20
                repeat(enemy.cellCount) { offset ->
                    add(
                        EnemyCellState(
                            id = enemyCellId++,
                            enemyId = enemy.id,
                            col = zoneStart + 8 + (enemy.id % 2) * 4 + offset * 2,
                            row = 4 + (enemy.id * 2 + offset * 3) % 9
                        )
                    )
                }
            }
        }
    }
}
