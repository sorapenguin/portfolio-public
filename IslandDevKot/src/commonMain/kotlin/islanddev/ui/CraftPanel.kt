package islanddev.ui

import islanddev.data.GameData
import islanddev.game.CraftManager
import islanddev.model.SaveData
import korlibs.image.color.RGBA
import korlibs.korge.input.onClick
import korlibs.korge.view.Container
import korlibs.korge.view.position
import korlibs.korge.view.solidRect

class CraftPanel : Container() {
    companion object {
        private const val FACILITIES_PER_PAGE = 3
        private const val FACILITY_ROW_HEIGHT = 58.0
        private const val FACILITY_ROW_STEP = 62.0
    }

    init {
        visible = false
    }

    fun show(
        save: SaveData,
        onCraft: (SaveData) -> Unit,
        onClosed: () -> Unit
    ) {
        showPage(save, onCraft, onClosed, facilityPage = 0)
    }

    private fun showPage(
        save: SaveData,
        onCraft: (SaveData) -> Unit,
        onClosed: () -> Unit,
        facilityPage: Int,
        resultMessage: String = ""
    ) {
        removeChildren()
        visible = true
        solidRect(360, 640, IslandTheme.Color.ModalScrim).mouseEnabled = true
        panelRect(344.0, 560.0, fill = IslandTheme.Color.Panel, border = IslandTheme.Color.PanelBorder)
            .position(8, 64)
        label("装備ゲート", 22.0, 82.0, 20.0, IslandTheme.Color.Accent)
        val equipped = GameData.weaponById(save.equippedWeaponId)?.name ?: "素手"
        label("素材が揃うと自動作成・自動装備", 22.0, 108.0, 11.0, IslandTheme.Color.MutedText)
        label("現在: $equipped / ATK ${save.currentAtk}", 22.0, 126.0, 13.0, IslandTheme.Color.Text)
        if (resultMessage.isNotBlank()) {
            label(resultMessage, 22.0, 141.0, 11.0, IslandTheme.Color.Accent)
        }
        var y = 152.0
        nextWeapons(save).forEach { weapon ->
            val affordable = save.canAfford(weapon.cost)
            val weaponRow = panelRect(
                328.0,
                54.0,
                fill = if (affordable) RGBA(36, 68, 58, 255) else IslandTheme.Color.ButtonDisabled,
                border = if (affordable) IslandTheme.Color.Accent else RGBA(76, 88, 102, 255)
            ).position(16, y)
            weaponRow.onClick weapon@{
                val failure = weaponCraftFailureMessage(save, weapon.id, weapon.cost)
                if (failure != null) {
                    showPage(save, onCraft, onClosed, facilityPage, failure)
                    return@weapon
                }
                val updated = CraftManager.craftWeapon(save, weapon.id)
                if (updated == null || updated == save) {
                    showPage(save, onCraft, onClosed, facilityPage, "実行できません")
                    return@weapon
                }
                onCraft(updated)
                showPage(updated, onCraft, onClosed, facilityPage, "武器を作成しました")
            }
            label(
                "${weapon.name}  ATK:${weapon.atk}",
                28.0,
                y + 7.0,
                13.0,
                IslandTheme.Color.Text
            )
            label(
                resourceProgressText(weapon.cost, save),
                28.0,
                y + 29.0,
                10.0,
                IslandTheme.Color.MutedText
            )
            y += 58
        }

        label("施設", 22.0, y + 4, 19.0, IslandTheme.Color.Accent)
        label("建てると島の効率アップ", 78.0, y + 11.0, 11.0, IslandTheme.Color.MutedText)
        y += 34
        val facilities = orderedFacilities(save)
        val pageCount = ((facilities.size + FACILITIES_PER_PAGE - 1) / FACILITIES_PER_PAGE).coerceAtLeast(1)
        val currentPage = facilityPage.coerceIn(0, pageCount - 1)
        facilities
            .drop(currentPage * FACILITIES_PER_PAGE)
            .take(FACILITIES_PER_PAGE)
            .forEach { facility ->
                val affordable = save.canAfford(facility.cost)
                val built = facility.id in save.builtFacilityIds
                infoButton(
                    "${facility.name}  ${facilityStateLabel(built, affordable)}",
                    "${compactFacilityEffect(facility.effectDescription)} / ${formatCompactResourceCost(facility.cost)}",
                    16.0,
                    y,
                    328.0,
                    FACILITY_ROW_HEIGHT,
                    affordable && !built,
                    clickableWhenDisabled = true
                ) facility@{
                    val failure = facilityBuildFailureMessage(save, facility.id, facility.cost)
                    if (failure != null) {
                        showPage(save, onCraft, onClosed, currentPage, failure)
                        return@facility
                    }
                    val consumed = save.consumeInventory(facility.cost)
                    if (consumed == null) {
                        showPage(save, onCraft, onClosed, currentPage, "実行できません")
                        return@facility
                    }
                    val updated = consumed.copy(
                        builtFacilityIds = consumed.builtFacilityIds + facility.id
                    )
                    onCraft(updated)
                    showPage(updated, onCraft, onClosed, currentPage, "施設を建設しました")
                }
                y += FACILITY_ROW_STEP
            }
        if (pageCount > 1) {
            actionButton("前へ", 50.0, 540.0, 74.0, 30.0) {
                if (currentPage > 0) {
                    showPage(save, onCraft, onClosed, currentPage - 1)
                }
            }
            label("${currentPage + 1}/$pageCount", 166.0, 547.0, 12.0, IslandTheme.Color.Text)
            actionButton("次へ", 236.0, 540.0, 74.0, 30.0) {
                if (currentPage < pageCount - 1) {
                    showPage(save, onCraft, onClosed, currentPage + 1)
                }
            }
        }
        actionButton("閉じる", 125.0, 580.0, 110.0, 40.0) {
            visible = false
            onClosed()
        }
    }

}

