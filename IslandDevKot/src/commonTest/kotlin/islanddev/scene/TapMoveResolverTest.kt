package islanddev.scene

import islanddev.data.GameData
import islanddev.game.GridPoint
import islanddev.model.SaveData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TapMoveResolverTest {
    @Test
    fun drawnCellCentersRoundTripWithoutScroll() {
        val viewport = MapViewport(
            viewportLeft = 0.0,
            viewportTop = 128.0,
            tileOriginLeft = 0.0,
            tileOriginTop = 128.0,
            viewportWidth = 360.0,
            viewportHeight = 376.0,
            scrollLeft = 0.0,
            scrollTop = 0.0
        )

        listOf(
            GridPoint(2, 0),
            GridPoint(5, 5),
            GridPoint(8, 10)
        ).forEach { cell ->
            val (stageX, stageY) = viewport.drawnCenter(cell)
            assertEquals(
                cell,
                TapMoveResolver.screenToGridCell(
                    stageX = stageX.toFloat(),
                    stageY = stageY.toFloat(),
                    viewport = viewport
                )
            )
        }
    }

    @Test
    fun drawnCellCentersRoundTripWithVerticalScroll() {
        val viewport = MapViewport(
            viewportLeft = 0.0,
            viewportTop = 128.0,
            tileOriginLeft = 0.0,
            tileOriginTop = 128.0,
            viewportWidth = 360.0,
            viewportHeight = 376.0,
            scrollLeft = 0.0,
            scrollTop = 96.0
        )

        listOf(
            GridPoint(3, 3),
            GridPoint(4, 8),
            GridPoint(6, 13)
        ).forEach { cell ->
            val (stageX, stageY) = viewport.drawnCenter(cell)
            assertEquals(
                cell,
                TapMoveResolver.screenToGridCell(
                    stageX = stageX.toFloat(),
                    stageY = stageY.toFloat(),
                    viewport = viewport
                )
            )
        }
    }

    @Test
    fun screenToGridCellUsesMapOffsetAndCameraScroll() {
        assertEquals(
            GridPoint(7, 4),
            TapMoveResolver.screenToGridCell(
                stageX = 74f,
                stageY = 148f,
                mapViewLeft = 10.0,
                mapViewTop = 20.0,
                viewportWidth = 320.0,
                viewportHeight = 360.0,
                scrollLeft = 160.0,
                scrollTop = 0.0
            )
        )
    }

    @Test
    fun screenToGridCellRejectsOutsideViewport() {
        assertNull(
            TapMoveResolver.screenToGridCell(
                stageX = 4f,
                stageY = 148f,
                mapViewLeft = 10.0,
                mapViewTop = 20.0,
                viewportWidth = 320.0,
                viewportHeight = 360.0,
                scrollLeft = 0.0,
                scrollTop = 0.0
            )
        )
    }

    @Test
    fun reachableOpenCellReturnsPath() {
        val resolution = TapMoveResolver.resolve(
            save = SaveData(),
            start = GridPoint(2, 8),
            tappedCell = GridPoint(5, 8)
        )

        val request = assertNotNull(resolution.request)
        assertEquals(GridPoint(5, 8), request.destination)
        assertEquals(3, request.path.size)
        assertNull(resolution.rejectReason)
    }

    @Test
    fun lockedOrOutsideCellIsRejected() {
        assertEquals(
            TapMoveRejectReason.LOCKED_OR_BLOCKED,
            TapMoveResolver.resolve(
                save = SaveData(),
                start = GridPoint(2, 8),
                tappedCell = GridPoint(20, 8)
            ).rejectReason
        )

        assertEquals(
            TapMoveRejectReason.OUTSIDE_MAP,
            TapMoveResolver.resolve(
                save = SaveData(),
                start = GridPoint(2, 8),
                tappedCell = null
            ).rejectReason
        )
    }

    @Test
    fun bossCellTapChoosesAdjacentReachableDestination() {
        val resolution = TapMoveResolver.resolve(
            save = SaveData(),
            start = GridPoint(17, 8),
            tappedCell = GridPoint(19, 8)
        )

        val request = assertNotNull(resolution.request)
        assertEquals(GridPoint(19, 8), request.tappedCell)
        assertEquals(GridPoint(18, 8), request.destination)
        assertEquals(1, request.path.size)
    }

    @Test
    fun bossCellInLockedZoneIsRejected() {
        val resolution = TapMoveResolver.resolve(
            save = SaveData(),
            start = GridPoint(2, 8),
            tappedCell = GridPoint(GameData.ZONE_FOREST * 20 + 19, 8)
        )

        assertNull(resolution.request)
        assertEquals(TapMoveRejectReason.LOCKED_OR_BLOCKED, resolution.rejectReason)
    }
}
