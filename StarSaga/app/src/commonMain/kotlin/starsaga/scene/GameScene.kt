package starsaga.scene

import korlibs.image.color.Colors
import korlibs.image.color.RGBA
import korlibs.korge.scene.Scene
import korlibs.korge.view.Container
import korlibs.korge.view.SContainer
import korlibs.korge.view.Text
import korlibs.korge.view.View
import korlibs.korge.view.addUpdater
import korlibs.korge.view.container
import korlibs.korge.view.image
import korlibs.korge.view.position
import korlibs.korge.view.solidRect
import korlibs.korge.view.text
import starsaga.ui.CreatureImages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import starsaga.StarSagaSession
import starsaga.currentEpochMillis
import starsaga.battle.BattleCompanionState
import starsaga.battle.AutoBattlePolicy
import starsaga.battle.BattleBalance
import starsaga.battle.BattleEngine
import starsaga.battle.BattlePhase
import starsaga.battle.BattleState
import starsaga.battle.EncounterResolver
import starsaga.battle.Leveling
import starsaga.battle.RecruitmentProgress
import starsaga.camera.CameraController
import starsaga.data.CompanionState
import starsaga.data.CreatureData
import starsaga.data.CreatureDatabase
import starsaga.data.CreatureRole
import starsaga.data.ItemData
import starsaga.data.ItemDatabase
import starsaga.data.ItemKind
import starsaga.data.ObjectiveResolver
import starsaga.data.T1Objective
import starsaga.data.T1ObjectiveContext
import starsaga.data.RpgSaveData
import starsaga.data.SkillDatabase
import starsaga.input.MapInput
import starsaga.map.ExitSide
import starsaga.map.GridCell
import starsaga.map.MapData
import starsaga.map.MapExit
import starsaga.map.MapSpawn
import starsaga.map.SaveMigration
import starsaga.map.T1MapProgress
import starsaga.map.T1WarpPolicy
import starsaga.map.TileRenderer
import starsaga.map.TileType
import starsaga.player.PlayerController
import starsaga.ui.StarSagaFonts
import starsaga.ui.UiPanelLayout
import starsaga.ui.centeredButtonTextY
import starsaga.ui.createUiButton
import starsaga.ui.createUiText
import kotlin.math.floor
import kotlin.coroutines.coroutineContext

private enum class EncounterUiState {
    Hidden,
    ChooseAction,
    Result,
    BefriendOffer,
    Battle,
    SkillActorSelect,
    SkillSelect,
    ItemSelect,
    Shop,
    T1Clear,
    BossConfirm,
    WarpConfirm,
}

private enum class CompanionPanelMode {
    PartyView,
    RanchView,
}

private enum class FacilityActionType {
    Heal,
    Shop,
    RanchGate,
    RanchTerminal,
    RanchExit,
    TrainingPad,
    DeepGate,
    OutpostParty,
    WarpGate,
    Message,
}

private data class PendingFacilityAction(
    val type: FacilityActionType,
    val facilityCell: GridCell,
    val interactionCell: GridCell,
    val messageTitle: String? = null,
    val messageDetail: String? = null,
)

private data class RanchCompanionActor(
    val companion: CompanionState,
    val creature: CreatureData,
    val view: Container,
    var cell: GridCell,
    var moveIndex: Int = 0,
)

private data class VictoryExpResult(
    val lines: List<String>,
    val leveledUp: Boolean,
)

private data class RecruitProgressResult(
    val line: String,
    val important: Boolean,
    val joined: Boolean = false,
)

class GameScene : Scene() {
    private val viewportWidth = 360.0
    private val viewportHeight = 640.0
    private var map = MapData.get(T1MapProgress.DEFAULT_MAP_ID)
    private val camera = CameraController(viewportWidth, viewportHeight)
    private val player = PlayerController(T1MapProgress.DEFAULT_SPAWN)
    private lateinit var saveScope: CoroutineScope
    private lateinit var sceneRoot: SContainer
    private lateinit var world: Container
    private lateinit var terrainLayer: Container
    private lateinit var actorLayer: Container
    private var ranchCompanionLayer: Container? = null
    private lateinit var debugLayer: Container
    private lateinit var objectiveLayer: Container
    private lateinit var encounterLayer: Container
    private lateinit var shopLayer: Container
    private lateinit var t1ClearLayer: Container
    private lateinit var companionButtonLayer: Container
    private lateinit var companionPanelLayer: Container
    private lateinit var companionGridLayer: Container
    private var playerView: Container? = null
    private var debugText: Text? = null
    private var objectiveText: Text? = null
    private var encounterTitleText: Text? = null
    private var encounterStatsText: Text? = null
    private var fightButtonView: View? = null
    private var skillButtonView: View? = null
    private var itemButtonView: View? = null
    private var runButtonView: View? = null
    private var fightLabelText: Text? = null
    private var skillLabelText: Text? = null
    private var itemLabelText: Text? = null
    private var runLabelText: Text? = null
    private var autoButtonView: View? = null
    private var autoLabelText: Text? = null
    private var okLabelText: Text? = null
    private var row1ButtonView: View? = null
    private var row2ButtonView: View? = null
    private var row3ButtonView: View? = null
    private var rowBackButtonView: View? = null
    private var row1LabelText: Text? = null
    private var row2LabelText: Text? = null
    private var row3LabelText: Text? = null
    private var rowBackLabelText: Text? = null
    private var companionListText: Text? = null
    private var companionPanelTitleText: Text? = null
    private var companionPrevButtonView: View? = null
    private var companionNextButtonView: View? = null
    private var companionPrevLabelText: Text? = null
    private var companionNextLabelText: Text? = null
    private var encounterUiState: EncounterUiState = EncounterUiState.Hidden
    private var companionPanelOpen = false
    private var companionPanelMode = CompanionPanelMode.PartyView
    private var companionPage = 0
    private var selectedCompanionId: String? = null
    private var companionPanelMessage = ""
    private var tappedCell: GridCell? = null
    private var handoffCell: GridCell? = null
    private var lastTransition: String? = null
    private var ranchReturnMapId: String? = null
    private var ranchReturnCell: GridCell? = null
    private var pendingFacilityAction: PendingFacilityAction? = null
    private var activeEncounter: CreatureData? = null
    private var activeEncounterIsBoss = false
    private var lastEncounterCreatureName: String? = null
    private var lastEncounterRoll: Double? = null
    private val defeatCountByCreatureId: MutableMap<Int, Int> = mutableMapOf()
    private val recruitProgressByCreatureId: MutableMap<Int, Int> = mutableMapOf()
    private val companions: MutableList<CompanionState> = mutableListOf()
    private val ranchCompanionActors: MutableList<RanchCompanionActor> = mutableListOf()
    private val partyCompanionIds: MutableList<String> = mutableListOf()
    private val activeCompanionIds: MutableList<String> = mutableListOf()
    private val trainingCompanionIds: MutableList<String> = mutableListOf()
    private val seenCreatureIds: MutableSet<Int> = mutableSetOf()
    private val befriendedCreatureIds: MutableSet<Int> = mutableSetOf()
    private var gold = 0
    private val itemCounts: MutableMap<Int, Int> = mutableMapOf()
    private var lastBefriendChance: Double? = null
    private var lastBefriendRoll: Double? = null
    private var battleState: BattleState? = null
    private var pendingBefriendOffer: CreatureData? = null
    private var lastFacilityAction: String? = null
    private var selectedSkillActorId: String? = null
    private var autoBattleEnabled = false
    private var autoBattleSeconds = 0.0
    private var pendingAutoStartSeconds = 0.0
    private var resultAutoCloseSeconds = 0.0
    private var currentEncounterFirstSeen = false
    private var saveLoaded = false
    private var savedCount = 0
    private var lastSavedSec = 0L
    private var lastTrainingAtEpochMillis = 0L
    private var saveStatus = "not loaded"
    private var lockedExitSide: ExitSide? = null
    private var lastMove: Pair<GridCell, GridCell>? = null
    private var activeExitSide: ExitSide? = null
    private var inputLockedUntilRelease = false
    private var inputLockSeconds = 0.0
    private var wasPressed = false
    private var debugVisible = false
    private var tutorialSeen = false
    private var t1BossCleared = false
    private var t1ClearAcknowledged = false
    private var reachedT1Outpost = false
    private var t1OutpostWarpUnlocked = false
    private var pendingWarpTarget: MapSpawn? = null

    override suspend fun SContainer.sceneMain() {
        StarSagaFonts.load()
        CreatureImages.load()
        saveScope = CoroutineScope(coroutineContext)
        loadGame()
        normalizePartyState()
        sceneRoot = this
        solidRect(viewportWidth, viewportHeight, Colors.BLACK)

        world = container()
        terrainLayer = world.container()
        actorLayer = world.container()
        objectiveLayer = container()
        debugLayer = container()
        companionButtonLayer = container()
        companionPanelLayer = container()
        encounterLayer = container()

        TileRenderer(terrainLayer).draw(map)
        drawPlayer()
        rebuildRanchCompanions()
        drawObjectivePanel()
        drawDebug()
        drawCompanionButton()
        drawCompanionPanel()
        drawEncounterPanel()
        camera.update(map, player.currentCell)
        applyCamera()
        updateObjectivePanel()
        updateDebugText()
        showInitialGuideIfNeeded()

        addUpdater { delta ->
            val deltaSeconds = delta.inWholeMicroseconds / 1_000_000.0
            if (inputLockSeconds > 0.0) {
                inputLockSeconds = (inputLockSeconds - deltaSeconds).coerceAtLeast(0.0)
            }
            consumeTap()
            updatePendingAutoStart(deltaSeconds)
            updateAutoBattle(deltaSeconds)
            updateResultAutoClose(deltaSeconds)
            val beforeCell = player.currentCell
            player.update(deltaSeconds)
            if (player.currentCell != beforeCell) {
                val afterCell = player.currentCell
                lastMove = beforeCell to afterCell
                updateExitSideLock(beforeCell, afterCell)
                val transitioned = processEdgeTransition(beforeCell, afterCell)
                if (!transitioned) {
                    if (!processPendingFacilityArrival(afterCell)) {
                        processEncounterIfNeeded(afterCell)
                    }
                }
                requestSave()
            }
            updatePlayerView()
            camera.update(map, player.currentCell)
            applyCamera()
            updateObjectivePanel()
            updateDebugText()
        }
    }

    private fun consumeTap() {
        val views = sceneRoot.stage?.views ?: return
        val pressed = views.input.mouseButtons != 0
        if (companionPanelOpen) {
            if (!wasPressed && pressed) {
                handleCompanionPanelTap(views.input.mousePos.x, views.input.mousePos.y)
            }
            wasPressed = pressed
            return
        }
        if (encounterUiState != EncounterUiState.Hidden) {
            if (!wasPressed && pressed) {
                handleEncounterTap(views.input.mousePos.x, views.input.mousePos.y)
            }
            wasPressed = pressed
            return
        }
        if (inputLockedUntilRelease || inputLockSeconds > 0.0) {
            if (!pressed || inputLockSeconds <= 0.0) {
                inputLockedUntilRelease = false
            }
            wasPressed = pressed
            return
        }
        if (!wasPressed && pressed) {
            val pos = views.input.mousePos
            if (isInside(pos.x, pos.y, COMPANION_BUTTON_X, COMPANION_BUTTON_Y, COMPANION_BUTTON_W, COMPANION_BUTTON_H)) {
                openPartyPanel()
                consumeUiPointer()
                wasPressed = pressed
                return
            }
            if (debugVisible && isInside(pos.x, pos.y, HP1_BUTTON_X, HP1_BUTTON_Y, COMPANION_BUTTON_W, COMPANION_BUTTON_H)) {
                setActiveHpToOneForDebug()
                consumeUiPointer()
                wasPressed = pressed
                return
            }
            val worldX = pos.x + camera.state.left
            val worldY = pos.y + camera.state.top
            val cell = MapInput.cellAt(
                worldX = worldX,
                worldY = worldY,
                map = map,
            )
            if (map.id == T1MapProgress.RANCH_MAP_ID && showRanchCompanionMessageAt(worldX, worldY)) {
                consumeUiPointer()
                wasPressed = pressed
                return
            }
            if (cell != null && startFacilityInteraction(cell)) {
                consumeUiPointer()
                wasPressed = pressed
                return
            }
            val destination = destinationForTap(worldX, worldY)
            if (destination != null) {
                pendingFacilityAction = null
                tappedCell = cell ?: destination
                handoffCell = destination
                player.moveTo(map, destination)
            }
        }
        wasPressed = pressed
    }

    private fun destinationForTap(worldX: Double, worldY: Double): GridCell? {
        val rawCol = floor(worldX / MapData.TILE_SIZE).toInt()
        val rawRow = floor(worldY / MapData.TILE_SIZE).toInt()
        val safeRow = rawRow.coerceIn(1, map.rows - 2)
        val eastExit = map.exits.firstOrNull { it.side == ExitSide.East }
        val westExit = map.exits.firstOrNull { it.side == ExitSide.West }
        val safeCol = when {
            eastExit != null && rawCol >= eastExit.triggerCol - 1 -> eastExit.triggerCol
            westExit != null && rawCol <= westExit.triggerCol + 1 -> westExit.triggerCol
            else -> rawCol.coerceIn(1, map.columns - 2)
        }
        return GridCell(safeCol, safeRow).takeIf {
            map.isPassable(it.col, it.row)
        }
    }

    private fun processEdgeTransition(before: GridCell, after: GridCell): Boolean {
        val exit = map.exitForMove(before, after) ?: run {
            activeExitSide = null
            return false
        }
        activeExitSide = exit.side
        if (lockedExitSide == exit.side) return false
        transitionTo(exit)
        return true
    }

    private fun processEncounterIfNeeded(cell: GridCell) {
        if (map.id == T1MapProgress.RANCH_MAP_ID) return
        if (map.tileAt(cell.col, cell.row) != TileType.Grass) return
        val result = EncounterResolver.roll(map.id)
        lastEncounterRoll = result.roll
        val creature = result.creature ?: return
        activeEncounter = creature
        activeEncounterIsBoss = false
        currentEncounterFirstSeen = creature.id !in seenCreatureIds
        if (currentEncounterFirstSeen) {
            seenCreatureIds += creature.id
            requestSave()
        }
        lastEncounterCreatureName = creature.name
        tappedCell = null
        handoffCell = null
        player.clearMovement()
        showEncounterPanel(creature)
    }

    private fun transitionTo(exit: MapExit) {
        val fromMapId = map.id
        map = MapData.get(exit.targetMapId)
        player.warpTo(exit.targetSpawn)
        tappedCell = null
        handoffCell = null
        wasPressed = true
        inputLockedUntilRelease = true
        inputLockSeconds = 0.35
        lockedExitSide = exit.lockedSideOnArrival
        activeExitSide = null
        lastMove = null
        lastTransition = "${exit.id}: $fromMapId -> ${map.id}"
        markOutpostReachedIfNeeded()

        TileRenderer(terrainLayer).draw(map)
        rebuildRanchCompanions()
        updatePlayerView()
        camera.reset()
        camera.update(map, player.currentCell)
        applyCamera()
        requestSave()
    }

    private fun markOutpostReachedIfNeeded() {
        if (map.id == T1MapProgress.OUTPOST_MAP_ID && !reachedT1Outpost) {
            reachedT1Outpost = true
        }
    }

    private fun warpToRanch() {
        if (map.id != T1MapProgress.RANCH_MAP_ID) {
            ranchReturnMapId = map.id
            ranchReturnCell = player.currentCell
        }
        companionPanelOpen = false
        companionPanelLayer.visible = false
        selectedCompanionId = null
        companionPanelMessage = ""
        map = MapData.get(T1MapProgress.RANCH_MAP_ID)
        player.warpTo(T1MapProgress.RANCH_SPAWN)
        tappedCell = null
        handoffCell = null
        player.clearMovement()
        lastFacilityAction = "warp ranch"
        refreshMapAfterWarp()
        requestSave()
    }

    private fun returnFromRanch() {
        val targetMap = ranchReturnMapId ?: T1MapProgress.FIRST_TOWN_MAP_ID
        val targetCell = ranchReturnCell ?: T1MapProgress.DEFAULT_SPAWN
        map = MapData.get(targetMap)
        player.warpTo(targetCell)
        ranchReturnMapId = null
        ranchReturnCell = null
        tappedCell = null
        handoffCell = null
        player.clearMovement()
        lastFacilityAction = "return ranch"
        refreshMapAfterWarp()
        requestSave()
    }

    private fun refreshMapAfterWarp() {
        TileRenderer(terrainLayer).draw(map)
        rebuildRanchCompanions()
        updatePlayerView()
        camera.reset()
        camera.update(map, player.currentCell)
        applyCamera()
    }

