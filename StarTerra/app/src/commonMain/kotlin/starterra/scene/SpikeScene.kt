package starterra.scene

import korlibs.image.color.Colors
import korlibs.image.color.RGBA
import korlibs.korge.input.keys
import korlibs.korge.input.onClick
import korlibs.korge.scene.Scene
import korlibs.korge.view.Container
import korlibs.korge.view.SContainer
import korlibs.korge.view.Text
import korlibs.korge.view.View
import korlibs.korge.view.container
import korlibs.korge.view.position
import korlibs.korge.view.solidRect
import korlibs.korge.view.text
import korlibs.korge.view.addUpdater
import korlibs.event.Key
import korlibs.time.seconds
import starterra.game.CoreActivationResult
import starterra.game.CoreState
import starterra.game.OutpostProgress
import starterra.camera.FollowCamera
import starterra.debug.DebugGameLog
import starterra.debug.DebugInputLog
import starterra.entity.ActorState
import starterra.entity.PlayerController
import starterra.world.GridCell
import starterra.world.SpikeMap
import starterra.world.TileType
import starterra.world.FirstChapterContent
import starterra.save.PlatformOutpostSave
import starterra.visual.OutpostVisuals
import starterra.visual.SignalFieldVisuals
import starterra.area.AreaId
import starterra.area.Areas
import starterra.area.AreaDefinition
import starterra.debug.DebugAreaLog
import starterra.debug.DebugSignalLog
import starterra.game.SignalPuzzleProgress
import starterra.game.SignalLinkState
import starterra.game.BeaconId
import starterra.game.BeaconResult

/** Batch C star-outpost visual spike. Interaction remains the Batch A grid movement only. */
class SpikeScene : Scene() {
    private var map = Areas.starOutpost.map
    private var currentArea = AreaId.STAR_OUTPOST
    private val camera = FollowCamera(VIEWPORT_WIDTH, VIEWPORT_HEIGHT)
    private val player = PlayerController(Areas.starOutpost.defaultSpawn)
    private val playerActor = ActorState("player", player.cell)
    private lateinit var world: Container
    private lateinit var worldHost: Container
    private lateinit var sceneRoot: SContainer
    private lateinit var uiLayer: Container
    private lateinit var actorLayer: Container
    private lateinit var playerView: View
    private lateinit var debugCellText: Text
    private val actorViews = mutableMapOf<ActorState, View>()
    private val shardActors = FirstChapterContent.starShards.associate { it.id to ActorState("shard:${it.id}", it.cell) }
    private val shardViews = mutableMapOf<String, View>()
    private var progress = OutpostProgress()
    private var signalLinked = false
    private var signalPuzzle = SignalPuzzleProgress()
    private var completionRemaining = 0.0
    private lateinit var hudText: Text
    private lateinit var hintText: Text
    private lateinit var completionText: Text
    private lateinit var areaBannerText: Text
    private lateinit var fadeOverlay: View
    private var areaBannerRemaining = 0.0
    private var transitionElapsed = 0.0
    private var transitionTarget: AreaId? = null
    private var inputLocked = false

    override suspend fun SContainer.sceneMain() {
        sceneRoot = this
        // Synchronous, tiny key-value load happens before any actor/HUD view is created.
        PlatformOutpostSave.load().let { (loaded, linked) -> progress = loaded; signalLinked = linked; signalPuzzle = if (linked) SignalPuzzleProgress(SignalLinkState.ONLINE) else SignalPuzzleProgress() }
        worldHost = container()
        uiLayer = container()
        createDebugControls()
        rebuildWorld(AreaId.STAR_OUTPOST, Areas.starOutpost.defaultSpawn, announce = true)
        keys {
            down(Key.UP) { requestMove(GridCell(0, -1)) }
            down(Key.W) { requestMove(GridCell(0, -1)) }
            down(Key.DOWN) { requestMove(GridCell(0, 1)) }
            down(Key.S) { requestMove(GridCell(0, 1)) }
            down(Key.LEFT) { requestMove(GridCell(-1, 0)) }
            down(Key.A) { requestMove(GridCell(-1, 0)) }
            down(Key.RIGHT) { requestMove(GridCell(1, 0)) }
            down(Key.D) { requestMove(GridCell(1, 0)) }
            down(Key.E) { tryAct() }
            down(Key.SPACE) { tryAct() }
        }
        sceneRoot.addUpdater { delta ->
            if (completionRemaining > 0.0) {
                completionRemaining -= delta.seconds
                if (completionRemaining <= 0.0) completionText.visible = false
            }
            updateTransition(delta.seconds)
            if (areaBannerRemaining > 0.0) { areaBannerRemaining -= delta.seconds; if (areaBannerRemaining <= 0.0) areaBannerText.visible = false }
        }
        refreshWorld()
        refreshUi()
    }

