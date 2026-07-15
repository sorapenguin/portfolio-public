package islanddev.data

data class ResourceDef(
    val id: Int,
    val name: String,
    val zoneIds: List<Int>,
    val respawnSeconds: Long,
    val ideaValue: Int
)

data class WeaponDef(
    val id: Int,
    val name: String,
    val cost: Map<Int, Int>,
    val atk: Int,
    val unlockZoneId: Int
)

data class EnemyDef(
    val id: Int,
    val name: String,
    val zoneId: Int,
    val requiredAtk: Int,
    val dropResourceId: Int?,
    val dropAmount: Int,
    val respawnSeconds: Long,
    val cellCount: Int
)

data class BossDef(
    val id: Int,
    val name: String,
    val fromZoneId: Int,
    val toZoneId: Int,
    val requiredAtk: Int,
    val dropResourceId: Int?,
    val dropAmount: Int
)

data class FacilityDef(
    val id: Int,
    val name: String,
    val cost: Map<Int, Int>,
    val effectDescription: String
)

data class SubZoneDef(
    val id: Int,
    val name: String,
    val parentZoneId: Int,
    val order: Int,
    val ideaCost: Int,
    val waitSeconds: Long
)

object GameData {
    // リソースID
    const val RES_WOOD = 0
    const val RES_STONE = 1
    const val RES_FRUIT = 2
    const val RES_FIBER = 3
    const val RES_SHELL = 4
    const val RES_CLAY = 5
    const val RES_BAMBOO = 6
    const val RES_ORE = 7

    // ドロップ専用素材
    const val RES_JELLY_MEMBRANE = 100
    const val RES_CRAB_SHELL = 101
    const val RES_POISON_LIQUID = 102
    const val RES_BOAR_TUSK = 103
    const val RES_SEA_URCHIN_SPINE = 104
    const val RES_SEA_SNAKE_SCALE = 105
    const val RES_MONKEY_FUR = 106
    const val RES_GIANT_SNAKE_FANG = 107
    const val RES_WOLF_CLAW = 108
    const val RES_EAGLE_FEATHER = 109
    const val RES_TURTLE_SHELL = 200
    const val RES_FANG_CRYSTAL = 201
    const val RES_OCTOPUS_INK = 202
    const val RES_LEOPARD_CLAW = 203
    const val RES_EAGLE_FEATHER_SPECIAL = 204

    // ゾーンID
    const val ZONE_BEACH = 0
    const val ZONE_FOREST = 1
    const val ZONE_REEF = 2
    const val ZONE_DEPTHS = 3
    const val ZONE_SUMMIT = 4

    // 施設ID
    const val FAC_FURNACE = 0
    const val FAC_WATCHTOWER = 1
    const val FAC_LUMBER = 2
    const val FAC_FRUIT_SHELF = 3
    const val FAC_BREAKWATER = 4
    const val FAC_KILN = 5
    const val FAC_BASE = 6
    const val FAC_MINE = 7

    val RESOURCES: List<ResourceDef> = listOf(
        ResourceDef(RES_WOOD,   "木材", listOf(ZONE_BEACH, ZONE_FOREST), 60L,  1),
        ResourceDef(RES_STONE,  "石",   listOf(ZONE_REEF, ZONE_SUMMIT),  120L, 2),
        ResourceDef(RES_FRUIT,  "果実", listOf(ZONE_FOREST),             90L,  3),
        ResourceDef(RES_FIBER,  "繊維", listOf(ZONE_BEACH),              45L,  1),
        ResourceDef(RES_SHELL,  "貝殻", listOf(ZONE_BEACH, ZONE_REEF),   150L, 4),
        ResourceDef(RES_CLAY,   "粘土", listOf(ZONE_REEF),               100L, 2),
        ResourceDef(RES_BAMBOO, "竹",   listOf(ZONE_FOREST),             80L,  2),
        ResourceDef(RES_ORE,    "鉱石", listOf(ZONE_DEPTHS, ZONE_SUMMIT),200L, 5),
        ResourceDef(RES_JELLY_MEMBRANE, "クラゲ膜", emptyList(),          0L,   0),
        ResourceDef(RES_CRAB_SHELL, "カニ甲羅", emptyList(),             0L,   0),
        ResourceDef(RES_POISON_LIQUID, "毒液", emptyList(),              0L,   0),
        ResourceDef(RES_BOAR_TUSK, "猪牙", emptyList(),                  0L,   0),
        ResourceDef(RES_SEA_URCHIN_SPINE, "ウニ棘", emptyList(),         0L,   0),
        ResourceDef(RES_SEA_SNAKE_SCALE, "鱗", emptyList(),              0L,   0),
        ResourceDef(RES_MONKEY_FUR, "猿毛", emptyList(),                 0L,   0),
        ResourceDef(RES_GIANT_SNAKE_FANG, "大蛇の牙", emptyList(),       0L,   0),
        ResourceDef(RES_WOLF_CLAW, "狼爪", emptyList(),                  0L,   0),
        ResourceDef(RES_EAGLE_FEATHER, "大鷹の羽", emptyList(),          0L,   0),
        ResourceDef(RES_TURTLE_SHELL, "大亀の甲羅", emptyList(),         0L,   0),
        ResourceDef(RES_FANG_CRYSTAL, "牙結晶", emptyList(),             0L,   0),
        ResourceDef(RES_OCTOPUS_INK, "大タコ墨", emptyList(),            0L,   0),
        ResourceDef(RES_LEOPARD_CLAW, "大ヒョウ爪", emptyList(),         0L,   0),
        ResourceDef(RES_EAGLE_FEATHER_SPECIAL, "大鷲の羽", emptyList(),  0L,   0),
    )