    private fun drawEncounterPanel() {
        encounterLayer.visible = false
        encounterLayer.solidRect(viewportWidth, viewportHeight, RGBA(7, 12, 28, 255))
        encounterLayer.solidRect(2, 2, RGBA(176, 205, 240, 180)) { position(64, 72) }
        encounterLayer.solidRect(2, 2, RGBA(176, 205, 240, 140)) { position(286, 110) }
        encounterLayer.solidRect(1, 1, RGBA(210, 226, 255, 150)) { position(214, 34) }
        encounterLayer.solidRect(1, 1, RGBA(210, 226, 255, 130)) { position(104, 248) }
        encounterLayer.solidRect(312, 132, RGBA(13, 22, 45, 245)) {
            position(24, 34)
        }
        encounterLayer.solidRect(312, 2, RGBA(70, 112, 145, 160)) {
            position(24, 164)
        }
        encounterLayer.solidRect(312, 178, RGBA(18, 25, 38, 238)) {
            position(24, 184)
        }
        // Future art/HP gauge area: enemy and active companion sprites can be placed above the log/command bands.
        encounterLayer.solidRect(312, 86, RGBA(10, 15, 24, 238)) {
            position(24, 378)
        }
        encounterLayer.solidRect(312, 2, RGBA(56, 88, 116, 150)) {
            position(24, 378)
        }
        encounterLayer.solidRect(312, 118, RGBA(14, 21, 34, 245)) {
            position(24, 486)
        }
        encounterLayer.solidRect(312, 2, RGBA(56, 88, 116, 150)) {
            position(24, 486)
        }
        encounterTitleText = encounterLayer.text(
            "",
            textSize = 19.0,
            color = Colors.WHITE,
            font = StarSagaFonts.font,
        ) {
            position(BATTLE_TITLE_X, BATTLE_TITLE_Y)
        }
        encounterStatsText = encounterLayer.text(
            "",
            textSize = 14.0,
            color = Colors.WHITE,
            font = StarSagaFonts.font,
        ) {
            position(BATTLE_STATS_X, BATTLE_STATS_Y)
        }
        val fightButton = encounterLayer.createUiButton(
            x = FIGHT_BUTTON_X,
            y = BUTTON_Y,
            width = BUTTON_WIDTH,
            height = BUTTON_HEIGHT,
            label = "戦う",
            background = COMMAND_BUTTON_COLOR,
            fontSize = 18.0,
            textX = FIGHT_BUTTON_X + 30,
        )
        fightButtonView = fightButton.background
        fightLabelText = fightButton.label
        val skillButton = encounterLayer.createUiButton(
            x = SKILL_BUTTON_X,
            y = SKILL_BUTTON_Y,
            width = SKILL_BUTTON_WIDTH,
            height = BUTTON_HEIGHT,
            label = "スキル",
            background = COMMAND_BUTTON_COLOR,
            fontSize = 17.0,
            textX = SKILL_BUTTON_X + 18,
        )
        skillButtonView = skillButton.background
        skillLabelText = skillButton.label
        skillButton.setVisible(false)
        val itemButton = encounterLayer.createUiButton(
            x = ITEM_BUTTON_X,
            y = ITEM_BUTTON_Y,
            width = ITEM_BUTTON_WIDTH,
            height = BUTTON_HEIGHT,
            label = "道具",
            background = COMMAND_BUTTON_COLOR,
            fontSize = 17.0,
            textX = ITEM_BUTTON_X + 26,
        )
        itemButtonView = itemButton.background
        itemLabelText = itemButton.label
        itemButton.setVisible(false)
        val runButton = encounterLayer.createUiButton(
            x = RUN_BUTTON_X,
            y = BUTTON_Y,
            width = BUTTON_WIDTH,
            height = BUTTON_HEIGHT,
            label = "逃げる",
            background = COMMAND_ESCAPE_BUTTON_COLOR,
            fontSize = 18.0,
            textX = RUN_BUTTON_X + 24,
        )
        runButtonView = runButton.background
        runLabelText = runButton.label
        val autoButton = encounterLayer.createUiButton(
            x = AUTO_BUTTON_X,
            y = AUTO_BUTTON_Y,
            width = AUTO_BUTTON_W,
            height = AUTO_BUTTON_H,
            label = "AUTO",
            background = RGBA(70, 94, 112, 255),
            fontSize = 13.0,
            textX = AUTO_BUTTON_X + 18,
        )
        autoButtonView = autoButton.background
        autoLabelText = autoButton.label
        autoButton.setVisible(false)
        okLabelText = encounterLayer.createUiText(
            label = "OK",
            x = FIGHT_BUTTON_X + 42,
            y = centeredButtonTextY(BUTTON_Y, BUTTON_HEIGHT, 18.0),
            textSize = 18.0,
            color = Colors.WHITE,
        ).apply {
            visible = false
        }
        val row1Button = encounterLayer.createUiButton(ROW_BUTTON_X, ROW1_Y, ROW_BUTTON_W, ROW_BUTTON_H, "", RGBA(58, 88, 145, 255), fontSize = 15.0)
        row1ButtonView = row1Button.background
        row1LabelText = row1Button.label
        row1Button.setVisible(false)
        val row2Button = encounterLayer.createUiButton(ROW_BUTTON_X, ROW2_Y, ROW_BUTTON_W, ROW_BUTTON_H, "", RGBA(58, 88, 145, 255), fontSize = 15.0)
        row2ButtonView = row2Button.background
        row2LabelText = row2Button.label
        row2Button.setVisible(false)
        val row3Button = encounterLayer.createUiButton(ROW_BUTTON_X, ROW3_Y, ROW_BUTTON_W, ROW_BUTTON_H, "", RGBA(58, 88, 145, 255), fontSize = 15.0)
        row3ButtonView = row3Button.background
        row3LabelText = row3Button.label
        row3Button.setVisible(false)
        val rowBackButton = encounterLayer.createUiButton(
            x = ROW_BACK_X,
            y = ROW_BACK_Y,
            width = ROW_BACK_W,
            height = ROW_BUTTON_H,
            label = "戻る",
            background = RGBA(95, 105, 122, 255),
            fontSize = 16.0,
            textX = ROW_BACK_X + 30,
        )
        rowBackButtonView = rowBackButton.background
        rowBackLabelText = rowBackButton.label
        rowBackButton.setVisible(false)
        shopLayer = encounterLayer.container {
            visible = false
        }
        t1ClearLayer = encounterLayer.container {
            visible = false
        }
    }

    private fun showEncounterPanel(creature: CreatureData) {
        hideShopPanel()
        hideT1ClearLayer()
        pendingAutoStartSeconds = 0.0
        resultAutoCloseSeconds = 0.0
        stopAutoBattle()
        battleState = null
        pendingBefriendOffer = null
        encounterUiState = EncounterUiState.ChooseAction
        setFieldUiVisible(false)
        encounterTitleText?.text = if (currentEncounterFirstSeen && !activeEncounterIsBoss) {
            "はじめて ${creature.name} に出会った！"
        } else {
            "${creature.name}が現れた！"
        }
        encounterStatsText?.text =
            buildString {
                if (currentEncounterFirstSeen && !activeEncounterIsBoss) {
                    appendLine("星草の中から ${creature.name} が現れた")
                }
                appendLine("HP: ${creature.hp}")
                appendLine("ATK: ${creature.attack} / DEF: ${creature.defense} / MP: ${creature.mp}")
                recruitProgressLabel(creature)?.let { append(it) }
            }
        setActionButtonsVisible(true)
        updateAutoButtonVisible()
        encounterLayer.visible = true
        if (shouldAutoStartCurrentEncounter()) {
            pendingAutoStartSeconds = AUTO_START_DELAY_SECONDS
        }
    }

    private fun recruitProgressLabel(creature: CreatureData): String? {
        if (activeEncounterIsBoss) return null
        return if (creature.id in befriendedCreatureIds) {
            "仲間済み"
        } else {
            val progress = recruitProgressByCreatureId.getOrDefault(creature.id, 0).coerceIn(0, RECRUIT_THRESHOLD)
            "仲間化進行 $progress/$RECRUIT_THRESHOLD"
        }
    }

    private fun closeEncounterPanel() {
        hideShopPanel()
        hideT1ClearLayer()
        pendingAutoStartSeconds = 0.0
        resultAutoCloseSeconds = 0.0
        stopAutoBattle()
        activeEncounter = null
        activeEncounterIsBoss = false
        currentEncounterFirstSeen = false
        battleState = null
        pendingBefriendOffer = null
        selectedSkillActorId = null
        encounterUiState = EncounterUiState.Hidden
        encounterLayer.visible = false
        setFieldUiVisible(true)
        inputLockedUntilRelease = true
        inputLockSeconds = 0.15
        wasPressed = true
        tappedCell = null
        handoffCell = null
        player.clearMovement()
        requestSave()
    }

    private fun drawCompanionButton() {
        companionButtonLayer.createUiButton(
            x = COMPANION_BUTTON_X,
            y = COMPANION_BUTTON_Y,
            width = COMPANION_BUTTON_W,
            height = COMPANION_BUTTON_H,
            label = "仲間",
            background = RGBA(34, 72, 78, 235),
            fontSize = 16.0,
            textX = COMPANION_BUTTON_X + 14,
        )
        if (debugVisible) {
            companionButtonLayer.createUiButton(
                x = HP1_BUTTON_X,
                y = HP1_BUTTON_Y,
                width = COMPANION_BUTTON_W,
                height = COMPANION_BUTTON_H,
                label = "HP1",
                background = RGBA(130, 58, 68, 220),
                fontSize = 15.0,
                textX = HP1_BUTTON_X + 15,
            )
        }
    }

    private fun drawCompanionPanel() {
        val panel = COMPANION_PANEL_LAYOUT
        companionPanelLayer.visible = false
        companionPanelLayer.solidRect(panel.width + 4, panel.height + 4, RGBA(0, 0, 0, 225)) {
            position(panel.x - 2, panel.y - 2)
        }
        companionPanelLayer.solidRect(panel.width, panel.height, RGBA(18, 25, 38, 248)) {
            position(panel.x, panel.y)
        }
        companionPanelTitleText = companionPanelLayer.createUiText("パーティー", 42.0, panel.titleY, textSize = 20.0)
        companionListText = companionPanelLayer.createUiText("", 42.0, panel.contentTop, textSize = 12.0)
        companionGridLayer = companionPanelLayer.container()
        val prevButton = companionPanelLayer.createUiButton(
            x = COMPANION_PREV_X,
            y = COMPANION_NAV_Y,
            width = COMPANION_NAV_W,
            height = COMPANION_NAV_H,
            label = "前へ",
            background = RGBA(70, 90, 130, 255),
            fontSize = 15.0,
            textX = COMPANION_PREV_X + 22,
        )
        companionPrevButtonView = prevButton.background
        companionPrevLabelText = prevButton.label
        val nextButton = companionPanelLayer.createUiButton(
            x = COMPANION_NEXT_X,
            y = COMPANION_NAV_Y,
            width = COMPANION_NAV_W,
            height = COMPANION_NAV_H,
            label = "次へ",
            background = RGBA(70, 90, 130, 255),
            fontSize = 15.0,
            textX = COMPANION_NEXT_X + 22,
        )
        companionNextButtonView = nextButton.background
        companionNextLabelText = nextButton.label
        companionPanelLayer.createUiButton(
            x = COMPANION_CLOSE_X,
            y = COMPANION_CLOSE_Y,
            width = COMPANION_CLOSE_W,
            height = COMPANION_CLOSE_H,
            label = "閉じる",
            background = RGBA(95, 105, 122, 255),
            fontSize = 18.0,
            textX = COMPANION_CLOSE_X + 26,
        )
    }

    private fun openPartyPanel() {
        companionPanelOpen = true
        companionPanelMode = CompanionPanelMode.PartyView
        companionPanelMessage = ""
        selectedCompanionId = null
        tappedCell = null
        handoffCell = null
        player.clearMovement()
        updateCompanionPanel()
        companionPanelLayer.visible = true
    }

    private fun openRanchPanel() {
        applyTrainingExp(showMessage = true)
        companionPanelOpen = true
        companionPanelMode = CompanionPanelMode.RanchView
        companionPage = companionPage.coerceIn(0, companionMaxPage())
        if (selectedCompanionId == null) selectedCompanionId = companions.firstOrNull()?.instanceId
        tappedCell = null
        handoffCell = null
        player.clearMovement()
        updateCompanionPanel()
        companionPanelLayer.visible = true
    }

    private fun closeCompanionPanel() {
        companionPanelOpen = false
        companionPanelLayer.visible = false
        inputLockedUntilRelease = true
        inputLockSeconds = 0.15
        wasPressed = true
        tappedCell = null
        handoffCell = null
        player.clearMovement()
        requestSave()
    }

    private fun handleCompanionPanelTap(x: Double, y: Double) {
        if (companionPanelMode == CompanionPanelMode.PartyView) {
            if (isInside(x, y, PARTY_RANCH_BUTTON_X, PARTY_RANCH_BUTTON_Y, PARTY_RANCH_BUTTON_W, PARTY_RANCH_BUTTON_H)) {
                warpToRanch()
                consumeUiPointer()
                return
            }
            if (isInside(x, y, COMPANION_CLOSE_X, COMPANION_CLOSE_Y, COMPANION_CLOSE_W, COMPANION_CLOSE_H)) {
                closeCompanionPanel()
                consumeUiPointer()
            }
            return
        }
        val hasPages = companionMaxPage() > 0
        if (hasPages && isInside(x, y, COMPANION_PREV_X, COMPANION_NAV_Y, COMPANION_NAV_W, COMPANION_NAV_H)) {
            companionPage = (companionPage - 1).coerceAtLeast(0)
            updateCompanionPanel()
            consumeUiPointer()
            return
        }
        if (hasPages && isInside(x, y, COMPANION_NEXT_X, COMPANION_NAV_Y, COMPANION_NAV_W, COMPANION_NAV_H)) {
            companionPage = (companionPage + 1).coerceAtMost(companionMaxPage())
            updateCompanionPanel()
            consumeUiPointer()
            return
        }
        companionAtPanelPoint(x, y)?.let { companion ->
            selectedCompanionId = companion.instanceId
            companionPanelMessage = ""
            updateCompanionPanel()
            consumeUiPointer()
            return
        }
        if (selectedCompanionId != null && isInside(x, y, COMPANION_LEAD_X, COMPANION_ACTION_Y, COMPANION_ACTION_W, COMPANION_ACTION_H)) {
            moveSelectedCompanionToLead()
            consumeUiPointer()
            return
        }
        if (selectedCompanionId != null && isInside(x, y, COMPANION_TOGGLE_X, COMPANION_TOGGLE_Y, COMPANION_ACTION_W, COMPANION_ACTION_H)) {
            toggleSelectedCompanionParty()
            consumeUiPointer()
            return
        }
        if (selectedCompanionId != null && isInside(x, y, COMPANION_TRAINING_X, COMPANION_TRAINING_Y, COMPANION_TRAINING_W, COMPANION_ACTION_H)) {
            toggleSelectedCompanionTraining()
            consumeUiPointer()
            return
        }
        if (isInside(x, y, COMPANION_CLOSE_X, COMPANION_CLOSE_Y, COMPANION_CLOSE_W, COMPANION_CLOSE_H)) {
            closeCompanionPanel()
            consumeUiPointer()
        }
    }

    private suspend fun loadGame() {
        val loaded = StarSagaSession.saveManager?.load()
        saveLoaded = loaded != null
        if (loaded == null) {
            saveStatus = "empty"
            return
        }
        val migrated = SaveMigration.migrate(loaded)
        map = MapData.get(migrated.currentMapId)
        player.warpTo(GridCell(migrated.playerCol, migrated.playerRow))
        companions.clear()
        companions += migrated.companions
        gold = migrated.gold
        itemCounts.clear()
        itemCounts += migrated.itemCounts.filterValues { it > 0 }
        partyCompanionIds.clear()
        partyCompanionIds += migrated.partyCompanionIds
        activeCompanionIds.clear()
        activeCompanionIds += migrated.activeCompanionIds
        trainingCompanionIds.clear()
        trainingCompanionIds += migrated.trainingCompanionIds
        t1BossCleared = migrated.t1BossCleared
        t1ClearAcknowledged = migrated.t1ClearAcknowledged
        reachedT1Outpost = migrated.reachedT1Outpost || migrated.currentMapId == T1MapProgress.OUTPOST_MAP_ID
        t1OutpostWarpUnlocked = migrated.t1OutpostWarpUnlocked
        seenCreatureIds.clear()
        seenCreatureIds += migrated.seenCreatureIds
        seenCreatureIds += migrated.befriendedCreatureIds
        befriendedCreatureIds.clear()
        befriendedCreatureIds += migrated.befriendedCreatureIds
        defeatCountByCreatureId.clear()
        defeatCountByCreatureId += migrated.defeatCountByCreatureId
        recruitProgressByCreatureId.clear()
        recruitProgressByCreatureId += migrated.recruitProgressByCreatureId
            .mapValues { (_, progress) -> progress.coerceIn(0, RECRUIT_THRESHOLD) }
        tutorialSeen = migrated.tutorialSeen
        lastSavedSec = migrated.lastSavedSec
        lastTrainingAtEpochMillis = migrated.lastTrainingAtEpochMillis
        normalizePartyState()
        clampCompanionVitals()
        applyTrainingExp(showMessage = false)
        saveStatus = "loaded"
    }

