package islanddev.scene

import islanddev.model.EnemyCellState
import islanddev.model.ResourceCellState
import islanddev.model.SaveData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class MapRenderSignatureTest {
    @Test
    fun playerPositionAndInventoryDoNotRequireEntityRedraw() {
        val save = visualSave()
        val changed = save.copy(
            playerCol = 8,
            playerRow = 9,
            inventory = mapOf(0 to 10),
            idea = 20
        )

        assertEquals(
            MapRenderSignature.entities(save),
            MapRenderSignature.entities(changed)
        )
    }

    @Test
    fun resourceOrEnemyVisualChangeRequiresEntityRedraw() {
        val save = visualSave()
        val depleted = save.copy(
            resourceCells = save.resourceCells.map { it.copy(depleted = true) }
        )
        val defeated = save.copy(
            enemyCells = save.enemyCells.map { it.copy(defeated = true) }
        )

        assertNotEquals(
            MapRenderSignature.entities(save),
            MapRenderSignature.entities(depleted)
        )
        assertNotEquals(
            MapRenderSignature.entities(save),
            MapRenderSignature.entities(defeated)
        )
    }

    @Test
    fun unlockedZonesOnlyChangeFogSignature() {
        val save = visualSave()
        val unlocked = save.copy(unlockedZoneIds = setOf(0, 1))

        assertEquals(
            MapRenderSignature.entities(save),
            MapRenderSignature.entities(unlocked)
        )
        assertNotEquals(
            MapRenderSignature.fog(save),
            MapRenderSignature.fog(unlocked)
        )
    }

    @Test
    fun manualMoveSpeedIsConfiguredForResponsiveMovement() {
        assertEquals(0.18, PlayerSprite.MOVE_SECONDS_PER_CELL)
    }

    private fun visualSave() = SaveData(
        resourceCells = listOf(
            ResourceCellState(id = 1, resourceId = 0, col = 3, row = 4)
        ),
        enemyCells = listOf(
            EnemyCellState(id = 2, enemyId = 0, col = 8, row = 4)
        )
    )
}