    private fun drawGround(layer: Container) {
        if (currentArea == AreaId.STAR_OUTPOST) OutpostVisuals.drawGround(layer, map) else SignalFieldVisuals.drawGround(layer, map)
    }

    private fun drawTerrain(layer: Container) {
        if (currentArea == AreaId.STAR_OUTPOST) {
            OutpostVisuals.drawTerrain(layer, map)
            SignalFieldVisuals.drawGate(layer, progress.coreState == CoreState.ACTIVE, 7 * 32.0, 2 * 32.0)
        } else {
            SignalFieldVisuals.drawTerrain(layer, map)
            SignalFieldVisuals.drawGate(layer, true, 7 * 32.0, 21 * 32.0)
        }
    }

    private fun drawActors() {
        areaDefinition().scenery.forEach { actor ->
            val view = actorLayer.container {
                if (currentArea == AreaId.STAR_OUTPOST) OutpostVisuals.run { drawActor(actor.name, progress.coreState, signalLinked) }
                else SignalFieldVisuals.run { drawActor(actor.name, signalPuzzle, signalLinked) }
            }
            actorViews[actor] = view
        }
        if (currentArea == AreaId.STAR_OUTPOST) FirstChapterContent.starShards.filter { it.id !in progress.collectedShardIds }.forEach { shard ->
            val actor = shardActors.getValue(shard.id)
            val view = actorLayer.container { OutpostVisuals.run { drawShard() } }
            actorViews[actor] = view
            shardViews[shard.id] = view
        }
        playerView = actorLayer.container {
            solidRect(20, 5, RGBA(25, 51, 39, 105)) { position(6, 28) }
            solidRect(12, 14, RGBA(48, 77, 122)) { position(10, 14) }
            solidRect(16, 16, RGBA(68, 116, 166)) { position(8, 0) }
            solidRect(12, 12, RGBA(244, 218, 112)) { position(10, -12) }
            solidRect(4, 10, RGBA(32, 51, 84)) { position(8, 25) }
            solidRect(4, 10, RGBA(32, 51, 84)) { position(20, 25) }
        }
        actorViews[playerActor] = playerView
    }

    private fun createDebugControls() {
        // This screen-fixed control is intentionally a Batch A DEBUG aid, not game UI.
        val controls = uiLayer.container { position(VIEWPORT_WIDTH - D_PAD_EXTENT - D_PAD_MARGIN, VIEWPORT_HEIGHT - D_PAD_EXTENT - D_PAD_MARGIN) }
        controls.text("DEBUG", textSize = 10.0, color = Colors.WHITE) { position(D_PAD_SIZE + D_PAD_GAP, -16) }
        debugCellText = uiLayer.text("", textSize = 10.0, color = Colors.WHITE) { position(8, 8) }
        hudText = uiLayer.text("", textSize = 10.0, color = Colors.WHITE) { position(8, 26) }
        hintText = uiLayer.text("", textSize = 10.0, color = Colors.WHITE) { position(96, 150) }
        completionText = uiLayer.text("STAR CORE RESTORED\nOUTPOST ONLINE", textSize = 16.0, color = Colors.WHITE) {
            position(95, 260)
            visible = false
        }
        areaBannerText = uiLayer.text("", textSize = 16.0, color = Colors.WHITE) { position(105, 150); visible = false }
        fadeOverlay = uiLayer.solidRect(VIEWPORT_WIDTH, VIEWPORT_HEIGHT, RGBA(0, 0, 0, 210)) { alpha = 0.0; visible = false }
        val act = uiLayer.container { position(D_PAD_MARGIN, VIEWPORT_HEIGHT - D_PAD_SIZE - D_PAD_MARGIN) }
        act.solidRect(D_PAD_SIZE, D_PAD_SIZE, RGBA(19, 35, 52, 112)) { onClick { tryAct() } }
        act.text("ACT", textSize = 12.0, color = Colors.WHITE) { position(11, 17) }
        button(controls, DPadDirection.UP, D_PAD_SIZE + D_PAD_GAP, 0.0, GridCell(0, -1), "UP")
        button(controls, DPadDirection.LEFT, 0.0, D_PAD_SIZE + D_PAD_GAP, GridCell(-1, 0), "LEFT")
        button(controls, DPadDirection.DOWN, D_PAD_SIZE + D_PAD_GAP, D_PAD_SIZE + D_PAD_GAP, GridCell(0, 1), "DOWN")
        button(controls, DPadDirection.RIGHT, (D_PAD_SIZE + D_PAD_GAP) * 2, D_PAD_SIZE + D_PAD_GAP, GridCell(1, 0), "RIGHT")
    }

