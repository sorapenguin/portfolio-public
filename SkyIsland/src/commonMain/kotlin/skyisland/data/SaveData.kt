package skyisland.data

data class SkillData(val skillId: String, val proficiency: Int = 0)

data class SaveData(
    val schemaVersion: Int = 5,
    val tutorialCompleted: Boolean = false,
    val playerLevel: Int = 1,
    val playerExp: Int = 0,
    val materials: Map<String, Int> = emptyMap(),
    val items: Map<String, Int> = emptyMap(),
    val ownedEquipments: List<String> = emptyList(),
    val equipmentLevels: Map<String, Int> = emptyMap(),
    val equippedWeaponId: String? = null,
    val equippedArmorId: String? = null,
    val skills: List<SkillData> = listOf(
        SkillData(Ids.SKILL_WIND_BLADE),
        SkillData(Ids.SKILL_MIST_HEAL),
    ),
    val floor1Cleared: Boolean = false,
    val autoMoveEnabled: Boolean = true,
    val autoSkillEnabled: Boolean = false,
    val lastSavedAt: Long = 0L,
    val skillEnhancements: Map<String, Int> = emptyMap(),
    val skillCrystallizationAttempts: Map<String, Int> = emptyMap(),
    val entranceGoalGuidanceShown: Boolean = false,
    val floor2Cleared: Boolean = false,
    val stamina: Int = 8,
    val floor3Cleared: Boolean = false,
)