    val WEAPONS: List<WeaponDef> = listOf(
        WeaponDef(0,  "素手",       emptyMap(),                                          1,  ZONE_BEACH),
        WeaponDef(1,  "木の棒",     mapOf(RES_WOOD to 3),                               5,  ZONE_BEACH),
        WeaponDef(2,  "貝殻ナイフ", mapOf(RES_SHELL to 3, RES_FIBER to 2),              8,  ZONE_BEACH),
        WeaponDef(3,  "竹槍",       mapOf(RES_BAMBOO to 5, RES_FIBER to 3),             16, ZONE_FOREST),
        WeaponDef(4,  "石の剣",     mapOf(RES_STONE to 5, RES_CLAY to 2),               22, ZONE_REEF),
        WeaponDef(5,  "猪牙槍",     mapOf(RES_BOAR_TUSK to 1, RES_BAMBOO to 3),        28, ZONE_FOREST),
        WeaponDef(6,  "鱗の剣",     mapOf(RES_SEA_SNAKE_SCALE to 1, RES_STONE to 5),   38, ZONE_REEF),
        WeaponDef(7,  "鉱石剣",     mapOf(RES_ORE to 3, RES_STONE to 3),               48, ZONE_DEPTHS),
        WeaponDef(8,  "大牙剣",     mapOf(RES_GIANT_SNAKE_FANG to 1, RES_ORE to 2),    60, ZONE_DEPTHS),
        WeaponDef(9,  "狼の爪剣",   mapOf(RES_WOLF_CLAW to 1, RES_ORE to 3),           75, ZONE_SUMMIT),
        WeaponDef(10, "大鷲の刃",   mapOf(RES_EAGLE_FEATHER to 1, RES_ORE to 4),       85, ZONE_SUMMIT),
    )

    val ENEMIES: List<EnemyDef> = listOf(
        EnemyDef(0, "クラゲ",     ZONE_BEACH,  5,  RES_JELLY_MEMBRANE,   1, 2700L, 3),
        EnemyDef(1, "大ガニ",     ZONE_BEACH,  8,  RES_CRAB_SHELL,       1, 3600L, 2),
        EnemyDef(2, "毒ガエル",   ZONE_FOREST, 12, RES_POISON_LIQUID,    1, 3600L, 3),
        EnemyDef(3, "イノシシ",   ZONE_FOREST, 16, RES_BOAR_TUSK,        1, 5400L, 2),
        EnemyDef(4, "大ウニ",     ZONE_REEF,   22, RES_SEA_URCHIN_SPINE, 1, 4500L, 3),
        EnemyDef(5, "ウミヘビ",   ZONE_REEF,   26, RES_SEA_SNAKE_SCALE,  1, 5400L, 2),
        EnemyDef(6, "大サル",     ZONE_DEPTHS, 42, RES_MONKEY_FUR,       1, 7200L, 3),
        EnemyDef(7, "大蛇",       ZONE_DEPTHS, 46, RES_GIANT_SNAKE_FANG, 1, 7200L, 2),
        EnemyDef(8, "岩オオカミ", ZONE_SUMMIT, 55, RES_WOLF_CLAW,        1, 9000L, 3),
        EnemyDef(9, "大鷹",       ZONE_SUMMIT, 70, RES_EAGLE_FEATHER,    1, 9000L, 2),
    )