    private fun requestSave() {
        if (!::saveScope.isInitialized) return
        saveScope.launch {
            saveGame()
        }
    }

    private suspend fun saveGame() {
        val manager = StarSagaSession.saveManager ?: run {
            saveStatus = "no manager"
            return
        }
        val saved = manager.save(toSaveData())
        lastSavedSec = saved.lastSavedSec
        savedCount += 1
        saveStatus = "saved"
    }

    private fun toSaveData(): RpgSaveData =
        RpgSaveData(
            currentMapId = map.id,
            playerCol = player.currentCell.col,
            playerRow = player.currentCell.row,
            t1MapRevision = T1MapProgress.CURRENT_REVISION,
            currentT1AreaId = T1MapProgress.areaIdFor(map.id, player.currentCell),
            gold = gold,
            itemCounts = itemCounts.toMap(),
            companions = companions.toList(),
            partyCompanionIds = partyCompanionIds.toList(),
            activeCompanionIds = activeCompanionIds.toList(),
            trainingCompanionIds = trainingCompanionIds.toList(),
            lastTrainingAtEpochMillis = lastTrainingAtEpochMillis,
            t1BossCleared = t1BossCleared,
            t1ClearAcknowledged = t1ClearAcknowledged,
            reachedT1Outpost = reachedT1Outpost || map.id == T1MapProgress.OUTPOST_MAP_ID,
            t1OutpostWarpUnlocked = t1OutpostWarpUnlocked,
            seenCreatureIds = seenCreatureIds.toSet(),
            befriendedCreatureIds = befriendedCreatureIds.toSet(),
            defeatCountByCreatureId = defeatCountByCreatureId.toMap(),
            recruitProgressByCreatureId = recruitProgressByCreatureId.toMap(),
            tutorialSeen = tutorialSeen,
            lastSavedSec = lastSavedSec,
        )

    @Suppress("unused")
    private fun deleteSaveAndReset() {
        if (!::saveScope.isInitialized) return
        saveScope.launch {
            StarSagaSession.saveManager?.delete()
            resetGameState()
            saveStatus = "deleted"
        }
    }

    private fun resetGameState() {
        map = MapData.get(T1MapProgress.DEFAULT_MAP_ID)
        player.warpTo(T1MapProgress.DEFAULT_SPAWN)
        companions.clear()
        partyCompanionIds.clear()
        activeCompanionIds.clear()
        trainingCompanionIds.clear()
        seenCreatureIds.clear()
        befriendedCreatureIds.clear()
        defeatCountByCreatureId.clear()
        recruitProgressByCreatureId.clear()
        gold = 0
        itemCounts.clear()
        activeEncounter = null
        activeEncounterIsBoss = false
        currentEncounterFirstSeen = false
        battleState = null
        pendingBefriendOffer = null
        selectedSkillActorId = null
        pendingAutoStartSeconds = 0.0
        resultAutoCloseSeconds = 0.0
        encounterUiState = EncounterUiState.Hidden
        companionPanelOpen = false
        encounterLayer.visible = false
        companionPanelLayer.visible = false
        tappedCell = null
        handoffCell = null
        lastTransition = null
        lastEncounterCreatureName = null
        lastEncounterRoll = null
        lastBefriendChance = null
        lastBefriendRoll = null
        lastFacilityAction = null
        saveLoaded = false
        tutorialSeen = false
        t1BossCleared = false
        t1ClearAcknowledged = false
        reachedT1Outpost = false
        t1OutpostWarpUnlocked = false
        pendingWarpTarget = null
        savedCount = 0
        lastSavedSec = 0L
        lastTrainingAtEpochMillis = 0L
        ensureStarterCompanion()
        normalizePartyState()
        clampCompanionVitals()
        TileRenderer(terrainLayer).draw(map)
        rebuildRanchCompanions()
        updatePlayerView()
        camera.reset()
        camera.update(map, player.currentCell)
        applyCamera()
    }

    private fun updateCompanionPanel() {
        companionGridLayer.removeChildren()
        companionPanelTitleText?.text = if (companionPanelMode == CompanionPanelMode.RanchView) "スター牧場" else "パーティー"
        if (companions.isEmpty()) {
            setCompanionPageButtonsVisible(false)
            selectedCompanionId = null
            companionListText?.text = "まだ仲間はいません\n草むらで出会ったキャラを仲間にしよう"
            return
        }
        if (companionPanelMode == CompanionPanelMode.PartyView) {
            drawPartyView()
            return
        }
        companionPage = companionPage.coerceIn(0, companionMaxPage())
        if (companions.none { it.instanceId == selectedCompanionId }) {
            selectedCompanionId = companions.firstOrNull()?.instanceId
        }
        setCompanionPageButtonsVisible(companionMaxPage() > 0)
        companionListText?.text =
            "全仲間 ${companions.size}体 / Party ${partyCompanionIds.size}/$MAX_PARTY_SIZE / 育成所 ${trainingCompanionIds.size}/$MAX_TRAINING_SIZE\n" +
                "${ObjectiveResolver.t1StatusText(befriendedCreatureIds)}  P${companionPage + 1}/${companionMaxPage() + 1}"

        drawActiveCompanionIcons()
        drawSelectedCompanionDetail()
        drawCompanionGrid()
    }

    private fun drawPartyView() {
        setCompanionPageButtonsVisible(false)
        companionListText?.text =
            "Active ${activeCompanionIds.size}/$MAX_ACTIVE_SIZE  Party ${partyCompanionIds.size}/$MAX_PARTY_SIZE\n" +
                ObjectiveResolver.t1StatusText(befriendedCreatureIds)
        partyCompanionIds.take(MAX_PARTY_SIZE).forEachIndexed { index, instanceId ->
            val companion = companions.firstOrNull { it.instanceId == instanceId } ?: return@forEachIndexed
            val creature = CreatureDatabase.get(companion.creatureId) ?: return@forEachIndexed
            val marker = if (instanceId in activeCompanionIds) " [ACTIVE]" else ""
            val rowY = 190 + index * 30
            drawCreatureIcon(companionGridLayer, companion.creatureId, 48, rowY - 6, 24)
            companionGridLayer.text("${index + 1}. ${creature.name} Lv.${companion.level}$marker", textSize = 12.0, color = Colors.WHITE, font = StarSagaFonts.font) {
                position(82, rowY)
            }
        }
        companionGridLayer.text("入れ替えは拠点の牧場で行えます", textSize = 11.0, color = RGBA(226, 214, 158, 255), font = StarSagaFonts.font) {
            position(52, 396)
        }
        companionGridLayer.createUiButton(
            x = PARTY_RANCH_BUTTON_X,
            y = PARTY_RANCH_BUTTON_Y,
            width = PARTY_RANCH_BUTTON_W,
            height = PARTY_RANCH_BUTTON_H,
            label = "牧場へ行く",
            background = RGBA(58, 88, 145, 255),
            fontSize = 15.0,
            textX = PARTY_RANCH_BUTTON_X + 26,
        )
    }

    private fun companionMaxPage(): Int =
        if (companions.isEmpty()) 0 else (companions.size - 1) / COMPANION_PAGE_SIZE

    private fun companionAtPanelPoint(x: Double, y: Double): CompanionState? {
        val pageStart = companionPage * COMPANION_PAGE_SIZE
        return companions.drop(pageStart).take(COMPANION_PAGE_SIZE).firstOrNull { companion ->
            val index = companions.indexOf(companion) - pageStart
            val col = index % COMPANION_GRID_COLUMNS
            val row = index / COMPANION_GRID_COLUMNS
            val cellX = COMPANION_GRID_X + col * COMPANION_GRID_CELL_W - 5
            val cellY = COMPANION_GRID_Y + row * COMPANION_GRID_CELL_H - 5
            isInside(x, y, cellX.toDouble(), cellY.toDouble(), COMPANION_GRID_HIT_W, COMPANION_GRID_HIT_H)
        }
    }

    private fun setCompanionPageButtonsVisible(visible: Boolean) {
        companionPrevButtonView?.visible = visible
        companionPrevLabelText?.visible = visible
        companionNextButtonView?.visible = visible
        companionNextLabelText?.visible = visible
    }

    private fun drawActiveCompanionIcons() {
        companionGridLayer.text("ACTIVE", textSize = 10.0, color = RGBA(180, 210, 220, 230), font = StarSagaFonts.font) {
            position(46, 190)
        }
        activeCompanionIds.take(MAX_ACTIVE_SIZE).forEachIndexed { index, instanceId ->
            val companion = companions.firstOrNull { it.instanceId == instanceId } ?: return@forEachIndexed
            val x = 82 + index * 76
            val y = 184
            drawCreatureIcon(companionGridLayer, companion.creatureId, x, y, 34)
        }
    }

    private fun drawSelectedCompanionDetail() {
        val companion = companions.firstOrNull { it.instanceId == selectedCompanionId } ?: return
        val creature = CreatureDatabase.get(companion.creatureId) ?: return
        val maxHp = Leveling.maxHp(creature.hp, companion.level)
        val maxMp = Leveling.maxMp(creature.mp, companion.level)
        val nextExp = Leveling.requiredExpForNextLevel(companion.level)
        val marker = buildString {
            append(
                when (companion.instanceId) {
                    in activeCompanionIds -> " [ACTIVE]"
                    in partyCompanionIds -> " [PARTY]"
                    else -> " [牧場]"
                },
            )
            if (companion.instanceId in trainingCompanionIds) append("[育成中]")
        }
        val skills = companion.skillIds.mapNotNull { SkillDatabase.get(it)?.name }
            .joinToString("/")
            .ifEmpty { "-" }
        companionGridLayer.solidRect(172, 82, RGBA(8, 13, 24, 190)) { position(42, 226) }
        companionGridLayer.text("${creature.name} Lv.${companion.level}$marker", textSize = 11.0, color = Colors.WHITE, font = StarSagaFonts.font) {
            position(48, 230)
        }
        companionGridLayer.text("HP ${companion.hp}/$maxHp MP ${companion.mp}/$maxMp", textSize = 10.0, color = RGBA(210, 230, 236, 255), font = StarSagaFonts.font) {
            position(48, 246)
        }
        companionGridLayer.text("EXP ${companion.exp}/$nextExp Skill:${shortSkillText(skills)}", textSize = 10.0, color = RGBA(226, 214, 158, 255), font = StarSagaFonts.font) {
            position(48, 262)
        }
        companionGridLayer.text(BattleBalance.roleEffectText(creature.role), textSize = 9.0, color = RGBA(190, 220, 245, 255), font = StarSagaFonts.font) {
            position(48, 278)
        }
        if (companionPanelMessage.isNotEmpty()) {
            companionGridLayer.text(companionPanelMessage, textSize = 10.0, color = RGBA(255, 224, 150, 255), font = StarSagaFonts.font) {
                position(48, 292)
            }
        }
        companionGridLayer.createUiButton(
            x = COMPANION_LEAD_X,
            y = COMPANION_ACTION_Y,
            width = COMPANION_ACTION_W,
            height = COMPANION_ACTION_H,
            label = "先頭へ",
            background = RGBA(58, 88, 145, 255),
            fontSize = 13.0,
            textX = COMPANION_LEAD_X + 16,
        )
        val toggleLabel = if (companion.instanceId in partyCompanionIds) "外す" else "加入"
        companionGridLayer.createUiButton(
            x = COMPANION_TOGGLE_X,
            y = COMPANION_TOGGLE_Y,
            width = COMPANION_ACTION_W,
            height = COMPANION_ACTION_H,
            label = toggleLabel,
            background = RGBA(70, 94, 112, 255),
            fontSize = 13.0,
            textX = COMPANION_TOGGLE_X + 24,
        )
        val trainingLabel = if (companion.instanceId in trainingCompanionIds) "育成所から外す" else "育成所へ"
        val trainingTextX = COMPANION_TRAINING_X + (if (trainingLabel == "育成所へ") 15.0 else 3.0)
        companionGridLayer.createUiButton(
            x = COMPANION_TRAINING_X,
            y = COMPANION_TRAINING_Y,
            width = COMPANION_TRAINING_W,
            height = COMPANION_ACTION_H,
            label = trainingLabel,
            background = RGBA(82, 106, 70, 255),
            fontSize = 12.0,
            textX = trainingTextX,
        )
    }

    private fun drawCompanionGrid() {
        val pageStart = companionPage * COMPANION_PAGE_SIZE
        companions.drop(pageStart).take(COMPANION_PAGE_SIZE).forEachIndexed { index, companion ->
            val creature = CreatureDatabase.get(companion.creatureId) ?: return@forEachIndexed
            val col = index % COMPANION_GRID_COLUMNS
            val row = index / COMPANION_GRID_COLUMNS
            val cellX = COMPANION_GRID_X + col * COMPANION_GRID_CELL_W
            val cellY = COMPANION_GRID_Y + row * COMPANION_GRID_CELL_H
            val border = when (companion.instanceId) {
                selectedCompanionId -> RGBA(240, 218, 112, 240)
                in activeCompanionIds -> RGBA(112, 188, 210, 210)
                in partyCompanionIds -> RGBA(90, 120, 160, 180)
                else -> RGBA(48, 58, 76, 170)
            }
            companionGridLayer.solidRect(70, 58, RGBA(9, 15, 26, 185)) { position(cellX - 5, cellY - 5) }
            companionGridLayer.solidRect(70, 2, border) { position(cellX - 5, cellY - 5) }
            companionGridLayer.solidRect(2, 58, border) { position(cellX - 5, cellY - 5) }
            drawCreatureIcon(companionGridLayer, companion.creatureId, cellX + 14, cellY, 32)
            companionGridLayer.text(shortName(creature.name), textSize = 10.0, color = Colors.WHITE, font = StarSagaFonts.font) {
                position(cellX, cellY + 34)
            }
            companionGridLayer.text("Lv${companion.level}", textSize = 9.0, color = RGBA(226, 214, 158, 255), font = StarSagaFonts.font) {
                position(cellX, cellY + 47)
            }
            val marker = when (companion.instanceId) {
                in activeCompanionIds -> "A"
                in partyCompanionIds -> "P"
                in trainingCompanionIds -> "T"
                else -> ""
            }
            if (marker.isNotEmpty()) {
                companionGridLayer.solidRect(14, 14, border) { position(cellX + 48, cellY + 42) }
                companionGridLayer.text(marker, textSize = 9.0, color = Colors.WHITE, font = StarSagaFonts.font) {
                    position(cellX + 52, cellY + 42)
                }
            }
        }
    }

    private fun drawCreatureIcon(parent: Container, creatureId: Int, x: Int, y: Int, size: Int) {
        val bitmap = CreatureImages.get(creatureId)
        if (bitmap != null) {
            val sz = size.toDouble()
            parent.image(bitmap) {
                smoothing = false
                width = sz
                height = sz
                position(x.toDouble(), y.toDouble())
            }
            return
        }
        val creature = CreatureDatabase.get(creatureId)
        val base = colorForCreatureRole(creature?.role)
        parent.solidRect(size, size, RGBA(8, 12, 20, 230)) { position(x, y) }
        parent.solidRect(size - 4, size - 4, base) { position(x + 2, y + 2) }
        parent.solidRect(size - 12, size / 2, brighten(base)) { position(x + 6, y + 5) }
        parent.solidRect(4, 4, RGBA(20, 28, 42, 255)) { position(x + 10, y + 11) }
        parent.solidRect(4, 4, RGBA(20, 28, 42, 255)) { position(x + size - 14, y + 11) }
        parent.solidRect(size - 16, 4, RGBA(245, 231, 160, 230)) { position(x + 8, y + size - 10) }
    }

