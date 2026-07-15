package islanddev.scene

import islanddev.data.GameData
import islanddev.game.GridPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StepMoveTest {
    @Test
    fun directionsMoveExactlyOneCell() {
        val origin = GridPoint(5, 5)

        assertEquals(GridPoint(5, 4), StepMove.destination(origin, 0, -1))
        assertEquals(GridPoint(5, 6), StepMove.destination(origin, 0, 1))
        assertEquals(GridPoint(4, 5), StepMove.destination(origin, -1, 0))
        assertEquals(GridPoint(6, 5), StepMove.destination(origin, 1, 0))
    }

    @Test
    fun rejectsOutsideMap() {
        assertFalse(
            StepMove.canMoveTo(
                destination = GridPoint(-1, 0),
                unlockedZoneIds = setOf(GameData.ZONE_BEACH)
            )
        )
        assertFalse(
            StepMove.canMoveTo(
                destination = GridPoint(0, GridScene.ROWS),
                unlockedZoneIds = setOf(GameData.ZONE_BEACH)
            )
        )
    }

    @Test
    fun rejectsLockedZoneAndAcceptsUnlockedZone() {
        val forestCell = GridPoint(20, 8)

        assertFalse(
            StepMove.canMoveTo(
                destination = forestCell,
                unlockedZoneIds = setOf(GameData.ZONE_BEACH)
            )
        )
        assertTrue(
            StepMove.canMoveTo(
                destination = forestCell,
                unlockedZoneIds = setOf(GameData.ZONE_BEACH, GameData.ZONE_FOREST)
            )
        )
    }

    @Test
    fun tapMoveIsEnabled() {
        assertTrue(MovementInputConfig.ENABLE_TAP_MOVE)
    }

    @Test
    fun modalBlocksStepInput() {
        assertTrue(StepMoveInputPolicy.acceptsInput(isModalOpen = false))
        assertFalse(StepMoveInputPolicy.acceptsInput(isModalOpen = true))
    }
}
