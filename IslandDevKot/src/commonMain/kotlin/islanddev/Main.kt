package islanddev

import islanddev.data.GameData
import islanddev.game.AutoNavigator
import islanddev.game.AutoInputPolicy
import islanddev.game.AutoCollectionState
import islanddev.game.AutoDebugInfo
import islanddev.game.AutoStopReason
import islanddev.game.AutoTarget
import islanddev.game.CraftManager
import islanddev.game.ISaveManager
import islanddev.game.ResourceTicker
import islanddev.game.SavePolicy
import islanddev.game.SubZoneManager
import islanddev.game.WorldFactory
import islanddev.model.SaveData
import islanddev.scene.EndingScene
import islanddev.scene.GridScene
import islanddev.scene.InputDebug
import islanddev.scene.MapInputPolicy
import islanddev.scene.MovementInputConfig
import islanddev.scene.PlayerSprite
import islanddev.scene.StepMoveInputPolicy
import islanddev.scene.TapMoveResolver
import islanddev.scene.TouchInput
import islanddev.ui.BossPanel
import islanddev.ui.CraftPanel
import islanddev.ui.GameHUD
import islanddev.ui.IslandUiFonts
import islanddev.ui.IslandTheme
import islanddev.ui.SubZonePanel
import korlibs.korge.Korge
import korlibs.korge.scene.Scene
import korlibs.korge.scene.sceneContainer
import korlibs.korge.view.SContainer
import korlibs.korge.view.addUpdater
import korlibs.korge.view.addTo
import korlibs.korge.view.position
import korlibs.math.geom.Size
import korlibs.time.DateTime
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object IslandSession {
    var save: SaveData = SaveData()
    var saveManager: ISaveManager? = null
    var saveDirty: Boolean = false
    private val saveMutex = Mutex()

    fun update(data: SaveData) {
        save = data
        saveDirty = true
    }

    suspend fun persist(force: Boolean = false) {
        saveMutex.withLock {
            val manager = saveManager ?: return@withLock
            if (!force && !saveDirty) return@withLock
            val snapshot = save
            runCatching { manager.save(snapshot) }
                .onSuccess {
                    if (save == snapshot) saveDirty = false
                }
        }
    }
}

suspend fun main() = Korge(
    windowSize = Size(360, 640),
    title = "IslandDev",
) {
    IslandUiFonts.load()
    injector.mapPrototype { MainScene() }
    injector.mapPrototype { EndingScene() }
    val sc = sceneContainer()
    sc.changeTo<MainScene>()
}

