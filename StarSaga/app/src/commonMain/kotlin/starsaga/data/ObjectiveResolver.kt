package starsaga.data

import starsaga.map.T1Area
import starsaga.map.T1MapProgress
import starsaga.map.TileType

data class T1ObjectiveContext(
    val currentAreaId: String,
    val befriendedCreatureIds: Set<Int>,
    val t1BossCleared: Boolean,
    val reachedT1Outpost: Boolean,
    val t1OutpostWarpUnlocked: Boolean,
)

data class T1Objective(
    val main: String,
    val hint: String? = null,
)

data class T1HabitatHint(
    val role: CreatureRole,
    val areaId: String,
    val areaName: String,
    val message: String,
)

object ObjectiveResolver {
    private val routeAreaOrder = listOf(
        T1Area.SETTLEMENT_OUTSKIRTS.id,
        T1Area.STARGRASS_FORK.id,
        T1Area.DEEP_GATE_ROAD.id,
    )

    private val roleHabitatHints = mapOf(
        CreatureRole.ATCK to T1HabitatHint(CreatureRole.ATCK, T1Area.SETTLEMENT_OUTSKIRTS.id, "集落近郊", "集落近郊で見つかりやすい"),
        CreatureRole.DEFN to T1HabitatHint(CreatureRole.DEFN, T1Area.SETTLEMENT_OUTSKIRTS.id, "集落近郊", "集落近郊で見つかりやすい"),
        CreatureRole.AREA to T1HabitatHint(CreatureRole.AREA, T1Area.STARGRASS_FORK.id, "星草の分かれ道", "星草の分かれ道で見つかりやすい"),
        CreatureRole.HEAL to T1HabitatHint(CreatureRole.HEAL, T1Area.STARGRASS_FORK.id, "星草の分かれ道", "星草の分かれ道で見つかりやすい"),
        CreatureRole.LUCK to T1HabitatHint(CreatureRole.LUCK, T1Area.DEEP_GATE_ROAD.id, "深門への道", "深門への道で見つかりやすい"),
    )

    fun resolveT1(context: T1ObjectiveContext): T1Objective {
        val owned = t1OwnedCount(context.befriendedCreatureIds)
        val total = CreatureDatabase.t1Creatures.size
        return when {
            context.t1BossCleared -> T1Objective("第1惑星クリア", "探索や育成を続けられます")
            owned == 0 -> firstStarObjective(context.currentAreaId)
            owned < total -> T1Objective(
                main = "T1スターを仲間にしよう $owned/$total",
                hint = nextHabitatHint(context.befriendedCreatureIds, context.currentAreaId)?.message,
            )
            !context.reachedT1Outpost -> outpostRouteObjective(context.currentAreaId)
            !context.t1OutpostWarpUnlocked -> T1Objective("前哨地の星門を調べよう", "DeepGateにも挑戦できます")
            context.currentAreaId == T1Area.T1_OUTPOST.id -> T1Objective("DeepGateから星草の主に挑もう", "編成と回復を整えよう")
            else -> T1Objective("編成と回復を整えて星草の主に挑もう", "前哨地のDeepGateへ向かおう")
        }
    }

    fun currentObjective(
        companionsCount: Int,
        befriendedCreatureIds: Set<Int>,
        hasDamagedParty: Boolean,
        gold: Int,
        partyCount: Int,
        currentTile: TileType?,
        t1BossCleared: Boolean,
    ): String = when {
        t1BossCleared -> "第1惑星クリア"
        t1OwnedCount(befriendedCreatureIds) == 0 -> "草地で最初のスターを探そう"
        t1OwnedCount(befriendedCreatureIds) < CreatureDatabase.t1Creatures.size ->
            "T1スターを仲間にしよう ${t1OwnedCount(befriendedCreatureIds)}/${CreatureDatabase.t1Creatures.size}"
        t1OwnedCount(befriendedCreatureIds) >= CreatureDatabase.t1Creatures.size -> "編成して星草の主に挑もう"
        currentTile == TileType.Grass -> "草むらで仲間を集めよう"
        companionsCount <= 1 -> "草むらで仲間を増やそう"
        hasDamagedParty -> "回復所で休もう"
        gold >= 10 -> "ショップでポーションを買おう"
        partyCount >= 3 -> "牧場で先頭を入れ替えよう"
        else -> "草むらで仲間を集めよう"
    }

    fun habitatHintForRole(role: CreatureRole): T1HabitatHint? =
        roleHabitatHints[role]

    fun nextHabitatHint(
        befriendedCreatureIds: Set<Int>,
        currentAreaId: String = T1MapProgress.DEFAULT_AREA_ID,
    ): T1HabitatHint? {
        val missing = CreatureDatabase.t1Creatures
            .filterNot { it.id in befriendedCreatureIds }
        if (missing.isEmpty()) return null

        val currentIndex = routeAreaOrder.indexOf(currentAreaId).takeIf { it >= 0 } ?: 0
        return missing
            .mapNotNull { creature -> roleHabitatHints[creature.role]?.let { creature to it } }
            .sortedWith(
                compareBy<Pair<CreatureData, T1HabitatHint>> {
                    val areaIndex = routeAreaOrder.indexOf(it.second.areaId).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE
                    if (areaIndex >= currentIndex) areaIndex - currentIndex else areaIndex + routeAreaOrder.size - currentIndex
                }.thenBy { it.first.id },
            )
            .firstOrNull()
            ?.second
    }

    fun t1OwnedCount(befriendedCreatureIds: Set<Int>): Int =
        CreatureDatabase.t1Creatures.count { it.id in befriendedCreatureIds }

    fun t1StatusText(befriendedCreatureIds: Set<Int>): String {
        val owned = t1OwnedCount(befriendedCreatureIds)
        val missing = CreatureDatabase.t1Creatures
            .filterNot { it.id in befriendedCreatureIds }
            .joinToString(",") { it.name }
        return if (missing.isEmpty()) {
            "仲間: $owned/${CreatureDatabase.t1Creatures.size}  育成や探索を続けられます"
        } else {
            "仲間: $owned/${CreatureDatabase.t1Creatures.size}  未: ${shortMissing(missing)}"
        }
    }

    private fun shortMissing(text: String): String =
        if (text.length <= 12) text else text.take(12)

    private fun firstStarObjective(currentAreaId: String): T1Objective =
        if (currentAreaId == T1Area.FIRST_TOWN.id) {
            T1Objective("東の街道から草地へ向かおう", "集落近郊で最初のスターを探そう")
        } else {
            T1Objective("集落近郊で最初のスターを探そう", "草地でスターを探そう")
        }

    private fun outpostRouteObjective(currentAreaId: String): T1Objective =
        when (currentAreaId) {
            T1Area.DEEP_GATE_ROAD.id -> T1Objective("深門への道を東へ進もう", "深門前哨地を目指そう")
            T1Area.T1_OUTPOST.id -> T1Objective("前哨地の星門を調べよう")
            else -> T1Objective("深門前哨地を目指そう", "東の街道を進もう")
        }
}
