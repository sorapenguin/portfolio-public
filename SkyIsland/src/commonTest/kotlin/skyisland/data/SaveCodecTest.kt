package skyisland.data

import kotlin.test.Test
import kotlin.test.assertEquals

class SaveCodecTest {
    @Test
    fun roundTripPreservesMvpFields() {
        val data = SaveData(
            tutorialCompleted = true,
            materials = mapOf(Ids.MAT_MIST_CRYSTAL to 7),
            items = mapOf(Ids.ITEM_ESCAPE_STONE to 1),
            ownedEquipments = listOf(Ids.EQ_WIND_SWORD),
            equipmentLevels = mapOf(Ids.EQ_WIND_SWORD to 2),
            equippedWeaponId = Ids.EQ_WIND_SWORD,
            autoSkillEnabled = false,
            skillEnhancements = mapOf(Ids.SKILL_WIND_BLADE to 2),
            skillCrystallizationAttempts = mapOf(Ids.SKILL_MIST_HEAL to 3),
            entranceGoalGuidanceShown = true,
        )
        assertEquals(data, SaveCodec.decode(SaveCodec.encode(data)))
    }

    @Test
    fun decodeKeepsCompatibilityWithVersionOnePayload() {
        val payload = "1|true|2|10|||||||SKILL_WIND_BLADE:4|false|true|false|123"
        val data = SaveCodec.decode(payload)
        assertEquals(3, data.schemaVersion)
        assertEquals(4, data.skills.single().proficiency)
        assertEquals(emptyMap(), data.equipmentLevels)
        assertEquals(emptyMap(), data.skillEnhancements)
        assertEquals(emptyMap(), data.skillCrystallizationAttempts)
        assertEquals(false, data.entranceGoalGuidanceShown)
    }
}