    private fun colorForCreatureRole(role: CreatureRole?): RGBA = when (role) {
        CreatureRole.ATCK -> RGBA(164, 82, 76, 255)
        CreatureRole.DEFN -> RGBA(76, 116, 170, 255)
        CreatureRole.AREA -> RGBA(124, 86, 170, 255)
        CreatureRole.HEAL -> RGBA(68, 148, 112, 255)
        CreatureRole.LUCK -> RGBA(184, 156, 72, 255)
        else -> RGBA(108, 126, 148, 255)
    }

    private fun brighten(color: RGBA): RGBA =
        RGBA(
            (color.r + 42).coerceAtMost(255),
            (color.g + 42).coerceAtMost(255),
            (color.b + 42).coerceAtMost(255),
            color.a,
        )

    private fun shortName(name: String): String =
        if (name.length <= 4) name else name.take(4)

    private fun shortSkillText(text: String): String =
        if (text.length <= 8) text else text.take(8)

    private fun moveSelectedCompanionToLead() {
        val selectedId = selectedCompanionId ?: return
        val companion = companions.firstOrNull { it.instanceId == selectedId } ?: return
        val creatureName = CreatureDatabase.get(companion.creatureId)?.name ?: "仲間"
        if (selectedId !in partyCompanionIds) {
            companionPanelMessage = "まず加入してください"
            updateCompanionPanel()
            return
        }
        partyCompanionIds.remove(selectedId)
        partyCompanionIds.add(0, selectedId)
        syncActiveCompanions()
        companionPanelMessage = "${creatureName}を先頭にした"
        rebuildRanchCompanions()
        requestSave()
        updateCompanionPanel()
    }

    private fun toggleSelectedCompanionParty() {
        val selectedId = selectedCompanionId ?: return
        val companion = companions.firstOrNull { it.instanceId == selectedId } ?: return
        val creatureName = CreatureDatabase.get(companion.creatureId)?.name ?: "仲間"
        if (selectedId in partyCompanionIds) {
            if (partyCompanionIds.size <= 1) {
                companionPanelMessage = "パーティーを空にはできません"
                updateCompanionPanel()
                return
            }
            partyCompanionIds.remove(selectedId)
            syncActiveCompanions()
            companionPanelMessage = "${creatureName}を牧場で待機"
        } else {
            if (partyCompanionIds.size >= MAX_PARTY_SIZE) {
                companionPanelMessage = "これ以上入れられません"
                updateCompanionPanel()
                return
            }
            partyCompanionIds += selectedId
            syncActiveCompanions()
            companionPanelMessage = "${creatureName}を加えた"
        }
        rebuildRanchCompanions()
        requestSave()
        updateCompanionPanel()
    }

    private fun toggleSelectedCompanionTraining() {
        val selectedId = selectedCompanionId ?: return
        val companion = companions.firstOrNull { it.instanceId == selectedId } ?: return
        val creatureName = CreatureDatabase.get(companion.creatureId)?.name ?: "仲間"
        sanitizeTrainingCompanions()
        if (selectedId in trainingCompanionIds) {
            trainingCompanionIds.remove(selectedId)
            companionPanelMessage = "${creatureName}を育成所から外しました"
        } else {
            if (trainingCompanionIds.size >= MAX_TRAINING_SIZE) {
                companionPanelMessage = "育成枠がいっぱいです"
                updateCompanionPanel()
                return
            }
            if (lastTrainingAtEpochMillis <= 0L) {
                lastTrainingAtEpochMillis = currentEpochMillis()
            }
            trainingCompanionIds += selectedId
            companionPanelMessage = "${creatureName}を育成所に預けました"
        }
        requestSave()
        updateCompanionPanel()
    }

    private fun handleEncounterTap(x: Double, y: Double) {
        when (encounterUiState) {
            EncounterUiState.ChooseAction -> {
                if (isInside(x, y, AUTO_BUTTON_X, AUTO_BUTTON_Y, AUTO_BUTTON_W, AUTO_BUTTON_H)) {
                    pendingAutoStartSeconds = 0.0
                    startBattle()
                    startAutoBattle()
                    consumeUiPointer()
                    return
                }
                if (isInside(x, y, FIGHT_BUTTON_X, BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT)) {
                    pendingAutoStartSeconds = 0.0
                    startBattle()
                    consumeUiPointer()
                    return
                }
                if (isInside(x, y, RUN_BUTTON_X, BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT)) {
                    pendingAutoStartSeconds = 0.0
                    showEncounterResult("うまく逃げ切った！")
                    consumeUiPointer()
                }
            }
            EncounterUiState.Battle -> {
                if (isInside(x, y, AUTO_BUTTON_X, AUTO_BUTTON_Y, AUTO_BUTTON_W, AUTO_BUTTON_H)) {
                    toggleAutoBattle()
                    consumeUiPointer()
                    return
                }
                if (autoBattleEnabled) {
                    consumeUiPointer()
                    return
                }
                if (isInside(x, y, BATTLE_ATTACK_X, BATTLE_CMD_TOP_Y, BATTLE_CMD_W, BATTLE_CMD_H)) {
                    handleBattleAttack()
                    consumeUiPointer()
                    return
                }
                if (isInside(x, y, BATTLE_SKILL_X, BATTLE_CMD_TOP_Y, BATTLE_CMD_W, BATTLE_CMD_H)) {
                    showSkillActorSelect()
                    consumeUiPointer()
                    return
                }
                if (isInside(x, y, BATTLE_ITEM_X, BATTLE_CMD_BOTTOM_Y, BATTLE_CMD_W, BATTLE_CMD_H)) {
                    showItemSelect()
                    consumeUiPointer()
                    return
                }
                if (isInside(x, y, BATTLE_RUN_X, BATTLE_CMD_BOTTOM_Y, BATTLE_CMD_W, BATTLE_CMD_H)) {
                    handleBattleEscape()
                    consumeUiPointer()
                }
            }
            EncounterUiState.SkillActorSelect -> {
                val actors = currentSkillActors()
                if (isInside(x, y, ROW_BACK_X, ROW_BACK_Y, ROW_BACK_W, ROW_BUTTON_H)) {
                    selectedSkillActorId = null
                    encounterUiState = EncounterUiState.Battle
                    setBattleButtonsVisible()
                    updateBattlePanel()
                    consumeUiPointer()
                    return
                }
                if (actors.isNotEmpty() && isInside(x, y, ROW_BUTTON_X, ROW1_Y, ROW_BUTTON_W, ROW_BUTTON_H)) {
                    showSkillSelect(actors[0].instanceId)
                    consumeUiPointer()
                    return
                }
                if (actors.size >= 2 && isInside(x, y, ROW_BUTTON_X, ROW2_Y, ROW_BUTTON_W, ROW_BUTTON_H)) {
                    showSkillSelect(actors[1].instanceId)
                    consumeUiPointer()
                    return
                }
                if (actors.size >= 3 && isInside(x, y, ROW_BUTTON_X, ROW3_Y, ROW_BUTTON_W, ROW_BUTTON_H)) {
                    showSkillSelect(actors[2].instanceId)
                    consumeUiPointer()
                }
            }
            EncounterUiState.SkillSelect -> {
                val skills = currentBattleSkillOptions()
                if (isInside(x, y, ROW_BACK_X, ROW_BACK_Y, ROW_BACK_W, ROW_BUTTON_H)) {
                    showSkillActorSelect()
                    consumeUiPointer()
                    return
                }
                if (skills.isNotEmpty() && isInside(x, y, ROW_BUTTON_X, ROW1_Y, ROW_BUTTON_W, ROW_BUTTON_H)) {
                    handleBattleSkill(skills[0].skillId)
                    consumeUiPointer()
                    return
                }
                if (skills.size >= 2 && isInside(x, y, ROW_BUTTON_X, ROW2_Y, ROW_BUTTON_W, ROW_BUTTON_H)) {
                    handleBattleSkill(skills[1].skillId)
                    consumeUiPointer()
                }
            }
            EncounterUiState.ItemSelect -> {
                val items = battleItemOptions()
                if (isInside(x, y, ROW_BACK_X, ROW_BACK_Y, ROW_BACK_W, ROW_BUTTON_H)) {
                    encounterUiState = EncounterUiState.Battle
                    setBattleButtonsVisible()
                    updateBattlePanel()
                    consumeUiPointer()
                    return
                }
                if (items.isNotEmpty() && isInside(x, y, ROW_BUTTON_X, ROW1_Y, ROW_BUTTON_W, ROW_BUTTON_H)) {
                    handleBattleItem(items[0].itemId)
                    consumeUiPointer()
                    return
                }
                if (items.size >= 2 && isInside(x, y, ROW_BUTTON_X, ROW2_Y, ROW_BUTTON_W, ROW_BUTTON_H)) {
                    handleBattleItem(items[1].itemId)
                    consumeUiPointer()
                }
            }
            EncounterUiState.Shop -> {
                val shopItems = ItemDatabase.shopItems
                if (isInside(x, y, SHOP_CLOSE_X, SHOP_CLOSE_Y, SHOP_CLOSE_W, SHOP_BUTTON_H)) {
                    closeEncounterPanel()
                    consumeUiPointer()
                    return
                }
                if (isInside(x, y, SHOP_ROW_X, SHOP_ITEM1_Y, SHOP_ROW_W, SHOP_BUTTON_H)) {
                    buyItem(shopItems[0])
                    consumeUiPointer()
                    return
                }
                if (isInside(x, y, SHOP_ROW_X, SHOP_ITEM2_Y, SHOP_ROW_W, SHOP_BUTTON_H)) {
                    buyItem(shopItems[1])
                    consumeUiPointer()
                    return
                }
                if (debugVisible && isInside(x, y, SHOP_ROW_X, SHOP_DEBUG_Y, SHOP_ROW_W, SHOP_BUTTON_H)) {
                    addDebugGold()
                    consumeUiPointer()
                }
            }
            EncounterUiState.BefriendOffer -> {
                if (isInside(x, y, FIGHT_BUTTON_X, BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT)) {
                    val creatureName = activeEncounter?.name ?: "仲間"
                    val partyMessage = addCompanionIfNeeded(activeEncounter)
                    showEncounterResult(
                        if (partyMessage == "牧場がいっぱいです") partyMessage else "${creatureName}が仲間になった！",
                        partyMessage,
                    )
                    consumeUiPointer()
                    return
                }
                if (isInside(x, y, RUN_BUTTON_X, BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT)) {
                    showEncounterResult("${activeEncounter?.name ?: "相手"}を見送った")
                    consumeUiPointer()
                }
            }
            EncounterUiState.Result -> {
                if (isInside(x, y, OK_BUTTON_X, BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT)) {
                    resultAutoCloseSeconds = 0.0
                    val offer = pendingBefriendOffer
                    if (offer != null) {
                        pendingBefriendOffer = null
                        showBefriendOffer(offer)
                    } else {
                        closeEncounterPanel()
                    }
                    consumeUiPointer()
                }
            }
            EncounterUiState.T1Clear -> {
                if (isInside(x, y, OK_BUTTON_X, BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT)) {
                    t1ClearAcknowledged = true
                    requestSave()
                    closeEncounterPanel()
                    consumeUiPointer()
                }
            }
            EncounterUiState.BossConfirm -> {
                if (isInside(x, y, FIGHT_BUTTON_X, BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT)) {
                    startT1BossBattle()
                    consumeUiPointer()
                    return
                }
                if (isInside(x, y, RUN_BUTTON_X, BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT)) {
                    closeEncounterPanel()
                    consumeUiPointer()
                }
            }
            EncounterUiState.WarpConfirm -> {
                if (isInside(x, y, FIGHT_BUTTON_X, BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT)) {
                    executePendingWarp()
                    consumeUiPointer()
                    return
                }
                if (isInside(x, y, RUN_BUTTON_X, BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT)) {
                    pendingWarpTarget = null
                    closeEncounterPanel()
                    consumeUiPointer()
                }
            }
            EncounterUiState.Hidden -> Unit
        }
    }

    private fun resolveTemporaryVictory() {
        val creature = activeEncounter ?: return
        val expResult = grantVictoryExperience(creature)
        gold += creature.goldReward
        if (activeEncounterIsBoss) {
            val firstClear = !t1BossCleared
            t1BossCleared = true
            itemCounts[ItemDatabase.POTION] = itemCounts.getOrDefault(ItemDatabase.POTION, 0) + 1
            activeEncounterIsBoss = false
            returnToOutpostAfterBoss()
            requestSave()
            if (firstClear && !t1ClearAcknowledged) {
                showT1ClearOverlay(expResult.lines + "${creature.goldReward}Gを手に入れた" + "ポーションを1個手に入れた")
            } else {
                showEncounterResult(
                    "${creature.name}をしずめた！",
                    (expResult.lines + "${creature.goldReward}Gを手に入れた" + "ポーションを1個手に入れた" + "第1惑星クリア").takeLast(5).joinToString("\n"),
                )
            }
            return
        }
        val defeatCount = defeatCountByCreatureId.getOrDefault(creature.id, 0) + 1
        defeatCountByCreatureId[creature.id] = defeatCount
        val rewardLines = expResult.lines + "${creature.goldReward}Gを手に入れた"
        val recruitResult = progressRecruitmentOnDefeat(creature)
        val autoClose = shouldAutoCloseVictoryResult(
            leveledUp = expResult.leveledUp,
            recruitImportant = recruitResult.important,
        )
        val closeLine = if (autoClose) "自動で戻ります / OKで戻る" else "OKを押すとフィールドへ戻ります"
        requestSave()
        val detail = (rewardLines + recruitResult.line + closeLine).takeLast(5).joinToString("\n")
        if (recruitResult.joined) {
            showRecruitJoinedResult(creature, detail)
        } else {
            showEncounterResult(
                "${creature.name}を倒した！",
                detail,
                autoCloseSeconds = if (autoClose) AUTO_RESULT_CLOSE_SECONDS else null,
            )
        }
    }

    private fun returnToOutpostAfterBoss() {
        map = MapData.get(T1MapProgress.OUTPOST_MAP_ID)
        player.warpTo(T1MapProgress.OUTPOST_DEEP_GATE_SPAWN)
        reachedT1Outpost = true
        tappedCell = null
        handoffCell = null
        player.clearMovement()
        TileRenderer(terrainLayer).draw(map)
        rebuildRanchCompanions()
        updatePlayerView()
        camera.reset()
        camera.update(map, player.currentCell)
        applyCamera()
        lastFacilityAction = "boss return outpost"
    }

    private fun grantVictoryExperience(enemy: CreatureData): VictoryExpResult {
        if (activeCompanionIds.isEmpty()) return VictoryExpResult(emptyList(), leveledUp = false)
        val lines = mutableListOf<String>()
        var leveledUp = false
        activeCompanionIds.forEach { instanceId ->
            val index = companions.indexOfFirst { it.instanceId == instanceId }
            if (index < 0) return@forEach
            val before = companions[index]
            val name = CreatureDatabase.get(before.creatureId)?.name ?: "仲間"
            // MVP temporary rule: all active companions gain EXP, even if they were down in this battle.
            val result = Leveling.grantExp(before, enemy.expReward)
            companions[index] = result.companion
            lines += "${name}は${result.gainedExp}EXPを得た"
            if (result.levelsGained > 0) {
                leveledUp = true
                lines += "${name}はLv.${result.companion.level}になった！"
            }
        }
        return VictoryExpResult(lines, leveledUp)
    }

    private fun startBattle() {
        val enemy = activeEncounter ?: return
        val active = activeCompanionIds.mapNotNull { id ->
            val companion = companions.firstOrNull { it.instanceId == id } ?: return@mapNotNull null
            val creature = CreatureDatabase.get(companion.creatureId) ?: return@mapNotNull null
            BattleCompanionState(
                instanceId = companion.instanceId,
                name = creature.name,
                role = creature.role,
                attack = Leveling.attackWithLevel(creature.attack, companion.level),
                defense = Leveling.defenseWithLevel(creature.defense, companion.level),
                currentHp = companion.hp.coerceIn(0, Leveling.maxHp(creature.hp, companion.level)),
                maxHp = Leveling.maxHp(creature.hp, companion.level),
                currentMp = companion.mp.coerceIn(0, Leveling.maxMp(creature.mp, companion.level)),
                maxMp = Leveling.maxMp(creature.mp, companion.level),
                skillIds = companion.skillIds,
            )
        }
        if (active.isEmpty()) {
            showEncounterResult("戦える仲間がいない…")
            return
        }
        if (active.none { it.currentHp > 0 }) {
            healPartyAndActive()
            requestSave()
            lastFacilityAction = "auto heal before battle"
            showEncounterResult("仲間が倒れていたため回復所で休んだ")
            return
        }
        battleState = BattleState(
            enemy = enemy,
            enemyCurrentHp = enemy.hp,
            enemyMaxHp = enemy.hp,
            activeCompanions = active,
            message = "${enemy.name}が現れた！",
            phase = BattlePhase.PlayerTurn,
            logLines = listOf("${enemy.name}が現れた！"),
        )
        encounterUiState = EncounterUiState.Battle
        setBattleButtonsVisible()
        updateBattlePanel()
    }