private fun weaponCraftFailureMessage(save: SaveData, weaponId: Int, cost: Map<Int, Int>): String? = when {
    weaponId in save.craftedWeaponIds -> "すでに作成済みです"
    !save.canAfford(cost) -> "素材が足りません"
    GameData.weaponById(weaponId) == null -> "実行できません"
    else -> null
}

private fun facilityBuildFailureMessage(save: SaveData, facilityId: Int, cost: Map<Int, Int>): String? = when {
    facilityId in save.builtFacilityIds -> "すでに建設済みです"
    !save.canAfford(cost) -> "素材が足りません"
    GameData.FACILITIES.none { it.id == facilityId } -> "実行できません"
    else -> null
}

private fun nextWeapons(save: SaveData) =
    GameData.WEAPONS
        .filter { it.id != 0 && it.id !in save.craftedWeaponIds }
        .sortedBy { it.atk }
        .take(3)

private fun orderedFacilities(save: SaveData) =
    GameData.FACILITIES.sortedWith(
        compareBy(
            { it.id in save.builtFacilityIds },
            { !save.canAfford(it.cost) },
            { recommendedFacilityRank(it.id) },
            { it.id }
        )
    )

private fun recommendedFacilityRank(facilityId: Int): Int = when (facilityId) {
    GameData.FAC_FURNACE -> 0
    GameData.FAC_WATCHTOWER -> 1
    GameData.FAC_LUMBER -> 2
    GameData.FAC_BASE -> 3
    else -> 10
}

private fun facilityStateLabel(built: Boolean, affordable: Boolean): String = when {
    built -> "建設済み"
    affordable -> "建設可"
    else -> "素材不足"
}

internal fun formatResourceCost(cost: Map<Int, Int>): String =
    cost.entries.joinToString(" ") { (resourceId, amount) ->
        val name = GameData.resourceById(resourceId)?.name ?: "不明素材($resourceId)"
        "$name×$amount"
    }

internal fun formatCompactResourceCost(cost: Map<Int, Int>): String =
    cost.entries.joinToString(" ") { (resourceId, amount) ->
        val name = GameData.resourceById(resourceId)?.name ?: "不明"
        "$name$amount"
    }

internal fun recipeStatus(cost: Map<Int, Int>, save: SaveData): String =
    if (save.canAfford(cost)) {
        "必要: ${formatResourceCost(cost)}  / 作成可能"
    } else {
        val missing = cost.entries
            .filter { (id, amount) -> save.inventory.getOrDefault(id, 0) < amount }
            .joinToString(" ") { (id, amount) ->
                val name = GameData.resourceById(id)?.name ?: "不明素材"
                "${name}不足(${save.inventory.getOrDefault(id, 0)}/$amount)"
            }
        "必要: ${formatResourceCost(cost)}  / $missing"
    }

internal fun compactRecipeStatus(cost: Map<Int, Int>, save: SaveData): String =
    if (save.canAfford(cost)) {
        "素材OK ${formatResourceCost(cost)}"
    } else {
        "不足 ${missingResourceText(cost, save)}"
    }

private fun missingResourceText(cost: Map<Int, Int>, save: SaveData): String =
    cost.entries
        .filter { (id, amount) -> save.inventory.getOrDefault(id, 0) < amount }
        .joinToString(" ") { (id, amount) ->
            val name = GameData.resourceById(id)?.name ?: "不明素材"
            "${name}${save.inventory.getOrDefault(id, 0)}/$amount"
        }

private fun resourceProgressText(cost: Map<Int, Int>, save: SaveData): String =
    cost.entries.joinToString(" ") { (id, amount) ->
        val name = GameData.resourceById(id)?.name ?: "不明"
        "$name ${save.inventory.getOrDefault(id, 0)}/$amount"
    }

private fun compactFacilityEffect(effect: String): String =
    effect
        .replace("獲得", "")
        .replace("全リスポーン", "リスポーン")
        .replace("増加", "")
