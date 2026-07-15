package islanddev.ui

import islanddev.data.GameData
import islanddev.game.SubZoneManager
import islanddev.model.SaveData
import korlibs.image.color.RGBA
import korlibs.korge.view.Container
import korlibs.korge.view.position
import korlibs.korge.view.solidRect

class SubZonePanel : Container() {
    private var currentSave: SaveData? = null
    private var currentZoneId = 0
    private var currentNowSec = 0L
    private var currentAction: ((SaveData) -> Unit)? = null
    private var currentOnClosed: (() -> Unit)? = null
    private var resultMessage = ""

    init {
        visible = false
    }

    fun show(
        save: SaveData,
        zoneId: Int,
        nowSec: Long,
        onAction: (SaveData) -> Unit,
        onClosed: () -> Unit
    ) {
        currentSave = save
        currentZoneId = zoneId
        currentNowSec = nowSec
        currentAction = onAction
        currentOnClosed = onClosed
        resultMessage = ""
        rebuild()
    }

    fun update(save: SaveData, nowSec: Long) {
        if (!visible || currentNowSec / 60 == nowSec / 60) return
        currentSave = save
        currentNowSec = nowSec
        rebuild()
    }

    private fun rebuild() {
        val save = currentSave ?: return
        removeChildren()
        visible = true
        solidRect(360, 640, IslandTheme.Color.ModalScrim).mouseEnabled = true
        panelRect(340.0, 430.0, fill = IslandTheme.Color.Panel, border = IslandTheme.Color.PanelBorder)
            .position(10, 176)
        label("開拓", 24.0, 194.0, 22.0, IslandTheme.Color.Accent)
        label("イデアで島を広げる", 78.0, 202.0, 11.0, IslandTheme.Color.MutedText)
        var y = 252.0
        val zones = GameData.SUB_ZONES
            .filter { it.parentZoneId == currentZoneId }
            .sortedWith(
                compareBy(
                    { zoneSortRank(it.id, save) },
                    { it.order },
                    { it.id }
                )
            )
        zones.forEach { zone ->
            when {
                zone.id in save.unlockedSubZoneIds -> {
                    panelRect(304.0, 48.0, fill = RGBA(30, 70, 55, 255), border = RGBA(57, 107, 83, 255))
                        .position(28, y - 10)
                    label("${zone.name}  解放済み", 38.0, y, 17.0, RGBA(139, 245, 166, 255))
                }
                zone.id in save.developingSubZones -> {
                    val remaining = (
                        save.developingSubZones.getValue(zone.id) - currentNowSec
                        ).coerceAtLeast(0)
                    panelRect(304.0, 48.0, fill = RGBA(35, 65, 85, 255), border = IslandTheme.Color.Accent)
                        .position(28, y - 10)
                    label(
                        "${zone.name}  開拓中  ${formatApproxDuration(remaining)}",
                        38.0,
                        y,
                        16.0,
                        RGBA(120, 220, 255, 255)
                    )
                }
                previousUnlocked(zone.id, save) -> {
                    actionButton(
                        "${zone.name}  開拓可  イデア${zone.ideaCost}",
                        24.0,
                        y - 10,
                        310.0,
                        48.0,
                        enabled = true
                    ) {
                        developmentFailureMessage(save, zone.id)?.let { message ->
                            resultMessage = message
                            rebuild()
                            return@actionButton
                        }
                        val updated = SubZoneManager.startDevelopment(
                            save,
                            zone.id,
                            currentNowSec
                        ) ?: run {
                            resultMessage = "開拓を開始できません"
                            rebuild()
                            return@actionButton
                        }
                        currentAction?.invoke(updated)
                        currentSave = updated
                        resultMessage = ""
                        rebuild()
                    }
                }
                else -> {
                    label(
                        "${zone.name}  未解放",
                        28.0,
                        y,
                        17.0,
                        IslandTheme.Color.MutedText
                    )
                }
            }
            y += 56
        }
        if (resultMessage.isNotBlank()) {
            label(resultMessage, 28.0, 526.0, 12.0, IslandTheme.Color.Accent)
        }
        actionButton("閉じる", 125.0, 550.0, 110.0, 40.0) {
            visible = false
            currentOnClosed?.invoke()
        }
    }

    private fun zoneSortRank(subZoneId: Int, save: SaveData): Int = when {
        subZoneId in save.developingSubZones -> 0
        previousUnlocked(subZoneId, save) &&
            subZoneId !in save.unlockedSubZoneIds -> 1
        subZoneId in save.unlockedSubZoneIds -> 2
        else -> 3
    }

    private fun previousUnlocked(subZoneId: Int, save: SaveData): Boolean {
        val zone = GameData.subZoneById(subZoneId) ?: return false
        if (zone.order == 0) return false
        val previous = GameData.SUB_ZONES.firstOrNull {
            it.parentZoneId == zone.parentZoneId && it.order == zone.order - 1
        }
        return previous?.id in save.unlockedSubZoneIds
    }

    private fun developmentFailureMessage(save: SaveData, subZoneId: Int): String? {
        val zone = GameData.subZoneById(subZoneId) ?: return "開拓を開始できません"
        if (zone.id in save.unlockedSubZoneIds) return "すでに解放済みです"
        if (zone.id in save.developingSubZones) return "すでに開拓中です"
        if (!previousUnlocked(zone.id, save)) return "まだ解放条件を満たしていません"
        if (save.idea < zone.ideaCost) return "イデアが足りません"
        return null
    }
}