    private fun handleBattleAttack() {
        val afterPlayer = BattleEngine.playerAttack(battleState ?: return)
        battleState = if (afterPlayer.phase == BattlePhase.Victory) {
            afterPlayer
        } else {
            BattleEngine.enemyAttack(afterPlayer)
        }
        val state = battleState ?: return
        updateBattlePanel()
        when (state.phase) {
            BattlePhase.Victory -> resolveBattleVictory()
            BattlePhase.Defeat -> resolveBattleDefeat()
            else -> Unit
        }
    }

    private fun toggleAutoBattle() {
        if (autoBattleEnabled) {
            stopAutoBattle()
        } else {
            startAutoBattle()
        }
    }

    private fun startAutoBattle() {
        if (encounterUiState != EncounterUiState.Battle || battleState?.phase != BattlePhase.PlayerTurn) return
        autoBattleEnabled = true
        autoBattleSeconds = AUTO_BATTLE_STEP_SECONDS
        updateAutoButtonVisible()
    }

    private fun stopAutoBattle() {
        autoBattleEnabled = false
        autoBattleSeconds = 0.0
        updateAutoButtonVisible()
    }

    private fun shouldAutoStartCurrentEncounter(): Boolean =
        activeEncounter != null &&
            !activeEncounterIsBoss &&
            !currentEncounterFirstSeen &&
            encounterUiState == EncounterUiState.ChooseAction

    private fun updatePendingAutoStart(deltaSeconds: Double) {
        if (pendingAutoStartSeconds <= 0.0) return
        if (!shouldAutoStartCurrentEncounter()) {
            pendingAutoStartSeconds = 0.0
            return
        }
        pendingAutoStartSeconds -= deltaSeconds
        if (pendingAutoStartSeconds > 0.0) return
        pendingAutoStartSeconds = 0.0
        startBattle()
        startAutoBattle()
    }

    private fun updateAutoBattle(deltaSeconds: Double) {
        if (!autoBattleEnabled) return
        if (encounterUiState != EncounterUiState.Battle || battleState?.phase != BattlePhase.PlayerTurn) {
            stopAutoBattle()
            return
        }
        autoBattleSeconds -= deltaSeconds
        if (autoBattleSeconds > 0.0) return
        autoBattleSeconds = AUTO_BATTLE_STEP_SECONDS
        runAutoBattleStep()
    }

    private fun runAutoBattleStep() {
        val state = battleState ?: return
        val afterPlayer = AutoBattlePolicy.chooseSkill(state)?.let { choice ->
            BattleEngine.useSkill(state, choice.casterInstanceId, choice.skill)
        } ?: BattleEngine.playerAttack(state)

        battleState = if (afterPlayer.phase == BattlePhase.EnemyTurn) {
            BattleEngine.enemyAttack(afterPlayer)
        } else {
            afterPlayer
        }
        val next = battleState ?: return
        updateBattlePanel()
        when (next.phase) {
            BattlePhase.Victory -> {
                stopAutoBattle()
                resolveBattleVictory()
            }
            BattlePhase.Defeat -> {
                stopAutoBattle()
                resolveBattleDefeat()
            }
            else -> Unit
        }
    }

    private fun updateResultAutoClose(deltaSeconds: Double) {
        if (resultAutoCloseSeconds <= 0.0) return
        if (encounterUiState != EncounterUiState.Result || pendingBefriendOffer != null) {
            resultAutoCloseSeconds = 0.0
            return
        }
        resultAutoCloseSeconds -= deltaSeconds
        if (resultAutoCloseSeconds > 0.0) return
        resultAutoCloseSeconds = 0.0
        closeEncounterPanel()
    }

    private fun shouldAutoCloseVictoryResult(leveledUp: Boolean, recruitImportant: Boolean): Boolean =
        !activeEncounterIsBoss &&
            !currentEncounterFirstSeen &&
            !leveledUp &&
            !recruitImportant

    private fun handleBattleEscape() {
        stopAutoBattle()
        battleState = BattleEngine.escape(battleState ?: return)
        persistBattleVitals()
        requestSave()
        showEncounterResult("うまく逃げ切った！")
    }

    private fun resolveBattleVictory() {
        persistBattleVitals()
        resolveTemporaryVictory()
    }

    private fun resolveBattleDefeat() {
        persistBattleVitals()
        healPartyAndActive()
        map = MapData.get(T1MapProgress.OUTPOST_MAP_ID)
        player.warpTo(T1MapProgress.OUTPOST_DEEP_GATE_SPAWN)
        reachedT1Outpost = true
        tappedCell = null
        handoffCell = null
        TileRenderer(terrainLayer).draw(map)
        rebuildRanchCompanions()
        updatePlayerView()
        camera.reset()
        camera.update(map, player.currentCell)
        applyCamera()
        lastFacilityAction = "defeat return outpost"
        requestSave()
        showEncounterResult("全滅した…前哨地へ戻った")
    }

    private fun updateBattlePanel() {
        val state = battleState ?: return
        encounterTitleText?.text = if (state.bossEnraged) "${state.enemy.name} 後半" else state.enemy.name
        val activeHpLines = state.activeCompanions.chunked(2).map { row ->
            row.joinToString("  ") {
                val down = if (it.currentHp <= 0) "D" else ""
                "${it.name} ${it.currentHp}/${it.maxHp}$down"
            }
        }
        encounterStatsText?.text = buildString {
            appendLine("Enemy HP: ${state.enemyCurrentHp}/${state.enemyMaxHp}")
            appendLine("味方HP")
            activeHpLines.take(2).forEach {
                appendLine(it)
            }
            state.logLines.takeLast(2).forEach {
                appendLine(it)
            }
        }
    }

    private fun showSkillActorSelect() {
        encounterUiState = EncounterUiState.SkillActorSelect
        selectedSkillActorId = null
        val actors = currentSkillActors()
        encounterTitleText?.text = "誰のスキル？"
        encounterStatsText?.text = buildString {
            appendLine("使う仲間を選んでください")
            if (actors.isEmpty()) append("行動できる仲間がいません")
        }
        setCommandButtonsHidden()
        setRowButtons(
            row1 = actors.getOrNull(0)?.let { "${it.name}  MP:${it.currentMp}/${it.maxMp}" },
            row2 = actors.getOrNull(1)?.let { "${it.name}  MP:${it.currentMp}/${it.maxMp}" },
            row3 = actors.getOrNull(2)?.let { "${it.name}  MP:${it.currentMp}/${it.maxMp}" },
            back = "戻る",
        )
    }

    private fun showSkillSelect(actorId: String) {
        encounterUiState = EncounterUiState.SkillSelect
        selectedSkillActorId = actorId
        val caster = battleState?.activeCompanions?.firstOrNull { it.instanceId == actorId }
        val skills = currentBattleSkillOptions()
        encounterTitleText?.text = caster?.let { "${it.name}のスキル" } ?: "スキル"
        encounterStatsText?.text = if (caster == null) {
            "行動できる仲間がいません"
        } else if (skills.isEmpty()) {
            "${caster.name}はスキルを持っていません\n戻るで別の仲間を選べます"
        } else {
            "使うスキルを選んでください"
        }
        setCommandButtonsHidden()
        setRowButtons(
            row1 = skills.getOrNull(0)?.let { "${it.name}  MP${it.mpCost}" },
            row2 = skills.getOrNull(1)?.let { "${it.name}  MP${it.mpCost}" },
            row3 = null,
            back = "戻る",
        )
    }

    private fun handleBattleSkill(skillId: Int) {
        val skill = SkillDatabase.get(skillId) ?: return
        val actorId = selectedSkillActorId ?: return
        val afterSkill = BattleEngine.useSkill(battleState ?: return, actorId, skill)
        battleState = if (afterSkill.phase == BattlePhase.EnemyTurn) {
            BattleEngine.enemyAttack(afterSkill)
        } else {
            afterSkill
        }
        encounterUiState = EncounterUiState.Battle
        setBattleButtonsVisible()
        val state = battleState ?: return
        updateBattlePanel()
        when (state.phase) {
            BattlePhase.Victory -> resolveBattleVictory()
            BattlePhase.Defeat -> resolveBattleDefeat()
            else -> Unit
        }
    }

    private fun showItemSelect(message: String? = null) {
        encounterUiState = EncounterUiState.ItemSelect
        val items = battleItemOptions()
        encounterTitleText?.text = "道具"
        encounterStatsText?.text = buildString {
            appendLine("使う道具を選んでください")
            if (message != null) appendLine(message)
            if (items.isEmpty()) append("道具を持っていません")
        }
        setCommandButtonsHidden()
        setRowButtons(
            row1 = items.getOrNull(0)?.let { itemLabel(it) },
            row2 = items.getOrNull(1)?.let { itemLabel(it) },
            row3 = null,
            back = "戻る",
        )
    }

    private fun handleBattleItem(itemId: Int) {
        val item = ItemDatabase.get(itemId) ?: return
        if (itemCounts.getOrDefault(item.itemId, 0) <= 0) {
            showItemSelect("${item.name}を持っていません")
            return
        }
        val state = battleState ?: return
        val target = targetForItem(state, item)
        if (target == null) {
            showItemSelect("使える対象がいません")
            return
        }
        itemCounts[item.itemId] = itemCounts.getOrDefault(item.itemId, 0) - 1
        if (itemCounts.getOrDefault(item.itemId, 0) <= 0) itemCounts.remove(item.itemId)

        val updatedCompanions = state.activeCompanions.map {
            if (it.instanceId != target.instanceId) {
                it
            } else {
                when (item.kind) {
                    ItemKind.HealHp -> it.copy(currentHp = (it.currentHp + item.power).coerceAtMost(it.maxHp))
                    ItemKind.HealMp -> it.copy(currentMp = (it.currentMp + item.power).coerceAtMost(it.maxMp))
                }
            }
        }
        val afterUseTarget = updatedCompanions.first { it.instanceId == target.instanceId }
        val recovered = when (item.kind) {
            ItemKind.HealHp -> afterUseTarget.currentHp - target.currentHp
            ItemKind.HealMp -> afterUseTarget.currentMp - target.currentMp
        }
        val log = "${item.name}を使った！ ${target.name}が${recovered}回復"
        val afterUse = state.copy(
            activeCompanions = updatedCompanions,
            phase = BattlePhase.EnemyTurn,
            message = log,
            logLines = (state.logLines + log).takeLast(3),
        )
        battleState = BattleEngine.enemyAttack(afterUse)
        persistBattleVitals()
        requestSave()
        encounterUiState = EncounterUiState.Battle
        setBattleButtonsVisible()
        val next = battleState ?: return
        updateBattlePanel()
        if (next.phase == BattlePhase.Defeat) {
            resolveBattleDefeat()
        }
    }

    private fun battleItemOptions(): List<ItemData> =
        ItemDatabase.shopItems.filter { itemCounts.getOrDefault(it.itemId, 0) > 0 }

    private fun itemLabel(item: ItemData): String =
        "${item.name} x${itemCounts.getOrDefault(item.itemId, 0)}"

    private fun inventorySummary(): String =
        ItemDatabase.shopItems.joinToString(" ") {
            "${it.name.take(2)}x${itemCounts.getOrDefault(it.itemId, 0)}"
        }

    private fun targetForItem(state: BattleState, item: ItemData): BattleCompanionState? {
        val alive = state.activeCompanions.filter { it.currentHp > 0 }
        return when (item.kind) {
            ItemKind.HealHp -> alive.maxByOrNull { it.maxHp - it.currentHp }
            ItemKind.HealMp -> alive.maxByOrNull { it.maxMp - it.currentMp }
        }
    }

    private fun currentBattleSkillOptions(): List<starsaga.data.SkillData> {
        val actorId = selectedSkillActorId ?: return emptyList()
        val caster = battleState?.activeCompanions?.firstOrNull {
            it.instanceId == actorId && it.currentHp > 0
        } ?: return emptyList()
        return caster.skillIds.mapNotNull { SkillDatabase.get(it) }.take(2)
    }

    private fun currentSkillActors(): List<BattleCompanionState> =
        battleState?.activeCompanions?.filter { it.currentHp > 0 }?.take(3) ?: emptyList()

    private fun showBefriendOffer(creature: CreatureData) {
        hideShopPanel()
        encounterUiState = EncounterUiState.BefriendOffer
        encounterTitleText?.text = "${creature.name}が仲間になりたそうにしている"
        encounterStatsText?.text = "仲間にしますか？"
        fightLabelText?.text = "仲間にする"
        fightLabelText?.position(FIGHT_BUTTON_X + 10, centeredButtonTextY(BUTTON_Y, BUTTON_HEIGHT, 18.0))
        runLabelText?.text = "見送る"
        runLabelText?.position(RUN_BUTTON_X + 24, centeredButtonTextY(BUTTON_Y, BUTTON_HEIGHT, 18.0))
        setActionButtonsVisible(true)
    }

    private fun showEncounterResult(
        message: String,
        detail: String = "OKを押すとフィールドへ戻ります",
        autoCloseSeconds: Double? = null,
    ) {
        hideShopPanel()
        hideT1ClearLayer()
        pendingAutoStartSeconds = 0.0
        resultAutoCloseSeconds = autoCloseSeconds ?: 0.0
        stopAutoBattle()
        encounterUiState = EncounterUiState.Result
        setFieldUiVisible(false)
        encounterTitleText?.text = message
        encounterStatsText?.text = detail
        setActionButtonsVisible(false)
    }

    private fun showT1ClearOverlay(rewardLines: List<String>) {
        hideShopPanel()
        hideT1ClearLayer()
        pendingAutoStartSeconds = 0.0
        resultAutoCloseSeconds = 0.0
        stopAutoBattle()
        encounterUiState = EncounterUiState.T1Clear
        setFieldUiVisible(false)
        setActionButtonsVisible(false)
        encounterTitleText?.text = "第1惑星クリア！"
        encounterStatsText?.text = buildString {
            appendLine("星草の主をしずめ、T1の物語を完結しました")
            appendLine("収集: ${ObjectiveResolver.t1OwnedCount(befriendedCreatureIds)}/${CreatureDatabase.t1Creatures.size}")
            rewardLines.takeLast(2).forEach { appendLine(it) }
        }
        okLabelText?.text = "探索を続ける"
        okLabelText?.position(OK_BUTTON_X + 10, centeredButtonTextY(BUTTON_Y, BUTTON_HEIGHT, 16.0))
        t1ClearLayer.visible = true
        t1ClearLayer.solidRect(296, 84, RGBA(9, 15, 26, 210)) {
            position(32, 232)
        }
        t1ClearLayer.text(
            "T1スター",
            textSize = 13.0,
            color = RGBA(238, 244, 236, 255),
            font = StarSagaFonts.font,
        ) {
            position(44, 244)
        }
        CreatureDatabase.t1Creatures.forEachIndexed { index, creature ->
            val x = 48 + index * 52
            val y = 270
            drawCreatureIcon(t1ClearLayer, creature.id, x, y, 34)
            val labelColor = if (creature.id in befriendedCreatureIds) {
                RGBA(240, 218, 112, 255)
            } else {
                RGBA(120, 132, 146, 255)
            }
            t1ClearLayer.text(
                if (creature.id in befriendedCreatureIds) "OK" else "--",
                textSize = 9.0,
                color = labelColor,
                font = StarSagaFonts.font,
            ) {
                position(x + 9, y + 38)
            }
        }
        encounterLayer.visible = true
    }

    private fun showRecruitJoinedResult(creature: CreatureData, detail: String) {
        showEncounterResult(
            "${creature.name}が仲間になった！",
            detail,
        )
        t1ClearLayer.visible = true
        t1ClearLayer.solidRect(132, 112, RGBA(9, 15, 26, 220)) {
            position(114, 220)
        }
        drawCreatureIcon(t1ClearLayer, creature.id, 148, 236, 64)
        t1ClearLayer.text(
            creature.name,
            textSize = 15.0,
            color = RGBA(240, 218, 112, 255),
            font = StarSagaFonts.font,
        ) {
            position(142, 304)
        }
    }

