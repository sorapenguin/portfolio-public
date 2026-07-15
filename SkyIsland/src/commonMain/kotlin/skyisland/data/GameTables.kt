package skyisland.data

object Ids {
    const val FLOOR_01 = "FLOOR_01"
    const val FLOOR_02 = "FLOOR_02"
    const val FLOOR_03 = "FLOOR_03"
    const val SKILL_WIND_BLADE = "SKILL_WIND_BLADE"
    const val SKILL_MIST_HEAL = "SKILL_MIST_HEAL"
    const val SKILL_CLOUD_SHIELD = "SKILL_CLOUD_SHIELD"
    const val SKILL_THUNDER_CLOUD = "SKILL_THUNDER_CLOUD"
    const val SKILL_GUST = "SKILL_GUST"
    const val SKILL_FREEZE_MIST = "SKILL_FREEZE_MIST"
    const val SKILL_STARDUST = "SKILL_STARDUST"
    const val SKILL_LIGHTNING = "SKILL_LIGHTNING"
    const val SKILL_ISLAND_QUAKE = "SKILL_ISLAND_QUAKE"
    const val SKILL_LEVITATE = "SKILL_LEVITATE"
    const val MAT_MIST_CRYSTAL = "MAT_MIST_CRYSTAL"
    const val MAT_WIND_FEATHER = "MAT_WIND_FEATHER"
    const val MAT_THUNDER_SHARD = "MAT_THUNDER_SHARD"
    const val MAT_CLOUD_CORE = "MAT_CLOUD_CORE"
    const val MAT_STAR_SAND = "MAT_STAR_SAND"
    const val MAT_STORM_CORE = "MAT_STORM_CORE"
    const val EQ_MIST_COAT = "EQ_MIST_COAT"
    const val EQ_MIST_AMULET = "EQ_MIST_AMULET"
    const val EQ_WIND_SWORD = "EQ_WIND_SWORD"
    const val EQ_CLOUD_ARMOR = "EQ_CLOUD_ARMOR"
    const val EQ_STORM_SWORD = "EQ_STORM_SWORD"
    const val ITEM_HEAL_HERB = "ITEM_HEAL_HERB"
    const val ITEM_ESCAPE_STONE = "ITEM_ESCAPE_STONE"
    const val ITEM_SKILL_CRYSTAL = "ITEM_SKILL_CRYSTAL"
    const val ENEMY_CLOUD_SLIME = "ENEMY_CLOUD_SLIME"
    const val ENEMY_WIND_BIRD = "ENEMY_WIND_BIRD"
    const val ENEMY_THUNDER_BUG = "ENEMY_THUNDER_BUG"
    const val ENEMY_MIST_JELLYFISH = "ENEMY_MIST_JELLYFISH"
    const val ENEMY_STONE_GOLEM = "ENEMY_STONE_GOLEM"
    const val ENEMY_ROCK_BIRD = "ENEMY_ROCK_BIRD"
    const val BOSS_KASUMI = "BOSS_KASUMI"
    const val BOSS_TEMPE = "BOSS_TEMPE"
    const val BOSS_LUMEN = "BOSS_LUMEN"
}

enum class EquipmentSlot { WEAPON, ARMOR }
enum class Difficulty(val label: String, val dropMultiplier: Double, val attackMultiplier: Double) {
    RELAXED("のんびり", 0.8, 0.9),
    ADVENTURE("冒険", 1.2, 1.1),
}
enum class SkillEffect {
    FRONT_ATTACK,
    HEAL,
    SHIELD,
    AREA_ATTACK,
    KNOCKBACK,
    STUN,
    MULTI_HIT,
    LINE_ATTACK,
    ALL_ATTACK,
    INVINCIBLE,
}
enum class EnemyBehavior { APPROACH, RUSH, STATIC, BOSS }

data class SkillDef(
    val id: String,
    val name: String,
    val effect: SkillEffect,
    val power: Int,
    val crystallizationBaseChance: Double,
    val crystallizationChanceStep: Double,
)
data class MaterialDef(val id: String, val name: String)
data class ItemDef(val id: String, val name: String)
data class EquipmentDef(
    val id: String,
    val name: String,
    val slot: EquipmentSlot,
    val attack: Int = 0,
    val defense: Int = 0,
    val hp: Int = 0,
    val recipe: Map<String, Int>,
)
data class EnemyDef(
    val id: String,
    val name: String,
    val maxHp: Int,
    val attack: Int,
    val exp: Int,
    val behavior: EnemyBehavior,
)