    private fun button(parent: Container, arrow: DPadDirection, x: Double, y: Double, direction: GridCell, name: String) {
        parent.solidRect(D_PAD_SIZE, D_PAD_SIZE, RGBA(19, 35, 52, 112)) {
            position(x, y)
            onClick { requestMove(direction, debugTouch = true, directionName = name) }
        }
        drawArrow(parent, arrow, x, y)
    }

    /** Font-independent DEBUG glyph composed from rectangles; it does not receive input. */
    private fun drawArrow(parent: Container, direction: DPadDirection, x: Double, y: Double) {
        val color = RGBA(235, 245, 250, 230)
        fun part(width: Int, height: Int, offsetX: Int, offsetY: Int) =
            parent.solidRect(width, height, color) { position(x + offsetX, y + offsetY) }
        when (direction) {
            DPadDirection.UP -> {
                part(6, 24, 21, 18); part(6, 6, 21, 8); part(14, 6, 17, 14); part(22, 6, 13, 20)
            }
            DPadDirection.DOWN -> {
                part(6, 24, 21, 6); part(22, 6, 13, 22); part(14, 6, 17, 28); part(6, 6, 21, 34)
            }
            DPadDirection.LEFT -> {
                part(24, 6, 18, 21); part(6, 6, 8, 21); part(6, 14, 14, 17); part(6, 22, 20, 13)
            }
            DPadDirection.RIGHT -> {
                part(24, 6, 6, 21); part(6, 22, 22, 13); part(6, 14, 28, 17); part(6, 6, 34, 21)
            }
        }
    }

    private fun requestMove(direction: GridCell, debugTouch: Boolean = false, directionName: String = "KEY") {
        if (inputLocked) return
        val before = player.cell
        if (!player.tryMove(direction, map)) {
            if (debugTouch) DebugInputLog.record("direction=$directionName from=(${before.column},${before.row}) result=BLOCKED")
            return
        }
        playerActor.cell = player.cell
        if (debugTouch) DebugInputLog.record("direction=$directionName from=(${before.column},${before.row}) to=(${player.cell.column},${player.cell.row}) result=MOVED")
        refreshWorld()
        if (currentArea == AreaId.STAR_OUTPOST) collectShardAtPlayer()
    }

    private fun collectShardAtPlayer() {
        val shard = FirstChapterContent.starShards.firstOrNull { it.cell == player.cell && it.id !in progress.collectedShardIds } ?: return
        progress = progress.collectShard(shard.id)
        shardViews.remove(shard.id)?.removeFromParent()
        shardActors[shard.id]?.let { actorViews.remove(it) }
        DebugGameLog.record("shardCollected id=${shard.id} count=${progress.shardCount}/${OutpostProgress.REQUIRED_SHARDS}" +
            if (progress.coreState == CoreState.READY) " coreState=READY" else "")
        hintText.text = "SHARD FOUND"
        if (progress.coreState == CoreState.READY) redrawScenery()
        refreshUi()
        PlatformOutpostSave.save(progress, signalLinked)
    }

    private fun tryAct() {
        if (inputLocked) return
        val area = areaDefinition()
        if (Areas.isGateAdjacent(area, player.cell)) {
            if (currentArea == AreaId.STAR_OUTPOST && progress.coreState != CoreState.ACTIVE) {
                hintText.text = "RESTORE STAR CORE"
                DebugAreaLog.record("transition blocked reason=CORE_${progress.coreState}")
            } else startTransition(if (currentArea == AreaId.STAR_OUTPOST) AreaId.SIGNAL_FIELD else AreaId.STAR_OUTPOST)
            return
        }
        if (currentArea == AreaId.SIGNAL_FIELD) { trySignalAct(); return }
        tryActivateCore()
    }

