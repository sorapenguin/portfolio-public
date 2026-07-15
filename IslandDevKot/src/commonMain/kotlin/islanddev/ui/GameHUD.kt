package islanddev.ui

import islanddev.data.GameData
import islanddev.game.ObjectiveGuide
import islanddev.game.AutoDebugInfo
import islanddev.model.SaveData
import korlibs.image.color.RGBA
import korlibs.korge.view.Container
import korlibs.korge.view.Text
import korlibs.korge.input.onClick
import korlibs.korge.view.addUpdater
import korlibs.korge.view.container
import korlibs.korge.view.position
import korlibs.korge.view.solidRect
import korlibs.korge.view.text

class GameHUD(
    private val onBossChallenge: (Int) -> Unit,
    onCraftOpen: () -> Unit,
    onSubZoneOpen: () -> Unit,
    onStepMove: (Int, Int) -> Unit,
    onAutoToggle: () -> Boolean
) : Container() {
    companion object {
        const val AUTO_DEBUG_VISIBLE = false
    }

    private val inventoryText: Text
    private val atkText: Text
    private val ideaText: Text
    private val clearedText: Text
    private val developmentText: Text
    private val objectiveText: Text
    private val challengeButton: Container
    private val bannerPanel: Container
    private val bannerText: Text
    private lateinit var autoText: Text
    private lateinit var autoStatusText: Text
    private val autoDebugText: Text
    private val autoDebugPanel: Container
    private var nearBossId: Int? = null
    private var bannerRemainingSeconds = 0.0
    private var lastDisplayedMinute = Long.MIN_VALUE
    private var lastHudSnapshot = ""
    private var lastObjective = ""
    private var lastAutoDebugInfo: AutoDebugInfo? = null

    init {
        val bottomTop = IslandTheme.Size.BottomPanelTop
        solidRect(IslandTheme.Size.StageWidth, IslandTheme.Size.HudHeight, IslandTheme.Color.Background)

        panelRect(352.0, 58.0).apply {
            position(4, 4)
            mouseEnabled = true
        }
        inventoryText = label("", 12.0, 7.0, 10.0, IslandTheme.Color.Text)
        atkText = label("", 12.0, 32.0, 11.0, IslandTheme.Color.Text)
        ideaText = label("", 12.0, 47.0, 11.0, IslandTheme.Color.Accent)
        clearedText = label("CLEARED", 282.0, 12.0, 11.0, IslandTheme.Color.Accent)
        clearedText.visible = false

        panelRect(352.0, 36.0, fill = RGBA(23, 46, 60, 255)).apply {
            position(4, 64)
            mouseEnabled = true
        }
        objectiveText = label("", 12.0, 67.0, 10.0, IslandTheme.Color.Accent)

        panelRect(352.0, 22.0, fill = RGBA(20, 39, 54, 255)).position(4, 102)
        developmentText = label("", 12.0, 106.0, 11.0, RGBA(151, 220, 242, 255))

        bannerPanel = container {
            position(22, IslandTheme.Size.HudHeight + 8.0)
            panelRect(316.0, 34.0, fill = RGBA(20, 39, 54, 238), border = IslandTheme.Color.Accent)
        }
        bannerText = bannerPanel.label("", 14.0, 8.0, 13.0, IslandTheme.Color.Text)
        bannerPanel.visible = false

        panelRect(360.0, IslandTheme.Size.BottomPanelHeight, fill = IslandTheme.Color.Hud).position(
            0,
            IslandTheme.Size.BottomPanelTop
        )

        actionButton("島づくり", 12.0, bottomTop + 12.0, 94.0, IslandTheme.Size.ButtonHeight, onClick = onCraftOpen)
        actionButton("開拓", 116.0, bottomTop + 12.0, 82.0, IslandTheme.Size.ButtonHeight, onClick = onSubZoneOpen)
        challengeButton = actionButton("挑戦する", 112.0, bottomTop - 48.0, 136.0, 42.0) {
            nearBossId?.let(onBossChallenge)
        }
        challengeButton.visible = false

        actionButton("↑", 272.0, bottomTop + 10.0, 40.0, 40.0) {
            onStepMove(0, -1)
        }
        actionButton("←", 224.0, bottomTop + 54.0, 40.0, 40.0) {
            onStepMove(-1, 0)
        }
        actionButton("↓", 272.0, bottomTop + 54.0, 40.0, 40.0) {
            onStepMove(0, 1)
        }
        actionButton("→", 320.0, bottomTop + 54.0, 40.0, 40.0) {
            onStepMove(1, 0)
        }
        container {
            position(12, bottomTop + 66.0)
            panelRect(186.0, 46.0, fill = IslandTheme.Color.Button, border = IslandTheme.Color.ButtonBorder)
            autoText = text(
                "AUTO: OFF",
                textSize = 15.0,
                color = IslandTheme.Color.Text,
                font = IslandUiFonts.font
            ) {
                position(12, 5)
            }
            autoStatusText = text(
                "待機",
                textSize = 11.0,
                color = RGBA(210, 230, 255, 255),
                font = IslandUiFonts.font
            ) {
                position(112, 9)
            }
            onClick {
                setAutoEnabled(onAutoToggle())
            }
        }
        autoDebugPanel = container {
            position(4, 120)
            solidRect(210, 40, RGBA(10, 15, 24, 225))
        }
        autoDebugText = autoDebugPanel.label(
            "",
            6.0,
            3.0,
            10.0,
            RGBA(220, 235, 255, 255)
        )
        autoDebugPanel.visible = AUTO_DEBUG_VISIBLE

        addUpdater { delta ->
            if (bannerRemainingSeconds <= 0.0) return@addUpdater
            bannerRemainingSeconds -= delta.inWholeMicroseconds / 1_000_000.0
            if (bannerRemainingSeconds <= 0.0) {
                bannerPanel.visible = false
                bannerText.text = ""
            }
        }
    }

    fun setNearBoss(bossId: Int?) {
        nearBossId = bossId
        challengeButton.visible = bossId != null
    }

    fun setAutoEnabled(enabled: Boolean) {
        val enabledText = if (enabled) "AUTO: ON" else "AUTO: OFF"
        val statusText = if (enabled) "移動中" else "待機"
        if (autoText.text != enabledText) autoText.text = enabledText
        if (autoStatusText.text != statusText) autoStatusText.text = statusText
    }

    fun setAutoStatus(status: String) {
        if (autoStatusText.text != status) autoStatusText.text = status
    }

    fun showBanner(message: String) {
        bannerText.text = message
        bannerPanel.visible = true
        bannerRemainingSeconds = 2.5
    }

    fun updateAutoDebug(info: AutoDebugInfo) {
        if (!AUTO_DEBUG_VISIBLE || info == lastAutoDebugInfo) return
        lastAutoDebugInfo = info
        autoDebugText.text = info.displayText()
    }

    fun capturesTouch(stageX: Float, stageY: Float): Boolean {
        if (stageX in 0f..360f && stageY in 0f..128f) return true
        if (stageX in 0f..360f && stageY in IslandTheme.Size.BottomPanelTop.toFloat()..640f) return true
        if (AUTO_DEBUG_VISIBLE && stageX in 4f..214f && stageY in 120f..160f) return true
        if (
            challengeButton.visible &&
            stageX in 112f..248f &&
            stageY in (IslandTheme.Size.BottomPanelTop - 48.0).toFloat()..
                (IslandTheme.Size.BottomPanelTop - 6.0).toFloat()
        ) return true
        return false
    }

    fun update(save: SaveData, nowSec: Long) {
        val objective = ObjectiveGuide.currentObjective(save)
        if (objective != lastObjective) {
            objectiveText.text = objective
            lastObjective = objective
        }
        val currentMinute = nowSec / 60
        val hudSnapshot = buildString {
            append(save.inventory)
            append('|')
            append(save.currentAtk)
            append('|')
            append(save.equippedWeaponId)
            append('|')
            append(save.idea)
            append('|')
            append(save.developingSubZones)
            append('|')
            append(save.playerCol)
            append('|')
            append(save.gameCleared)
        }
        if (lastDisplayedMinute == currentMinute && lastHudSnapshot == hudSnapshot) return
        lastDisplayedMinute = currentMinute
        lastHudSnapshot = hudSnapshot

        inventoryText.text = buildString {
            append("木材: ${save.inventory.getOrDefault(GameData.RES_WOOD, 0)}  ")
            append("石: ${save.inventory.getOrDefault(GameData.RES_STONE, 0)}  ")
            append("果実: ${save.inventory.getOrDefault(GameData.RES_FRUIT, 0)}  ")
            append("繊維: ${save.inventory.getOrDefault(GameData.RES_FIBER, 0)}\n")
            append("貝殻: ${save.inventory.getOrDefault(GameData.RES_SHELL, 0)}  ")
            append("粘土: ${save.inventory.getOrDefault(GameData.RES_CLAY, 0)}  ")
            append("竹: ${save.inventory.getOrDefault(GameData.RES_BAMBOO, 0)}  ")
            append("鉱石: ${save.inventory.getOrDefault(GameData.RES_ORE, 0)}")
        }
        val zoneName = when (GameData.columnToZone(save.playerCol)) {
            GameData.ZONE_BEACH -> "砂浜"
            GameData.ZONE_FOREST -> "森"
            GameData.ZONE_REEF -> "岩礁"
            GameData.ZONE_DEPTHS -> "奥地"
            GameData.ZONE_SUMMIT -> "山頂"
            else -> "島"
        }
        atkText.text = "ATK: ${save.currentAtk}  $zoneName"
        ideaText.text = "イデア: ${save.idea}"
        clearedText.visible = save.gameCleared

        val developing = save.developingSubZones.minByOrNull { it.value }
        developmentText.text = if (developing != null) {
            if (save.developingSubZones.size == 1) {
                val zoneName = GameData.subZoneById(developing.key)?.name ?: "区画"
                "開拓中: $zoneName"
            } else {
                "開拓中: ${save.developingSubZones.size}件"
            }
        } else {
            "開拓中: なし"
        }
    }
}
