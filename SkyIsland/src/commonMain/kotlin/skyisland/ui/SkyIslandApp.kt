package skyisland.ui

import korlibs.event.Key
import korlibs.image.color.Colors
import korlibs.image.font.Font
import korlibs.image.font.readTtfFont
import korlibs.io.file.std.resourcesVfs
import korlibs.korge.Korge
import korlibs.korge.input.keys
import korlibs.korge.view.Container
import korlibs.korge.view.SolidRect
import korlibs.korge.view.Text
import korlibs.korge.view.addUpdater
import korlibs.korge.view.position
import korlibs.korge.view.solidRect
import korlibs.korge.view.text
import korlibs.math.geom.Size
import kotlin.time.DurationUnit
import skyisland.data.Difficulty
import skyisland.data.EquipmentSlot
import skyisland.data.GameTables
import skyisland.data.Ids
import skyisland.data.SkillEffect
import skyisland.data.createSaveRepository
import skyisland.data.createBalanceLogRepository
import skyisland.game.Cell
import skyisland.game.Chunk
import skyisland.game.DungeonGenerator
import skyisland.game.PathFinder
import skyisland.game.RunEnd
import skyisland.game.SkyIslandGame
import skyisland.game.Tile

suspend fun main() = Korge(
    windowSize = Size(720, 1280),
    backgroundColor = Colors["#DDF4FF"],
) {
    val font: Font? = try {
        resourcesVfs["NotoSansJP.ttf"].readTtfFont()
    } catch (e: Exception) {
        null
    }
    val game = SkyIslandGame(createSaveRepository(), balanceLog = createBalanceLogRepository())
    SkyIslandHud(this, game, font).build()
}

private data class Btn(
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int = 48,
    val rect: SolidRect,
    val label: Text,
    val action: () -> Unit,
    var enabled: Boolean = true,
)

private enum class PanelMode { ENTRANCE, FORGE, CHARACTER }
private enum class ForgeTab { CRAFT, ENHANCE, EQUIP }
private data class SkillButtonUi(
    var id: String,
    val button: Btn,
    val effect: Text,
    val level: Text,
    val progressBg: SolidRect,
    val progressBar: SolidRect,
)