    private fun tryActivateCore() {
        if (!map.isCoreActAdjacent(player.cell)) {
            DebugGameLog.record("coreActivation result=BLOCKED reason=NOT_ADJACENT")
            return
        }
        val (next, result) = progress.activateCore()
        when (result) {
            CoreActivationResult.INSUFFICIENT_SHARDS -> {
                hintText.text = "NEED 3 SHARDS"
                DebugGameLog.record("coreActivation result=BLOCKED reason=INSUFFICIENT_SHARDS")
            }
            CoreActivationResult.SUCCESS -> {
                progress = next
                hintText.text = ""
                redrawScenery()
                PlatformOutpostSave.save(progress, signalLinked)
                completionText.visible = true
                completionRemaining = 2.5
                DebugGameLog.record("coreActivation result=SUCCESS state=ACTIVE completed=true")
            }
            CoreActivationResult.ALREADY_ACTIVE -> DebugGameLog.record("coreActivation result=IGNORED state=ACTIVE")
        }
        refreshUi()
    }

    private fun redrawScenery() {
        val scenery = areaDefinition().scenery
        scenery.forEach { actor -> actorViews.remove(actor)?.removeFromParent() }
        scenery.forEach { actor ->
            actorViews[actor] = actorLayer.container { if (currentArea == AreaId.STAR_OUTPOST) OutpostVisuals.run { drawActor(actor.name, progress.coreState, signalLinked) } else SignalFieldVisuals.run { drawActor(actor.name, signalPuzzle, signalLinked) } }
        }
        refreshWorld()
    }

    private fun refreshUi() {
        hudText.text = when {
            currentArea == AreaId.SIGNAL_FIELD && signalPuzzle.state == SignalLinkState.ROUTING -> "LINK ORDER: A > C > B\nSIGNAL ROUTE ${signalPuzzle.acceptedBeaconIds.size}/3\nNEXT: ${signalPuzzle.expectedNext()}"
            signalLinked -> "OUTPOST ONLINE\nSIGNAL LINK ONLINE"
            currentArea == AreaId.SIGNAL_FIELD -> "OUTPOST ONLINE\nSIGNAL LINK OFFLINE"
            progress.completed -> "OUTPOST ONLINE"
            progress.coreState == CoreState.READY -> "STAR SHARDS 3/3  CORE READY"
            else -> "STAR SHARDS ${progress.shardCount}/${OutpostProgress.REQUIRED_SHARDS}"
        }
        if (currentArea == AreaId.STAR_OUTPOST && progress.coreState == CoreState.READY && map.isCoreActAdjacent(player.cell)) hintText.text = "PRESS ACT"
    }

    /** Movement-only refresh: actor feet define depth order and camera follows player center. */
    private fun refreshWorld() {
        // UI is a sibling of world and must never inherit a camera translation.
        uiLayer.position(0, 0)
        debugCellText.text = "DEBUG area=${currentArea.name} cell=(${player.cell.column},${player.cell.row})"
        actorViews.forEach { (actor, view) -> positionActor(view, actor) }
        actorViews.entries.sortedBy { it.key.footY }.forEach { (_, view) ->
            view.removeFromParent()
            actorLayer.addChild(view)
        }
        val playerCenterX = map.cellLeft(player.cell) + SpikeMap.TILE_SIZE / 2.0
        val playerCenterY = map.cellTop(player.cell) + SpikeMap.TILE_SIZE / 2.0
        val offset = camera.follow(playerCenterX, playerCenterY, map)
        world.position(-offset.left, -offset.top)
        refreshUi()
    }

    private fun positionActor(view: View, actor: ActorState) = view.position(map.cellLeft(actor.cell), map.cellTop(actor.cell))

    private fun areaDefinition(): AreaDefinition = Areas.definition(currentArea)