    val BOSSES: List<BossDef> = listOf(
        BossDef(0, "砂浜の主・大ガメ",  ZONE_BEACH,  ZONE_FOREST,  8,  RES_TURTLE_SHELL,         1),
        BossDef(1, "森の主・大イノシシ", ZONE_FOREST, ZONE_REEF,    18, RES_FANG_CRYSTAL,          1),
        BossDef(2, "岩礁の主・大タコ",  ZONE_REEF,   ZONE_DEPTHS,  35, RES_OCTOPUS_INK,           1),
        BossDef(3, "奥地の主・大ヒョウ", ZONE_DEPTHS, ZONE_SUMMIT,  55, RES_LEOPARD_CLAW,          1),
        BossDef(4, "島の主・大鷲",      ZONE_SUMMIT, -1,           80, RES_EAGLE_FEATHER_SPECIAL, 1),
    )

    val FACILITIES: List<FacilityDef> = listOf(
        FacilityDef(FAC_FURNACE,    "かまど",     mapOf(RES_WOOD to 10, RES_STONE to 5),                    "イデア獲得+20%"),
        FacilityDef(FAC_WATCHTOWER, "物見台",     mapOf(RES_WOOD to 15, RES_FIBER to 5),                    "全リスポーン-15%"),
        FacilityDef(FAC_LUMBER,     "木こり小屋", mapOf(RES_WOOD to 20, RES_FIBER to 10),                   "木材採集量×2"),
        FacilityDef(FAC_FRUIT_SHELF,"果実棚",     mapOf(RES_BAMBOO to 5, RES_FIBER to 8),                   "果実リスポーン-30%"),
        FacilityDef(FAC_BREAKWATER, "防波堤",     mapOf(RES_STONE to 15, RES_CLAY to 8),                    "貝殻セル+2増加"),
        FacilityDef(FAC_KILN,       "窯",         mapOf(RES_CLAY to 10, RES_STONE to 8),                    "粘土・鉱石リスポーン-20%"),
        FacilityDef(FAC_BASE,       "探索拠点",   mapOf(RES_WOOD to 20, RES_STONE to 10, RES_BAMBOO to 10), "ドロップ確率+30%"),
        FacilityDef(FAC_MINE,       "採掘場",     mapOf(RES_ORE to 5, RES_STONE to 20),                     "鉱石採集量×2"),
    )

    val SUB_ZONES: List<SubZoneDef> = listOf(
        SubZoneDef(0,  "砂浜A", ZONE_BEACH,  0, 0,   0L),
        SubZoneDef(1,  "砂浜B", ZONE_BEACH,  1, 50,  1800L),
        SubZoneDef(2,  "砂浜C", ZONE_BEACH,  2, 100, 3600L),
        SubZoneDef(3,  "砂浜D", ZONE_BEACH,  3, 200, 7200L),
        SubZoneDef(4,  "森A",   ZONE_FOREST, 0, 0,   0L),
        SubZoneDef(5,  "森B",   ZONE_FOREST, 1, 50,  1800L),
        SubZoneDef(6,  "森C",   ZONE_FOREST, 2, 100, 3600L),
        SubZoneDef(7,  "森D",   ZONE_FOREST, 3, 200, 7200L),
        SubZoneDef(8,  "岩礁A", ZONE_REEF,   0, 0,   0L),
        SubZoneDef(9,  "岩礁B", ZONE_REEF,   1, 50,  1800L),
        SubZoneDef(10, "岩礁C", ZONE_REEF,   2, 100, 3600L),
        SubZoneDef(11, "岩礁D", ZONE_REEF,   3, 200, 7200L),
        SubZoneDef(12, "奥地A", ZONE_DEPTHS, 0, 0,   0L),
        SubZoneDef(13, "奥地B", ZONE_DEPTHS, 1, 50,  1800L),
        SubZoneDef(14, "奥地C", ZONE_DEPTHS, 2, 100, 3600L),
        SubZoneDef(15, "山頂A", ZONE_SUMMIT, 0, 0,   0L),
        SubZoneDef(16, "山頂B", ZONE_SUMMIT, 1, 50,  1800L),
        SubZoneDef(17, "山頂C", ZONE_SUMMIT, 2, 100, 3600L),
    )

    // マップ列からゾーンID: 列0-19=砂浜, 20-39=森, 40-59=岩礁, 60-79=奥地, 80-99=山頂
    fun columnToZone(col: Int): Int = (col / 20).coerceIn(0, 4)

    fun columnToSubZoneId(col: Int): Int? {
        return SubZoneLayout.atColumn(col)?.subZoneId
    }

    fun resourceById(id: Int): ResourceDef? = RESOURCES.find { it.id == id }
    fun weaponById(id: Int): WeaponDef? = WEAPONS.find { it.id == id }
    fun enemyById(id: Int): EnemyDef? = ENEMIES.find { it.id == id }
    fun bossById(id: Int): BossDef? = BOSSES.find { it.id == id }
    fun facilityById(id: Int): FacilityDef? = FACILITIES.find { it.id == id }
    fun subZoneById(id: Int): SubZoneDef? = SUB_ZONES.find { it.id == id }
}
