package starsaga.data

data class CreatureData(
    val id: Int,
    val name: String,
    val tier: Int,
    val role: CreatureRole,
    val hp: Int,
    val attack: Int,
    val defense: Int,
    val mp: Int,
    val expReward: Int,
    val goldReward: Int,
)

enum class CreatureRole(val code: String) {
    ATCK("ATCK"),
    DEFN("DEFN"),
    AREA("AREA"),
    HEAL("HEAL"),
    LUCK("LUCK"),
}

object CreatureDatabase {
    val t1Creatures: List<CreatureData> = listOf(
        CreatureData(1, "ティンカ", 1, CreatureRole.ATCK, hp = 28, attack = 9, defense = 4, mp = 6, expReward = 12, goldReward = 8),
        CreatureData(2, "ルーミ", 1, CreatureRole.DEFN, hp = 34, attack = 6, defense = 8, mp = 5, expReward = 14, goldReward = 10),
        CreatureData(3, "シマール", 1, CreatureRole.AREA, hp = 26, attack = 7, defense = 5, mp = 9, expReward = 11, goldReward = 9),
        CreatureData(4, "ピクシル", 1, CreatureRole.HEAL, hp = 24, attack = 5, defense = 5, mp = 12, expReward = 10, goldReward = 8),
        CreatureData(5, "スパーラ", 1, CreatureRole.LUCK, hp = 25, attack = 6, defense = 4, mp = 8, expReward = 9, goldReward = 7),
    )

    val t1Boss: CreatureData =
        CreatureData(1001, "星草の主", 1, CreatureRole.DEFN, hp = 96, attack = 15, defense = 10, mp = 0, expReward = 38, goldReward = 45)

    fun get(id: Int): CreatureData? =
        t1Creatures.firstOrNull { it.id == id } ?: if (id == t1Boss.id) t1Boss else null
}
