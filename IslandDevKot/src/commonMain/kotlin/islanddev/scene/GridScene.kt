package islanddev.scene

import islanddev.data.GameData
import islanddev.game.GridPoint
import islanddev.model.SaveData
import islanddev.ui.IslandUiFonts
import islanddev.ui.IslandTheme
import korlibs.image.color.Colors
import korlibs.image.color.RGBA
import korlibs.korge.view.Container
import korlibs.korge.view.Text
import korlibs.korge.view.clipContainer
import korlibs.korge.view.container
import korlibs.korge.view.position
import korlibs.korge.view.solidRect
import korlibs.korge.view.text
import korlibs.math.geom.Size
import kotlin.math.abs
import kotlin.math.floor

enum class CameraMode {
    FOLLOW_PLAYER,
    ZONE_DEADZONE,
    PAGE_CAMERA
}

data class CameraTarget(val left: Double, val top: Double)

object CameraTargetPolicy {
    fun target(
        mode: CameraMode,
        playerCol: Double,
        playerRow: Double,
        viewportWidth: Double,
        viewportHeight: Double,
        currentLeft: Double = Double.NaN,
        currentTop: Double = Double.NaN
    ): CameraTarget {
        val maxX = GridScene.COLUMNS * GridScene.CELL_SIZE - viewportWidth
        val maxY = GridScene.ROWS * GridScene.CELL_SIZE - viewportHeight
        return when (mode) {
            CameraMode.FOLLOW_PLAYER -> {
                val centerX = (playerCol + 0.5) * GridScene.CELL_SIZE
                val centerY = (playerRow + 0.5) * GridScene.CELL_SIZE
                CameraTarget(
                    left = (centerX - viewportWidth / 2)
                        .coerceIn(0.0, maxX.coerceAtLeast(0.0)),
                    top = (centerY - viewportHeight / 2)
                        .coerceIn(0.0, maxY.coerceAtLeast(0.0))
                )
            }

            CameraMode.ZONE_DEADZONE -> {
                val zoneId = GameData.columnToZone(playerCol.toInt())
                val zoneWidth = 20 * GridScene.CELL_SIZE
                val zoneLeft = zoneId * zoneWidth
                val zoneRight = zoneLeft + zoneWidth
                val worldMaxLeft = maxX.coerceAtLeast(0.0)
                val minLeft = zoneLeft.coerceIn(0.0, worldMaxLeft)
                val maxLeft = if (viewportWidth >= zoneWidth) {
                    minLeft
                } else {
                    (zoneRight - viewportWidth)
                        .coerceIn(minLeft, worldMaxLeft)
                }
                val cameraLeft = if (
                    currentLeft.isNaN() ||
                    currentLeft < minLeft ||
                    currentLeft > maxLeft
                ) {
                    minLeft
                } else {
                    currentLeft
                }
                val playerWorldX = (playerCol + 0.5) * GridScene.CELL_SIZE
                val playerScreenX = playerWorldX - cameraLeft
                val leftMargin = viewportWidth * 0.25
                val rightMargin = viewportWidth * 0.75
                val desiredLeft = when {
                    playerScreenX < leftMargin ->
                        playerWorldX - leftMargin

                    playerScreenX > rightMargin ->
                        playerWorldX - rightMargin

                    else -> cameraLeft
                }
                val worldMaxTop = maxY.coerceAtLeast(0.0)
                val cameraTop = if (
                    currentTop.isNaN() ||
                    currentTop < 0.0 ||
                    currentTop > worldMaxTop
                ) {
                    0.0
                } else {
                    currentTop
                }
                val playerWorldY = (playerRow + 0.5) * GridScene.CELL_SIZE
                val playerScreenY = playerWorldY - cameraTop
                val topMargin = viewportHeight * 0.25
                val bottomMargin = viewportHeight * 0.75
                val desiredTop = when {
                    playerScreenY < topMargin ->
                        playerWorldY - topMargin

                    playerScreenY > bottomMargin ->
                        playerWorldY - bottomMargin

                    else -> cameraTop
                }
                CameraTarget(
                    left = desiredLeft.coerceIn(minLeft, maxLeft),
                    top = desiredTop.coerceIn(0.0, worldMaxTop)
                )
            }

            CameraMode.PAGE_CAMERA -> {
                val playerWorldX = (playerCol + 0.5) * GridScene.CELL_SIZE
                val zoneId = GameData.columnToZone(playerCol.toInt())
                val zoneWidth = 20 * GridScene.CELL_SIZE
                val zoneLeft = zoneId * zoneWidth
                val zoneRight = zoneLeft + zoneWidth
                val worldMaxLeft = maxX.coerceAtLeast(0.0)
                val minLeft = zoneLeft.coerceIn(0.0, worldMaxLeft)
                val maxLeft = if (viewportWidth >= zoneWidth) {
                    minLeft
                } else {
                    (zoneRight - viewportWidth)
                        .coerceIn(minLeft, worldMaxLeft)
                }
                val currentHorizontalPageIsValid =
                    !currentLeft.isNaN() &&
                        currentLeft in minLeft..maxLeft &&
                        playerWorldX >= currentLeft &&
                        playerWorldX < currentLeft + viewportWidth
                val pageLeft = if (currentHorizontalPageIsValid) {
                    currentLeft
                } else {
                    val localPlayerX = playerWorldX - zoneLeft
                    val localPageLeft =
                        floor(localPlayerX / viewportWidth) * viewportWidth
                    (zoneLeft + localPageLeft).coerceIn(minLeft, maxLeft)
                }

                val paddedMapHeight =
                    GridScene.ROWS * GridScene.CELL_SIZE
                val worldMaxTop =
                    (paddedMapHeight - viewportHeight).coerceAtLeast(0.0)
                // Use the full cell bounds of the player sprite for page validity.
                // playerRow * CELL_SIZE = top of the cell the sprite occupies.
                // (playerRow + 1) * CELL_SIZE = bottom of that cell.
                // This ensures the camera switches as soon as any part of the cell
                // goes outside the viewport, preventing mid-animation clipping.
                val spriteTop = playerRow * GridScene.CELL_SIZE
                val spriteBottom = (playerRow + 1.0) * GridScene.CELL_SIZE
                val cameraTop = if (currentTop.isNaN()) {
                    Double.NaN
                } else {
                    currentTop.coerceIn(0.0, worldMaxTop)
                }
                val currentVerticalPageIsValid =
                    !cameraTop.isNaN() &&
                        spriteTop >= cameraTop &&
                        spriteBottom <= cameraTop + viewportHeight
                val safeCurrentTop = if (cameraTop.isNaN()) 0.0 else cameraTop
                val pageTop = when {
                    viewportHeight >= paddedMapHeight -> 0.0
                    currentVerticalPageIsValid -> cameraTop
                    spriteBottom > safeCurrentTop + viewportHeight ->
                        (floor(spriteBottom / viewportHeight) * viewportHeight)
                            .coerceIn(0.0, worldMaxTop)
                    else ->
                        (floor(spriteTop / viewportHeight) * viewportHeight)
                            .coerceIn(0.0, worldMaxTop)
                }

                CameraTarget(left = pageLeft, top = pageTop)
            }
        }
    }
}