private class SkyIslandHud(
    private val root: Container,
    private val game: SkyIslandGame,
    private val font: Font?,
) {
    private val cells = mutableListOf<SolidRect>()
    private val attackOverlays = mutableListOf<SolidRect>()
    private val playerBorders = mutableListOf<SolidRect>()
    private val symbolTexts = mutableListOf<Text>()
    private val miniMap = mutableListOf<SolidRect>()
    private lateinit var status: Text
    private lateinit var message: Text
    private lateinit var log1: Text
    private lateinit var log2: Text
    private lateinit var result: Text
    private lateinit var panel: Text
    private lateinit var staminaLabel: Text
    private lateinit var story: Text
    private lateinit var crystalFlash: SolidRect
    private val buttons = mutableListOf<Btn>()
    private var autoElapsed = 0.0
    private var targetElapsed = 0.0
    private var targetCell: Cell? = null
    private var crystalFlashElapsed = 0.0
    private var warningBlinkElapsed = 0.0
    private var pulseElapsed = 0.0
    private val flashTimers = mutableMapOf<Cell, Double>()
    private var observedCrystallizationEffects = 0
    private var panelMode = PanelMode.ENTRANCE
    private var forgeTab = ForgeTab.CRAFT
    private var selectedEquipmentId = Ids.EQ_MIST_COAT
    private var selectedFloorId = Ids.FLOOR_01
    private var lastDifficulty = Difficulty.RELAXED
    private val arrowButtons = mutableListOf<Btn>()
    private lateinit var autoButton: Btn
    private lateinit var entranceNavButton: Btn
    private lateinit var forgeNavButton: Btn
    private lateinit var characterNavButton: Btn
    private lateinit var floor1Button: Btn
    private lateinit var floor2Button: Btn
    private lateinit var floor3Button: Btn
    private lateinit var relaxedButton: Btn
    private lateinit var adventureButton: Btn
    private lateinit var returnToBaseButton: Btn
    private lateinit var nextFloorButton: Btn
    private lateinit var skipTutorialButton: Btn
    private lateinit var forgeCraftTabButton: Btn
    private lateinit var forgeEnhanceTabButton: Btn
    private lateinit var forgeEquipTabButton: Btn
    private lateinit var forgeCraftButton: Btn
    private lateinit var forgeEnhanceButton: Btn
    private lateinit var forgeEquipButton: Btn
    private val forgeEquipmentButtons = mutableListOf<Pair<String, Btn>>()
    private val skillButtons = mutableListOf<SkillButtonUi>()
    private lateinit var healHerbButton: Btn
    private lateinit var escapeButton: Btn
    private var debugOverlayVisible = false
    private lateinit var debugBg: SolidRect
    private lateinit var debugText: Text
    private lateinit var debugStaminaButton: Btn
    private var knownFloor1Cleared = game.saveData.floor1Cleared
    private var knownFloor3Cleared = game.saveData.floor3Cleared
    private var endingVisible = false
    private lateinit var endingBg: SolidRect
    private lateinit var endingText: Text
    private var uiStoryText = ""
    private var uiStorySecondsRemaining = 0.0

    fun build() = with(root) {
        label("SkyIsland  v1.0", 34.0, Colors["#1A4F75"]).position(22, 18)
        status  = label("", 25.0, Colors["#1A3A5C"]).position(22, 72)
        message = label("", 24.0, Colors["#8B4A00"]).position(22, 108)
        log1    = label("", 18.0, Colors["#556677"]).position(22, 136)
        log2    = label("", 18.0, Colors["#778899"]).position(22, 158)
        result  = label("", 18.0, Colors["#1A3A5C"]).position(22, 900)
        panel   = label("", 21.0, Colors["#1A3A5C"]).position(22, 960)
        staminaLabel = label("", 17.0, Colors["#1A3A5C"]).position(275, 1138)
        story   = label("", 23.0, Colors["#8A4B08"]).position(80, 560)
        buildGrid()
        buildMiniMap()
        crystalFlash = solidRect(620.0, 620.0, Colors["#FFF2A8"]).position(GRID_ORIGIN_X, GRID_ORIGIN_Y)
        crystalFlash.visible = false
        arrowButton("↑", 90, 890, 0, -1)
        arrowButton("←", 15, 960, -1, 0)
        arrowButton("↓", 90, 960, 0, 1)
        arrowButton("→", 165, 960, 1, 0)
        autoButton = button("オート ON", 570, 62, 125, 50) { game.toggleAuto() }
        skillButton(Ids.SKILL_WIND_BLADE, 285, 890)
        skillButton(Ids.SKILL_MIST_HEAL, 425, 890)
        skillButton(Ids.SKILL_CLOUD_SHIELD, 285, 960)
        skillButton(Ids.SKILL_THUNDER_CLOUD, 425, 960)
        healHerbButton = button("回復草", 570, 895, 130, 58) { game.useItem(Ids.ITEM_HEAL_HERB) }
        escapeButton = button("脱出", 570, 960, 130, 48) {
            game.escape()
        }
        entranceNavButton = button("入口", 25, 1215, 70) { showEntrance() }
        forgeNavButton = button("鍛冶屋", 105, 1215, 80) { showForge() }
        characterNavButton = button("キャラ", 195, 1215, 70) { showCharacter() }
        floor1Button = button("F1 霧の島", 245, 1080, 130) { selectedFloorId = Ids.FLOOR_01; showEntrance() }
        floor2Button = button("F2 嵐の島", 385, 1080, 165) { selectedFloorId = Ids.FLOOR_02; showEntrance() }
        floor3Button = button("F3 光の島", 560, 1080, 130) { selectedFloorId = Ids.FLOOR_03; showEntrance() }
        relaxedButton = button("のんびり", 275, 1215, 85) { startSelectedFloor(Difficulty.RELAXED) }
        adventureButton = button("冒険", 370, 1215, 65) { startSelectedFloor(Difficulty.ADVENTURE) }
        returnToBaseButton = button("拠点に戻る", 460, 1215, 130) {
            game.returnToBase()
            showEntrance()
        }
        nextFloorButton = button("次のフロアへ▶", 245, 1215, 205, 48) {
            val nextFloor = nextAvailableFloor() ?: return@button
            if (game.saveData.stamina < 1) {
                game.returnToBase()
                showEntrance()
                return@button
            }
            game.returnToBase()
            selectedFloorId = nextFloor
            startSelectedFloor(lastDifficulty)
        }
        skipTutorialButton = button("チュートリアルをスキップ", 25, 1145, 245) {
            game.skipTutorial()
            showEntrance()
        }
        buildForgeButtons()
        buildDebugOverlay()
        buildEndingOverlay()
        showEntrance()

        keys {
            down(Key.UP)    { targetCell = null; game.move(0, -1) }
            down(Key.DOWN)  { targetCell = null; game.move(0,  1) }
            down(Key.LEFT)  { targetCell = null; game.move(-1, 0) }
            down(Key.RIGHT) { targetCell = null; game.move( 1, 0) }
        }

        addUpdater { dt ->
            val elapsed = dt.toDouble(DurationUnit.SECONDS)
            val vi = root.stage?.views
            if (vi != null) {
                TouchState.install(vi.gameWindow.androidContextAny)
                val tap = TouchState.consumeTap()
                if (tap != null) {
                    val pW = vi.gameWindow.width.toFloat()
                    val pH = vi.gameWindow.height.toFloat()
                    val renderScale = minOf(pW / vi.virtualWidth.toFloat(), pH / vi.virtualHeight.toFloat())
                    val leftPad = (pW - vi.virtualWidth.toFloat() * renderScale) / 2f
                    val topPad = (pH - vi.virtualHeight.toFloat() * renderScale) / 2f
                    val vx = (tap.x - leftPad) / renderScale
                    val vy = (tap.y - topPad) / renderScale
                    triggerButton(vx, vy)
                }
            }
            SwipeState.consumeSwipe()?.let { handleDirectionInput(it.dx, it.dy) }
            if (observedCrystallizationEffects != game.crystallizationEffectCount) {
                observedCrystallizationEffects = game.crystallizationEffectCount
                crystalFlashElapsed = 0.35
            }
            crystalFlashElapsed = maxOf(0.0, crystalFlashElapsed - elapsed)
            crystalFlash.visible = crystalFlashElapsed > 0.0
            while (game.pendingFlash.isNotEmpty()) {
                val event = game.pendingFlash.removeFirst()
                flashTimers[event.cell] = 0.25
            }
            flashTimers.keys.toList().forEach { cell ->
                flashTimers[cell] = (flashTimers[cell] ?: 0.0) - elapsed
            }
            flashTimers.entries.removeAll { (_, remaining) -> remaining <= 0.0 }
            game.tickStory(elapsed)
            uiStorySecondsRemaining = maxOf(0.0, uiStorySecondsRemaining - elapsed)
            warningBlinkElapsed += elapsed
            autoElapsed += elapsed
            targetElapsed += elapsed
            if (targetElapsed >= 0.15) {
                targetElapsed = 0.0
                advanceTowardTarget()
            }
            if (autoElapsed >= 0.5 && targetCell == null) {
                autoElapsed = 0.0
                game.autoStep()
            }
            refresh()
        }
    }

    private fun Container.buildGrid() {
        val size = GRID_CELL_SIZE.toDouble()
        for (y in 0 until DungeonGenerator.CHUNK_SIZE) for (x in 0 until DungeonGenerator.CHUNK_SIZE) {
            cells += solidRect(size - 1, size - 1, Colors.WHITE).position(GRID_ORIGIN_X + x * size, GRID_ORIGIN_Y + y * size)
            attackOverlays += solidRect(size - 1, size - 1, Colors["#FFCDD259"]).position(GRID_ORIGIN_X + x * size, GRID_ORIGIN_Y + y * size).also {
                it.visible = false
            }
            symbolTexts += label("", 20.0, Colors.WHITE).position(GRID_ORIGIN_X + x * size + 8, GRID_ORIGIN_Y + y * size + 4)
        }
        playerBorders += solidRect(size, PLAYER_BORDER_SIZE, Colors.WHITE)
        playerBorders += solidRect(size, PLAYER_BORDER_SIZE, Colors.WHITE)
        playerBorders += solidRect(PLAYER_BORDER_SIZE, size, Colors.WHITE)
        playerBorders += solidRect(PLAYER_BORDER_SIZE, size, Colors.WHITE)
        playerBorders.forEach { it.visible = false }
    }

    private fun Container.buildMiniMap() {
        val mmX = GRID_ORIGIN_X + 8 * GRID_CELL_SIZE
        val mmY = GRID_ORIGIN_Y
        for (y in 0..2) for (x in 0..2) {
            miniMap += solidRect(34.0, 34.0, Colors["#ADC4D1"]).position(mmX + x * 38, mmY + y * 38)
        }
        label("MAP", 15.0, Colors["#24658C"]).position(mmX, mmY - 18)
    }

    private fun refresh() {
        val dungeon = game.dungeon
        val chunkX = game.player.cell.x / DungeonGenerator.CHUNK_SIZE
        val chunkY = game.player.cell.y / DungeonGenerator.CHUNK_SIZE
        if (dungeon != null) {
            val enemyCells = dungeon.enemies.filter { it.hp > 0 }.map { it.cell }.toSet()
            val attackCells = dungeon.enemies.filter { it.hp > 0 }
                .flatMap { it.cell.neighbors() }
                .filter { it != game.player.cell && it !in enemyCells }
                .toSet()
            cells.forEachIndexed { index, rect ->
                val localX = index % DungeonGenerator.CHUNK_SIZE
                val localY = index / DungeonGenerator.CHUNK_SIZE
                val cell = skyisland.game.Cell(chunkX * 10 + localX, chunkY * 10 + localY)
                rect.color = if (flashTimers.containsKey(cell)) Colors.WHITE else when {
                    cell == game.player.cell -> Colors["#2478B5"]
                    dungeon.bossFogTurns > 0 && cell.distance(game.player.cell) > 2 -> Colors["#66757F"]
                    dungeon.enemies.any { it.enemyId == Ids.BOSS_KASUMI && it.hp > 0 && it.turn % 3 == 2 && it.cell.distance(cell) == 1 } -> Colors["#EF9A9A"]
                    dungeon.enemies.any { isBossId(it.enemyId) && it.hp > 0 && it.cell == cell } -> Colors["#C0392B"]
                    dungeon.enemies.any { it.hp > 0 && it.cell == cell } -> Colors["#E96D71"]
                    dungeon.groundItems.any { it.cell == cell } -> Colors["#E9C46A"]
                    cell in dungeon.chests -> Colors["#E9C46A"]
                    dungeon.tiles[cell] == Tile.WALL -> Colors["#7A91A1"]
                    dungeon.tiles[cell] == Tile.EXIT -> Colors["#27AE60"]
                    else -> Colors.WHITE
                }
                val lightning = cell in dungeon.lightningPreview
                attackOverlays[index].visible = cell in attackCells || lightning
                if (attackOverlays[index].visible) {
                    attackOverlays[index].color = if (lightning) Colors["#EF5350"] else Colors["#FFCDD259"]
                    attackOverlays[index].alpha = if (lightning) 0.4 else 0.25
                }
                updateCellSymbol(symbolTexts[index], cell, dungeon)
            }
            positionPlayerBorder()
            miniMap.forEachIndexed { index, rect ->
                val cols = dungeonChunkCols(dungeon)
                val chunk = Chunk(index % 3, index / 3)
                rect.visible = chunk.x < cols && chunk.y < dungeonChunkRows(dungeon)
                rect.color = when {
                    chunk == Chunk(chunkX, chunkY) -> Colors["#2478B5"]
                    chunk in dungeon.visitedChunks -> Colors["#FFFFFF"]
                    else -> Colors["#ADC4D1"]
                }
            }
        } else {
            attackOverlays.forEach { it.visible = false }
            playerBorders.forEach { it.visible = false }
            symbolTexts.forEach { it.text = "" }
        }
        status.text = if (dungeon == null && game.saveData.floor1Cleared) {
            "スタミナ: ${game.saveData.stamina}/${SkyIslandGame.MAX_STAMINA}  ATK ${game.attack}  DEF ${game.defense}"
        } else {
            val bossName = dungeon?.floorId?.let(::bossNameForFloor) ?: "カスミ"
            "HP ${game.player.hp}/${game.maxHp}  ATK ${game.attack}  DEF ${game.defense}  $bossName: ${game.bossStrength()}"
        }
        message.text = game.message
        message.color = when {
            game.result == null && game.chunkTurns >= 100 -> Colors["#D62828"]
            game.result == null && game.chunkTurns >= 80 -> Colors["#C69200"]
            else -> Colors["#8B4A00"]
        }
        log1.text = game.actionLog.getOrNull(1) ?: ""
        log2.text = game.actionLog.getOrNull(0) ?: ""
        log1.color = Colors["#556677"]
        log2.color = Colors["#778899"]
        message.visible = game.result != null || game.chunkTurns < 100 || (warningBlinkElapsed % 0.8) < 0.4
        story.text = if (uiStorySecondsRemaining > 0.0) uiStoryText else game.storyText
        story.visible = uiStorySecondsRemaining > 0.0 || game.isStoryVisible
        autoButton.label.text = if (game.saveData.autoMoveEnabled) "オート ON" else "オート OFF"
        autoButton.rect.color = if (game.saveData.autoMoveEnabled) Colors["#A8D5BA"] else Colors["#FFFFFF"]
        refreshSkillButtons()
        setButtonVisible(autoButton, dungeon != null && game.result == null)
        val inDungeon = dungeon != null && game.result == null
        val atBase = dungeon == null
        arrowButtons.forEach { setButtonVisible(it, inDungeon) }
        skillButtons.forEach { ui ->
            val visible = inDungeon
            ui.button.rect.visible = visible
            ui.button.label.visible = visible
            ui.effect.visible = visible
            ui.level.visible = visible
            ui.progressBg.visible = visible
            ui.progressBar.visible = visible
        }
        setButtonVisible(escapeButton, inDungeon)
        setButtonVisible(entranceNavButton, atBase && game.result == null)
        setButtonVisible(forgeNavButton, atBase && game.result == null)
        setButtonVisible(characterNavButton, atBase && game.result == null)
        setButtonVisible(returnToBaseButton, game.result != null)
        val showNextFloor = game.result != null && nextAvailableFloor() != null
        setButtonVisible(nextFloorButton, showNextFloor)
        if (showNextFloor) nextFloorButton.rect.color = Colors["#64B5F6"]
        setButtonVisible(skipTutorialButton, panelMode == PanelMode.ENTRANCE && !game.saveData.tutorialCompleted)
        val herbs = game.saveData.items[Ids.ITEM_HEAL_HERB] ?: 0
        val herbVisible = dungeon != null && game.result == null && herbs > 0
        setButtonVisible(healHerbButton, herbVisible)
        if (herbVisible) {
            healHerbButton.label.text = "回復草×$herbs"
            val lowHp = game.player.hp * 10 < game.maxHp * 3
            healHerbButton.rect.color = if (lowHp) Colors["#FF7043"] else Colors["#C8E6C9"]
        }
        panel.visible = game.result == null
        staminaLabel.visible = panelMode == PanelMode.ENTRANCE && game.result == null && game.saveData.floor1Cleared
        result.text = game.result?.let {
            val materials = it.materials.entries.joinToString(" / ") { (id, amount) ->
                "${GameTables.materials[id]?.name ?: id}×$amount"
            }.ifBlank { "なし" }
            val skills = game.saveData.skills.joinToString(" / ") { skill ->
                val name = GameTables.skills[skill.skillId]?.name ?: skill.skillId
                "$name Lv2まであと${maxOf(0, 10 - skill.proficiency)}回"
            }
            buildString {
                append("リザルト\n")
                append("晴らした霧: ${it.revealedChunks}チャンク  倒した敵: ${it.defeatedEnemies}体\n")
                append("入手素材: $materials\n")
                append("スキル熟練: $skills\n")
                if (!it.nextHint.isNullOrEmpty()) append("${it.nextHint}\n")
                append(it.message)
            }
        }.orEmpty()
        if (!knownFloor3Cleared && game.saveData.floor3Cleared) {
            showEnding()
        }
        knownFloor3Cleared = game.saveData.floor3Cleared
        if (panelMode == PanelMode.FORGE) refreshForgePanel()
        refreshEntranceControls()
        refreshForgeControls()
        refreshBaseGuideButtons()
        if (endingVisible) buttons.forEach { setButtonVisible(it, false) }
        endingBg.visible = endingVisible
        endingText.visible = endingVisible
        debugBg.visible = debugOverlayVisible
        debugText.visible = debugOverlayVisible
        setButtonVisible(debugStaminaButton, debugOverlayVisible)
        if (debugOverlayVisible) refreshDebugOverlay()
    }

    private fun showEntrance() {
        panelMode = PanelMode.ENTRANCE
        if (!game.saveData.floor1Cleared ||
            (selectedFloorId == Ids.FLOOR_02 && game.saveData.stamina < 1) ||
            (selectedFloorId == Ids.FLOOR_03 && (!game.saveData.floor2Cleared || game.saveData.stamina < 1))
        ) {
            selectedFloorId = Ids.FLOOR_01
        } else if (!knownFloor1Cleared && game.saveData.floor1Cleared && !game.saveData.floor2Cleared && game.saveData.stamina >= 1) {
            selectedFloorId = Ids.FLOOR_02
        }
        val hints = mutableListOf<String>()
        if (game.consumeEntranceGoalGuidance()) hints += "目標: カスミを倒して霧を晴らそう"
        if (game.saveData.equippedWeaponId == null) {
            hints += if (game.canCraft(Ids.EQ_WIND_SWORD)) {
                "武器未装備！鍛冶屋で風の剣を作ろう"
            } else {
                "武器未装備（鍛冶屋 → 風の剣）"
            }
        }
        if (game.saveData.equippedArmorId == null &&
            game.saveData.ownedEquipments.any { GameTables.equipments[it]?.slot == EquipmentSlot.ARMOR }
        ) {
            hints += "防具が未装備です！キャラ画面から装備しましょう"
        }
        hints += baseNextHint()
        val nextStage = game.saveData.floor1Cleared && !game.saveData.floor2Cleared
        val lines = mutableListOf<String>()
        if (nextStage) lines += "フロア2に挑戦しよう！"
        lines += "ダンジョン入口  今の強さ: ${game.bossStrength(selectedFloorId)}"
        lines += "のんびり / 冒険 を選んで探索を開始"
        lines += hints.distinct().take(4 - lines.size)
        panel.text = lines.take(4).joinToString("\n")
        staminaLabel.text = if (game.saveData.floor1Cleared) {
            val selected = when (selectedFloorId) {
                Ids.FLOOR_03 -> "選択中: フロア3 光の島（スタミナ-1）"
                Ids.FLOOR_02 -> "選択中: フロア2 嵐の島（スタミナ-1）"
                else -> "選択中: フロア1 霧の島（消費なし）"
            }
            val warning = if (game.saveData.stamina < 1) "\nスタミナが足りません" else ""
            "スタミナ: ${game.saveData.stamina}/${SkyIslandGame.MAX_STAMINA}\n$selected$warning"
        } else {
            ""
        }
        if (!knownFloor1Cleared && game.saveData.floor1Cleared) {
            showUiStory("霧が少し晴れた。奥の島が見えてくる\n→ フロア2に挑戦できるようになりました！", 2.0)
        }
        knownFloor1Cleared = game.saveData.floor1Cleared
    }

    private fun startSelectedFloor(difficulty: Difficulty) {
        lastDifficulty = difficulty
        targetCell = null
        val floorId = if (game.saveData.floor1Cleared) selectedFloorId else Ids.FLOOR_01
        game.startDungeon(difficulty, floorId)
        if (game.dungeon == null) showEntrance()
    }

    private fun nextAvailableFloor(): String? {
        val result = game.result ?: return null
        if (result.reason != RunEnd.CLEAR) return null
        return when (game.dungeon?.floorId) {
            Ids.FLOOR_01 -> Ids.FLOOR_02
            Ids.FLOOR_02 -> Ids.FLOOR_03
            else -> null
        }
    }

    private fun showForge() {
        panelMode = PanelMode.FORGE
        staminaLabel.visible = false
        refreshForgePanel()
    }

    private fun refreshForgePanel() {
        staminaLabel.visible = false
        val mist = game.saveData.materials[Ids.MAT_MIST_CRYSTAL] ?: 0
        val feather = game.saveData.materials[Ids.MAT_WIND_FEATHER] ?: 0
        val def = GameTables.equipments[selectedEquipmentId]
        if (def == null) {
            panel.text = "鍛冶屋  装備データを読み込めません"
            return
        }
        val owned = selectedEquipmentId in game.saveData.ownedEquipments
        val level = game.equipmentLevel(selectedEquipmentId)
        val recipe = def.recipe.entries.joinToString(" + ") { (id, amount) ->
            "${GameTables.materials[id]?.name ?: id}×$amount"
        }
        val detail = when (forgeTab) {
            ForgeTab.CRAFT -> "${def.name}: $recipe  ${if (owned) "所持済み" else if (game.canCraft(def.id)) "作成可能" else "素材不足"}"
            ForgeTab.ENHANCE -> "${def.name}: +$level  ${if (!owned) "未所持" else if (level >= 3) "強化上限" else "霧の結晶×${level + 1}"}"
            ForgeTab.EQUIP -> "${def.name}: ${if (owned) "装備できます" else "未所持"}"
        }
        panel.text = "鍛冶屋 [${forgeTab.label()}]  霧の結晶 $mist / 風の羽 $feather\n$detail"
    }

    private fun showCharacter() {
        panelMode = PanelMode.CHARACTER
        staminaLabel.visible = false
        val skills = game.saveData.skills.joinToString(" / ") {
            val name = GameTables.skills[it.skillId]?.name ?: it.skillId
            val enhancement = game.saveData.skillEnhancements[it.skillId] ?: 0
            "$name(+${enhancement * 10}%)"
        }
        val crystals = game.saveData.items[Ids.ITEM_SKILL_CRYSTAL] ?: 0
        panel.text = "キャラ確認  Lv${game.saveData.playerLevel}  ATK ${game.attack}  DEF ${game.defense}\nスキル: $skills\n結晶 $crystals  未習得スキルは欠片2個で再習得"
    }

    private fun Container.skillButton(id: String, x: Int, y: Int) {
        val def = GameTables.skills.getValue(id)
        val rect = solidRect(130.0, 80.0, Colors["#FFFFFF"]).position(x, y)
        val name = label(def.name, 18.0, Colors["#1A4F75"]).position(x + 8, y + 6)
        val effect = label(def.effect.shortLabel(), 14.0, Colors["#556677"]).position(x + 8, y + 31)
        val level = label("", 14.0, Colors["#556677"]).position(x + 8, y + 52)
        val progressBg = solidRect(130.0, 5.0, Colors["#DDDDDD"]).position(x, y + 73)
        val progressBar = solidRect(0.0, 5.0, Colors["#A8D5BA"]).position(x, y + 73)
        lateinit var ui: SkillButtonUi
        val button = Btn(x, y, 130, 80, rect, name, {
            if (!game.useSkill(ui.id)) game.relearnSkill(ui.id)
        }).also(buttons::add)
        ui = SkillButtonUi(id, button, effect, level, progressBg, progressBar)
        skillButtons += ui
    }

    private fun Container.arrowButton(label: String, x: Int, y: Int, dx: Int, dy: Int) {
        val btn = button(label, x, y, 70, 60) {
            handleDirectionInput(dx, dy)
        }
        arrowButtons += btn
    }

    private fun handleDirectionInput(dx: Int, dy: Int) {
        targetCell = null
        game.move(dx, dy)
    }

    private fun Container.button(label: String, x: Int, y: Int, width: Int = 58, height: Int = 48, action: () -> Unit): Btn {
        val rect = solidRect(width.toDouble(), height.toDouble(), Colors["#FFFFFF"]).position(x, y)
        val text = this.label(label, 18.0, Colors["#1A4F75"]).position(x + 8, y + 12)
        return Btn(x, y, width, height, rect, text, action).also(buttons::add)
    }

    private fun triggerButton(x: Float, y: Float) {
        if (endingVisible) {
            endingVisible = false
            game.returnToBase()
            showEntrance()
            return
        }
        if (debugOverlayVisible) {
            debugOverlayVisible = false
            return
        }
        if (y < 62f && x < 500f) {
            debugOverlayVisible = true
            return
        }
        val hit = buttons.firstOrNull { btn ->
            btn.enabled && btn.rect.visible && x >= btn.x && x <= btn.x + btn.w && y >= btn.y && y <= btn.y + btn.h
        }
        if (hit != null) {
            hit.action.invoke()
        } else if (!handleMiniMapTap(x, y)) {
            handleGridTap(x, y)
        }
    }

    private fun handleMiniMapTap(vx: Float, vy: Float): Boolean {
        val dungeon = game.dungeon ?: return false
        if (game.result != null) return false
        val mmX = GRID_ORIGIN_X + 8 * GRID_CELL_SIZE
        val mmY = GRID_ORIGIN_Y
        val maxChunkX = dungeonChunkCols(dungeon) - 1
        val maxChunkY = dungeonChunkRows(dungeon) - 1
        val insideX = vx >= mmX && vx < mmX + 34f + maxChunkX * 38f
        val insideY = vy >= mmY && vy < mmY + 34f + maxChunkY * 38f
        if (!insideX || !insideY) return false
        val chunkX = ((vx - mmX) / 38f).toInt().coerceIn(0, maxChunkX)
        val chunkY = ((vy - mmY) / 38f).toInt().coerceIn(0, maxChunkY)
        val currentChunk = Chunk(
            game.player.cell.x / DungeonGenerator.CHUNK_SIZE,
            game.player.cell.y / DungeonGenerator.CHUNK_SIZE,
        )
        if (Chunk(chunkX, chunkY) == currentChunk) return true
        targetCell = Cell(
            chunkX * DungeonGenerator.CHUNK_SIZE + DungeonGenerator.CHUNK_SIZE / 2,
            chunkY * DungeonGenerator.CHUNK_SIZE + DungeonGenerator.CHUNK_SIZE / 2,
        )
        targetElapsed = 0.0
        return true
    }

    private fun showUiStory(text: String, seconds: Double) {
        uiStoryText = text
        uiStorySecondsRemaining = seconds
    }

    private fun showEnding() {
        endingText.text = "「霧の核が砕けた。空島に光が戻ってくる」\n\n霧の原因を突き止め、光を取り戻した。\nでもまだ見ぬ島が、霧の向こうに待っている。\n\n--- END ---\nタップで拠点に戻る"
        endingVisible = true
    }

    private fun handleGridTap(vx: Float, vy: Float) {
        val cellSize = GRID_CELL_SIZE
        val gridOriginX = GRID_ORIGIN_X
        val gridOriginY = GRID_ORIGIN_Y
        val localX = ((vx - gridOriginX) / cellSize).toInt()
        val localY = ((vy - gridOriginY) / cellSize).toInt()
        if (localX < 0 || localY < 0 || localX >= DungeonGenerator.CHUNK_SIZE || localY >= DungeonGenerator.CHUNK_SIZE) return
        val chunkX = game.player.cell.x / DungeonGenerator.CHUNK_SIZE
        val chunkY = game.player.cell.y / DungeonGenerator.CHUNK_SIZE
        val target = Cell(chunkX * DungeonGenerator.CHUNK_SIZE + localX, chunkY * DungeonGenerator.CHUNK_SIZE + localY)
        targetCell = target.takeUnless { it == game.player.cell }
        targetElapsed = 0.0
    }

    private fun advanceTowardTarget() {
        val target = targetCell ?: return
        val dungeon = game.dungeon ?: return
        if (game.result != null || game.isStoryVisible) {
            targetCell = null
            return
        }
        if (target == game.player.cell || game.adjacentEnemy() != null) {
            targetCell = null
            return
        }
        val occupied = dungeon.enemies.filter { it.hp > 0 }.map { it.cell }.toSet()
        val path = PathFinder.find(game.player.cell, target) { cell ->
            dungeon.tiles[cell] != Tile.WALL && cell !in occupied
        }
        val next = path.firstOrNull()
        if (next == null) {
            targetCell = null
            return
        }
        game.move(next.x - game.player.cell.x, next.y - game.player.cell.y)
        if (target == game.player.cell || game.adjacentEnemy() != null) targetCell = null
    }

    private fun Container.label(str: String, size: Double, color: korlibs.image.color.RGBA): Text =
        font?.let { text(str, textSize = size, color = color, font = it) }
            ?: text(str, textSize = size, color = color)

    private fun positionPlayerBorder() {
        val localX = game.player.cell.x % DungeonGenerator.CHUNK_SIZE
        val localY = game.player.cell.y % DungeonGenerator.CHUNK_SIZE
        val x = GRID_ORIGIN_X + localX * GRID_CELL_SIZE
        val y = GRID_ORIGIN_Y + localY * GRID_CELL_SIZE
        playerBorders[0].position(x, y)
        playerBorders[1].position(x, y + GRID_CELL_SIZE - PLAYER_BORDER_SIZE)
        playerBorders[2].position(x, y)
        playerBorders[3].position(x + GRID_CELL_SIZE - PLAYER_BORDER_SIZE, y)
        playerBorders.forEach { it.visible = true }
    }

    private fun updateCellSymbol(symbol: Text, cell: Cell, dungeon: skyisland.game.Dungeon) {
        when {
            cell == game.player.cell -> {
                symbol.text = "◆"
                symbol.textSize = 20.0
                symbol.color = Colors["#FFFFFF"]
            }
            dungeon.enemies.any { isBossId(it.enemyId) && it.hp > 0 && it.cell == cell } -> {
                symbol.text = "✕"
                symbol.textSize = 22.0
                symbol.color = Colors["#FFFFFF"]
            }
            dungeon.enemies.any { it.hp > 0 && it.cell == cell } -> {
                symbol.text = "✕"
                symbol.textSize = 16.0
                symbol.color = Colors["#FFFFFF"]
            }
            dungeon.groundItems.any { it.cell == cell } || cell in dungeon.chests -> {
                symbol.text = "○"
                symbol.textSize = 16.0
                symbol.color = Colors["#5D4037"]
            }
            dungeon.tiles[cell] == Tile.EXIT -> {
                symbol.text = "★"
                symbol.textSize = 18.0
                symbol.color = Colors["#FFFFFF"]
            }
            else -> symbol.text = ""
        }
    }

    private fun refreshSkillButtons() {
        val defaultSkillIds = listOf(
            Ids.SKILL_WIND_BLADE,
            Ids.SKILL_MIST_HEAL,
            Ids.SKILL_CLOUD_SHIELD,
            Ids.SKILL_THUNDER_CLOUD,
        )
        val displayIds = (game.saveData.skills.map { it.skillId } + defaultSkillIds).distinct().take(SkyIslandGame.SKILL_SLOT_COUNT)
        skillButtons.forEachIndexed { index, ui ->
            ui.id = displayIds.getOrElse(index) { defaultSkillIds[index] }
            val def = GameTables.skills[ui.id] ?: return@forEachIndexed
            val skill = game.saveData.skills.firstOrNull { it.skillId == ui.id }
            ui.button.label.text = def.name
            ui.effect.text = def.effect.shortLabel()
            ui.level.text = skill?.let { proficiencyLevel(it.proficiency) } ?: "再習得 欠片×2"
            ui.button.rect.color = if (skill?.proficiency == SkyIslandGame.MAX_SKILL_PROFICIENCY) Colors["#FFF176"] else Colors["#FFFFFF"]
            val ratio = (skill?.proficiency ?: 0).toDouble() / SkyIslandGame.MAX_SKILL_PROFICIENCY
            ui.progressBar.width = 130.0 * ratio
            ui.progressBar.color = if (skill?.proficiency == SkyIslandGame.MAX_SKILL_PROFICIENCY) Colors["#FFC107"] else Colors["#A8D5BA"]
        }
    }

    private fun Container.buildForgeButtons() {
        forgeCraftTabButton = button("クラフト", 275, 1080, 100) { forgeTab = ForgeTab.CRAFT; refreshForgePanel() }
        forgeEnhanceTabButton = button("強化", 385, 1080, 70) { forgeTab = ForgeTab.ENHANCE; refreshForgePanel() }
        forgeEquipTabButton = button("装備", 465, 1080, 70) { forgeTab = ForgeTab.EQUIP; refreshForgePanel() }
        listOf(
            Ids.EQ_MIST_COAT to "外套",
            Ids.EQ_MIST_AMULET to "護符",
            Ids.EQ_WIND_SWORD to "風剣",
            Ids.EQ_CLOUD_ARMOR to "盾鎧",
            Ids.EQ_STORM_SWORD to "嵐剣",
        ).forEachIndexed { index, (id, name) ->
            forgeEquipmentButtons += id to button(name, 275 + index * 60, 1138, 60) {
                selectedEquipmentId = id
                refreshForgePanel()
            }
        }
        forgeCraftButton = button("作る", 625, 1138, 70) { game.craft(selectedEquipmentId); refreshForgePanel() }
        forgeEnhanceButton = button("+強化", 625, 1138, 70) { game.enhance(selectedEquipmentId); refreshForgePanel() }
        forgeEquipButton = button("装備", 625, 1138, 70) { game.equip(selectedEquipmentId); refreshForgePanel() }
    }

    private fun refreshForgeControls() {
        val forgeVisible = panelMode == PanelMode.FORGE && game.result == null
        listOf(forgeCraftTabButton, forgeEnhanceTabButton, forgeEquipTabButton).forEach { setButtonVisible(it, forgeVisible) }
        forgeEquipmentButtons.forEach { (_, button) -> setButtonVisible(button, forgeVisible) }
        setButtonVisible(forgeCraftButton, forgeVisible && forgeTab == ForgeTab.CRAFT)
        setButtonVisible(forgeEnhanceButton, forgeVisible && forgeTab == ForgeTab.ENHANCE)
        setButtonVisible(forgeEquipButton, forgeVisible && forgeTab == ForgeTab.EQUIP)
        setButtonEnabled(forgeCraftButton, game.canCraft(selectedEquipmentId), Colors["#A8D5BA"])
        setButtonEnabled(forgeEnhanceButton, game.canEnhance(selectedEquipmentId))
        setButtonEnabled(forgeEquipButton, selectedEquipmentId in game.saveData.ownedEquipments)
        forgeEquipmentButtons.forEach { (id, button) -> setButtonEnabled(button, id != selectedEquipmentId) }
        if (forgeVisible && game.saveData.equippedWeaponId == null) {
            forgeEquipmentButtons.firstOrNull { it.first == Ids.EQ_WIND_SWORD }?.second?.let { btn ->
                if (btn.enabled) btn.rect.color = Colors["#FFF176"]
            }
        }
        if (forgeVisible && game.saveData.equippedArmorId == null) {
            forgeEquipmentButtons.filter { GameTables.equipments[it.first]?.slot == EquipmentSlot.ARMOR }.forEach { (id, btn) ->
                if (btn.enabled && game.canCraft(id)) btn.rect.color = Colors["#FFF176"]
            }
        }
    }

    private fun refreshEntranceControls() {
        val entranceVisible = panelMode == PanelMode.ENTRANCE && game.result == null
        val floorSelectVisible = entranceVisible && game.saveData.floor1Cleared
        setButtonVisible(floor1Button, floorSelectVisible)
        setButtonVisible(floor2Button, floorSelectVisible)
        setButtonVisible(floor3Button, floorSelectVisible && game.saveData.floor2Cleared)
        setButtonVisible(relaxedButton, entranceVisible)
        setButtonVisible(adventureButton, entranceVisible)
        if ((selectedFloorId == Ids.FLOOR_02 && game.saveData.stamina < 1) ||
            (selectedFloorId == Ids.FLOOR_03 && (!game.saveData.floor2Cleared || game.saveData.stamina < 1))
        ) {
            selectedFloorId = Ids.FLOOR_01
        }
        if (floorSelectVisible) {
            val nextStage = game.saveData.floor1Cleared && !game.saveData.floor2Cleared
            floor1Button.label.text = if (nextStage) "F1 霧の島（周回）" else "F1 霧の島"
            floor2Button.label.text = if (nextStage) "▶ F2 嵐の島（次のステージ）" else "F2 嵐の島"
            floor1Button.label.textSize = if (nextStage) 13.0 else 18.0
            floor2Button.label.textSize = if (nextStage) 11.0 else 18.0
            floor1Button.enabled = true
            floor1Button.rect.color = when {
                nextStage -> Colors["#D0D6DB"]
                selectedFloorId == Ids.FLOOR_01 -> Colors["#90CAF9"]
                else -> Colors["#FFFFFF"]
            }
            floor1Button.label.color = Colors["#1A4F75"]
            floor2Button.enabled = game.saveData.stamina >= 1
            floor2Button.rect.color = when {
                game.saveData.stamina < 1 -> Colors["#A9A9A9"]
                nextStage -> Colors["#64B5F6"]
                selectedFloorId == Ids.FLOOR_02 -> Colors["#90CAF9"]
                else -> Colors["#FFFFFF"]
            }
            floor2Button.label.color = if (floor2Button.enabled) Colors["#1A4F75"] else Colors["#7A91A1"]
            floor3Button.enabled = game.saveData.floor2Cleared && game.saveData.stamina >= 1
            floor3Button.rect.color = when {
                game.saveData.stamina < 1 -> Colors["#A9A9A9"]
                selectedFloorId == Ids.FLOOR_03 -> Colors["#90CAF9"]
                else -> Colors["#FFFFFF"]
            }
            floor3Button.label.color = if (floor3Button.enabled) Colors["#1A4F75"] else Colors["#7A91A1"]
        }
        val canStart = when (selectedFloorId) {
            Ids.FLOOR_03 -> game.saveData.floor2Cleared && game.saveData.stamina >= 1
            Ids.FLOOR_02 -> game.saveData.stamina >= 1
            else -> true
        }
        setButtonEnabled(relaxedButton, entranceVisible && canStart)
        setButtonEnabled(adventureButton, entranceVisible && canStart)
    }

    private fun refreshBaseGuideButtons() {
        if (game.result != null) {
            forgeNavButton.rect.color = Colors["#FFFFFF"]
            characterNavButton.rect.color = Colors["#FFFFFF"]
            return
        }
        forgeNavButton.rect.color = if (shouldHighlightForgeForArmor() || shouldHighlightForgeForWeapon()) Colors["#FFF176"] else Colors["#FFFFFF"]
        characterNavButton.rect.color = if (shouldHighlightCharacterForArmor()) Colors["#FFF176"] else Colors["#FFFFFF"]
    }

    private fun baseNextHint(): String {
        val save = game.saveData
        val armorIds = GameTables.equipments.values.filter { it.slot == EquipmentSlot.ARMOR }.map { it.id }
        return when {
            save.equippedWeaponId == null && game.canCraft(Ids.EQ_WIND_SWORD) -> "次にやること: 鍛冶屋で武器を作ろう"
            save.equippedArmorId == null && armorIds.any(game::canCraft) -> "次にやること: 鍛冶屋で防具を作ろう"
            save.equippedArmorId == null && save.ownedEquipments.any { GameTables.equipments[it]?.slot == EquipmentSlot.ARMOR } -> "次にやること: キャラ画面から防具を装備しよう"
            !save.floor1Cleared -> "次にやること: ダンジョンに入ってボスを倒そう"
            !save.floor2Cleared -> "次にやること: フロア2: 嵐の島に挑戦しよう"
            !save.floor3Cleared -> "次にやること: フロア3: 光の島に挑戦しよう"
            save.skills.any { it.proficiency == 0 } -> "次にやること: スキルを使って熟練度を上げよう"
            else -> "次にやること: ダンジョンに入って素材を集めよう"
        }
    }

    private fun isBossId(enemyId: String) = enemyId == Ids.BOSS_KASUMI || enemyId == Ids.BOSS_TEMPE || enemyId == Ids.BOSS_LUMEN
    private fun bossNameForFloor(floorId: String) = when (floorId) {
        Ids.FLOOR_03 -> "ルーメン"
        Ids.FLOOR_02 -> "テンペ"
        else -> "カスミ"
    }
    private fun dungeonChunkRows(dungeon: skyisland.game.Dungeon) =
        ((dungeon.tiles.keys.maxOf { it.y } + 1) + DungeonGenerator.CHUNK_SIZE - 1) / DungeonGenerator.CHUNK_SIZE
    private fun dungeonChunkCols(dungeon: skyisland.game.Dungeon) =
        ((dungeon.tiles.keys.maxOf { it.x } + 1) + DungeonGenerator.CHUNK_SIZE - 1) / DungeonGenerator.CHUNK_SIZE

    private fun shouldHighlightForgeForWeapon(): Boolean =
        game.saveData.equippedWeaponId == null && game.canCraft(Ids.EQ_WIND_SWORD)

    private fun shouldHighlightForgeForArmor(): Boolean =
        game.saveData.equippedArmorId == null && armorEquipmentIds().any(game::canCraft)

    private fun shouldHighlightCharacterForArmor(): Boolean =
        game.saveData.equippedArmorId == null &&
            game.saveData.ownedEquipments.any { GameTables.equipments[it]?.slot == EquipmentSlot.ARMOR }

    private fun armorEquipmentIds(): List<String> =
        GameTables.equipments.values.filter { it.slot == EquipmentSlot.ARMOR }.map { it.id }

    private fun setButtonVisible(button: Btn, visible: Boolean) {
        button.rect.visible = visible
        button.label.visible = visible
    }

    private fun setButtonEnabled(button: Btn, enabled: Boolean, enabledColor: korlibs.image.color.RGBA = Colors["#FFFFFF"]) {
        button.enabled = enabled
        button.rect.color = if (enabled) enabledColor else Colors["#A9A9A9"]
        button.label.color = if (enabled) Colors["#1A4F75"] else Colors["#7A91A1"]
    }

    private fun ForgeTab.label() = when (this) {
        ForgeTab.CRAFT -> "クラフト"
        ForgeTab.ENHANCE -> "強化"
        ForgeTab.EQUIP -> "装備"
    }

    private fun SkillEffect.shortLabel() = when (this) {
        SkillEffect.FRONT_ATTACK -> "前方攻撃"
        SkillEffect.HEAL -> "HP回復"
        SkillEffect.SHIELD -> "防御UP"
        SkillEffect.AREA_ATTACK -> "範囲攻撃"
        SkillEffect.KNOCKBACK -> "吹き飛ばし"
        SkillEffect.STUN -> "1T行動不能"
        SkillEffect.MULTI_HIT -> "ランダム3打"
        SkillEffect.LINE_ATTACK -> "直線全体攻撃"
        SkillEffect.ALL_ATTACK -> "全敵ダメージ"
        SkillEffect.INVINCIBLE -> "3T無敵"
    }

    private fun proficiencyLevel(proficiency: Int) = when {
        proficiency >= SkyIslandGame.MAX_SKILL_PROFICIENCY -> "Lv3 MAX"
        proficiency >= 10 -> "Lv2"
        else -> "Lv1"
    }

    private fun Container.buildDebugOverlay() {
        debugBg = solidRect(720.0, 1280.0, Colors["#000000E8"]).position(0, 0)
        debugBg.visible = false
        debugText = label("", 16.0, Colors["#D0EEFF"]).position(16, 16)
        debugText.visible = false
        debugStaminaButton = button("スタミナ全回復", 16, 1200, 180, 48) {
            game.debugResetStamina()
            debugOverlayVisible = false
        }.also { btn ->
            btn.rect.color = Colors["#E53935"]
            btn.label.color = Colors["#FFFFFF"]
            btn.rect.visible = false
            btn.label.visible = false
        }
    }

    private fun Container.buildEndingOverlay() {
        endingBg = solidRect(720.0, 1280.0, Colors["#F8FBFFE8"]).position(0, 0)
        endingBg.visible = false
        endingText = label("", 25.0, Colors["#1A3A5C"]).position(56, 360)
        endingText.visible = false
    }

    private fun refreshDebugOverlay() {
        val dungeon = game.dungeon
        val sd = game.saveData
        val skills = sd.skills.joinToString("  ") {
            val name = GameTables.skills[it.skillId]?.name ?: it.skillId
            "$name:${it.proficiency}"
        }.ifBlank { "なし" }
        val weapon = sd.equippedWeaponId?.let {
            "${GameTables.equipments[it]?.name ?: it}+${game.equipmentLevel(it)}"
        } ?: "なし"
        val armor = sd.equippedArmorId?.let {
            "${GameTables.equipments[it]?.name ?: it}+${game.equipmentLevel(it)}"
        } ?: "なし"
        val mats = sd.materials.entries.filter { it.value > 0 }
            .joinToString("  ") { (id, amt) -> "${GameTables.materials[id]?.name ?: id}×$amt" }
            .ifBlank { "なし" }
        val items = sd.items.entries.filter { it.value > 0 }
            .joinToString("  ") { (id, amt) -> "${GameTables.items[id]?.name ?: id}×$amt" }
            .ifBlank { "なし" }
        val enemyLines = dungeon?.enemies?.joinToString("\n") { e ->
            val name = GameTables.enemies[e.enemyId]?.name ?: e.enemyId
            val maxHp = GameTables.enemies[e.enemyId]?.maxHp ?: 0
            val status = if (e.hp <= 0) "撃破" else "${e.hp}/${maxHp}"
            "  $name[$status] @(${e.cell.x},${e.cell.y})"
        } ?: "  ダンジョン外"
        val logLines = game.actionLog.toList().asReversed().take(15)
            .joinToString("\n") { "  $it" }
        debugText.text = buildString {
            appendLine("[DEBUG]  タップで閉じる")
            appendLine("Turn:${game.turnCount}  ChunkT:${game.chunkTurns}  Auto:${if (sd.autoMoveEnabled) "ON" else "OFF"}")
            appendLine("F1:${sd.floor1Cleared}  F2:${sd.floor2Cleared}  F3:${sd.floor3Cleared}  stamina:${sd.stamina}")
            appendLine("HP:${game.player.hp}/${game.maxHp}  ATK:${game.attack}  DEF:${game.defense}  盾:${game.player.shieldTurns}T")
            appendLine("スキル: $skills")
            appendLine("武器:$weapon  防具:$armor")
            appendLine("素材: $mats")
            appendLine("アイテム: $items")
            appendLine("敵一覧:")
            appendLine(enemyLines)
            appendLine("─── 行動ログ(${game.actionLog.size}件) ───")
            append(logLines)
        }
    }

    companion object {
        const val GRID_CELL_SIZE = 62f
        const val GRID_ORIGIN_X = 50f
        const val GRID_ORIGIN_Y = 205f
        const val PLAYER_BORDER_SIZE = 3.0
    }
}