object GameTables {
    val skills = listOf(
        SkillDef(Ids.SKILL_WIND_BLADE, "風刃", SkillEffect.FRONT_ATTACK, 12, 0.05, 0.05),
        SkillDef(Ids.SKILL_MIST_HEAL, "霧癒", SkillEffect.HEAL, 50, 0.05, 0.05),
        SkillDef(Ids.SKILL_CLOUD_SHIELD, "雲盾", SkillEffect.SHIELD, 3, 0.10, 0.10),
        SkillDef(Ids.SKILL_THUNDER_CLOUD, "雷雲", SkillEffect.AREA_ATTACK, 9, 0.10, 0.10),
        SkillDef(Ids.SKILL_GUST, "突風", SkillEffect.KNOCKBACK, 0, 0.05, 0.05),
        SkillDef(Ids.SKILL_FREEZE_MIST, "凍霧", SkillEffect.STUN, 0, 0.05, 0.05),
        SkillDef(Ids.SKILL_STARDUST, "星屑", SkillEffect.MULTI_HIT, 8, 0.05, 0.05),
        SkillDef(Ids.SKILL_LIGHTNING, "落雷", SkillEffect.LINE_ATTACK, 20, 0.15, 0.10),
        SkillDef(Ids.SKILL_ISLAND_QUAKE, "島揺れ", SkillEffect.ALL_ATTACK, 12, 0.20, 0.15),
        SkillDef(Ids.SKILL_LEVITATE, "浮遊", SkillEffect.INVINCIBLE, 3, 0.15, 0.10),
    ).associateBy { it.id }
    val materials = listOf(
        MaterialDef(Ids.MAT_MIST_CRYSTAL, "霧の結晶"),
        MaterialDef(Ids.MAT_WIND_FEATHER, "風の羽"),
        MaterialDef(Ids.MAT_THUNDER_SHARD, "雷の欠片"),
        MaterialDef(Ids.MAT_CLOUD_CORE, "雲の核"),
        MaterialDef(Ids.MAT_STAR_SAND, "星の砂"),
        MaterialDef(Ids.MAT_STORM_CORE, "嵐の核"),
    ).associateBy { it.id }
    val items = listOf(
        ItemDef(Ids.ITEM_HEAL_HERB, "回復草"),
        ItemDef(Ids.ITEM_ESCAPE_STONE, "脱出の石"),
        ItemDef(Ids.ITEM_SKILL_CRYSTAL, "スキルの結晶"),
    ).associateBy { it.id }
    val equipments = listOf(
        EquipmentDef(Ids.EQ_MIST_COAT, "霧の外套", EquipmentSlot.ARMOR, defense = 5, recipe = mapOf(Ids.MAT_MIST_CRYSTAL to 5)),
        EquipmentDef(Ids.EQ_MIST_AMULET, "霧の護符", EquipmentSlot.ARMOR, hp = 15, recipe = mapOf(Ids.MAT_MIST_CRYSTAL to 8)),
        EquipmentDef(Ids.EQ_WIND_SWORD, "風の剣", EquipmentSlot.WEAPON, attack = 8, recipe = mapOf(Ids.MAT_WIND_FEATHER to 3, Ids.MAT_MIST_CRYSTAL to 3)),
        EquipmentDef(Ids.EQ_CLOUD_ARMOR, "雲の盾鎧", EquipmentSlot.ARMOR, defense = 12, hp = 20, recipe = mapOf(Ids.MAT_CLOUD_CORE to 4, Ids.MAT_STAR_SAND to 1)),
        EquipmentDef(Ids.EQ_STORM_SWORD, "嵐の大剣", EquipmentSlot.WEAPON, attack = 25, recipe = mapOf(Ids.MAT_STORM_CORE to 2, Ids.MAT_STAR_SAND to 3)),
    ).associateBy { it.id }
    val enemies = listOf(
        EnemyDef(Ids.ENEMY_CLOUD_SLIME, "雲スライム", 18, 5, 10, EnemyBehavior.APPROACH),
        EnemyDef(Ids.ENEMY_WIND_BIRD, "風鳥", 12, 7, 15, EnemyBehavior.RUSH),
        EnemyDef(Ids.ENEMY_THUNDER_BUG, "雷虫", 20, 8, 20, EnemyBehavior.STATIC),
        EnemyDef(Ids.ENEMY_MIST_JELLYFISH, "霧クラゲ", 10, 4, 10, EnemyBehavior.APPROACH),
        EnemyDef(Ids.ENEMY_STONE_GOLEM, "石ゴーレム", 45, 10, 40, EnemyBehavior.APPROACH),
        EnemyDef(Ids.ENEMY_ROCK_BIRD, "岩鳥", 20, 12, 30, EnemyBehavior.APPROACH),
        EnemyDef(Ids.BOSS_KASUMI, "カスミ", 90, 8, 100, EnemyBehavior.BOSS),
        EnemyDef(Ids.BOSS_TEMPE, "テンペ", 135, 14, 200, EnemyBehavior.BOSS),
        EnemyDef(Ids.BOSS_LUMEN, "ルーメン", 270, 16, 400, EnemyBehavior.BOSS),
    ).associateBy { it.id }
}
