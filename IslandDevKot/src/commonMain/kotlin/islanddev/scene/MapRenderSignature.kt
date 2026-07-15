package islanddev.scene

import islanddev.model.SaveData

data class ResourceRenderState(
    val id: Int,
    val resourceId: Int,
    val col: Int,
    val row: Int,
    val depleted: Boolean
)

data class EnemyRenderState(
    val id: Int,
    val enemyId: Int,
    val col: Int,
    val row: Int,
    val defeated: Boolean
)

data class EntityRenderSignature(
    val resources: List<ResourceRenderState>,
    val enemies: List<EnemyRenderState>,
    val defeatedBossIds: Set<Int>,
    val builtFacilityIds: Set<Int>
)

object MapRenderSignature {
    fun entities(save: SaveData): EntityRenderSignature = EntityRenderSignature(
        resources = save.resourceCells.map {
            ResourceRenderState(it.id, it.resourceId, it.col, it.row, it.depleted)
        },
        enemies = save.enemyCells.map {
            EnemyRenderState(it.id, it.enemyId, it.col, it.row, it.defeated)
        },
        defeatedBossIds = save.defeatedBossIds,
        builtFacilityIds = save.builtFacilityIds
    )

    fun fog(save: SaveData): Set<Int> = save.unlockedZoneIds
}