    private fun setActionButtonsVisible(visible: Boolean) {
        setRowButtonsHidden()
        fightButtonView?.visible = true
        fightButtonView?.position(FIGHT_BUTTON_X, BUTTON_Y)
        skillButtonView?.visible = false
        itemButtonView?.visible = false
        runButtonView?.visible = visible
        runButtonView?.position(RUN_BUTTON_X, BUTTON_Y)
        fightLabelText?.visible = visible
        skillLabelText?.visible = false
        itemLabelText?.visible = false
        runLabelText?.visible = visible
        okLabelText?.visible = !visible
        if (!visible) {
            okLabelText?.text = "OK"
            okLabelText?.position(FIGHT_BUTTON_X + 42, centeredButtonTextY(BUTTON_Y, BUTTON_HEIGHT, 18.0))
        }
        if (visible && encounterUiState == EncounterUiState.ChooseAction) {
            fightLabelText?.text = "戦う"
            fightLabelText?.position(FIGHT_BUTTON_X + 30, centeredButtonTextY(BUTTON_Y, BUTTON_HEIGHT, 18.0))
            runLabelText?.text = "逃げる"
            runLabelText?.position(RUN_BUTTON_X + 24, centeredButtonTextY(BUTTON_Y, BUTTON_HEIGHT, 18.0))
        }
        updateAutoButtonVisible()
    }

    private fun setBattleButtonsVisible() {
        setRowButtonsHidden()
        fightButtonView?.visible = true
        skillButtonView?.visible = true
        itemButtonView?.visible = true
        runButtonView?.visible = true
        fightButtonView?.position(BATTLE_ATTACK_X, BATTLE_CMD_TOP_Y)
        skillButtonView?.position(BATTLE_SKILL_X, BATTLE_CMD_TOP_Y)
        itemButtonView?.position(BATTLE_ITEM_X, BATTLE_CMD_BOTTOM_Y)
        runButtonView?.position(BATTLE_RUN_X, BATTLE_CMD_BOTTOM_Y)
        fightLabelText?.visible = true
        skillLabelText?.visible = true
        itemLabelText?.visible = true
        runLabelText?.visible = true
        okLabelText?.visible = false
        fightLabelText?.text = "攻撃"
        fightLabelText?.position(BATTLE_ATTACK_X + 44, centeredButtonTextY(BATTLE_CMD_TOP_Y, BATTLE_CMD_H, 18.0))
        skillLabelText?.text = "スキル"
        skillLabelText?.position(BATTLE_SKILL_X + 36, centeredButtonTextY(BATTLE_CMD_TOP_Y, BATTLE_CMD_H, 17.0))
        itemLabelText?.text = "道具"
        itemLabelText?.position(BATTLE_ITEM_X + 44, centeredButtonTextY(BATTLE_CMD_BOTTOM_Y, BATTLE_CMD_H, 17.0))
        runLabelText?.text = "逃げる"
        runLabelText?.position(BATTLE_RUN_X + 36, centeredButtonTextY(BATTLE_CMD_BOTTOM_Y, BATTLE_CMD_H, 18.0))
        updateAutoButtonVisible()
    }

    private fun setFieldUiVisible(visible: Boolean) {
        companionButtonLayer.visible = visible
        debugLayer.visible = debugVisible && visible && encounterUiState == EncounterUiState.Hidden && !companionPanelOpen
    }

    private fun setCommandButtonsHidden() {
        fightButtonView?.visible = false
        skillButtonView?.visible = false
        itemButtonView?.visible = false
        runButtonView?.visible = false
        fightLabelText?.visible = false
        skillLabelText?.visible = false
        itemLabelText?.visible = false
        runLabelText?.visible = false
        okLabelText?.visible = false
        autoButtonView?.visible = false
        autoLabelText?.visible = false
    }

    private fun updateAutoButtonVisible() {
        val visible = encounterUiState == EncounterUiState.ChooseAction || encounterUiState == EncounterUiState.Battle
        autoButtonView?.visible = visible
        autoLabelText?.visible = visible
        autoLabelText?.text = if (autoBattleEnabled) "STOP" else "AUTO"
        autoLabelText?.position(
            AUTO_BUTTON_X + if (autoBattleEnabled) 16.0 else 18.0,
            centeredButtonTextY(AUTO_BUTTON_Y, AUTO_BUTTON_H, 13.0),
        )
    }

    private fun setRowButtons(
        row1: String?,
        row2: String?,
        row3: String?,
        back: String?,
    ) {
        setRowButton(row1ButtonView, row1LabelText, row1)
        setRowButton(row2ButtonView, row2LabelText, row2)
        setRowButton(row3ButtonView, row3LabelText, row3)
        setRowButton(rowBackButtonView, rowBackLabelText, back)
    }

    private fun setRowButton(view: View?, label: Text?, text: String?) {
        view?.visible = text != null
        label?.visible = text != null
        label?.text = text ?: ""
    }

    private fun setRowButtonsHidden() {
        setRowButtons(null, null, null, null)
    }

    private fun sanitizeTrainingCompanions() {
        val existingIds = companions.map { it.instanceId }.toSet()
        val sanitized = trainingCompanionIds
            .filter { it in existingIds }
            .distinct()
            .take(MAX_TRAINING_SIZE)
        if (sanitized != trainingCompanionIds) {
            trainingCompanionIds.clear()
            trainingCompanionIds += sanitized
        }
        if (trainingCompanionIds.isEmpty() && lastTrainingAtEpochMillis <= 0L) {
            lastTrainingAtEpochMillis = currentEpochMillis()
        }
    }

    private fun applyTrainingExp(showMessage: Boolean): Boolean {
        val beforeIds = trainingCompanionIds.toList()
        sanitizeTrainingCompanions()
        val now = currentEpochMillis()
        if (lastTrainingAtEpochMillis <= 0L) {
            lastTrainingAtEpochMillis = now
            if (beforeIds != trainingCompanionIds) requestSave()
            return false
        }
        if (trainingCompanionIds.isEmpty()) {
            lastTrainingAtEpochMillis = now
            if (beforeIds != trainingCompanionIds) requestSave()
            return false
        }

        val elapsedMillis = (now - lastTrainingAtEpochMillis).coerceAtLeast(0L)
        val cappedMillis = elapsedMillis.coerceAtMost(TRAINING_MAX_ACCUMULATION_MILLIS)
        val gainedExp = ((cappedMillis * TRAINING_EXP_PER_HOUR) / TRAINING_HOUR_MILLIS).toInt()
        if (gainedExp <= 0) {
            if (beforeIds != trainingCompanionIds) requestSave()
            return false
        }

        val levelUpLines = mutableListOf<String>()
        trainingCompanionIds.forEach { instanceId ->
            val index = companions.indexOfFirst { it.instanceId == instanceId }
            if (index < 0) return@forEach
            val before = companions[index]
            val result = Leveling.grantExp(before, gainedExp)
            companions[index] = result.companion
            if (result.levelsGained > 0) {
                val name = CreatureDatabase.get(before.creatureId)?.name ?: "仲間"
                levelUpLines += "${name}がLv.${result.companion.level}になった"
            }
        }
        lastTrainingAtEpochMillis = now
        clampCompanionVitals()
        rebuildRanchCompanionsIfReady()
        if (showMessage) {
            companionPanelMessage = (
                listOf("育成所の仲間が EXP $gainedExp を獲得しました") + levelUpLines
                ).take(3).joinToString("\n")
        }
        requestSave()
        return true
    }

    private fun rebuildRanchCompanionsIfReady() {
        if (::actorLayer.isInitialized) {
            rebuildRanchCompanions()
        }
    }

    private fun progressRecruitmentOnDefeat(creature: CreatureData): RecruitProgressResult {
        if (creature.id in befriendedCreatureIds) {
            return RecruitProgressResult(
                line = "${creature.name}は仲間済み",
                important = false,
            )
        }
        val currentProgress = recruitProgressByCreatureId.getOrDefault(creature.id, 0).coerceIn(0, RECRUIT_THRESHOLD)
        val progress = RecruitmentProgress.advance(
            currentProgress = currentProgress,
            threshold = RECRUIT_THRESHOLD,
            hasLuckRole = partyHasLuckRole(),
        )
        val progressLine = if (progress.luckBonus) {
            "LUCKボーナス！ ${progress.before} → ${progress.after}"
        } else {
            "仲間化進行 ${progress.before} → ${progress.after}"
        }
        if (!progress.completed) {
            recruitProgressByCreatureId[creature.id] = progress.after
            return RecruitProgressResult(
                line = progressLine,
                important = progress.luckBonus,
            )
        }

        if (companions.size >= MAX_COMPANIONS) {
            recruitProgressByCreatureId[creature.id] = RECRUIT_THRESHOLD
            return RecruitProgressResult(
                line = "$progressLine\n${creature.name}が仲間になりたそうだが、牧場に空きがない",
                important = true,
            )
        }

        addCompanionFromCreature(creature)
        recruitProgressByCreatureId[creature.id] = 0
        return RecruitProgressResult(
            line = "$progressLine\n${creature.name}が仲間になった！",
            important = true,
            joined = true,
        )
    }

    private fun partyHasLuckRole(): Boolean =
        partyCompanionIds.any { instanceId ->
            val companion = companions.firstOrNull { it.instanceId == instanceId } ?: return@any false
            CreatureDatabase.get(companion.creatureId)?.role == CreatureRole.LUCK
        }

    private fun addCompanionIfNeeded(creature: CreatureData?): String {
        if (creature == null) return "仲間が見つかりません"
        if (companions.size >= MAX_COMPANIONS) return "牧場がいっぱいです"
        val companion = addCompanionFromCreature(creature)
        requestSave()
        return if (companion.instanceId in partyCompanionIds) {
            "パーティーに加わった"
        } else {
            "牧場で待機しています"
        }
    }

    private fun addCompanionFromCreature(creature: CreatureData): CompanionState {
        val instanceId = "mem-${creature.id}-${companions.size + 1}"
        befriendedCreatureIds += creature.id
        val companion = CompanionState(
            instanceId = instanceId,
            creatureId = creature.id,
            hp = creature.hp,
            mp = creature.mp,
            skillIds = SkillDatabase.initialSkillIdsFor(creature.id),
        )
        companions += companion
        if (partyCompanionIds.size < MAX_PARTY_SIZE) {
            partyCompanionIds += instanceId
            syncActiveCompanions()
        }
        rebuildRanchCompanions()
        return companion
    }

    private fun ensureStarterCompanion() {
        if (companions.isNotEmpty()) return
        val starter = CreatureDatabase.get(STARTER_CREATURE_ID) ?: return
        val instanceId = "starter-${starter.id}"
        companions += CompanionState(
            instanceId = instanceId,
            creatureId = starter.id,
            hp = starter.hp,
            mp = starter.mp,
            skillIds = SkillDatabase.initialSkillIdsFor(starter.id),
        )
        befriendedCreatureIds += starter.id
        partyCompanionIds += instanceId
        syncActiveCompanions()
    }

    private fun normalizePartyState() {
        ensureStarterCompanion()
        val existingIds = companions.map { it.instanceId }.toSet()
        partyCompanionIds.retainAll(existingIds)
        activeCompanionIds.retainAll(existingIds)
        if (partyCompanionIds.isEmpty() && companions.isNotEmpty()) {
            partyCompanionIds += companions.first().instanceId
        }
        while (partyCompanionIds.size > MAX_PARTY_SIZE) {
            partyCompanionIds.removeAt(partyCompanionIds.lastIndex)
        }
        sanitizeTrainingCompanions()
        companions.indices.forEach { index ->
            val companion = companions[index]
            if (companion.skillIds.isEmpty()) {
                val initialSkillIds = SkillDatabase.initialSkillIdsFor(companion.creatureId)
                if (initialSkillIds.isNotEmpty()) {
                    companions[index] = companion.copy(skillIds = initialSkillIds)
                }
            }
        }
        syncActiveCompanions()
    }

    private fun persistBattleVitals() {
        val state = battleState ?: return
        state.activeCompanions.forEach { battleCompanion ->
            val index = companions.indexOfFirst { it.instanceId == battleCompanion.instanceId }
            if (index >= 0) {
                val current = companions[index]
                companions[index] = current.copy(
                    hp = battleCompanion.currentHp.coerceAtLeast(0),
                    mp = battleCompanion.currentMp.coerceAtLeast(0),
                )
            }
        }
    }

    private fun healPartyAndActive() {
        val idsToHeal = (partyCompanionIds + activeCompanionIds).toSet()
        companions.indices.forEach { index ->
            val companion = companions[index]
            if (companion.instanceId in idsToHeal) {
                val creature = CreatureDatabase.get(companion.creatureId) ?: return@forEach
                companions[index] = companion.copy(
                    hp = Leveling.maxHp(creature.hp, companion.level),
                    mp = Leveling.maxMp(creature.mp, companion.level),
                )
            }
        }
    }

    private fun startFacilityInteraction(cell: GridCell): Boolean {
        val tile = map.tileAt(cell.col, cell.row)
        val actionType = when {
            map.id == T1MapProgress.RANCH_MAP_ID && tile == TileType.Exit -> FacilityActionType.RanchExit
            map.id == T1MapProgress.RANCH_MAP_ID && tile == TileType.Ranch -> FacilityActionType.RanchTerminal
            map.id == T1MapProgress.RANCH_MAP_ID && tile == TileType.TrainingPad -> FacilityActionType.TrainingPad
            isWarpGateCell(cell, tile) -> FacilityActionType.WarpGate
            map.id == T1MapProgress.OUTPOST_MAP_ID && tile == TileType.Ranch -> FacilityActionType.OutpostParty
            (map.id == T1MapProgress.LEGACY_PLANET_MAP_ID || map.id == T1MapProgress.OUTPOST_MAP_ID) && tile == TileType.DeepGate -> FacilityActionType.DeepGate
            tile == TileType.Shop -> FacilityActionType.Shop
            tile == TileType.Ranch -> FacilityActionType.RanchGate
            tile == TileType.Heal -> FacilityActionType.Heal
            tile in MESSAGE_TILES -> FacilityActionType.Message
            else -> return false
        }
        val interactionCell = nearestFacilityInteractionCell(cell) ?: return true
        val message = if (actionType == FacilityActionType.Message) messageForTile(cell, tile) else null
        pendingFacilityAction = PendingFacilityAction(
            type = actionType,
            facilityCell = cell,
            interactionCell = interactionCell,
            messageTitle = message?.first,
            messageDetail = message?.second,
        )
        tappedCell = null
        handoffCell = interactionCell
        player.clearMovement()
        player.moveTo(map, interactionCell)
        if (interactionCell == player.currentCell) {
            processPendingFacilityArrival(player.currentCell)
        }
        return true
    }

    private fun processPendingFacilityArrival(cell: GridCell): Boolean {
        val pending = pendingFacilityAction ?: return false
        if (pending.interactionCell != cell) return true
        pendingFacilityAction = null
        executeFacilityAction(pending)
        return true
    }

    private fun executeFacilityAction(action: PendingFacilityAction) {
        tappedCell = null
        handoffCell = null
        player.clearMovement()
        when (action.type) {
            FacilityActionType.Shop -> {
                lastFacilityAction = "shop at ${action.facilityCell.col},${action.facilityCell.row}"
                showShopPanel()
                encounterLayer.visible = true
            }
            FacilityActionType.RanchGate -> {
                lastFacilityAction = "ranch gate at ${action.facilityCell.col},${action.facilityCell.row}"
                warpToRanch()
            }
            FacilityActionType.RanchTerminal -> {
                lastFacilityAction = "ranch terminal"
                openRanchPanel()
            }
            FacilityActionType.OutpostParty -> {
                lastFacilityAction = "outpost party"
                openPartyPanel()
            }
            FacilityActionType.RanchExit -> {
                returnFromRanch()
            }
            FacilityActionType.TrainingPad -> {
                lastFacilityAction = "training pad"
                applyTrainingExp(showMessage = false)
                showEncounterResult(
                    "育成所 ${trainingCompanionIds.size}/$MAX_TRAINING_SIZE",
                    "預けた仲間は時間経過で少しずつ育ちます\n牧場端末から仲間を預けられます",
                )
                encounterLayer.visible = true
            }
            FacilityActionType.DeepGate -> {
                lastFacilityAction = "deep gate"
                handleDeepGateAction()
            }
            FacilityActionType.WarpGate -> {
                handleWarpGateAction()
            }
            FacilityActionType.Heal -> {
                healPartyAndActive()
                lastFacilityAction = "heal at ${action.facilityCell.col},${action.facilityCell.row}"
                saveStatus = "healed"
                requestSave()
                showEncounterResult(
                    "回復所で休んだ",
                    "パーティーのHP/MPが全回復した！\nOKを押すとフィールドへ戻ります",
                )
                encounterLayer.visible = true
            }
            FacilityActionType.Message -> {
                lastFacilityAction = "message at ${action.facilityCell.col},${action.facilityCell.row}"
                showEncounterResult(
                    action.messageTitle ?: "案内",
                    action.messageDetail ?: "まだ準備中です\nOKを押すとフィールドへ戻ります",
                )
                encounterLayer.visible = true
            }
        }
    }

