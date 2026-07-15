package islanddev.scene

import islanddev.game.GridPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MapInputTest {
    @Test
    fun convertsMapLocalPositionToCell() {
        assertEquals(
            GridPoint(7, 3),
            MapInput.cellAt(
                mapLocalX = 224.0,
                mapLocalY = 96.0
            )
        )
    }

    @Test
    fun mapOriginConvertsToFirstCell() {
        assertEquals(
            GridPoint(0, 0),
            MapInput.cellAt(
                mapLocalX = 0.0,
                mapLocalY = 0.0
            )
        )
    }

    @Test
    fun outsideMapReturnsNull() {
        assertNull(
            MapInput.cellAt(
                mapLocalX = 10.0,
                mapLocalY = 520.0
            )
        )
        assertNull(
            MapInput.cellAt(
                mapLocalX = -1.0,
                mapLocalY = 10.0
            )
        )
    }
}
