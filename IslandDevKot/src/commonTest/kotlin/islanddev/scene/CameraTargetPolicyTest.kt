package islanddev.scene

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CameraTargetPolicyTest {
    private val viewportWidth = 360.0
    private val viewportHeight = 384.0

    @Test
    fun deadZoneKeepsCameraStillWhilePlayerIsInsideMargins() {
        val currentLeft = 100.0
        val currentTop = 64.0
        val target = deadZoneTarget(
            playerCol = 8.0,
            playerRow = 8.0,
            currentLeft = currentLeft,
            currentTop = currentTop
        )

        assertEquals(currentLeft, target.left)
        assertEquals(currentTop, target.top)
    }

    @Test
    fun deadZoneMovesRightWhenPlayerPassesRightMargin() {
        val target = deadZoneTarget(playerCol = 12.0, currentLeft = 100.0)

        assertTrue(target.left > 100.0)
        assertEquals((12.5 * GridScene.CELL_SIZE) - viewportWidth * 0.75, target.left)
    }

    @Test
    fun deadZoneMovesLeftWhenPlayerPassesLeftMargin() {
        val target = deadZoneTarget(playerCol = 4.0, currentLeft = 100.0)

        assertTrue(target.left < 100.0)
        assertEquals((4.5 * GridScene.CELL_SIZE) - viewportWidth * 0.25, target.left)
    }

    @Test
    fun deadZoneCameraStaysInsideCurrentZone() {
        val beachRight = deadZoneTarget(playerCol = 19.0, currentLeft = 270.0)
        val forestLeft = deadZoneTarget(playerCol = 20.0, currentLeft = Double.NaN)

        assertEquals(280.0, beachRight.left)
        assertEquals(640.0, forestLeft.left)
    }

    @Test
    fun zoneNarrowerThanViewportUsesZoneLeft() {
        val target = CameraTargetPolicy.target(
            mode = CameraMode.ZONE_DEADZONE,
            playerCol = 25.0,
            playerRow = 8.0,
            viewportWidth = 700.0,
            viewportHeight = viewportHeight,
            currentLeft = 800.0
        )

        assertEquals(640.0, target.left)
    }

    @Test
    fun changingZoneChangesCameraRange() {
        val beach = deadZoneTarget(playerCol = 19.0, currentLeft = 280.0)
        val forest = deadZoneTarget(playerCol = 20.0, currentLeft = Double.NaN)

        assertNotEquals(beach, forest)
        assertTrue(forest.left >= 640.0)
    }

    @Test
    fun followPlayerModeStillTracksPlayerPosition() {
        val first = CameraTargetPolicy.target(
            CameraMode.FOLLOW_PLAYER,
            playerCol = 5.0,
            playerRow = 2.0,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight
        )
        val second = CameraTargetPolicy.target(
            CameraMode.FOLLOW_PLAYER,
            playerCol = 15.0,
            playerRow = 12.0,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight
        )

        assertNotEquals(first, second)
    }

    @Test
    fun deadZoneMovesDownWhenPlayerPassesBottomMargin() {
        val target = deadZoneTarget(
            playerCol = 8.0,
            playerRow = 12.0,
            currentLeft = 100.0,
            currentTop = 0.0
        )

        assertTrue(target.top > 0.0)
        assertEquals(
            (12.5 * GridScene.CELL_SIZE) - viewportHeight * 0.75,
            target.top
        )
    }

    @Test
    fun deadZoneMovesUpWhenPlayerPassesTopMargin() {
        val target = deadZoneTarget(
            playerCol = 8.0,
            playerRow = 2.0,
            currentLeft = 100.0,
            currentTop = 100.0
        )

        assertTrue(target.top < 100.0)
        assertEquals(0.0, target.top)
    }

    @Test
    fun deadZoneTopStaysInsideMapBounds() {
        val topEdge = deadZoneTarget(
            playerCol = 8.0,
            playerRow = 0.0,
            currentLeft = 100.0,
            currentTop = 0.0
        )
        val bottomEdge = deadZoneTarget(
            playerCol = 8.0,
            playerRow = 15.0,
            currentLeft = 100.0,
            currentTop = 128.0
        )

        assertEquals(0.0, topEdge.top)
        assertEquals(
            GridScene.ROWS * GridScene.CELL_SIZE - viewportHeight,
            bottomEdge.top
        )
    }

    @Test
    fun mapShorterThanViewportUsesTopZero() {
        val target = CameraTargetPolicy.target(
            mode = CameraMode.ZONE_DEADZONE,
            playerCol = 8.0,
            playerRow = 15.0,
            viewportWidth = viewportWidth,
            viewportHeight = 600.0,
            currentLeft = 100.0,
            currentTop = 100.0
        )

        assertEquals(0.0, target.top)
    }

    @Test
    fun pageCameraStaysStillWhilePlayerRemainsInsideCurrentPage() {
        val target = pageTarget(
            playerCol = 8.0,
            playerRow = 8.0,
            currentLeft = 0.0,
            currentTop = 0.0
        )

        assertEquals(CameraTarget(0.0, 0.0), target)
    }

    @Test
    fun pageCameraSwitchesRightAndBackLeftInsideZone() {
        val rightPage = pageTarget(
            playerCol = 12.0,
            playerRow = 8.0,
            currentLeft = 0.0,
            currentTop = 0.0
        )
        val leftPage = pageTarget(
            playerCol = 8.0,
            playerRow = 8.0,
            currentLeft = rightPage.left,
            currentTop = 0.0
        )

        assertEquals(280.0, rightPage.left)
        assertEquals(0.0, leftPage.left)
    }

    @Test
    fun pageCameraSwitchesDownAndBackUp() {
        val bottomPage = pageTarget(
            playerCol = 8.0,
            playerRow = 15.0,
            currentLeft = 0.0,
            currentTop = 0.0
        )
        val topPage = pageTarget(
            playerCol = 8.0,
            playerRow = 3.0,
            currentLeft = 0.0,
            currentTop = bottomPage.top
        )

        assertEquals(128.0, bottomPage.top)
        assertEquals(0.0, topPage.top)
    }

    @Test
    fun pageCameraStaysInsideZoneAndMapBounds() {
        val beachEnd = pageTarget(
            playerCol = 19.0,
            playerRow = 15.0,
            currentLeft = Double.NaN,
            currentTop = Double.NaN
        )
        val forestStart = pageTarget(
            playerCol = 20.0,
            playerRow = 0.0,
            currentLeft = beachEnd.left,
            currentTop = beachEnd.top
        )

        assertEquals(280.0, beachEnd.left)
        assertEquals(128.0, beachEnd.top)
        assertEquals(640.0, forestStart.left)
        assertEquals(0.0, forestStart.top)
    }

    @Test
    fun pageCameraUsesTopZeroWhenMapFitsViewport() {
        val target = CameraTargetPolicy.target(
            mode = CameraMode.PAGE_CAMERA,
            playerCol = 8.0,
            playerRow = 15.0,
            viewportWidth = viewportWidth,
            viewportHeight = 600.0,
            currentLeft = Double.NaN,
            currentTop = Double.NaN
        )

        assertEquals(0.0, target.top)
    }

    @Test
    fun pageCameraInitializesFromPlayerWhenCurrentPositionIsUnknown() {
        val target = pageTarget(
            playerCol = 19.0,
            playerRow = 15.0,
            currentLeft = Double.NaN,
            currentTop = Double.NaN
        )
        val playerScreenX = (19.5 * GridScene.CELL_SIZE) - target.left
        val playerScreenY = (15.5 * GridScene.CELL_SIZE) - target.top

        assertEquals(280.0, target.left)
        assertEquals(128.0, target.top)
        assertTrue(playerScreenX in 0.0..<viewportWidth)
        assertTrue(
            playerScreenY + GridScene.CAMERA_BOTTOM_PADDING_PX <=
                viewportHeight
        )
    }

    @Test
    fun pageCameraRow15RemainsOnValidPage() {
        // camera=128 already shows the row-15 cell fully (spriteBottom 512 == 128+384).
        // The page is valid so the camera must NOT jump to worldMaxTop.
        val target = pageTarget(
            playerCol = 8.0,
            playerRow = 15.0,
            currentLeft = 0.0,
            currentTop = 128.0
        )
        val spriteScreenBottom = (16.0 * GridScene.CELL_SIZE) - target.top // 512 - 128 = 384

        assertEquals(128.0, target.top)
        assertTrue(spriteScreenBottom <= viewportHeight)
    }

    @Test
    fun pageCameraRow15SwitchesFromTopPageToBottomPage() {
        // camera=0 cannot show row 15's cell (spriteBottom 512 > 384).
        // Camera must switch to worldMaxTop so the sprite stays visible.
        val target = pageTarget(
            playerCol = 8.0,
            playerRow = 15.0,
            currentLeft = 0.0,
            currentTop = 0.0
        )
        val worldMaxTop =
            GridScene.ROWS * GridScene.CELL_SIZE +
                GridScene.CAMERA_BOTTOM_PADDING_PX - viewportHeight
        val spriteScreenBottom = (16.0 * GridScene.CELL_SIZE) - target.top

        assertEquals(worldMaxTop, target.top)
        assertTrue(spriteScreenBottom <= viewportHeight)
    }

    @Test
    fun pageCameraPageSwitchTriggersOnCellBoundary() {
        // Row 11: spriteBottom = 12 * 32 = 384 = viewportHeight → exactly valid, stay on page 0.
        val atBoundary = pageTarget(
            playerCol = 8.0,
            playerRow = 11.0,
            currentLeft = 0.0,
            currentTop = 0.0
        )
        // Row 11.001: spriteBottom = 12.001 * 32 = 384.032 > 384 → switch to bottom page.
        val pastBoundary = pageTarget(
            playerCol = 8.0,
            playerRow = 11.001,
            currentLeft = 0.0,
            currentTop = 0.0
        )

        assertEquals(0.0, atBoundary.top)
        assertTrue(pastBoundary.top > 0.0)
    }

    @Test
    fun pageCameraUpSwitchTriggersOnCellBoundary() {
        // Row 4: spriteTop = 4 * 32 = 128 = currentTop=128 → exactly valid, stay at 128.
        val atBoundary = pageTarget(
            playerCol = 8.0,
            playerRow = 4.0,
            currentLeft = 0.0,
            currentTop = 128.0
        )
        // Row 3.999: spriteTop = 3.999 * 32 = 127.968 < 128 → switch to page 0.
        val pastBoundary = pageTarget(
            playerCol = 8.0,
            playerRow = 3.999,
            currentLeft = 0.0,
            currentTop = 128.0
        )

        assertEquals(128.0, atBoundary.top)
        assertEquals(0.0, pastBoundary.top)
    }

    @Test
    fun pageCameraUsesTopZeroWhenPaddedMapFitsViewport() {
        val paddedMapHeight =
            GridScene.ROWS * GridScene.CELL_SIZE +
                GridScene.CAMERA_BOTTOM_PADDING_PX
        val target = CameraTargetPolicy.target(
            mode = CameraMode.PAGE_CAMERA,
            playerCol = 8.0,
            playerRow = 15.0,
            viewportWidth = viewportWidth,
            viewportHeight = paddedMapHeight,
            currentLeft = 0.0,
            currentTop = 160.0
        )

        assertEquals(0.0, target.top)
    }

    private fun deadZoneTarget(
        playerCol: Double,
        playerRow: Double = 8.0,
        currentLeft: Double,
        currentTop: Double = 0.0
    ): CameraTarget = CameraTargetPolicy.target(
        mode = CameraMode.ZONE_DEADZONE,
        playerCol = playerCol,
        playerRow = playerRow,
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
        currentLeft = currentLeft,
        currentTop = currentTop
    )

    private fun pageTarget(
        playerCol: Double,
        playerRow: Double,
        currentLeft: Double,
        currentTop: Double
    ): CameraTarget = CameraTargetPolicy.target(
        mode = CameraMode.PAGE_CAMERA,
        playerCol = playerCol,
        playerRow = playerRow,
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
        currentLeft = currentLeft,
        currentTop = currentTop
    )
}