    private fun isWarpGateCell(cell: GridCell, tile: TileType?): Boolean =
        tile == TileType.Exit &&
            ((map.id == T1MapProgress.FIRST_TOWN_MAP_ID && cell == T1MapProgress.FIRST_TOWN_WARP) ||
                (map.id == T1MapProgress.OUTPOST_MAP_ID && cell == T1MapProgress.OUTPOST_WARP))

    private fun handleWarpGateAction() {
        lastFacilityAction = "warp gate"
        if (map.id == T1MapProgress.OUTPOST_MAP_ID && !t1OutpostWarpUnlocked) {
            t1OutpostWarpUnlocked = true
            requestSave()
            showEncounterResult(
                "星門がつながった",
                "星降りの集落へワープできます\nもう一度調べると移動できます",
            )
            encounterLayer.visible = true
            return
        }
        if (!t1OutpostWarpUnlocked) {
            showEncounterResult(
                "星門",
                "遠くの星門とはまだつながっていない",
            )
            encounterLayer.visible = true
            return
        }

        val target = T1WarpPolicy.targetFor(map.id, t1OutpostWarpUnlocked) ?: return
        showWarpConfirm(target)
    }

    private fun showWarpConfirm(target: MapSpawn) {
        hideShopPanel()
        hideT1ClearLayer()
        pendingAutoStartSeconds = 0.0
        resultAutoCloseSeconds = 0.0
        stopAutoBattle()
        pendingWarpTarget = target
        encounterUiState = EncounterUiState.WarpConfirm
        setFieldUiVisible(false)
        encounterTitleText?.text = "星門で移動する？"
        val destination = if (target.mapId == T1MapProgress.OUTPOST_MAP_ID) "深門前哨地" else "星降りの集落"
        encounterStatsText?.text = "$destination へ移動します"
        setActionButtonsVisible(true)
        fightLabelText?.text = "移動する"
        fightLabelText?.position(FIGHT_BUTTON_X + 14, centeredButtonTextY(BUTTON_Y, BUTTON_HEIGHT, 18.0))
        runLabelText?.text = "やめる"
        runLabelText?.position(RUN_BUTTON_X + 30, centeredButtonTextY(BUTTON_Y, BUTTON_HEIGHT, 18.0))
        encounterLayer.visible = true
    }

    private fun executePendingWarp() {
        val target = pendingWarpTarget ?: return
        pendingWarpTarget = null
        map = MapData.get(target.mapId)
        player.warpTo(target.cell)
        if (map.id == T1MapProgress.OUTPOST_MAP_ID) reachedT1Outpost = true
        tappedCell = null
        handoffCell = null
        player.clearMovement()
        lockedExitSide = null
        activeExitSide = null
        lastTransition = "warp -> ${map.id}"
        refreshMapAfterWarp()
        requestSave()
        closeEncounterPanel()
    }

    private fun messageForTile(cell: GridCell, tile: TileType?): Pair<String, String> =
        when (tile) {
            TileType.DeepGate -> deepGateMessage()
            TileType.TrainingPad -> "育成所" to "預けた仲間は時間経過で少しずつ育ちます"
            TileType.StarLamp -> "星灯" to "町の星灯があるから、ここは安全なんだ"
            TileType.Sign -> when {
                map.id == T1MapProgress.FIRST_TOWN_MAP_ID && cell.col <= 4 -> "旧道跡" to "旧道は星草に覆われている\n東の街道から深門を目指そう"
                map.id == T1MapProgress.FIRST_TOWN_MAP_ID && cell.col >= 17 -> "集落の案内" to "東の出口から星草の道へ進めます\n西の小門は星門への旧道です"
                map.id == T1MapProgress.SETTLEMENT_OUTSKIRTS_MAP_ID -> "星草の道" to "草むらでは星の仲間に出会えるよ\n奥の道はまだ調査中です"
                map.id == T1MapProgress.STARGRASS_FORK_MAP_ID -> "星草の分かれ道" to "回復の星灯があります\n草地の脇道で仲間を探そう"
                map.id == T1MapProgress.DEEP_GATE_ROAD_MAP_ID -> "深門への道" to "この先に星の主の気配がある\n準備して進もう"
                map.id == T1MapProgress.OUTPOST_MAP_ID -> bossInfoMessage()
                cell.col <= 22 -> "星くずの道" to "草むらでは星の仲間に出会えるよ"
                cell.col <= 31 -> "星の仲間" to "同じ子に何度も出会うと、心を開くらしい"
                else -> "案内板" to "奥地へ向かうなら仲間を集めておこう"
            }
            TileType.Planned -> when (cell.col) {
                2 -> "旧道跡" to "旧道は星草に覆われている\n東の街道から深門を目指そう"
                25 -> "星草の道" to "この先には星草の道が続いている\n先の区域はまだ調査中です"
                5 -> "育成所予定地" to "育成所はまだ準備中です"
                8 -> "民家予定地" to "小さな民家はまだ開いていない"
                11 -> "研究小屋予定地" to "研究小屋はまだ開いていない"
                else -> "予定地" to "まだ準備中です"
            }
            else -> "案内" to "まだ準備中です"
        }

    private fun deepGateMessage(): Pair<String, String> {
        val owned = ObjectiveResolver.t1OwnedCount(befriendedCreatureIds)
        return when {
            t1BossCleared -> "第1惑星クリア" to "仲間の育成や探索を続けられます"
            owned < CreatureDatabase.t1Creatures.size ->
                "奥地入口" to "この先には星の主の気配がする\nT1の仲間を5種類集めてから挑もう\n今の仲間: $owned/${CreatureDatabase.t1Creatures.size}"
            else -> "奥地入口" to "星の主に挑みます"
        }
    }

    private fun bossInfoMessage(): Pair<String, String> {
        val owned = ObjectiveResolver.t1OwnedCount(befriendedCreatureIds)
        val gateLine = if (owned >= CreatureDatabase.t1Creatures.size) "DeepGateは開いています" else "5体集めるとDeepGateが開きます"
        return "星草の主" to "収集 $owned/${CreatureDatabase.t1Creatures.size}\n$gateLine\n後半で強化、強攻撃前に予告"
    }

    private fun handleDeepGateAction() {
        val owned = ObjectiveResolver.t1OwnedCount(befriendedCreatureIds)
        if (t1BossCleared || owned < CreatureDatabase.t1Creatures.size) {
            val message = deepGateMessage()
            showEncounterResult(message.first, message.second)
            encounterLayer.visible = true
            return
        }
        showBossConfirm()
    }

    private fun showBossConfirm() {
        hideShopPanel()
        hideT1ClearLayer()
        pendingAutoStartSeconds = 0.0
        resultAutoCloseSeconds = 0.0
        stopAutoBattle()
        activeEncounter = null
        activeEncounterIsBoss = false
        battleState = null
        encounterUiState = EncounterUiState.BossConfirm
        setFieldUiVisible(false)
        val party = partyCompanionIds.mapNotNull { id -> companions.firstOrNull { it.instanceId == id } }
        val active = activeCompanionIds.mapNotNull { id -> companions.firstOrNull { it.instanceId == id } }
        val averageLevel = if (party.isEmpty()) {
            0.0
        } else {
            party.sumOf { it.level }.toDouble() / party.size
        }
        val hpSummary = active.joinToString(" ") { companion ->
            val creature = CreatureDatabase.get(companion.creatureId)
            val maxHp = creature?.let { Leveling.maxHp(it.hp, companion.level) } ?: companion.hp
            "${creature?.name ?: "仲間"}:${companion.hp}/$maxHp"
        }.ifEmpty { "戦える仲間なし" }
        encounterTitleText?.text = "${CreatureDatabase.t1Boss.name}に挑む？"
        encounterStatsText?.text = buildString {
            appendLine("編成 ${party.size}/$MAX_PARTY_SIZE  Active ${active.size}/$MAX_ACTIVE_SIZE")
            appendLine("平均Lv ${formatAverageLevel(averageLevel)}")
            appendLine("HP $hpSummary")
            append("準備不足でも挑戦できます")
        }
        setActionButtonsVisible(true)
        fightLabelText?.text = "挑戦する"
        fightLabelText?.position(FIGHT_BUTTON_X + 14, centeredButtonTextY(BUTTON_Y, BUTTON_HEIGHT, 18.0))
        runLabelText?.text = "まだ準備"
        runLabelText?.position(RUN_BUTTON_X + 16, centeredButtonTextY(BUTTON_Y, BUTTON_HEIGHT, 18.0))
        encounterLayer.visible = true
    }

    private fun formatAverageLevel(value: Double): String =
        if (value <= 0.0) "-" else ((value * 10).toInt() / 10.0).toString()

    private fun startT1BossBattle() {
        activeEncounter = CreatureDatabase.t1Boss
        activeEncounterIsBoss = true
        currentEncounterFirstSeen = false
        lastEncounterCreatureName = CreatureDatabase.t1Boss.name
        tappedCell = null
        handoffCell = null
        player.clearMovement()
        showEncounterPanel(CreatureDatabase.t1Boss)
    }

    private fun nearestFacilityInteractionCell(facilityCell: GridCell): GridCell? {
        val candidates = listOf(
            GridCell(facilityCell.col, facilityCell.row + 1),
            GridCell(facilityCell.col, facilityCell.row - 1),
            GridCell(facilityCell.col - 1, facilityCell.row),
            GridCell(facilityCell.col + 1, facilityCell.row),
        ).filter {
            val tile = map.tileAt(it.col, it.row)
            map.isPassable(it.col, it.row) && tile !in FACILITY_TILES
        }

        return candidates
            .mapNotNull { candidate ->
                val path = starsaga.map.PathFinder.findPath(map, player.currentCell, candidate)
                when {
                    candidate == player.currentCell -> candidate to 0
                    path.isNotEmpty() -> candidate to path.size
                    else -> null
                }
            }
            .minByOrNull { it.second }
            ?.first
    }

    private fun showShopPanel(message: String? = null) {
        encounterUiState = EncounterUiState.Shop
        setFieldUiVisible(false)
        selectedSkillActorId = null
        encounterTitleText?.visible = false
        encounterStatsText?.visible = false
        setCommandButtonsHidden()
        setRowButtonsHidden()
        shopLayer.removeChildren()
        shopLayer.visible = true
        shopLayer.createUiText("ショップ", SHOP_TEXT_X, SHOP_TITLE_Y, textSize = 20.0)
        shopLayer.createUiText("所持金: ${gold}G", SHOP_TEXT_X, SHOP_GOLD_Y, textSize = 15.0)
        shopLayer.createUiText(
            label = message ?: "",
            x = SHOP_TEXT_X,
            y = SHOP_MESSAGE_Y,
            textSize = 14.0,
            color = RGBA(255, 238, 140, 255),
        )
        shopLayer.createUiButton(
            x = SHOP_ROW_X,
            y = SHOP_ITEM1_Y,
            width = SHOP_ROW_W,
            height = SHOP_BUTTON_H,
            label = shopLabel(ItemDatabase.shopItems[0]),
            background = RGBA(58, 88, 145, 255),
            fontSize = 15.0,
            textX = SHOP_ROW_X + 12,
        )
        shopLayer.createUiButton(
            x = SHOP_ROW_X,
            y = SHOP_ITEM2_Y,
            width = SHOP_ROW_W,
            height = SHOP_BUTTON_H,
            label = shopLabel(ItemDatabase.shopItems[1]),
            background = RGBA(58, 88, 145, 255),
            fontSize = 15.0,
            textX = SHOP_ROW_X + 12,
        )
        if (debugVisible) {
            shopLayer.createUiButton(
                x = SHOP_ROW_X,
                y = SHOP_DEBUG_Y,
                width = SHOP_ROW_W,
                height = SHOP_BUTTON_H,
                label = "+50G Debug",
                background = RGBA(88, 92, 112, 255),
                fontSize = 15.0,
                textX = SHOP_ROW_X + 12,
            )
        }
        shopLayer.createUiButton(
            x = SHOP_CLOSE_X,
            y = SHOP_CLOSE_Y,
            width = SHOP_CLOSE_W,
            height = SHOP_BUTTON_H,
            label = "閉じる",
            background = RGBA(95, 105, 122, 255),
            fontSize = 16.0,
            textX = SHOP_CLOSE_X + 43,
        )
    }

    private fun buyItem(item: ItemData) {
        if (gold < item.price) {
            showShopPanel("不足G あと${item.price - gold}G")
            return
        }
        gold -= item.price
        itemCounts[item.itemId] = itemCounts.getOrDefault(item.itemId, 0) + 1
        requestSave()
        showShopPanel("購入しました")
    }

    private fun addDebugGold() {
        gold += 50
        requestSave()
        showShopPanel("+50G")
    }

    private fun shopLabel(item: ItemData): String =
        "${item.name} ${item.price}G 所持:${itemCounts.getOrDefault(item.itemId, 0)}"

    private fun hideShopPanel() {
        if (::shopLayer.isInitialized) {
            shopLayer.visible = false
            shopLayer.removeChildren()
        }
        encounterTitleText?.visible = true
        encounterStatsText?.visible = true
    }

    private fun hideT1ClearLayer() {
        if (::t1ClearLayer.isInitialized) {
            t1ClearLayer.visible = false
            t1ClearLayer.removeChildren()
        }
        encounterTitleText?.visible = true
        encounterStatsText?.visible = true
    }

    private fun setActiveHpToOneForDebug() {
        activeCompanionIds.forEach { instanceId ->
            val index = companions.indexOfFirst { it.instanceId == instanceId }
            if (index >= 0) {
                companions[index] = companions[index].copy(hp = 1)
            }
        }
        tappedCell = null
        handoffCell = null
        player.clearMovement()
        lastFacilityAction = "debug active hp1"
        saveStatus = "hp1"
        requestSave()
        showEncounterResult(
            "HP1デバッグ",
            "active companionのHPを1にしました\nOKを押すとフィールドへ戻ります",
        )
        encounterLayer.visible = true
    }

    private fun clampCompanionVitals() {
        companions.indices.forEach { index ->
            val companion = companions[index]
            val creature = CreatureDatabase.get(companion.creatureId) ?: return@forEach
            companions[index] = companion.copy(
                hp = companion.hp.coerceIn(0, Leveling.maxHp(creature.hp, companion.level)),
                mp = companion.mp.coerceIn(0, Leveling.maxMp(creature.mp, companion.level)),
            )
        }
    }

    private fun syncActiveCompanions() {
        activeCompanionIds.clear()
        activeCompanionIds += partyCompanionIds.take(MAX_ACTIVE_SIZE)
    }

    private fun isInside(
        x: Double,
        y: Double,
        left: Double,
        top: Double,
        width: Double,
        height: Double,
    ): Boolean =
        x >= left && x <= left + width && y >= top && y <= top + height

    private fun consumeUiPointer() {
        inputLockedUntilRelease = true
        inputLockSeconds = 0.15
    }

    private fun updateExitSideLock(before: GridCell, after: GridCell) {
        lockedExitSide = when (lockedExitSide) {
            ExitSide.West -> if (after.col > before.col) null else lockedExitSide
            ExitSide.East -> if (after.col < before.col) null else lockedExitSide
            null -> null
        }
    }

    private fun rebuildRanchCompanions() {
        ranchCompanionLayer?.removeFromParent()
        ranchCompanionLayer = null
        ranchCompanionActors.clear()
        if (map.id != T1MapProgress.RANCH_MAP_ID || companions.isEmpty()) return

        val layer = actorLayer.container()
        ranchCompanionLayer = layer
        val trainingCompanions = trainingCompanionIds.mapNotNull { id ->
            companions.firstOrNull { it.instanceId == id }
        }.take(MAX_TRAINING_SIZE)
        trainingCompanions.forEachIndexed { index, companion ->
            val creature = CreatureDatabase.get(companion.creatureId) ?: return@forEachIndexed
            val cell = RANCH_TRAINING_CELLS.getOrNull(index) ?: return@forEachIndexed
            addRanchCompanionActor(layer, companion, creature, cell, index)
        }

        val normalLimit = (MAX_RANCH_COMPANIONS - trainingCompanions.size).coerceAtLeast(0)
        selectedRanchCompanions()
            .filterNot { companion -> companion.instanceId in trainingCompanionIds }
            .take(normalLimit)
            .forEachIndexed { index, companion ->
                val creature = CreatureDatabase.get(companion.creatureId) ?: return@forEachIndexed
                val cell = RANCH_COMPANION_CELLS.getOrNull(index) ?: return@forEachIndexed
                if (!isRanchCompanionFloor(cell)) return@forEachIndexed
                addRanchCompanionActor(layer, companion, creature, cell, trainingCompanions.size + index)
        }
        updatePlayerView()
    }