class MainScene : Scene() {
    override suspend fun SContainer.sceneMain() {
        val loadedSave = IslandSession.save
        val startNow = nowSec()
        var save = normalizeProgress(
            SubZoneManager.tickUnlock(
                ResourceTicker.tickRespawn(
                    WorldFactory.ensureInitialized(loadedSave),
                    startNow
                ),
                startNow
            )
        )
        if (save != loadedSave) {
            IslandSession.update(save)
            IslandSession.persist()
        } else {
            IslandSession.save = save
        }

        lateinit var player: PlayerSprite
        lateinit var hud: GameHUD
        lateinit var grid: GridScene
        var isModalOpen = false
        var suppressNextMapTap = false
        var autoState = AutoCollectionState()
        var autoStopReason = AutoStopReason.USER_OFF
        var autoTarget: AutoTarget? = null
        var autoDebugZoneId: Int? = null
        var autoDebugCandidateCount = 0
        var autoThinkCooldown = 0.0
        grid = GridScene(save, viewportHeight = IslandTheme.Size.MapHeight) { tappedCell ->
            if (autoState.enabled) {
                autoState = autoState.stop()
                autoStopReason = AutoStopReason.USER_OFF
                autoTarget = null
                autoDebugCandidateCount = 0
                autoThinkCooldown = 0.0
                hud.setAutoEnabled(false)
                hud.setAutoStatus("指定移動")
            }
            val resolution = TapMoveResolver.resolve(
                save = save,
                start = player.navigationOrigin(),
                tappedCell = tappedCell
            )
            resolution.request?.let { request ->
                player.requestMoveAlongPath(request.destination, request.path)
                grid.updateMovementDebug(player.movementDebugState())
            }
            resolution
        }.addTo(this) {
            position(0, IslandTheme.Size.MapTop)
        }
        val bossPanel = BossPanel().addTo(this)
        val craftPanel = CraftPanel().addTo(this)
        val subZonePanel = SubZonePanel().addTo(this)

        fun openModal() {
            isModalOpen = true
        }

        fun closeModal() {
            isModalOpen = false
            suppressNextMapTap = true
        }

        fun applySave(newSave: SaveData) {
            val previous = save
            save = normalizeProgress(newSave)
            IslandSession.update(save)
            player.updateSave(save)
            grid.update(save)
            val newlyUnlockedSubZones = save.unlockedSubZoneIds - previous.unlockedSubZoneIds
            newlyUnlockedSubZones.forEach { subZoneId ->
                val name = GameData.subZoneById(subZoneId)?.name ?: "区画"
                hud.showBanner("${name} が解放されました！")
            }
            if (SavePolicy.requiresImmediateSave(previous, save)) {
                launch { IslandSession.persist() }
            }
        }

        player = PlayerSprite(
            initialSave = save,
            onSaveChanged = ::applySave,
            onBossApproach = { bossId -> hud.setNearBoss(bossId) }
        ).addTo(grid.actorLayer)

        hud = GameHUD(
            onBossChallenge = { bossId ->
                openModal()
                bossPanel.show(
                    save = save,
                    bossId = bossId,
                    onResult = { result ->
                        applySave(result)
                        if (result.gameCleared) {
                            launch { sceneContainer.changeTo<EndingScene>() }
                        }
                    },
                    onClosed = ::closeModal
                )
            },
            onCraftOpen = {
                openModal()
                craftPanel.show(save, ::applySave, ::closeModal)
            },
            onSubZoneOpen = {
                openModal()
                val zoneId = GameData.columnToZone(save.playerCol)
                subZonePanel.show(
                    save,
                    zoneId,
                    nowSec(),
                    ::applySave,
                    ::closeModal
                )
            },
            onStepMove = { dx, dy ->
                if (StepMoveInputPolicy.acceptsInput(isModalOpen)) {
                    if (autoState.enabled) {
                        autoState = autoState.stop()
                        autoStopReason = AutoStopReason.USER_OFF
                        autoThinkCooldown = 0.0
                        hud.setAutoEnabled(false)
                        hud.setAutoStatus("手動操作")
                    }
                    autoTarget = null
                    player.requestStepMove(dx, dy)
                }
            },
            onAutoToggle = {
                player.cancelPlannedMovement()
                val wasEnabled = autoState.enabled
                autoState = autoState.toggle(save.playerCol)
                autoStopReason = if (wasEnabled) {
                    AutoStopReason.USER_OFF
                } else {
                    AutoStopReason.NONE
                }
                if (autoState.enabled) {
                    autoDebugZoneId = autoState.zoneId
                }
                autoTarget = null
                autoDebugCandidateCount = 0
                autoThinkCooldown = 0.0
                autoState.enabled
            }
        ).addTo(this)

        // Panels must remain above the HUD.
        bossPanel.removeFromParent()
        craftPanel.removeFromParent()
        subZonePanel.removeFromParent()
        addChild(bossPanel)
        addChild(craftPanel)
        addChild(subZonePanel)

        var lastSecond = nowSec()
        var lastAutoSave = lastSecond
        var lastHudMinute = Long.MIN_VALUE
        var lastHudSave: SaveData? = null
        var touchInputInstalled = false
        addUpdater { delta ->
            val vi = stage?.views
            if (vi != null) {
                if (!touchInputInstalled) {
                    TouchInput.install(vi.gameWindow.androidContextAny)
                    touchInputInstalled = true
                }
                val tap = TouchInput.consumeTap()
                if (tap != null) {
                    val pW = vi.gameWindow.width.toFloat()
                    val pH = vi.gameWindow.height.toFloat()
                    val virtualW = vi.virtualWidth.toFloat()
                    val virtualH = vi.virtualHeight.toFloat()
                    val renderScale = minOf(pW / virtualW, pH / virtualH)
                    val leftPad = (pW - virtualW * renderScale) / 2f
                    val topPad = (pH - virtualH * renderScale) / 2f
                    val manualVx = (tap.x - leftPad) / renderScale
                    val manualVy = (tap.y - topPad) / renderScale
                    val mousePos = vi.input.mousePos
                    val vx = mousePos.x.toFloat()
                    val vy = mousePos.y.toFloat()
                    if (TapMoveResolver.DEBUG_TAP_MOVE) {
                        println(
                            "tap rawMotion=(${tap.rawX},${tap.rawY}) " +
                                "event=(${tap.x},${tap.y}) " +
                                "korgeStage=($vx,$vy) manualVirtual=($manualVx,$manualVy) " +
                                "window=($pW,$pH) virtualSize=($virtualW,$virtualH) " +
                                "scale=$renderScale pad=($leftPad,$topPad)"
                        )
                    }
                    val suppressTap = suppressNextMapTap
                    suppressNextMapTap = false
                    if (MapInputPolicy.acceptsMapTap(
                            tapMoveEnabled = MovementInputConfig.ENABLE_TAP_MOVE,
                            isModalOpen = isModalOpen,
                            suppressTap = suppressTap,
                            isHudTouch = hud.capturesTouch(vx, vy)
                        )
                    ) {
                        grid.processTapAt(
                            stageX = vx,
                            stageY = vy,
                            rawMotionX = tap.rawX,
                            rawMotionY = tap.rawY
                        )
                    }
                }
            }
            val now = nowSec()
            val deltaSeconds = delta.inWholeMicroseconds / 1_000_000.0
            player.update(deltaSeconds, now)
            autoThinkCooldown = AutoInputPolicy.updateThinkCooldown(
                currentSeconds = autoThinkCooldown,
                deltaSeconds = deltaSeconds,
                enabled = autoState.enabled,
                isModalOpen = isModalOpen,
                playerIdle = player.isIdle
            )
            if (AutoInputPolicy.canAdvance(
                    autoState.enabled,
                    isModalOpen,
                    player.isIdle,
                    autoThinkCooldown
                )
            ) {
                autoThinkCooldown = AutoInputPolicy.THINK_INTERVAL_SECONDS
                val autoZoneId = autoState.zoneId
                val candidates = autoZoneId?.let {
                    AutoNavigator.candidates(save, it)
                }.orEmpty()
                autoDebugCandidateCount = candidates.size
                var advanced = false
                var collectFailed = false
                autoTarget = candidates.firstOrNull()
                for (candidate in candidates) {
                    val isCurrentCell =
                        candidate.destination.col == save.playerCol &&
                            candidate.destination.row == save.playerRow
                    if (isCurrentCell) {
                        if (player.collectCurrentCell(now)) {
                            autoTarget = candidate
                            advanced = true
                            break
                        }
                        collectFailed = true
                        continue
                    }
                    val direction = AutoNavigator.chooseDirection(
                        save,
                        candidate,
                        requireNotNull(autoZoneId)
                    )
                    if (direction != null &&
                        player.requestStepMove(direction.dx, direction.dy)
                    ) {
                        autoTarget = candidate
                        advanced = true
                        break
                    }
                }
                if (!advanced) {
                    autoState = autoState.stop()
                    autoStopReason = when {
                        save.gameCleared -> AutoStopReason.GAME_CLEARED
                        candidates.isEmpty() -> AutoStopReason.NO_TARGET
                        collectFailed -> AutoStopReason.COLLECT_FAILED
                        else -> AutoStopReason.NO_DIRECTION
                    }
                    hud.setAutoEnabled(false)
                    hud.setAutoStatus(
                        if (autoStopReason == AutoStopReason.NO_TARGET) {
                            "素材なし"
                        } else {
                            "停止"
                        }
                    )
                }
            }
            if (GameHUD.AUTO_DEBUG_VISIBLE) {
                hud.updateAutoDebug(
                    AutoDebugInfo(
                        enabled = autoState.enabled,
                        fixedZoneId = autoState.zoneId ?: autoDebugZoneId,
                        candidateCount = autoDebugCandidateCount,
                        target = autoTarget,
                        stopReason = if (autoState.enabled && isModalOpen) {
                            AutoStopReason.MODAL
                        } else {
                            autoStopReason
                        }
                    )
                )
            }
            if (InputDebug.ENABLED) {
                grid.updateMovementDebug(player.movementDebugState())
            }
            if (bossPanel.visible) bossPanel.update(deltaSeconds)
            grid.updateCamera(player.visualCol, player.visualRow)
            val currentHudMinute = now / 60
            if (currentHudMinute != lastHudMinute || save != lastHudSave) {
                hud.update(save, now)
                lastHudMinute = currentHudMinute
                lastHudSave = save
            }
            subZonePanel.update(save, now)

            if (now != lastSecond) {
                val respawned = ResourceTicker.tickRespawn(save, now)
                val unlocked = SubZoneManager.tickUnlock(respawned, now)
                if (unlocked != save) applySave(unlocked)
                lastSecond = now
            }
            if (now - lastAutoSave >= 30) {
                launch { IslandSession.persist() }
                lastAutoSave = now
            }
        }
    }
}

private fun nowSec(): Long = DateTime.now().unixMillisLong / 1000L

private fun normalizeProgress(save: SaveData): SaveData =
    CraftManager.autoEquip(save)