    private fun rebuildWorld(area: AreaId, spawn: GridCell, announce: Boolean) {
        if (this::world.isInitialized) world.removeFromParent()
        if (currentArea == AreaId.SIGNAL_FIELD && signalPuzzle.state == SignalLinkState.ROUTING) { signalPuzzle = signalPuzzle.leaveArea(); DebugSignalLog.record("routing reset reason=AREA_EXIT") }
        currentArea = area
        map = areaDefinition().map
        player.place(spawn); playerActor.cell = spawn
        actorViews.clear(); shardViews.clear()
        world = worldHost.container()
        val groundLayer = world.container(); val terrainLayer = world.container(); actorLayer = world.container()
        drawGround(groundLayer); drawTerrain(terrainLayer); drawActors(); hintText.text = ""; completionText.visible = false
        refreshWorld()
        if (announce) { areaBannerText.text = currentArea.name.replace('_', ' '); areaBannerText.visible = true; areaBannerRemaining = 1.5 }
    }

    private fun startTransition(target: AreaId) {
        inputLocked = true; transitionTarget = target; transitionElapsed = 0.0; fadeOverlay.visible = true; fadeOverlay.alpha = 0.0
        DebugAreaLog.record("transition started from=$currentArea to=$target")
    }

    private fun trySignalAct() {
        val terminal = Areas.signalField.scenery.first { it.name == "terminal" }
        val beacon = Areas.signalField.scenery.firstOrNull { it.name.startsWith("beacon") && adjacent(it.cell) }
        if (adjacent(terminal.cell)) {
            signalPuzzle = signalPuzzle.startRouting(); hintText.text = if (signalPuzzle.state == SignalLinkState.ONLINE) "SIGNAL LINK ONLINE" else "SIGNAL ROUTE FOUND"
            DebugSignalLog.record("routing started order=A,C,B"); refreshUi(); redrawScenery(); return
        }
        if (beacon == null) return
        val id = when (beacon.name.last()) { 'A' -> BeaconId.A; 'B' -> BeaconId.B; else -> BeaconId.C }
        val (next, result) = signalPuzzle.activate(id); signalPuzzle = next
        when (result) {
            BeaconResult.NEED_TERMINAL -> hintText.text = "SCAN AT TERMINAL"
            BeaconResult.REJECTED -> { hintText.text = "SIGNAL LOST\nSTART FROM A"; DebugSignalLog.record("beacon rejected id=$id expected=A reset=true") }
            BeaconResult.ACCEPTED -> DebugSignalLog.record("beacon accepted id=$id progress=${next.acceptedBeaconIds.size}/3 next=${next.expectedNext()}")
            BeaconResult.COMPLETED -> { signalLinked = true; PlatformOutpostSave.save(progress, true); completionText.text = "COMMUNICATION RESTORED\nSIGNAL LINK ONLINE"; completionText.visible = true; completionRemaining = 2.5; DebugSignalLog.record("signalLink result=SUCCESS state=ONLINE") }
            BeaconResult.IGNORED -> DebugSignalLog.record("signalLink result=IGNORED state=ONLINE")
        }
        redrawScenery(); refreshUi()
    }

    private fun adjacent(cell: GridCell) = Areas.isOrthogonallyAdjacent(player.cell, cell)

    private fun updateTransition(delta: Double) {
        val target = transitionTarget ?: return
        transitionElapsed += delta
        if (transitionElapsed < 0.3) { fadeOverlay.alpha = transitionElapsed / 0.3; return }
        if (transitionElapsed - delta < 0.3) {
            val spawn = if (target == AreaId.SIGNAL_FIELD) Areas.signalField.defaultSpawn else GridCell(7, 4)
            rebuildWorld(target, spawn, announce = true)
            DebugAreaLog.record("transition completed area=$target spawn=(${spawn.column},${spawn.row})")
        }
        if (transitionElapsed < 0.6) { fadeOverlay.alpha = 1.0 - ((transitionElapsed - 0.3) / 0.3); return }
        fadeOverlay.visible = false; transitionTarget = null; inputLocked = false
    }

    private companion object {
        const val VIEWPORT_WIDTH = 360.0
        const val VIEWPORT_HEIGHT = 640.0
        const val D_PAD_SIZE = 48.0
        const val D_PAD_GAP = 6.0
        const val D_PAD_EXTENT = D_PAD_SIZE * 3 + D_PAD_GAP * 2
        const val D_PAD_MARGIN = 24.0
    }

    private enum class DPadDirection { UP, DOWN, LEFT, RIGHT }
}