    private fun addRanchCompanionActor(
        layer: Container,
        companion: CompanionState,
        creature: CreatureData,
        cell: GridCell,
        index: Int,
    ) {
        val view = layer.container()
        drawCreatureIcon(view, companion.creatureId, 2, 0, 28)
        if (companion.instanceId in partyCompanionIds) {
            view.solidRect(10, 10, RGBA(240, 218, 112, 230)) { position(21, 21) }
            view.text("P", textSize = 8.0, color = Colors.WHITE, font = StarSagaFonts.font) {
                position(23, 20)
            }
        }
        if (companion.instanceId in trainingCompanionIds) {
            view.solidRect(10, 10, RGBA(116, 228, 224, 230)) { position(1, 21) }
            view.text("T", textSize = 8.0, color = Colors.WHITE, font = StarSagaFonts.font) {
                position(3, 20)
            }
        }
        val actor = RanchCompanionActor(
            companion = companion,
            creature = creature,
            view = view,
            cell = cell,
            moveIndex = index,
        )
        ranchCompanionActors += actor
        updateRanchCompanionView(actor)
    }

    private fun selectedRanchCompanions(): List<CompanionState> {
        val byId = companions.associateBy { it.instanceId }
        val selected = mutableListOf<CompanionState>()
        partyCompanionIds.forEach { instanceId ->
            byId[instanceId]?.let { selected += it }
        }
        companions.forEach { companion ->
            if (selected.none { it.instanceId == companion.instanceId }) {
                selected += companion
            }
        }
        return selected.take(MAX_RANCH_COMPANIONS)
    }

    private fun isRanchCompanionFloor(cell: GridCell): Boolean =
        map.tileAt(cell.col, cell.row) == TileType.Floor &&
            cell.col in 1 until map.columns - 1 &&
            cell.row in 1 until map.rows - 1

    private fun updateRanchCompanionView(actor: RanchCompanionActor) {
        actor.view.position(
            actor.cell.col * MapData.TILE_SIZE,
            actor.cell.row * MapData.TILE_SIZE,
        )
    }

    private fun showRanchCompanionMessageAt(worldX: Double, worldY: Double): Boolean {
        val actor = ranchCompanionActors.lastOrNull { ranchCompanionHit(it, worldX, worldY) } ?: return false
        tappedCell = null
        handoffCell = null
        pendingFacilityAction = null
        player.clearMovement()
        val detail = if (actor.companion.instanceId in trainingCompanionIds) {
            "${actor.creature.name} Lv${actor.companion.level}：星の光を浴びている\n育成中：1時間あたり${TRAINING_EXP_PER_HOUR}EXP"
        } else {
            "${actor.creature.name} Lv${actor.companion.level}：${ranchCompanionMood(actor)}"
        }
        showEncounterResult(
            "${actor.creature.name} Lv${actor.companion.level}",
            detail,
        )
        encounterLayer.visible = true
        return true
    }

    private fun ranchCompanionHit(actor: RanchCompanionActor, worldX: Double, worldY: Double): Boolean =
        isInside(worldX, worldY, actor.view.x, actor.view.y, MapData.TILE_SIZE.toDouble(), MapData.TILE_SIZE.toDouble())

    private fun ranchCompanionMood(actor: RanchCompanionActor): String {
        val moods = RANCH_COMPANION_MOODS
        val index = (actor.creature.id + actor.companion.level + actor.moveIndex).mod(moods.size)
        return moods[index]
    }

    private fun drawPlayer() {
        playerView?.removeFromParent()
        playerView = actorLayer.container {
            solidRect(18, 18, RGBA(226, 214, 178, 255)) {
                position(7, 2)
            }
            solidRect(12, 6, RGBA(78, 145, 178, 255)) {
                position(10, 7)
            }
            solidRect(20, 15, RGBA(52, 100, 145, 255)) {
                position(6, 16)
            }
            solidRect(6, 10, RGBA(32, 58, 86, 255)) {
                position(3, 18)
            }
            solidRect(6, 10, RGBA(32, 58, 86, 255)) {
                position(23, 18)
            }
            solidRect(4, 4, RGBA(236, 218, 92, 255)) {
                position(14, 20)
            }
        }
        updatePlayerView()
    }

    private fun updatePlayerView() {
        playerView?.position(
            player.currentCell.col * MapData.TILE_SIZE,
            player.currentCell.row * MapData.TILE_SIZE,
        )
    }

    private fun applyCamera() {
        world.position(-camera.state.left, -camera.state.top)
    }

    private fun drawObjectivePanel() {
        objectiveLayer.position(8, 42)
        objectiveLayer.solidRect(286, 50, RGBA(7, 12, 28, 176))
        objectiveLayer.solidRect(286, 2, RGBA(70, 112, 145, 150))
        objectiveText = objectiveLayer.text(
            "",
            textSize = 11.0,
            color = RGBA(238, 244, 236, 255),
            font = StarSagaFonts.font,
        ) {
            position(8, 6)
        }
    }

    private fun updateObjectivePanel() {
        objectiveLayer.visible = encounterUiState == EncounterUiState.Hidden && !companionPanelOpen
        if (!objectiveLayer.visible) return
        val objective = currentObjective()
        objectiveText?.text = buildString {
            appendLine("目標: ${objective.main}")
            append(objective.hint ?: ObjectiveResolver.t1StatusText(befriendedCreatureIds))
        }
    }

    private fun currentObjective(): T1Objective =
        if (map.id == T1MapProgress.RANCH_MAP_ID) {
            T1Objective("牧場端末で仲間を整理しよう", ObjectiveResolver.t1StatusText(befriendedCreatureIds))
        } else {
            ObjectiveResolver.resolveT1(
                T1ObjectiveContext(
                    currentAreaId = T1MapProgress.areaIdFor(map.id, player.currentCell),
                    befriendedCreatureIds = befriendedCreatureIds,
                    t1BossCleared = t1BossCleared,
                    reachedT1Outpost = reachedT1Outpost,
                    t1OutpostWarpUnlocked = t1OutpostWarpUnlocked,
                ),
            )
        }

    private fun hasDamagedParty(): Boolean =
        partyCompanionIds.any { instanceId ->
            val companion = companions.firstOrNull { it.instanceId == instanceId } ?: return@any false
            val creature = CreatureDatabase.get(companion.creatureId) ?: return@any false
            companion.hp < Leveling.maxHp(creature.hp, companion.level) ||
                companion.mp < Leveling.maxMp(creature.mp, companion.level)
        }

    private fun showInitialGuideIfNeeded() {
        if (tutorialSeen) return
        tutorialSeen = true
        requestSave()
        showEncounterResult(
            "StarSagaへようこそ",
            "草むらを歩くと星の仲間に出会えます\n回復所でHP/MPを回復できます\n牧場では仲間の入れ替えができます",
        )
        encounterLayer.visible = true
    }

    private fun drawDebug() {
        debugLayer.position(8, 8)
        debugLayer.visible = debugVisible
        debugLayer.solidRect(144, 118, RGBA(0, 0, 0, 115))
        debugText = debugLayer.text(
            "",
            textSize = 9.0,
            color = RGBA(230, 238, 246, 210),
            font = StarSagaFonts.font,
        ) {
            position(6, 4)
        }
    }

    private fun updateDebugText() {
        debugLayer.visible = debugVisible && encounterUiState == EncounterUiState.Hidden && !companionPanelOpen
        val tap = tappedCell
        val handoff = handoffCell
        val target = player.targetCell
        val move = lastMove
        val cameraDebug = camera.debug
        val tile = map.tileAt(player.currentCell.col, player.currentCell.row)
            ?: TileType.Wall
        val battle = battleState
        val battleHpSummary = battle?.activeCompanions
            ?.joinToString(",") { "${it.name}:${it.currentHp}/${it.maxHp}" }
        debugText?.text = buildString {
            appendLine("map: ${map.id}")
            appendLine("cur: ${player.currentCell.col},${player.currentCell.row} tile:${tile.name}")
            appendLine("heal: $HEAL_DEBUG_COORDS")
            appendLine("facility: ${lastFacilityAction ?: "-"}")
            appendLine("gold: $gold ${inventorySummary()}")
            appendLine("tap: ${tap?.let { "${it.col},${it.row}" } ?: "-"}")
            appendLine("target: ${target?.let { "${it.col},${it.row}" } ?: "-"}")
            appendLine("camera: ${camera.state.left.toInt()},${camera.state.top.toInt()}")
            appendLine("page: ${cameraDebug.pageX},${cameraDebug.pageY}")
            appendLine("lock: ${inputLockedUntilRelease || inputLockSeconds > 0.0}")
            appendLine("encounter: $encounterUiState")
            appendLine("party:${partyCompanionIds.size} active:${activeCompanionIds.size}")
            appendLine("battle: ${battle?.phase ?: "-"}")
            append("save: $saveStatus/$savedCount")
        }
    }

    private fun handoffDebugText(): String =
        map.exits.joinToString(" ") { "${it.side}:${it.triggerCol}" }.ifEmpty { "-" }

    private fun formatRoll(value: Double): String =
        ((value * 1000).toInt() / 1000.0).toString()

    private fun formatPercent(value: Double): String =
        "${(value * 100).toInt()}%"

    private companion object {
        const val FIGHT_BUTTON_X = 54.0
        const val SKILL_BUTTON_X = 160.0
        const val SKILL_BUTTON_Y = 448.0
        const val SKILL_BUTTON_WIDTH = 124.0
        const val ITEM_BUTTON_X = 54.0
        const val ITEM_BUTTON_Y = 448.0
        const val ITEM_BUTTON_WIDTH = 124.0
        const val ROW_BUTTON_X = 54.0
        const val ROW_BUTTON_W = 252.0
        const val ROW_BUTTON_H = 32.0
        const val ROW1_Y = 398.0
        const val ROW2_Y = 436.0
        const val ROW3_Y = 474.0
        const val ROW_BACK_X = 110.0
        const val ROW_BACK_Y = 512.0
        const val ROW_BACK_W = 140.0
        const val SHOP_TEXT_X = 46.0
        const val SHOP_TITLE_Y = 316.0
        const val SHOP_GOLD_Y = 348.0
        const val SHOP_MESSAGE_Y = 372.0
        const val SHOP_ROW_X = 54.0
        const val SHOP_ROW_W = 252.0
        const val SHOP_BUTTON_H = 32.0
        const val SHOP_ITEM1_Y = 408.0
        const val SHOP_ITEM2_Y = 446.0
        const val SHOP_DEBUG_Y = 484.0
        const val SHOP_CLOSE_X = 110.0
        const val SHOP_CLOSE_Y = 520.0
        const val SHOP_CLOSE_W = 140.0
        const val RUN_BUTTON_X = 190.0
        const val OK_BUTTON_X = FIGHT_BUTTON_X
        const val BUTTON_Y = 492.0
        const val BUTTON_WIDTH = 124.0
        const val BUTTON_HEIGHT = 42.0
        const val BATTLE_CMD_W = 124.0
        const val BATTLE_CMD_H = BUTTON_HEIGHT
        const val BATTLE_CMD_GAP_X = 8.0
        const val BATTLE_CMD_GAP_Y = 6.0
        const val BATTLE_CMD_GRID_W = BATTLE_CMD_W * 2 + BATTLE_CMD_GAP_X
        const val BATTLE_ATTACK_X = 52.0
        const val BATTLE_SKILL_X = BATTLE_ATTACK_X + BATTLE_CMD_W + BATTLE_CMD_GAP_X
        const val BATTLE_ITEM_X = BATTLE_ATTACK_X
        const val BATTLE_RUN_X = BATTLE_SKILL_X
        const val BATTLE_CMD_TOP_Y = 500.0
        const val BATTLE_CMD_BOTTOM_Y = BATTLE_CMD_TOP_Y + BATTLE_CMD_H + BATTLE_CMD_GAP_Y
        const val BATTLE_TITLE_X = 42.0
        const val BATTLE_TITLE_Y = 52.0
        const val BATTLE_STATS_X = 42.0
        const val BATTLE_STATS_Y = 92.0
        const val AUTO_BUTTON_X = 250.0
        const val AUTO_BUTTON_Y = 448.0
        const val AUTO_BUTTON_W = 70.0
        const val AUTO_BUTTON_H = 30.0
        const val AUTO_BATTLE_STEP_SECONDS = 0.55
        const val AUTO_START_DELAY_SECONDS = 0.65
        const val AUTO_RESULT_CLOSE_SECONDS = 1.2
        val COMMAND_BUTTON_COLOR = RGBA(58, 88, 145, 255)
        val COMMAND_ESCAPE_BUTTON_COLOR = RGBA(82, 90, 108, 255)
        const val MAX_PARTY_SIZE = 6
        const val MAX_ACTIVE_SIZE = 3
        const val MAX_COMPANIONS = 99
        const val RECRUIT_THRESHOLD = 5
        const val MAX_RANCH_COMPANIONS = 6
        const val MAX_TRAINING_SIZE = 3
        const val TRAINING_EXP_PER_HOUR = 8
        const val TRAINING_HOUR_MILLIS = 60L * 60L * 1000L
        const val TRAINING_MAX_ACCUMULATION_MILLIS = 8L * TRAINING_HOUR_MILLIS
        const val STARTER_CREATURE_ID = 1
        const val COMPANION_BUTTON_X = 284.0
        const val COMPANION_BUTTON_Y = 88.0
        const val HP1_BUTTON_X = 284.0
        const val HP1_BUTTON_Y = 128.0
        const val COMPANION_BUTTON_W = 64.0
        const val COMPANION_BUTTON_H = 34.0
        const val COMPANION_PAGE_SIZE = 6
        const val COMPANION_GRID_COLUMNS = 3
        const val COMPANION_GRID_X = 50
        const val COMPANION_GRID_Y = 314
        const val COMPANION_GRID_CELL_W = 92
        const val COMPANION_GRID_CELL_H = 62
        const val COMPANION_GRID_HIT_W = 70.0
        const val COMPANION_GRID_HIT_H = 58.0
        const val COMPANION_LEAD_X = 224.0
        const val COMPANION_TOGGLE_X = 224.0
        const val COMPANION_TRAINING_X = 224.0
        const val COMPANION_ACTION_Y = 226.0
        const val COMPANION_TOGGLE_Y = 258.0
        const val COMPANION_TRAINING_Y = 290.0
        const val COMPANION_ACTION_W = 78.0
        const val COMPANION_TRAINING_W = 102.0
        const val COMPANION_ACTION_H = 28.0
        const val PARTY_RANCH_BUTTON_X = 86.0
        const val PARTY_RANCH_BUTTON_Y = 408.0
        const val PARTY_RANCH_BUTTON_W = 188.0
        const val PARTY_RANCH_BUTTON_H = 30.0
        const val COMPANION_PREV_X = 42.0
        const val COMPANION_NEXT_X = 238.0
        const val COMPANION_NAV_Y = 444.0
        const val COMPANION_NAV_W = 78.0
        const val COMPANION_NAV_H = 34.0
        const val COMPANION_CLOSE_X = 122.0
        const val COMPANION_CLOSE_Y = 444.0
        const val COMPANION_CLOSE_W = 116.0
        const val COMPANION_CLOSE_H = 38.0
        val COMPANION_PANEL_LAYOUT = UiPanelLayout(
            x = 21.0,
            y = 120.0,
            width = 318.0,
            height = 378.0,
            padding = 18.0,
            footerHeight = 76.0,
        )
        const val HEAL_DEBUG_COORDS = "heal 6,4 7,4 shop 9,4 10,4 ranch 12,4 13,4"
        val MESSAGE_TILES = setOf(TileType.StarLamp, TileType.Sign, TileType.Planned, TileType.DeepGate)
        val FACILITY_TILES = setOf(TileType.Heal, TileType.Shop, TileType.Ranch, TileType.Exit, TileType.TrainingPad) + MESSAGE_TILES
        val RANCH_TRAINING_CELLS = listOf(
            GridCell(5, 5),
            GridCell(5, 7),
            GridCell(5, 9),
        )
        val RANCH_COMPANION_CELLS = listOf(
            GridCell(7, 5),
            GridCell(13, 6),
            GridCell(7, 8),
            GridCell(12, 8),
            GridCell(7, 10),
            GridCell(13, 9),
        )
        val RANCH_COMPANION_MOODS = listOf(
            "のんびりしている",
            "星空を見ている",
            "草の匂いをかいでいる",
            "小さく体をゆらしている",
            "こちらを見上げている",
        )
    }
}