class GridScene(
    initialSave: SaveData,
    viewportWidth: Double = 360.0,
    viewportHeight: Double = 540.0,
    private val onCellTap: (GridPoint) -> TapMoveResolution
) : Container() {
    companion object {
        const val COLUMNS = 100
        const val ROWS = 16
        const val CELL_SIZE = 32.0
        const val CAMERA_BOTTOM_PADDING_PX = 0.0
        private const val CAMERA_LERP = 0.2
        private const val CAMERA_SNAP_PIXELS = 0.5
        const val DEBUG_MAP_LAYOUT = false
        val CAMERA_MODE = CameraMode.PAGE_CAMERA
    }

    private var save = initialSave
    private val viewport = clipContainer(Size(viewportWidth, viewportHeight))
    private val worldContainer: Container = viewport.container()
    private val terrainLayer = worldContainer.container()
    private val entityLayer = worldContainer.container()
    private val fogLayer = worldContainer.container()
    val actorLayer: Container = worldContainer.container()
    private val debugWorldLayer = worldContainer.container()
    private val tapMarkerLayer = worldContainer.container()
    private val inputLayer = viewport.solidRect(
        viewportWidth,
        (kotlin.math.floor(viewportHeight / CELL_SIZE) * CELL_SIZE),
        RGBA(0, 0, 0, 0)
    )
    private val debugOverlay = viewport.container()
    private var debugText: Text? = null
    private var scrollLeft = 0.0
    private var scrollTop = 0.0
    private var lastTapStageX = 0.0
    private var lastTapStageY = 0.0
    private var lastLocalX = 0.0
    private var lastLocalY = 0.0
    private var lastTappedCell: GridPoint? = null
    private var lastTapDestination: GridPoint? = null
    private var lastTapPath: List<GridPoint> = emptyList()
    private var movementDebug = MovementDebugState(
        player = GridPoint(save.playerCol, save.playerRow),
        target = null,
        path = emptyList()
    )
    private var entitySignature = MapRenderSignature.entities(save)
    private var fogSignature = MapRenderSignature.fog(save)
    private var lastCameraLeft = Double.NaN
    private var lastCameraTop = Double.NaN
    private var lastCameraZoneId: Int? = null

    init {
        drawTerrain()
        redrawEntities()
        redrawFog()
        drawDebugOverlay()
        updateCamera(save.playerCol.toDouble(), save.playerRow.toDouble())
        logMapLayout()
    }

    fun processTapAt(
        stageX: Float,
        stageY: Float,
        rawMotionX: Float? = null,
        rawMotionY: Float? = null
    ) {
        val trace = tapCoordinateTrace(stageX, stageY, rawMotionX, rawMotionY)
        val tappedCell = trace.cell
        if (tappedCell == null) {
            logTapMove(trace, TapMoveRejectReason.OUTSIDE_MAP)
            return
        }
        lastTapStageX = stageX.toDouble()
        lastTapStageY = stageY.toDouble()
        lastLocalX = trace.localX + trace.scrollLeft
        lastLocalY = trace.localY + trace.scrollTop
        lastTappedCell = tappedCell
        val resolution = onCellTap(tappedCell)
        val request = resolution.request
        lastTapDestination = request?.destination
        lastTapPath = request?.path.orEmpty()
        logTapMove(trace, resolution.rejectReason)
        redrawTapMarker()
        redrawInputDebug()
    }

    fun screenToGridCell(stageX: Float, stageY: Float): GridPoint? =
        tapCoordinateTrace(stageX, stageY, rawMotionX = null, rawMotionY = null).cell

    private fun mapViewport(): MapViewport =
        MapViewport(
            viewportLeft = this.x,
            viewportTop = this.y,
            tileOriginLeft = this.x,
            tileOriginTop = this.y,
            viewportWidth = viewport.width,
            viewportHeight = viewport.height,
            scrollLeft = scrollLeft,
            scrollTop = scrollTop,
            cellSize = CELL_SIZE
        )

    private fun logMapLayout() {
        if (!DEBUG_MAP_LAYOUT) return
        val layout = mapViewport()
        val availableMapHeight = layout.viewportHeight
        val controlPanelTop = this.y + layout.tileDrawHeight
        val leftoverHeight = availableMapHeight - layout.tileDrawHeight
        val worldWidth = COLUMNS * CELL_SIZE
        val worldHeight = ROWS * CELL_SIZE
        val maxScrollLeft = (worldWidth - layout.tileDrawWidth).coerceAtLeast(0.0)
        val maxScrollTop = (worldHeight - layout.tileDrawHeight).coerceAtLeast(0.0)
        val firstVisibleRow = kotlin.math.floor(layout.scrollTop / CELL_SIZE).toInt()
        val lastVisibleRow = kotlin.math.floor(
            (layout.scrollTop + layout.tileDrawHeight - 0.001) / CELL_SIZE
        ).toInt()
        val actuallyDrawnRows =
            (firstVisibleRow..lastVisibleRow).count { it in 0 until ROWS }
        val outOfWorldRowsInViewport = layout.visibleRows - actuallyDrawnRows
        println(
            "mapLayout screenHeight=640.0 " +
                "hudBottom=${this.y} " +
                "mapTop=${this.y} " +
                "availableMapHeight=$availableMapHeight " +
                "tileSize=${layout.cellSize} " +
                "worldRows=$ROWS " +
                "worldCols=$COLUMNS " +
                "visibleRows=${layout.visibleRows} " +
                "visibleCols=${layout.visibleCols} " +
                "scrollTop=${layout.scrollTop} " +
                "scrollLeft=${layout.scrollLeft} " +
                "maxScrollTop=$maxScrollTop " +
                "maxScrollLeft=$maxScrollLeft " +
                "firstVisibleRow=$firstVisibleRow " +
                "lastVisibleRow=$lastVisibleRow " +
                "actuallyDrawnRows=$actuallyDrawnRows " +
                "outOfWorldRowsInViewport=$outOfWorldRowsInViewport " +
                "playerCell=${save.playerCol},${save.playerRow} " +
                "clampedCameraTop=$lastCameraTop " +
                "clampedCameraLeft=$lastCameraLeft " +
                "tileDrawHeight=${layout.tileDrawHeight} " +
                "tileDrawBottom=${layout.tileDrawBottom} " +
                "controlPanelTop=$controlPanelTop " +
                "leftoverHeight=$leftoverHeight"
        )
    }

    private fun tapCoordinateTrace(
        stageX: Float,
        stageY: Float,
        rawMotionX: Float?,
        rawMotionY: Float?
    ): TapMoveCoordinateTrace =
        TapMoveResolver.coordinateTrace(
            stageX = stageX,
            stageY = stageY,
            viewport = mapViewport()
        ).copy(
            rawMotionX = rawMotionX,
            rawMotionY = rawMotionY
        )

    private fun logTapMove(trace: TapMoveCoordinateTrace, rejectReason: TapMoveRejectReason?) {
        if (!TapMoveResolver.DEBUG_TAP_MOVE) return
        val cellText = trace.cell?.let { "${it.col},${it.row}" } ?: "null"
        val markerText = lastTappedCell?.let { "${it.col},${it.row}" } ?: "null"
        val rect = trace.selectedCellRect
        val rectText = rect?.let {
            "left=${it.left}, top=${it.top}, right=${it.right}, bottom=${it.bottom}"
        } ?: "null"
        val tapInsideRect = rect?.contains(trace.stageX.toDouble(), trace.stageY.toDouble())
        println(
            "tap rawMotion=(${trace.rawMotionX ?: "-"},${trace.rawMotionY ?: "-"})\n" +
            "tap stage=(${trace.stageX},${trace.stageY})\n" +
                "tileOrigin=(${trace.tileOriginLeft},${trace.tileOriginTop})\n" +
                "local=(${trace.localX},${trace.localY})\n" +
                "world=(${trace.worldX},${trace.worldY})\n" +
                "scroll=(${trace.scrollLeft},${trace.scrollTop})\n" +
                "tileSize=${trace.cellSize}\n" +
                "cell=($cellText)\n" +
                "markerDrawnCell=($markerText)\n" +
                "selectedRect=($rectText)\n" +
                "tapInsideSelectedRect=${tapInsideRect ?: "-"}\n" +
                "rejectReason=${rejectReason ?: "-"}"
        )
    }

    fun update(newSave: SaveData) {
        save = newSave
        val newEntitySignature = MapRenderSignature.entities(newSave)
        if (newEntitySignature != entitySignature) {
            entitySignature = newEntitySignature
            redrawEntities()
        }
        val newFogSignature = MapRenderSignature.fog(newSave)
        if (newFogSignature != fogSignature) {
            fogSignature = newFogSignature
            redrawFog()
        }
    }

    fun updateMovementDebug(state: MovementDebugState) {
        if (!InputDebug.ENABLED || state == movementDebug) return
        movementDebug = state
        redrawInputDebug()
    }

    fun updateCamera(playerCol: Double, playerRow: Double) {
        val zoneId = GameData.columnToZone(playerCol.toInt())
        val zoneChanged = lastCameraZoneId != zoneId

        val target = CameraTargetPolicy.target(
            mode = CAMERA_MODE,
            playerCol = playerCol,
            playerRow = playerRow,
            viewportWidth = viewport.width,
            viewportHeight = viewport.height,
            currentLeft = if (zoneChanged) Double.NaN else lastCameraLeft,
            currentTop = lastCameraTop
        )
        val targetLeft = target.left
        val targetTop = target.top

        val currentLeft = if (
            CAMERA_MODE == CameraMode.ZONE_DEADZONE ||
            CAMERA_MODE == CameraMode.PAGE_CAMERA
        ) {
            targetLeft
        } else {
            lerpCamera(lastCameraLeft, targetLeft)
        }
        val currentTop = if (
            CAMERA_MODE == CameraMode.ZONE_DEADZONE ||
            CAMERA_MODE == CameraMode.PAGE_CAMERA
        ) {
            targetTop
        } else {
            lerpCamera(lastCameraTop, targetTop)
        }
        if (currentLeft == lastCameraLeft && currentTop == lastCameraTop) return

        lastCameraZoneId = zoneId
        lastCameraLeft = currentLeft
        lastCameraTop = currentTop
        scrollLeft = currentLeft
        scrollTop = currentTop
        worldContainer.position(-currentLeft, -currentTop)
        logMapLayout()
    }

    private fun lerpCamera(current: Double, target: Double): Double {
        if (current.isNaN()) return target
        if (abs(target - current) < CAMERA_SNAP_PIXELS) return target
        return current + (target - current) * CAMERA_LERP
    }

    private fun drawTerrain() {
        for (col in 0 until COLUMNS) {
            val zoneId = GameData.columnToZone(col)
            for (row in 0 until ROWS) {
                val color = IslandTheme.zoneColor(zoneId, alt = (col + row) % 2 == 0)
                terrainLayer.solidRect(CELL_SIZE, CELL_SIZE, color) {
                    position(col * CELL_SIZE, row * CELL_SIZE)
                }
                if (row % 4 == 0) {
                    terrainLayer.solidRect(CELL_SIZE, 1.0, RGBA(255, 255, 255, 24)) {
                        position(col * CELL_SIZE, row * CELL_SIZE)
                    }
                }
            }
        }
    }

    private fun redrawEntities() {
        entityLayer.removeChildren()
        save.resourceCells.forEach { cell ->
            val color = if (cell.depleted) RGBA(64, 70, 74, 255) else resourceColor(cell.resourceId)
            drawLabeledMarker(
                label = MapLabels.resource(cell.resourceId),
                col = cell.col,
                row = cell.row,
                size = 22.0,
                color = color,
                borderColor = if (cell.depleted) RGBA(35, 40, 45, 255) else RGBA(28, 52, 32, 255),
                textColor = if (cell.depleted) IslandTheme.Color.MutedText else RGBA(18, 24, 20, 255)
            )
        }
        save.enemyCells.filterNot { it.defeated }.forEach { cell ->
            drawLabeledMarker(
                label = MapLabels.enemy(cell.enemyId),
                col = cell.col,
                row = cell.row,
                size = 24.0,
                color = IslandTheme.Color.Enemy,
                borderColor = RGBA(88, 22, 22, 255),
                textColor = IslandTheme.Color.Text
            )
        }
        GameData.BOSSES.filterNot { it.id in save.defeatedBossIds }.forEach { boss ->
            drawLabeledMarker(
                label = MapLabels.boss(boss.id),
                col = boss.fromZoneId * 20 + 19,
                row = 8,
                size = 26.0,
                color = IslandTheme.Color.Boss,
                borderColor = RGBA(105, 55, 10, 255),
                textColor = RGBA(36, 24, 10, 255)
            )
        }
        save.builtFacilityIds.forEach { facilityId ->
            val zone = facilityId.coerceIn(0, 4)
            val col = zone * 20 + 2 + (facilityId * 3) % 15
            val row = 1 + (facilityId * 2) % 14
            drawLabeledMarker(
                label = "施",
                col = col,
                row = row,
                size = 22.0,
                color = IslandTheme.Color.FacilityCore,
                borderColor = IslandTheme.Color.Facility,
                textColor = IslandTheme.Color.Text
            )
        }
    }

    private fun drawLabeledMarker(
        label: String,
        col: Int,
        row: Int,
        size: Double,
        color: RGBA,
        borderColor: RGBA,
        textColor: RGBA
    ) {
        val offset = (CELL_SIZE - size) / 2
        entityLayer.container {
            position(col * CELL_SIZE + offset, row * CELL_SIZE + offset)
            solidRect(size, size, borderColor)
            solidRect(size - 4.0, size - 4.0, color) {
                position(2, 2)
            }
            text(
                label,
                textSize = 14.0,
                color = textColor,
                font = IslandUiFonts.font
            ) {
                position((size - 14.0) / 2, (size - 18.0) / 2)
            }
        }
    }

    private fun drawDebugOverlay() {
        if (!InputDebug.ENABLED) {
            debugOverlay.visible = false
            return
        }
        debugOverlay.position(4, 116)
        debugOverlay.solidRect(352, 86, RGBA(10, 15, 25, 220))
        debugText = debugOverlay.text(
            "",
            textSize = 11.0,
            color = Colors.WHITE,
            font = IslandUiFonts.font
        ) {
            position(5, 3)
        }
        redrawInputDebug()
    }

    private fun redrawInputDebug() {
        if (!InputDebug.ENABLED) return
        debugWorldLayer.removeChildren()

        lastTappedCell?.let {
            drawCellFrame(debugWorldLayer, it.col, it.row, RGBA(30, 140, 255, 255), inset = 0.0)
        }
        (lastTapDestination ?: movementDebug.target)?.let {
            drawCellFrame(debugWorldLayer, it.col, it.row, RGBA(255, 230, 30, 255), inset = 4.0)
        }
        val path = if (lastTapPath.isNotEmpty()) lastTapPath else movementDebug.path
        path.forEach {
            debugWorldLayer.solidRect(6, 6, RGBA(80, 180, 255, 170)) {
                position(
                    it.col * CELL_SIZE + (CELL_SIZE - 6) / 2,
                    it.row * CELL_SIZE + (CELL_SIZE - 6) / 2
                )
            }
        }

        debugText?.text = buildString {
            append("tap: ${lastTapStageX.toInt()},${lastTapStageY.toInt()}  ")
            append("local: ${lastLocalX.toInt()},${lastLocalY.toInt()}\n")
            append("cell: ${lastTappedCell.asText()}  ")
            append("player: ${movementDebug.player.asText()}\n")
            append("target: ${(lastTapDestination ?: movementDebug.target).asText()}  ")
            append("path length: ${path.size}")
        }
    }

    private fun redrawTapMarker() {
        tapMarkerLayer.removeChildren()
        if (!TapMoveResolver.DEBUG_TAP_MOVE) return
        lastTappedCell?.let {
            drawCellFrame(tapMarkerLayer, it.col, it.row, RGBA(245, 220, 90, 170), inset = 1.0)
        }
        lastTapDestination?.let {
            drawCellFrame(tapMarkerLayer, it.col, it.row, RGBA(80, 180, 255, 190), inset = 5.0)
        }
    }

    private fun drawCellFrame(layer: Container, col: Int, row: Int, color: RGBA, inset: Double) {
        val x = col * CELL_SIZE + inset
        val y = row * CELL_SIZE + inset
        val size = CELL_SIZE - inset * 2
        val thickness = 3.0
        layer.solidRect(size, thickness, color).position(x, y)
        layer.solidRect(size, thickness, color)
            .position(x, y + size - thickness)
        layer.solidRect(thickness, size, color).position(x, y)
        layer.solidRect(thickness, size, color)
            .position(x + size - thickness, y)
    }

    private fun GridPoint?.asText(): String =
        this?.let { "${it.col},${it.row}" } ?: "-"

    private fun redrawFog() {
        fogLayer.removeChildren()
        for (zoneId in GameData.ZONE_BEACH..GameData.ZONE_SUMMIT) {
            if (zoneId in save.unlockedZoneIds) continue
            fogLayer.solidRect(
                20 * CELL_SIZE,
                ROWS * CELL_SIZE,
                IslandTheme.Color.Fog
            ) {
                position(zoneId * 20 * CELL_SIZE, 0)
            }
        }
    }

    private fun resourceColor(resourceId: Int): RGBA = when (resourceId) {
        GameData.RES_WOOD -> RGBA(105, 154, 72, 255)
        GameData.RES_STONE -> RGBA(158, 162, 170, 255)
        GameData.RES_FRUIT -> RGBA(236, 170, 73, 255)
        GameData.RES_FIBER -> RGBA(114, 188, 130, 255)
        GameData.RES_SHELL -> RGBA(230, 213, 178, 255)
        GameData.RES_CLAY -> RGBA(176, 105, 72, 255)
        GameData.RES_BAMBOO -> RGBA(93, 170, 82, 255)
        GameData.RES_ORE -> RGBA(126, 154, 178, 255)
        else -> RGBA(125, 180, 120, 255)
    }
}
