package islanddev.ui

import islanddev.data.GameData
import islanddev.game.BattleResolver
import islanddev.model.SaveData
import korlibs.image.color.RGBA
import korlibs.korge.view.Container
import korlibs.korge.view.Text
import korlibs.korge.view.position
import korlibs.korge.view.solidRect

class BossPanel : Container() {
    private var pendingSeconds = 0.0
    private var pendingSave: SaveData? = null
    private var pendingCallback: ((SaveData) -> Unit)? = null
    private var pendingOnClosed: (() -> Unit)? = null
    private lateinit var resultText: Text

    init {
        visible = false
    }

    fun show(
        save: SaveData,
        bossId: Int,
        onResult: (SaveData) -> Unit,
        onClosed: () -> Unit
    ) {
        removeChildren()
        visible = true
        pendingSeconds = 0.0
        pendingSave = null
        pendingCallback = onResult
        pendingOnClosed = onClosed

        solidRect(360, 640, IslandTheme.Color.ModalScrim).mouseEnabled = true
        panelRect(320.0, 238.0, fill = IslandTheme.Color.Panel, border = IslandTheme.Color.PanelBorder)
            .position(20, 184)
        val boss = GameData.bossById(bossId) ?: run {
            visible = false
            pendingOnClosed?.invoke()
            pendingOnClosed = null
            return
        }
        label(boss.name, 45.0, 214.0, 22.0, IslandTheme.Color.Accent)
        label("必要ATK: ${boss.requiredAtk}", 45.0, 258.0, 18.0)
        resultText = label("", 45.0, 304.0, 16.0, RGBA(151, 220, 242, 255))
        actionButton("挑戦する", 100.0, 356.0, 160.0, 44.0) {
            if (pendingSeconds > 0.0) return@actionButton
            val (updated, result) = BattleResolver.resolveBoss(save, bossId)
            if (result.won) {
                val nextZone = GameData.BOSSES.firstOrNull { it.id == bossId }?.toZoneId
                resultText.text = if (updated.gameCleared) {
                    "${boss.name}を撃破！"
                } else {
                    "${boss.name}を撃破！ゾーン${(nextZone ?: 0) + 1}が解放された！"
                }
                pendingSave = updated
            } else {
                val nextWeapon = GameData.WEAPONS
                    .filter { it.id !in save.craftedWeaponIds && it.atk >= boss.requiredAtk }
                    .minByOrNull { it.atk }
                val hint = if (nextWeapon != null) {
                    "\n→ ${nextWeapon.name}（ATK ${nextWeapon.atk}）を目指そう"
                } else {
                    ""
                }
                resultText.text = "ATKが足りない（現在 ${save.currentAtk} / 必要 ${boss.requiredAtk}）$hint"
            }
            pendingSeconds = 2.0
        }
    }

    fun update(deltaSeconds: Double) {
        if (!visible || pendingSeconds <= 0.0) return
        pendingSeconds -= deltaSeconds
        if (pendingSeconds > 0.0) return

        val result = pendingSave
        visible = false
        pendingOnClosed?.invoke()
        pendingOnClosed = null
        pendingSave = null
        if (result != null) {
            pendingCallback?.invoke(result)
        }
        pendingCallback = null
    }
}
