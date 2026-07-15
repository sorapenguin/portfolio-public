package islanddev.scene

import islanddev.data.GameData

object MapLabels {
    fun resource(resourceId: Int): String = when (resourceId) {
        GameData.RES_WOOD -> "木"
        GameData.RES_STONE -> "石"
        GameData.RES_FRUIT -> "実"
        GameData.RES_FIBER -> "繊"
        GameData.RES_SHELL -> "貝"
        GameData.RES_CLAY -> "粘"
        GameData.RES_BAMBOO -> "竹"
        GameData.RES_ORE -> "鉱"
        else -> "?"
    }

    fun enemy(enemyId: Int): String = when (enemyId) {
        1 -> "蟹"
        else -> GameData.enemyById(enemyId)?.name?.take(1) ?: "敵"
    }

    fun boss(bossId: Int): String =
        GameData.bossById(bossId)?.name?.take(1) ?: "B"

    fun player(): String = "P"
}
