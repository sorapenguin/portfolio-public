package skyisland.data

/**
 * Small versioned codec for the single-row Room payload. IDs are controlled table keys,
 * so a compact delimiter format keeps the Android persistence layer dependency-free.
 */
object SaveCodec {
    fun encode(data: SaveData): String = listOf(
        data.schemaVersion,
        data.tutorialCompleted,
        data.playerLevel,
        data.playerExp,
        encodeMap(data.materials),
        encodeMap(data.items),
        data.ownedEquipments.joinToString(","),
        encodeMap(data.equipmentLevels),
        data.equippedWeaponId.orEmpty(),
        data.equippedArmorId.orEmpty(),
        data.skills.joinToString(",") { "${it.skillId}:${it.proficiency}" },
        data.floor1Cleared,
        data.autoMoveEnabled,
        data.autoSkillEnabled,
        data.lastSavedAt,
        encodeMap(data.skillEnhancements),
        encodeMap(data.skillCrystallizationAttempts),
        data.entranceGoalGuidanceShown,
        data.floor2Cleared,
        data.stamina,
        data.floor3Cleared,
    ).joinToString("|")

    fun decode(payload: String): SaveData {
        val p = payload.split("|")
        if (p.size < 15) return SaveData()
        val sourceSchemaVersion = p[0].toIntOrNull() ?: 1
        return SaveData(
            schemaVersion = if (p.size <= 15) 3 else maxOf(CURRENT_SCHEMA_VERSION, sourceSchemaVersion),
            tutorialCompleted = p[1].toBooleanStrictOrNull() ?: false,
            playerLevel = p[2].toIntOrNull() ?: 1,
            playerExp = p[3].toIntOrNull() ?: 0,
            materials = decodeMap(p[4]),
            items = decodeMap(p[5]),
            ownedEquipments = split(p[6]),
            equipmentLevels = decodeMap(p[7]),
            equippedWeaponId = p[8].ifBlank { null },
            equippedArmorId = p[9].ifBlank { null },
            skills = split(p[10]).mapNotNull {
                val pair = it.split(":")
                pair.getOrNull(0)?.let { id -> SkillData(id, pair.getOrNull(1)?.toIntOrNull() ?: 0) }
            }.ifEmpty { SaveData().skills },
            floor1Cleared = p[11].toBooleanStrictOrNull() ?: false,
            autoMoveEnabled = p[12].toBooleanStrictOrNull() ?: true,
            autoSkillEnabled = p[13].toBooleanStrictOrNull() ?: false,
            lastSavedAt = p[14].toLongOrNull() ?: 0L,
            skillEnhancements = p.getOrNull(15)?.let(::decodeMap).orEmpty(),
            skillCrystallizationAttempts = p.getOrNull(16)?.let(::decodeMap).orEmpty(),
            entranceGoalGuidanceShown = p.getOrNull(17)?.toBooleanStrictOrNull() ?: false,
            floor2Cleared = p.getOrNull(18)?.toBooleanStrictOrNull() ?: false,
            stamina = p.getOrNull(19)?.toIntOrNull() ?: 8,
            floor3Cleared = p.getOrNull(20)?.toBooleanStrictOrNull() ?: false,
        )
    }

    private fun encodeMap(map: Map<String, Int>) = map.entries.joinToString(",") { "${it.key}:${it.value}" }
    private fun decodeMap(value: String) = split(value).associate {
        val pair = it.split(":")
        pair[0] to (pair.getOrNull(1)?.toIntOrNull() ?: 0)
    }
    private fun split(value: String) = value.split(",").filter(String::isNotBlank)

    private const val CURRENT_SCHEMA_VERSION = 5
}
